package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.audio.AppPipeline;
import io.github.Nyeksenn.grooveboat.audio.SongQueue;
import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import io.github.Nyeksenn.grooveboat.gobjects.MediaSubsonic;
import org.gnome.glib.GLib;
import org.gnome.gtk.Box;
import org.gnome.gtk.Image;
import org.gnome.gtk.Label;
import org.gnome.gtk.MediaControls;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.ExecutorService;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/music-player.ui", name = "MusicPlayer")
@RegisteredType(name = "MusicPlayer")
public class MusicPlayer extends Box {
    private static final Logger logger = LoggerFactory.getLogger(MusicPlayer.class);

    @GtkChild(name = "player-album-picture")
    public Image playerAlbumPicture;

    @GtkChild(name = "player-song-name")
    public Label playerSongName;

    @GtkChild(name = "player-album-artist")
    public Label playerAlbumArtist;

    @GtkChild(name = "media-controls")
    public MediaControls mediaControls;

    private Subsonic subsonic;

    private ExecutorService executor;

    private AppPipeline pipeline;

    private final SongQueue songQueue = SongQueue.getInstance();

    public void initClient() {
        if (this.subsonic == null && executor == null) {
            var window = (GrooveboatWindow) getRoot();
            var app = (GrooveboatApp) window.getApplication();

            this.executor = app.executor;
            this.pipeline = new AppPipeline();
            this.subsonic = app.subsonic;

            songQueue.connect("queue-changed", (SongQueue.QueueChanged) this::queueChanged);

            if (songQueue.isEmpty()) {
                var initSong = new QSongModel().findList().getFirst();
                songQueue.addSong(initSong);
            }
        }
    }

    // Make sure that no two threads try to change pipeline in parallel
    public void queueChanged() {
        var oldStream = (MediaSubsonic) this.mediaControls.getMediaStream();

        executor.submit(() -> {
            if (oldStream != null) {
                oldStream.close();
            }

            var nextSong = this.songQueue.peekSong();
            var stream = new MediaSubsonic(
                    nextSong.getSongId(),
                    nextSong.getDuration().getSeconds(),
                    subsonic, pipeline, executor
            );
            GLib.idleAddOnce(() -> this.mediaControls.setMediaStream(stream));
            stream.initializeStream();
        });
    }

    @InstanceInit
    public void init() {
        this.onRealize(this::initClient);
        this.onUnrealize(this::destroy);
    }

    public MusicPlayer() {
    }

    public MusicPlayer(MemorySegment address) {
        super(address);
    }

    public void destroy() {
        logger.info("Closing Music Player");
        var stream = (MediaSubsonic) this.mediaControls.getMediaStream();
        if (stream != null) {
            stream.close();
        }
    }
}

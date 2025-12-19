package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.audio.AppPipeline;
import io.github.Nyeksenn.grooveboat.audio.QueueStore;
import io.github.Nyeksenn.grooveboat.gobjects.MediaSubsonic;
import io.github.Nyeksenn.grooveboat.gobjects.RoundedImage;
import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import org.gnome.gtk.Box;
import org.gnome.gtk.Label;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/music-player.ui", name = "MusicPlayer")
@RegisteredType(name = "MusicPlayer")
public class MusicPlayer extends Box {
    private static final Logger logger = LoggerFactory.getLogger(MusicPlayer.class);

    @GtkChild(name = "player-album-picture")
    public RoundedImage playerAlbumPicture;

    @GtkChild(name = "player-song-name")
    public Label playerSongName;

    @GtkChild(name = "player-album-artist")
    public Label playerAlbumArtist;

    @GtkChild(name = "music-controls")
    public MusicControls mediaControls;

    @GtkChild(name = "song-queue")
    public SongQueue songQueue;

    private AppPipeline appPipeline;

    private GrooveboatApp app;

    private MediaSubsonic stream;

    private void initializeMediaStream(SongListItem songItem) {
        if (songItem != null) {
            this.mediaControls.setMediaStream(null);

            app.closeStream();
            this.stream = app.createStream(songItem.getSongId(), songItem.getDuration().getSeconds() * 1000000L, songItem.getContentType());
            this.mediaControls.setMediaStream(stream);
            this.playerAlbumArtist.setLabel(songItem.getArtistName());
            this.playerSongName.setLabel(songItem.getSongName());
            this.playerAlbumPicture.setTexture(songItem.getCoverArt());
        }
    }

    @InstanceInit
    public void init() {
        this.onRealize(() -> {
            var win = (GrooveboatWindow) getRoot();
            assert win != null;

            var app = (GrooveboatApp) win.getApplication();
            this.app = app;

            if (app.getStream() != null) {
                this.stream = app.getStream();
            }

            app.queueStore.connect("initialize-song", (QueueStore.InitializeSong) this::initializeMediaStream);

            this.appPipeline = app.pipeline;
        });
    }

    public MusicPlayer() {
    }

    public MusicPlayer(MemorySegment address) {
        super(address);
    }
}

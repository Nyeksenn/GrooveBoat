package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.audio.AppPipeline;
import io.github.Nyeksenn.grooveboat.gobjects.MediaSubsonic;
import io.github.Nyeksenn.grooveboat.gobjects.RoundedImage;
import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import org.freedesktop.gstreamer.gst.Bus;
import org.freedesktop.gstreamer.gst.Message;
import org.freedesktop.gstreamer.gst.MessageType;
import org.freedesktop.gstreamer.gst.State;
import org.gnome.gio.SimpleAction;
import org.gnome.glib.GError;
import org.gnome.glib.Variant;
import org.gnome.gtk.Box;
import org.gnome.gtk.Label;
import org.javagi.base.Out;
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

    public boolean handleBusMessage(Bus bus, Message msg) {
        if (msg.readType().contains(MessageType.EOS)) {
            logger.info("End of Stream");
            activateActionIfExists("win.play-next", Variant.boolean_(mediaControls.loopButton.getActive()));
        } else if (msg.readType().contains(MessageType.ERROR)) {
            Out<GError> error = new Out<>();
            Out<String> debug = new Out<>();
            msg.parseError(error, debug);

            logger.warn("Error: {}", error.get().readMessage());

            appPipeline.setState(State.NULL);
            appPipeline.setState(State.PAUSED);
        } else if (msg.readType().contains(MessageType.STATE_CHANGED)) {
            Out<State> prev = new Out<>();
            Out<State> curr = new Out<>();
            msg.parseStateChanged(prev, curr, null);
            if (prev.get().equals(State.PAUSED) && curr.get().equals(State.PLAYING)) {
                stream.startTimestampUpdate();
            } else if (curr.get().equals(State.PAUSED) && prev.get().equals(State.PLAYING)) {
                stream.cancelTimestampUpdate();
            }
        }

        return true;
    }

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

            var togglePlayingAction = new SimpleAction("toggle-play", null);
            togglePlayingAction.onActivate((_) -> {
                if (stream != null) {
                    stream.setPlaying(!stream.getPlaying());
                }
            });
            win.addAction(togglePlayingAction);
            songQueue.connect("initialize-song", (SongQueue.InitializeSong) this::initializeMediaStream);

            this.appPipeline = app.pipeline;

            var bus = appPipeline.getBus();
            bus.addWatch(0, this::handleBusMessage);
        });
    }

    public MusicPlayer() {
    }

    public MusicPlayer(MemorySegment address) {
        super(address);
    }
}

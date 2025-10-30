package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.audio.AppPipeline;
import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.Nyeksenn.grooveboat.gobjects.MediaSubsonic;
import org.gnome.gtk.Box;
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

    @GtkChild(name = "media-controls")
    public MediaControls mediaControls;

    private Subsonic subsonic;

    private MediaSubsonic subsonicStream;

    private ExecutorService executor;

    private AppPipeline pipeline;

    public void initClient() {
        if (this.subsonic == null && executor == null) {
            var window = (GrooveboatWindow) getRoot();
            var app = (GrooveboatApp) window.getApplication();

            this.executor = app.executor;

            this.pipeline = new AppPipeline();

            this.subsonic = app.subsonic;
            subsonicStream = new MediaSubsonic("5803cc5962db50805941225a2920dd1f", 194, subsonic, pipeline, executor);
            this.mediaControls.setMediaStream(subsonicStream);

            subsonicStream.prepare();
        }
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
        this.subsonicStream.close();
        this.mediaControls.setMediaStream(null);
    }
}

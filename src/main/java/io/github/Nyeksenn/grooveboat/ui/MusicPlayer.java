package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.Nyeksenn.grooveboat.gobjects.MediaSubsonic;
import io.github.jwharm.javagi.gobject.annotations.InstanceInit;
import io.github.jwharm.javagi.gobject.annotations.RegisteredType;
import io.github.jwharm.javagi.gtk.annotations.GtkChild;
import io.github.jwharm.javagi.gtk.annotations.GtkTemplate;
import org.gnome.gtk.Box;
import org.gnome.gtk.MediaControls;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/music-player.ui", name = "MusicPlayer")
@RegisteredType(name = "MusicPlayer")
public class MusicPlayer extends Box {
    @GtkChild(name = "media-controls")
    public MediaControls mediaControls;

    private Subsonic subsonic;

    public void initClient() {
        if (this.subsonic == null) {
            var window = (GrooveboatWindow) getRoot();
            var app = (GrooveboatApp) window.getApplication();
            this.subsonic = app.subsonic;
            var subsonicStream = new MediaSubsonic("5803cc5962db50805941225a2920dd1f", subsonic);
            this.mediaControls.setMediaStream(subsonicStream);
        }
    }

    @InstanceInit
    public void init() {
        this.onRealize(this::initClient);
    }

    public MusicPlayer() {
    }

    public MusicPlayer(MemorySegment address) {
        super(address);
    }
}

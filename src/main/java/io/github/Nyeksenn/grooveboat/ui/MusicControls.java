package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.gtk.Box;
import org.gnome.gtk.MediaStream;
import org.gnome.gtk.Scale;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.Property;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/music-controls.ui", name = "MusicControls")
public class MusicControls extends Box {
    private MediaStream mediaStream;

    @Property
    public MediaStream getMediaStream() {
        return mediaStream;
    }

    @Property
    public void setMediaStream(MediaStream mediaStream) {
        this.mediaStream = mediaStream;
    }

    @GtkChild(name = "playback-position-scale")
    public Scale playbackPositionScale;

    @InstanceInit
    public void init() {
    }

    public MusicControls() {
    }

    public MusicControls(MemorySegment address) {
        super(address);
    }
}

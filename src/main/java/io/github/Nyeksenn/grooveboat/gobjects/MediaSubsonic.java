package io.github.Nyeksenn.grooveboat.gobjects;

import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.jwharm.javagi.gobject.annotations.InstanceInit;
import io.github.jwharm.javagi.gobject.annotations.Property;
import org.gnome.gtk.MediaStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaSubsonic extends MediaStream {
    private static final Logger logger = LoggerFactory.getLogger(MediaSubsonic.class);

    private Subsonic subsonic;
    private String songId;


    @Property(skip = true)
    public Subsonic getSubsonic() {
        return subsonic;
    }

    @Property(skip = true)
    public void setSubsonic(Subsonic subsonic) {
        this.subsonic = subsonic;
    }

    public String getSongId() {
        return songId;
    }

    public void setSongId(String songId) {
        this.songId = songId;
    }

    // These are real methods

    @Override
    public void pause() {
        logger.info("Pause was called");
    }

    @Override
    public boolean play() {
        logger.info("Play was called");
        return true;
    }

    @Override
    public void seek(long timestamp) {
        asParent().seek(timestamp);
        logger.info("Seek was called with timestamp: {}", timestamp);
    }


    public MediaSubsonic() {
    }

    @InstanceInit
    public void init() {
        this.streamPrepared(true, false, true, 1000);
        this.updateAudio(false, 100);
        this.update(50);
    }

    public MediaSubsonic(String songId, Subsonic subsonic) {
        this.songId = songId;
        this.subsonic = subsonic;
    }
}

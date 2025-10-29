package io.github.Nyeksenn.grooveboat.gobjects;

import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.jwharm.javagi.gobject.annotations.InstanceInit;
import io.github.jwharm.javagi.gobject.annotations.Property;
import org.gnome.gtk.MediaStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaSubsonic extends MediaStream {
    private static final Logger logger = LoggerFactory.getLogger(MediaSubsonic.class);

    private boolean playing;
    private long duration;
    private boolean ended;
    private boolean loop;
    private boolean muted;
    private long timestamp;
    private double volume;
    private boolean hasAudio;
    private boolean hasVideo;
    private boolean prepared;
    private boolean seekable;
    private boolean seeking;
    private String songId;

    private Subsonic subsonic;

    // Getters and setters
    @Override
    public long getDuration() {
        return this.duration;
    }

    @Override
    public boolean getEnded() {
        return this.ended;
    }

    @Override
    public boolean getLoop() {
        return this.loop;
    }

    @Override
    public boolean getMuted() {
        return this.muted;
    }

    @Override
    public boolean getPlaying() {
        return this.playing;
    }

    @Override
    public long getTimestamp() {
        return this.timestamp;
    }

    @Override
    public double getVolume() {
        return this.volume;
    }

    @Override
    public boolean hasAudio() {
        return this.hasAudio;
    }

    @Override
    public boolean hasVideo() {
        return this.hasVideo;
    }

    @Override
    public boolean isPrepared() {
        return this.prepared;
    }

    @Override
    public boolean isSeekable() {
        return this.seekable;
    }

    @Override
    public boolean isSeeking() {
        return this.seeking;
    }

    @Override
    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    @Override
    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    @Override
    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    @Override
    public void setVolume(double volume) {
        this.volume = volume;
    }

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
        this.setPlaying(false);
        logger.info("Pause was called");
    }

    @Override
    public boolean play() {
        this.setPlaying(true);
        logger.info("Play was called");
        return true;
    }

    @Override
    public void seek(long timestamp) {
        logger.info("Seek was called with timestamp: {}", timestamp);
    }

    @Override
    public void seekFailed() {
        logger.info("Seek failed was called");
    }

    @Override
    public void seekSuccess() {
        logger.info("Seek success was called");
    }

    @Override
    public void streamEnded() {
        logger.info("Stream ended was called");
    }

    @Override
    public void streamPrepared(boolean hasAudio, boolean hasVideo, boolean seekable, long duration) {
        asParent().streamPrepared(hasAudio, hasVideo, seekable, duration);
        logger.info("Stream Prepared was called with hasAudio: {} hasVideo: {} seekable: {} duration: {}", hasAudio, hasVideo, seekable, duration);
    }

    @Override
    public void streamUnprepared() {
        logger.info("Stream unprepared was called");
    }

    @Override
    public void update(long timestamp) {
        this.timestamp = timestamp;
        logger.info("Update was called with timestamp {}", timestamp);
    }

    @Override
    protected void updateAudio(boolean muted, double volume) {
        this.muted = muted;
        this.volume = volume;
        logger.info("Update audio was called with muted: {} volume: {}", muted, volume);
    }


    public MediaSubsonic() {
    }

    @InstanceInit
    public void init() {
        this.streamPrepared(true, false, true, 100);
        this.updateAudio(false, 100);
    }

    public MediaSubsonic(String songId, Subsonic subsonic) {
        this.songId = songId;
        this.subsonic = subsonic;
    }
}

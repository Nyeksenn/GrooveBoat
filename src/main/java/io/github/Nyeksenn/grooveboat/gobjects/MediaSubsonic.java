package io.github.Nyeksenn.grooveboat.gobjects;

import io.github.Nyeksenn.grooveboat.audio.AppPipeline;
import io.github.Nyeksenn.grooveboat.audio.SongStreamer;
import io.github.Nyeksenn.grooveboat.client.Subsonic;
import org.freedesktop.gstreamer.gst.State;
import org.gnome.gtk.MediaStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

public class MediaSubsonic extends MediaStream implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MediaSubsonic.class);

    private final Subsonic subsonic;
    private final String songId;
    private final long duration;
    private final AppPipeline pipeline;
    private final ExecutorService executor;
    private SongStreamer currentStreamer;

    // These are the virtual methods

    @Override
    public void pause() {
        pipeline.pipeline.setState(State.PAUSED);
    }

    @Override
    public boolean play() {
        pipeline.pipeline.setState(State.PLAYING);
        return true;
    }

    @Override
    public void seek(long timestamp) {
        asParent().seek(timestamp);
        logger.info("Seek was called with timestamp: {}", timestamp);
    }

    @Override
    protected void updateAudio(boolean muted, double volume) {
        asParent().setVolume(volume);
        asParent().setMuted(muted);
        logger.warn("Update audio was called");
    }

    // These are the convenience overrides
    @Override
    public void streamPrepared(boolean hasAudio, boolean hasVideo, boolean seekable, long duration) {
        this.currentStreamer = new SongStreamer(this.pipeline.appsrc, this.songId, this.subsonic);
        var future = executor.submit(this.currentStreamer);
        asParent().streamPrepared(hasAudio, hasVideo, seekable, duration);
    }

    @Override
    public void streamUnprepared() {
        asParent().streamUnprepared();
    }

    public void prepare() {
        this.streamPrepared(true, false, false, this.duration);
    }

    private void feedData(int i) {
        this.currentStreamer.resume();
    }

    private void stopData() {
        this.currentStreamer.pause();
    }
    // The rest


    public MediaSubsonic(String songId, long duration, Subsonic subsonic, AppPipeline pipeline, ExecutorService executor) {
        this.songId = songId;
        this.subsonic = subsonic;
        this.pipeline = pipeline;
        this.duration = duration;
        this.executor = executor;

        this.pipeline.appsrc.onEnoughData(this::stopData);
        this.pipeline.appsrc.onNeedData(this::feedData);
    }

    @Override
    public void close() {
        logger.info("Closing MediaSubsonic");
        this.currentStreamer.stop();
        this.pipeline.pipeline.setState(State.NULL);
    }
}

package io.github.Nyeksenn.grooveboat.gobjects;

import io.github.Nyeksenn.grooveboat.audio.AppPipeline;
import io.github.Nyeksenn.grooveboat.audio.SongStreamer;
import io.github.Nyeksenn.grooveboat.client.Subsonic;
import org.freedesktop.gstreamer.gst.ClockTime;
import org.freedesktop.gstreamer.gst.Event;
import org.freedesktop.gstreamer.gst.State;
import org.freedesktop.gstreamer.gst.StateChangeReturn;
import org.gnome.glib.GLib;
import org.gnome.gtk.MediaStream;
import org.javagi.base.Out;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

public final class MediaSubsonic extends MediaStream implements AutoCloseable {
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
        // TODO: Implement actual volume change
        asParent().setVolume(1.0);
        asParent().setMuted(false);
        logger.warn("Update audio was called");
    }

    public void prepare() {
        while (true) {
            var state = new Out<State>();
            var pending = new Out<State>();
            var stateChangeReturn = this.pipeline.appsrc.getState(state, pending, new ClockTime(1));

            if ((state.get() == State.PAUSED) && stateChangeReturn == StateChangeReturn.SUCCESS) {
                break;
            }
        }

        this.currentStreamer = new SongStreamer(this.pipeline.appsrc, this.songId, this.subsonic);
        executor.submit(this.currentStreamer);

        updateAudio(false, 1.0);
        streamPrepared(true, false, false, this.duration);
    }

    private void feedData(int i) {
        logger.info("Need data");
        this.currentStreamer.resume();
    }

    private void stopData() {
        logger.info("Got enough data");
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

    // This stops playback and clears pipeline
    @Override
    public void close() {
        logger.info("Destroying Stream");

        this.pipeline.appsrc.endOfStream();

        // Stop sending buffers
        this.currentStreamer.stop();

        var state = new Out<State>();
        this.pipeline.appsrc.getState(state, null, new ClockTime(1));

        if (state.get() == State.PLAYING) {
            // Signal EOS if not PLAYING
            this.pipeline.appsrc.emitEndOfStream();
        } else {
            // Flush pipeline
            this.pipeline.appsrc.sendEvent(Event.flushStart());
            this.pipeline.appsrc.sendEvent(Event.flushStop(true));
        }
        streamUnprepared();
    }
}

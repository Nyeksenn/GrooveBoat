package io.github.Nyeksenn.grooveboat.gobjects;

import io.github.Nyeksenn.grooveboat.audio.AppPipeline;
import io.github.Nyeksenn.grooveboat.audio.SongStreamer;
import io.github.Nyeksenn.grooveboat.client.Subsonic;
import org.freedesktop.gstreamer.app.AppSrc;
import org.freedesktop.gstreamer.app.AppSrcCallbacks;
import org.freedesktop.gstreamer.gst.*;
import org.gnome.gtk.MediaStream;
import org.javagi.gobject.annotations.InstanceInit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.util.concurrent.ExecutorService;

import static org.freedesktop.gstreamer.gst.Gst.CLOCK_TIME_NONE;

public final class MediaSubsonic extends MediaStream implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MediaSubsonic.class);

    private final Subsonic subsonic;
    private final String songId;
    private final long duration;
    private final AppPipeline pipeline;
    private final ExecutorService executor;
    private SongStreamer currentStreamer;
    public InputStream socket;
    public volatile boolean enoughData = false;
    private static final int BUFFER_SIZE = 8192;
    private final byte[] buffer = new byte[BUFFER_SIZE];

    // These are the virtual methods

    @Override
    public void pause() {
        pipeline.pipeline.setState(State.PAUSED);
    }

    @Override
    public boolean play() {
        pipeline.pipeline.setState(State.PLAYING);
        logger.info("Now playing");
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

    public void initializeStream() {
        var response = this.subsonic.getSongStream(songId);
        if (response != null) {
            socket = response.body();
            updateAudio(false, 1.0);
            // This takes microseconds
            streamPrepared(true, false, false, this.duration * 1000000);
        }
    }

    // This stops playback and clears pipeline
    @Override
    public void close() {
        logger.info("Destroying Stream");
        try {
            socket.close();
        } catch (IOException e) {
            logger.warn("Couldn't close stream socket");
        }
        this.pipeline.appsrc.endOfStream();
        this.pipeline.appsrc.seek(1.0, Format.TIME, SeekFlags.FLUSH, SeekType.SET, 0L, SeekType.NONE, CLOCK_TIME_NONE.getValue());
        streamUnprepared();
    }

    private void needDataHandler(AppSrc appSrc, int i) {
        logger.info("Need data");
        enoughData = false;
        executor.submit(() -> {
            while (!enoughData) {
                try {
                    var len = socket.readNBytes(buffer, 0, BUFFER_SIZE);
                    var flowReturn = appSrc.pushBuffer(Buffer.memdup(buffer));
                    if (flowReturn == FlowReturn.FLUSHING) return;
                    if ((flowReturn == FlowReturn.OK) && (len < BUFFER_SIZE)) {
                        appSrc.endOfStream();
                        appSrc.seek(1.0, Format.TIME, SeekFlags.FLUSH, SeekType.SET, 0L, SeekType.NONE, CLOCK_TIME_NONE.getValue());
                        return;
                    }
                } catch (IOException e) {
                    logger.warn("Couldn't read from socket");
                }
            }
        });
    }

    private void enoughDataHandler(AppSrc appSrc) {
        logger.info("Enough Data");
        this.enoughData = true;
    }
    // The rest

    public MediaSubsonic(String songId, long duration, Subsonic subsonic, AppPipeline pipeline, ExecutorService executor) {
        this.songId = songId;
        this.subsonic = subsonic;
        this.pipeline = pipeline;
        this.duration = duration;
        this.executor = executor;

        // This takes nanoseconds
        this.pipeline.appsrc.setDuration(new ClockTime(this.duration * 1000000000));

        var enoughDataCallback = (AppSrcCallbacks.EnoughDataCallback) this::enoughDataHandler;
        var needDataCallback = (AppSrcCallbacks.NeedDataCallback) this::needDataHandler;
        var callbacks = new AppSrcCallbacks();
        callbacks.overrideEnoughData(enoughDataCallback, Arena.ofAuto());
        callbacks.overrideNeedData(needDataCallback, Arena.ofAuto());
        this.pipeline.appsrc.setCallbacks(callbacks, null);
    }
}

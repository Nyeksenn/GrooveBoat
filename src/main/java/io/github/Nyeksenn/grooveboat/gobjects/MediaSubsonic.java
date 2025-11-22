package io.github.Nyeksenn.grooveboat.gobjects;

import io.github.Nyeksenn.grooveboat.Utils;
import io.github.Nyeksenn.grooveboat.audio.AppPipeline;
import io.github.Nyeksenn.grooveboat.client.Subsonic;
import org.freedesktop.gstreamer.app.AppSrc;
import org.freedesktop.gstreamer.app.AppSrcCallbacks;
import org.freedesktop.gstreamer.audio.StreamVolumeFormat;
import org.freedesktop.gstreamer.gst.Buffer;
import org.freedesktop.gstreamer.gst.Caps;
import org.freedesktop.gstreamer.gst.FlowReturn;
import org.freedesktop.gstreamer.gst.State;
import org.gnome.gtk.MediaStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static io.github.Nyeksenn.grooveboat.audio.AppPipeline.BUFFER_SIZE;

public final class MediaSubsonic extends MediaStream {
    private static final Logger logger = LoggerFactory.getLogger(MediaSubsonic.class);

    private final Subsonic subsonic;
    private final String songId;
    private final long duration;
    private final AppPipeline pipeline;
    public AppSrc appSrc;
    private final ExecutorService executor;
    public InputStream socket;
    private final byte[] buffer = new byte[BUFFER_SIZE];
    private final String contentType;
    private final Arena instanceArena = Arena.ofConfined();
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture future;

    private final Lock enoughDataLock = new ReentrantLock();
    public volatile boolean enoughData = false;
    public volatile boolean pushingData = false;

    // These are the virtual methods
    @Override
    public void pause() {
        pipeline.setState(State.PAUSED);
    }

    @Override
    public boolean play() {
        pipeline.setState(State.PLAYING);
        logger.info("Now playing");
        return true;
    }

    @Override
    public void seek(long timestamp) {
        logger.info("Seek was called with timestamp: {}", timestamp);
        synchronized (enoughDataLock) {
            enoughData = true;
            var seekSuccess = pipeline.seek(timestamp);
            if (seekSuccess) {
                seekSuccess();
            } else {
                seekFailed();
            }
        }
    }

    @Override
    protected void updateAudio(boolean muted, double volume) {
        var streamVolume = pipeline.getStreamVolume();
        if (streamVolume != null) {
            try {
                // I need this because Element is constructed in native code
                streamVolume.setVolume(StreamVolumeFormat.LINEAR, volume);
                streamVolume.setMute(muted);
            } catch (Exception e) {
                logger.warn(e.getMessage());
            }
        }
    }

    public void initializeStream() {
        logger.info("Initializing stream");
        Caps caps = null;
        if (Utils.hasMapping(contentType)) {
            var gstCt = Utils.toGStreamerType(contentType);
            caps = Caps.emptySimple(gstCt);
        }

        appSrc.setCapabilities(caps);
        pipeline.setState(State.PAUSED);

        // This takes microseconds
        streamPrepared(true, false, true, this.duration * 1000000);
        updateAudio(false, 1.0);
        this.future = scheduler.scheduleAtFixedRate(this::updatePosition, 0L, 50L, TimeUnit.MILLISECONDS);
    }

    // This stops playback and clears pipeline
    public void destroyStream() {
        logger.info("Destroying Stream");
        try {
            socket.close();
        } catch (IOException e) {
            logger.warn("Couldn't close stream socket");
        }
        this.appSrc.endOfStream();
        var removed = pipeline.removeAppSrc(appSrc);
        pipeline.setState(State.READY);

        instanceArena.close();
        future.cancel(true);
        streamUnprepared();
    }

    public void needDataHandler(AppSrc src, int i) {
        if (!pushingData) {
            enoughData = false;
            executor.submit(() -> {
                while (true) {
                    synchronized (enoughDataLock) {
                        pushingData = true;
                        if (enoughData) {
                            pushingData = false;
                            return;
                        }
                        try {
                            var len = socket.readNBytes(buffer, 0, BUFFER_SIZE);
                            var flowReturn = src.pushBuffer(Buffer.memdup(buffer));
                            if (flowReturn == FlowReturn.FLUSHING) return;
                            if ((flowReturn == FlowReturn.OK) && (len < BUFFER_SIZE)) {
                                src.endOfStream();
                                pushingData = false;
                                return;
                            }
                        } catch (IOException e) {
                            logger.warn("Couldn't read from socket");
                            pushingData = false;
                            return;
                        }
                    }
                }
            });
        }
    }

    public void enoughDataHandler(AppSrc src) {
        synchronized (enoughDataLock) {
            this.enoughData = true;
        }
    }

    public boolean seekDataHandler(AppSrc src, long offset) {
        synchronized (enoughDataLock) {
            enoughData = true;

            logger.info("Seek data");
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.warn("Couldn't close socket");
                }
            }

            try {
                var response = this.subsonic.seekSongStream(songId, offset * 1000000000);
                this.socket = response.body();
                return true;
            } catch (IOException e) {
                logger.warn("Couldn't seek stream: {}", e.getMessage());
                return false;
            }
        }
    }

    public void updatePosition() {
        var pos = pipeline.queryPosition();
        if (pos >= 0L) {
            if (this.isPrepared()) {
                update(pos);
            }
        } else {
            logger.info("Couldn't read pos");
        }
    }

    // The rest

    public MediaSubsonic(String songId, long duration, Subsonic subsonic, AppPipeline pipeline, ExecutorService executor, String contentType, ScheduledExecutorService scheduler) {
        this.songId = songId;
        this.subsonic = subsonic;
        this.duration = duration;
        this.pipeline = pipeline;
        this.executor = executor;
        this.contentType = contentType;
        this.scheduler = scheduler;

        var enoughDataCallback = (AppSrcCallbacks.EnoughDataCallback) this::enoughDataHandler;
        var needDataCallback = (AppSrcCallbacks.NeedDataCallback) this::needDataHandler;
        var seekDataCallback = (AppSrcCallbacks.SeekDataCallback) this::seekDataHandler;
        var callbacks = new AppSrcCallbacks(Arena.global());
        callbacks.overrideEnoughData(enoughDataCallback, instanceArena);
        callbacks.overrideNeedData(needDataCallback, instanceArena);
        callbacks.overrideSeekData(seekDataCallback, instanceArena);

        appSrc = this.pipeline.initNewAppSrc(this.duration * 1000000000, callbacks, null);
    }
}

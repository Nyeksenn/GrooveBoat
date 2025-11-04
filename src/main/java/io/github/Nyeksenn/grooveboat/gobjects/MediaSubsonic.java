package io.github.Nyeksenn.grooveboat.gobjects;

import io.github.Nyeksenn.grooveboat.Utils;
import io.github.Nyeksenn.grooveboat.audio.AppPipeline;
import io.github.Nyeksenn.grooveboat.client.Subsonic;
import org.freedesktop.gstreamer.app.AppSrc;
import org.freedesktop.gstreamer.app.AppSrcCallbacks;
import org.freedesktop.gstreamer.gst.*;
import org.gnome.glib.GLib;
import org.gnome.gtk.MediaStream;
import org.javagi.base.Out;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static io.github.Nyeksenn.grooveboat.audio.AppPipeline.BUFFER_SIZE;
import static org.freedesktop.gstreamer.gst.Gst.CLOCK_TIME_NONE;

public final class MediaSubsonic extends MediaStream implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MediaSubsonic.class);

    private final Subsonic subsonic;
    private final String songId;
    private final long duration;
    public final AppPipeline pipeline = AppPipeline.getInstance();
    public AppSrc appSrc;
    private final ExecutorService executor;
    public InputStream socket;
    private final byte[] buffer = new byte[BUFFER_SIZE];
    private final String contentType;

    public volatile boolean enoughData = false;
    private final Lock enoughDataLock = new ReentrantLock();

    public volatile boolean pushingData = false;

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
        synchronized (enoughDataLock) {
            enoughData = true;
            pipeline.pipeline.seek(1.0, Format.TIME, SeekFlags.FLUSH, SeekType.SET, timestamp * 1000, SeekType.NONE, CLOCK_TIME_NONE.getValue());
            logger.info("Seek was called with timestamp: {}", timestamp);
        }
    }

    @Override
    protected void updateAudio(boolean muted, double volume) {
        // TODO: Implement actual volume change
        asParent().setVolume(1.0);
        asParent().setMuted(false);
        logger.warn("Update audio was called");
    }

    public void initializeStream() {
        logger.info("Initializing stream");
        Caps caps = null;
        if (Utils.hasMapping(contentType)) {
            var gstCt = Utils.toGStreamerType(contentType);
            caps = Caps.emptySimple(gstCt);
        }

        appSrc.setCapabilities(caps);
        pipeline.pipeline.setState(State.PAUSED);

        updateAudio(false, 1.0);
        // This takes microseconds
        streamPrepared(true, false, true, this.duration * 1000000);
        GLib.timeoutAdd(200, 100, this::updatePosition);
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
        this.appSrc.endOfStream();
        this.pipeline.pipeline.remove(appSrc);
        pipeline.pipeline.setState(State.READY);

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
                                pipeline.pipeline.setState(State.READY);
                                pipeline.pipeline.setState(State.PAUSED);
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

            var response = this.subsonic.seekSongStream(songId, offset * 1000000000);
            if (response != null) {
                this.socket = response.body();
                return true;
            } else {
                return false;
            }
        }
    }

    public boolean updatePosition() {
        Out<State> state = new Out<>();
        Out<State> pending = new Out<>();
        pipeline.pipeline.getState(state, pending, new ClockTime(CLOCK_TIME_NONE.getValue()));

        if (state.get() == State.PLAYING || state.get() == State.PAUSED) {
            if (pending.get() == State.READY || pending.get() == State.NULL) {
                return false;
            }
            Out<Long> pos = new Out<>();
            var position = pipeline.pipeline.queryPosition(Format.TIME, pos);
            if (position) {
                update(pos.get() / 1000);
            }
            return true;
        } else {
            return false;
        }
    }

    // The rest

    public MediaSubsonic(String songId, long duration, Subsonic subsonic, ExecutorService executor, String contentType) {
        this.songId = songId;
        this.subsonic = subsonic;
        this.duration = duration;
        this.executor = executor;
        this.contentType = contentType;

        var enoughDataCallback = (AppSrcCallbacks.EnoughDataCallback) this::enoughDataHandler;
        var needDataCallback = (AppSrcCallbacks.NeedDataCallback) this::needDataHandler;
        var seekDataCallback = (AppSrcCallbacks.SeekDataCallback) this::seekDataHandler;
        var callbacks = new AppSrcCallbacks(Arena.global());
        callbacks.overrideEnoughData(enoughDataCallback, Arena.global());
        callbacks.overrideNeedData(needDataCallback, Arena.global());
        callbacks.overrideSeekData(seekDataCallback, Arena.global());

        appSrc = this.pipeline.initNewAppSrc(this.duration * 1000000000, callbacks, null);
    }
}

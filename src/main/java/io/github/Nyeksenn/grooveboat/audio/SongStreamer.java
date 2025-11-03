package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.client.Subsonic;
import org.freedesktop.gstreamer.app.AppSrc;
import org.freedesktop.gstreamer.gst.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.freedesktop.gstreamer.gst.Gst.CLOCK_TIME_NONE;

public final class SongStreamer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(SongStreamer.class);
    private final Lock pauseLock = new ReentrantLock();
    private final Condition unpause = pauseLock.newCondition();
    private final Pipeline pipeline;
    private volatile boolean paused = false;
    private volatile boolean running = true;

    private final AppSrc appsrc;
    private final String songId;
    private final Subsonic subsonic;
    private final byte[] buffer;
    private final int BUFFER_SIZE = 8192;
    private InputStream stream;

    @Override
    public void run() {
        var response = this.subsonic.getSongStream(songId);

        if (response != null) {
            try {
                stream = response.body();
                logger.info("Now streaming");

                while (running) {
                    pauseLock.lock();

                    var len = stream.readNBytes(buffer, 0, BUFFER_SIZE);
                    while (true) {
                        try {
                            while (paused) {
                                unpause.await();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        } finally {
                            pauseLock.unlock();
                        }

                        var flowReturn = appsrc.pushBuffer(Buffer.memdup(buffer));

                        if (flowReturn == FlowReturn.EOS) {
                            pipeline.setState(State.NULL);
                            pipeline.setState(State.PAUSED);
                            return;
                        }
                        if (flowReturn == FlowReturn.FLUSHING) continue;

                        if ((flowReturn == FlowReturn.OK) && (len < BUFFER_SIZE)) {
                            appsrc.emitEndOfStream();
                            //appsrc.seek(1.0, Format.TIME, SeekFlags.FLUSH, SeekType.SET, 0L, SeekType.NONE, CLOCK_TIME_NONE.getValue());
                            pipeline.setState(State.NULL);
                            pipeline.setState(State.PAUSED);
                            return;
                        }
                    }
                }

                stream.close();
            } catch (IOException e) {
                logger.warn("IO Exception caught while streaming");
            }
        }
    }

    public void pause() {
        pauseLock.lock();
        try {
            paused = true;
        } finally {
            pauseLock.unlock();
        }
    }

    public void resume() {
        pauseLock.lock();
        try {
            paused = false;
            unpause.signalAll();
        } finally {
            pauseLock.unlock();
        }
    }

    public void stop() {
        running = false;
        resume();
        appsrc.emitEndOfStream();
        pipeline.setState(State.NULL);
        pipeline.setState(State.PAUSED);
        //appsrc.seek(1.0, Format.TIME, SeekFlags.FLUSH, SeekType.SET, 0L, SeekType.NONE, CLOCK_TIME_NONE.getValue());
        try {
            stream.close();
        } catch (IOException e) {
            logger.warn("Couldn't close streamer InputStream");
        }
        logger.info("SongStreamer is exiting");
    }

    public SongStreamer(Pipeline pipeline, AppSrc appsrc, String songId, Subsonic subsonic) {
        this.pipeline = pipeline;
        this.appsrc = appsrc;
        this.songId = songId;
        this.subsonic = subsonic;
        this.buffer = new byte[BUFFER_SIZE];
        logger.info("New streamer is starting");
    }
}

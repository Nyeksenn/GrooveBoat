package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.client.Subsonic;
import org.freedesktop.gstreamer.app.AppSrc;
import org.freedesktop.gstreamer.gst.Buffer;
import org.freedesktop.gstreamer.gst.FlowReturn;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SongStreamer implements Runnable {
    private final Lock pauseLock = new ReentrantLock();
    private final Condition unpause = pauseLock.newCondition();
    private volatile boolean paused = false;
    private volatile boolean running = true;

    private final AppSrc appsrc;
    private final String songId;
    private final Subsonic subsonic;
    private final byte[] buffer;
    private final int BUFFER_SIZE = 8192;

    @Override
    public void run() {
        var response = this.subsonic.getSongStream(songId);

        if (response != null) {
            InputStream stream = null;
            try {
                stream = response.getEntity().getContent();

                while (running) {
                    pauseLock.lock();
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

                    var stopReading = loadBuffers(stream);
                    if (stopReading) return;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private boolean loadBuffers(InputStream stream) throws IOException {
        var len = stream.readNBytes(buffer, 0, BUFFER_SIZE);
        var state = appsrc.pushBuffer(Buffer.memdup(buffer));
        if (!(state == FlowReturn.OK)) {
            return true;
        }
        if (len < BUFFER_SIZE) {
            return true;
        }

        return false;
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
    }

    public SongStreamer(AppSrc appsrc, String songId, Subsonic subsonic) {
        this.appsrc = appsrc;
        this.songId = songId;
        this.subsonic = subsonic;
        this.buffer = new byte[BUFFER_SIZE];
    }
}

package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.client.Subsonic;
import org.freedesktop.gstreamer.app.AppSrc;
import org.freedesktop.gstreamer.gst.Buffer;

import java.io.IOException;

public class SongStreamer implements Runnable {
    private final AppSrc appsrc;
    private final String songId;
    private final Subsonic subsonic;
    private boolean shouldStop = false;
    private byte[] buffer;
    private final int BUFFER_SIZE = 8192;

    @Override
    public void run() {
        var stream = this.subsonic.getSongStream(songId);
        while (!shouldStop) {
            try {
                stream.readNBytes(buffer, 0, BUFFER_SIZE);
                appsrc.pushBuffer(Buffer.memdup(buffer));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void setShouldStop(boolean shouldStop) {
        this.shouldStop = shouldStop;
    }

    public SongStreamer(AppSrc appsrc, String songId, Subsonic subsonic) {
        this.appsrc = appsrc;
        this.songId = songId;
        this.subsonic = subsonic;
        this.buffer = new byte[BUFFER_SIZE];
    }
}

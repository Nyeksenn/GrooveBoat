package io.github.Nyeksenn.grooveboat.gobjects;

import io.github.Nyeksenn.grooveboat.audio.AppPipeline;
import io.github.Nyeksenn.grooveboat.audio.SongStreamer;
import io.github.Nyeksenn.grooveboat.client.Subsonic;
import org.gnome.gtk.MediaStream;
import org.javagi.gobject.annotations.InstanceInit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MediaSubsonic extends MediaStream {
    private static final Logger logger = LoggerFactory.getLogger(MediaSubsonic.class);

    private final Subsonic subsonic;
    private final String songId;
    private final long duration;
    private final AppPipeline pipeline;
    private ExecutorService executor;
    private SongStreamer currentStreamer;

    private ArrayDeque<Future<?>> futures;

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

    @Override
    public void streamPrepared(boolean hasAudio, boolean hasVideo, boolean seekable, long duration) {
        this.currentStreamer = new SongStreamer(this.pipeline.appsrc, this.songId, this.subsonic);
        var future = executor.submit(this.currentStreamer);
        futures.add(future);

        asParent().streamPrepared(hasAudio, hasVideo, seekable, duration);
    }

    @Override
    public void streamUnprepared() {
        this.currentStreamer.setShouldStop(true);
        asParent().streamUnprepared();
    }

    @InstanceInit
    public void init() {
        this.futures = new ArrayDeque<>();
        this.executor = Executors.newSingleThreadExecutor();
        this.streamPrepared(true, false, false, this.duration);
        this.updateAudio(false, 100);
    }

    public MediaSubsonic(String songId, long duration, Subsonic subsonic, AppPipeline pipeline) {
        this.songId = songId;
        this.subsonic = subsonic;
        this.pipeline = pipeline;
        this.duration = duration;
    }
}

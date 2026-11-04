package io.github.Nyeksenn.grooveboat.gobjects;

import org.jspecify.annotations.Nullable;
import io.github.Nyeksenn.grooveboat.audio.AppPipeline;
import io.github.Nyeksenn.grooveboat.audio.TranscodePolicy;
import io.github.Nyeksenn.grooveboat.backend.MusicBackend;
import io.github.Nyeksenn.grooveboat.backend.PlaybackSource;
import io.github.Nyeksenn.grooveboat.backend.StreamQuality;
import org.freedesktop.gstreamer.gst.State;
import org.gnome.glib.GLib;
import org.gnome.gtk.MediaStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The application's one GTK media stream. It exists for the whole process and is bound to the player widgets once;
 * tracks are loaded into it rather than creating a stream per song.
 * <p>
 * Gapless playback: the queue tells the stream which song follows ({@link #setNext}). When the pipeline asks for the
 * next URI shortly before the current track ends, the prepared track is handed over; when the pipeline reports that
 * the new track has started ({@link #onStreamStart}), the stream switches its duration and position over without
 * pausing.
 * <p>
 * All methods except the next-URI supplier run on the GTK main thread.
 */
public final class RemoteMediaStream extends MediaStream {
    private static final Logger logger = LoggerFactory.getLogger(RemoteMediaStream.class);
    /** How often the playback position is polled while a song plays. */
    private static final long POSITION_TICK_MS = 30L;

    /** A song together with the playback URL built for it and the backend that has to release it. */
    private record Track(SongListItem item, PlaybackSource source, MusicBackend backend) {
    }

    private final Supplier<MusicBackend> backend;
    private final AppPipeline pipeline;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    /** Decides how a song is requested, or that it has to be skipped. */
    private final Function<String, TranscodePolicy.Decision> policy;
    /** Told (on the main thread) which song {@link #load} skipped and its unsupported codec. */
    private BiConsumer<SongListItem, String> skipListener = (_, _) -> {
    };

    private Track current;
    /** Prepared follow-up track, offered to the pipeline in about-to-finish. */
    private final AtomicReference<Track> next = new AtomicReference<>();
    /** Track the pipeline has accepted for a gapless switch but that hasn't started yet. */
    private final AtomicReference<Track> switching = new AtomicReference<>();
    /**
     * Set by {@link #load} until the loaded song's own stream-start arrives. A short or quickly downloaded song can
     * trigger about-to-finish before it even starts; its stream-start must not be taken for the gapless switch.
     */
    private boolean awaitingLoadStart = false;
    /**
     * Set when the loaded song's own stream-start arrives: the pipeline starts a loaded song silent, and the position
     * ticker fades it in once its position moves. Not armed earlier, because until then a position query can still
     * report the old song's position.
     */
    private volatile boolean fadeInPending = false;
    /** While set, play/pause calls from GTK only update GTK's state and don't touch the pipeline. */
    private boolean detached = false;
    private ScheduledFuture<?> future;
    /** Told (on the main thread) the new position in µs after every successful seek, e.g. for MPRIS. */
    private java.util.function.LongConsumer seekListener;

    public void setSeekListener(java.util.function.LongConsumer listener) {
        this.seekListener = listener;
    }

    private void notifySeeked(long positionUs) {
        var listener = seekListener;
        if (listener != null) {
            listener.accept(positionUs);
        }
    }

    public void setSkipListener(BiConsumer<SongListItem, String> listener) {
        this.skipListener = listener;
    }

    /**
     * @param policy maps a song's MIME type to the decision for it (transcode settings, or skip)
     */
    public RemoteMediaStream(Supplier<MusicBackend> backend, AppPipeline pipeline, ExecutorService executor,
                             ScheduledExecutorService scheduler, Function<String, TranscodePolicy.Decision> policy) {
        this.policy = policy;
        this.backend = backend;
        this.pipeline = pipeline;
        this.executor = executor;
        this.scheduler = scheduler;
        pipeline.setNextUriSupplier(this::takeNextUri);
    }

    // ---- GTK virtual methods

    // The position ticker follows the user's play/pause state. It deliberately doesn't watch the pipeline's
    // state-changed messages: playbin3 posts them for every internal element, and reading their source creates a
    // java-gi proxy per message for elements that are torn down at any time.
    @Override
    public void pause() {
        cancelTimestampUpdate();
        if (!detached) {
            pipeline.setState(State.PAUSED);
        }
    }

    @Override
    public boolean play() {
        if (!detached) {
            pipeline.setState(State.PLAYING);
            logger.info("Now playing");
        }
        if (!timestampUpdateRunning()) {
            startTimestampUpdate();
        }
        return true;
    }

    @Override
    public void seek(long timestamp) {
        logger.info("Seek to {} µs", timestamp);
        if (pipeline.seek(timestamp)) {
            seekSuccess();
            update(timestamp);
            notifySeeked(timestamp);
            setPlaying(true);
            // The position scale pauses this stream's pipeline and ticker directly while it is dragged, without
            // changing GTK's playing state, so setPlaying(true) above can be a no-op. Resume explicitly.
            if (!detached) {
                pipeline.setState(State.PLAYING);
            }
            if (!timestampUpdateRunning()) {
                startTimestampUpdate();
            }
        } else {
            seekFailed();
        }
    }

    @Override
    protected void updateAudio(boolean muted, double volume) {
        pipeline.setVolume(volume, muted);
    }

    // ---- Tracks

    private static long durationUs(SongListItem item) {
        return item.getDuration().toNanos() / 1000L;
    }

    /** The track for a playable song, or {@code null} without a session or when the song has to be skipped. */
    private @Nullable Track build(SongListItem item) {
        var b = backend.get();
        if (b == null) {
            return null;
        }
        if (policy.apply(item.getContentType()) instanceof TranscodePolicy.Decision.Play play) {
            return new Track(item, b.playbackSource(item.getSongId(), item.getContentType(), item.getBitRate(),
                    play.settings()), b);
        }
        return null;
    }

    private void release(Track track) {
        if (track == null) {
            return;
        }
        try {
            executor.submit(() -> track.backend().releasePlayback(track.source()));
        } catch (RejectedExecutionException e) {
            // Shutting down; the server cleans up idle sessions itself.
            logger.debug("Not releasing the play session: the executor is shut down");
        }
    }

    /** Resets GTK's prepared state without the pipeline seeing a pause. */
    private void unprepareDetached() {
        if (isPrepared()) {
            detached = true;
            try {
                streamUnprepared();
            } finally {
                detached = false;
            }
        }
    }

    /** The song currently loaded, or {@code null}. */
    public SongListItem getCurrentItem() {
        return current == null ? null : current.item();
    }

    /** What the current song delivers (codec, bitrate, whether transcoded), or {@code null} when unknown. */
    public @Nullable StreamQuality getCurrentQuality() {
        return current == null ? null : current.source().quality();
    }

    public boolean isSwitching() {
        return switching.get() != null;
    }

    /**
     * Loads a song from the start, replacing the current one, and prerolls it paused.
     *
     * @return false when the song was skipped because its format isn't supported on this device; playback is
     * stopped and the skip listener has been told
     */
    public boolean load(SongListItem item) {
        if (backend.get() == null) {
            throw new IllegalStateException("No active session. Can't load a track.");
        }
        if (policy.apply(item.getContentType()) instanceof TranscodePolicy.Decision.Skip skip) {
            logger.warn("Skipping a song: no decoder for {}", skip.codec());
            cancelTimestampUpdate();
            release(current);
            current = null;
            awaitingLoadStart = false;
            fadeInPending = false;
            pipeline.stop();
            skipListener.accept(item, skip.codec());
            return false;
        }
        var track = build(item);
        if (track == null) {
            throw new IllegalStateException("No active session. Can't load a track.");
        }
        cancelTimestampUpdate();
        release(current);
        current = track;
        logger.info("Loading {}", track.source());
        unprepareDetached();
        // Drop a pending switch only once the old song's streaming threads are stopped, which happens before the
        // new URI is set; otherwise its about-to-finish could still slip one in.
        pipeline.playUri(track.source().uri(), () -> release(switching.getAndSet(null)));
        awaitingLoadStart = true;
        fadeInPending = false;
        // This takes microseconds
        streamPrepared(true, false, true, durationUs(item));
        return true;
    }

    /**
     * Sets the song that follows the current one for gapless playback, or {@code null} for none. Building the URL
     * needs no network access; unused play sessions are never seen by the server.
     */
    public void setNext(SongListItem item) {
        next.set(item == null ? null : build(item));
    }

    /** Called by the pipeline on a streaming thread in about-to-finish. */
    private @Nullable String takeNextUri() {
        var track = next.getAndSet(null);
        if (track == null) {
            return null;
        }
        switching.set(track);
        return track.source().uri();
    }

    /**
     * Handles the pipeline's stream-start. After a gapless switch it moves the stream over to the new track and
     * returns its song, so the queue can follow; otherwise returns {@code null}.
     */
    public @Nullable SongListItem onStreamStart() {
        if (awaitingLoadStart) {
            awaitingLoadStart = false;
            fadeInPending = true;
            return null;
        }
        var track = switching.getAndSet(null);
        if (track == null) {
            return null;
        }
        logger.info("Gapless switch to {}", track.source());
        release(current);
        current = track;
        var wasPlaying = getPlaying();
        unprepareDetached();
        streamPrepared(true, false, true, durationUs(track.item()));
        if (wasPlaying) {
            detached = true;
            try {
                setPlaying(true);
            } finally {
                detached = false;
            }
        }
        update(Math.max(0L, pipeline.queryPosition()));
        return track.item();
    }

    /** The queue ran out: stop at the end and rewind, so play starts the last song again. */
    public void endOfQueue() {
        setPlaying(false);
        if (pipeline.seek(0L)) {
            update(0L);
            notifySeeked(0L);
        }
    }

    /** After a playback error: stop the pipeline without GTK's pause restarting the failing download. */
    public void fail() {
        cancelTimestampUpdate();
        awaitingLoadStart = false;
        fadeInPending = false;
        pipeline.stop();
        detached = true;
        try {
            setPlaying(false);
        } finally {
            detached = false;
        }
        release(switching.getAndSet(null));
    }

    /** Stops playback entirely and releases all play sessions, e.g. on logout. */
    public void stop() {
        cancelTimestampUpdate();
        awaitingLoadStart = false;
        fadeInPending = false;
        next.set(null);
        release(switching.getAndSet(null));
        release(current);
        current = null;
        pipeline.stop();
        detached = true;
        try {
            setPlaying(false);
        } finally {
            detached = false;
        }
        unprepareDetached();
    }

    // ---- Position

    public void updatePosition() {
        if (isPrepared()) {
            var pos = pipeline.queryPosition();
            if (pos > 0L && fadeInPending) {
                // The sink is rendering the new song: bring the volume up.
                fadeInPending = false;
                pipeline.fadeIn();
            }
            if (pos >= 0L) {
                GLib.idleAddOnce(() -> {
                    if (isPrepared()) {
                        update(pos);
                    }
                });
            }
        }
    }

    public void startTimestampUpdate() {
        future = scheduler.scheduleAtFixedRate(this::updatePosition, 0L, POSITION_TICK_MS, TimeUnit.MILLISECONDS);
    }

    public void cancelTimestampUpdate() {
        if (future != null) {
            future.cancel(true);
            future = null;
        }
    }

    public boolean timestampUpdateRunning() {
        return future != null;
    }
}

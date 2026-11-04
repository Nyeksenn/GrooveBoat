package io.github.Nyeksenn.grooveboat.audio;

import org.freedesktop.gstreamer.audio.StreamVolume;
import org.freedesktop.gstreamer.audio.StreamVolumeFormat;
import org.freedesktop.gstreamer.gst.*;
import org.gnome.glib.MainContext;
import org.gnome.glib.MainLoop;
import org.javagi.base.GErrorException;
import org.javagi.base.Out;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.freedesktop.gstreamer.gst.Gst.CLOCK_TIME_NONE;

/**
 * The application's single player, a {@code playbin3} that lives for the whole process.
 * <p>
 * Only the URI changes between tracks; the element tree is managed by playbin3 itself. For gapless playback, playbin3
 * emits {@code about-to-finish} shortly before the current track runs out of data; the
 * {@linkplain #setNextUriSupplier next-URI supplier} is asked for the following track's URI there, and playbin3 then
 * switches to it without a gap, posting a {@code stream-start} message when the new track reaches the sink.
 */
public final class AppPipeline {
    private static final Logger logger = LoggerFactory.getLogger(AppPipeline.class);

    /** Element factories playback can't work without, mapped to the package that usually provides them. */
    private static final List<String[]> REQUIRED_ELEMENTS = List.of(
            new String[]{"playbin3", "gst-plugins-base"},
            new String[]{"autoaudiosink", "gst-plugins-good"},
            new String[]{"souphttpsrc", "gst-plugins-good"},
            new String[]{"qtdemux", "gst-plugins-good"}
    );

    /** Signal callback shape for playbin3's {@code about-to-finish} (the instance argument is implicit). */
    @FunctionalInterface
    public interface AboutToFinish {
        void run();
    }

    private final MainContext ctx = new MainContext();
    private final MainLoop loop = new MainLoop(ctx, false);
    private final Query posQuery = Query.position(Format.TIME);
    private final Element playbin;
    private final Bus bus;
    private volatile Supplier<String> nextUriSupplier = () -> null;
    /** Whether the next track is handed over before the current one ends. Off: each track ends with EOS. */
    private volatile boolean gapless = true;
    /**
     * ReplayGain filter ({@code rgvolume ! rglimiter}) for playbin3's {@code audio-filter}, or {@code null} when the
     * elements aren't installed. Only the bin and the rgvolume inside it, both created here, are referenced.
     */
    private final @Nullable Element replayGainFilter;
    private final @Nullable Element rgvolume;
    /** Whether {@link #replayGainFilter} is wanted; applied to playbin3 the next time a track is loaded. */
    private volatile boolean replayGainEnabled = false;
    /** Whether a track has been loaded since the last {@link #stop}, i.e. there is one that may be audible. */
    private boolean loaded = false;
    /** The state last requested through this class, to tell whether audio is audible right now. */
    private volatile State targetState = State.READY;

    // ---- Volume and fades. The effective volume is the user's volume times a fade gain.
    private static final int FADE_OUT_STEPS = 6;
    private static final long FADE_OUT_STEP_MS = 5L;
    private static final int FADE_IN_STEPS = 12;
    private static final long FADE_IN_STEP_MS = 10L;
    private final Object volumeLock = new Object();
    private final ScheduledExecutorService fader = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "gst-fader");
        t.setDaemon(true);
        return t;
    });
    private double userVolume = 1.0;
    private boolean userMuted = false;
    private double gain = 1.0;
    private ScheduledFuture<?> fadeTask;

    public AppPipeline() {
        Gst.init(null);
        playbin = ElementFactory.make("playbin3", "player");
        var filter = createReplayGainFilter();
        replayGainFilter = filter;
        rgvolume = filter instanceof Bin bin ? bin.getByName("rgvolume") : null;
        if (playbin == null) {
            // missingElements() reports this to the user; keep the app alive so it can.
            logger.error("Couldn't create playbin3");
            bus = null;
            return;
        }
        // Audio only: no video or subtitle branches, even if a container carries cover-art streams.
        Gst.utilSetObjectArg(playbin, "flags", "audio");
        playbin.connect("about-to-finish", (AboutToFinish) this::onAboutToFinish);
        bus = playbin.getBus();
        playbin.setState(State.READY);
    }

    /** Builds {@code rgvolume ! rglimiter} as one bin, or returns {@code null} when the plugin is missing. */
    private static @Nullable Element createReplayGainFilter() {
        if (ElementFactory.find("rgvolume") == null || ElementFactory.find("rglimiter") == null) {
            logger.warn("ReplayGain elements (rgvolume, rglimiter; gst-plugins-good) not installed, ReplayGain is unavailable");
            return null;
        }
        try {
            // The limiter keeps positive gain from clipping.
            return Gst.parseBinFromDescription("rgvolume name=rgvolume ! rglimiter", true);
        } catch (GErrorException e) {
            logger.warn("Couldn't build the ReplayGain filter: {}", e.getMessage());
            return null;
        }
    }

    /** @return whether the ReplayGain elements are installed, i.e. whether {@link #setReplayGain} can do anything */
    public boolean isReplayGainAvailable() {
        return replayGainFilter != null;
    }

    /** GStreamer caps that identify a codec's compressed stream; codecs missing here are assumed playable. */
    private static final Map<String, String> CODEC_CAPS = Map.ofEntries(
            Map.entry("flac", "audio/x-flac"),
            Map.entry("mp3", "audio/mpeg, mpegversion=(int)1, layer=(int)3"),
            Map.entry("aac", "audio/mpeg, mpegversion=(int)4"),
            Map.entry("alac", "audio/x-alac"),
            Map.entry("opus", "audio/x-opus"),
            Map.entry("vorbis", "audio/x-vorbis"),
            Map.entry("ac3", "audio/x-ac3"),
            Map.entry("eac3", "audio/x-eac3"),
            Map.entry("wav", "audio/x-wav"),
            Map.entry("wma", "audio/x-wma"),
            Map.entry("wavpack", "audio/x-wavpack"));
    private static final long FACTORY_TYPE_DECODER = 1L;
    private static final long FACTORY_TYPE_DEMUXER = 1L << 5;
    private static final long FACTORY_TYPE_PARSER = 1L << 6;

    private final Map<String, Boolean> decodable = new ConcurrentHashMap<>();

    /**
     * Whether this device has a GStreamer element that can take the codec's stream (decoder, demuxer or parser).
     * Unknown codecs count as playable, so playback is attempted. Call on the main thread.
     */
    public boolean canDecode(String codec) {
        return decodable.computeIfAbsent(codec, AppPipeline::lookupDecoder);
    }

    private static boolean lookupDecoder(String codec) {
        var capsString = CODEC_CAPS.get(codec);
        if (capsString == null) {
            return true;
        }
        try {
            var caps = Caps.fromString(capsString);
            for (var type : new long[]{FACTORY_TYPE_DECODER, FACTORY_TYPE_DEMUXER, FACTORY_TYPE_PARSER}) {
                var factories = ElementFactory.listGetElements(new ElementFactoryListType(type), Rank.MARGINAL);
                var matching = ElementFactory.listFilter(factories, caps, PadDirection.SINK, false);
                if (matching != null && !matching.isEmpty()) {
                    return true;
                }
            }
            logger.info("No GStreamer element can read {} streams", codec);
            return false;
        } catch (RuntimeException e) {
            logger.warn("Couldn't check decoder support for {}: {}", codec, e.toString());
            return true;
        }
    }

    /** Turns gapless playback on or off. When off, a track ends with EOS and the queue then loads the next one. */
    public void setGapless(boolean enabled) {
        gapless = enabled;
        logger.debug("Gapless playback {}", enabled ? "on" : "off");
    }

    /**
     * Configures ReplayGain. The mode takes effect immediately. Switching it on or off only takes effect when the next
     * track is loaded with {@link #playUri}: playbin3 can't swap its audio filter while playing.
     *
     * @param albumMode prefer the album gain (falling back to the track gain) instead of the track gain
     */
    public void setReplayGain(boolean enabled, boolean albumMode) {
        replayGainEnabled = enabled;
        if (rgvolume != null) {
            rgvolume.setProperty("album-mode", albumMode);
        }
        logger.debug("ReplayGain {} ({} mode)", enabled ? "on" : "off", albumMode ? "album" : "track");
    }

    /** Called on a GStreamer streaming thread. Setting the URI here makes playbin3 continue gaplessly. */
    private void onAboutToFinish() {
        if (!gapless) {
            logger.debug("Gapless playback is off, letting the track end");
            return;
        }
        String next;
        try {
            next = nextUriSupplier.get();
        } catch (RuntimeException e) {
            logger.warn("Couldn't get next track", e);
            next = null;
        }
        if (next != null) {
            logger.debug("Queueing next track for gapless playback");
            playbin.setProperty("uri", next);
        }
    }

    /**
     * Sets what to play after the current track. The supplier runs on a GStreamer streaming thread, must not block
     * or touch GTK, and returns {@code null} when nothing should follow (playback then ends with EOS).
     */
    public void setNextUriSupplier(Supplier<String> supplier) {
        this.nextUriSupplier = supplier == null ? () -> null : supplier;
    }

    /** Names of required GStreamer elements that are not installed, with the package that provides them. */
    public static List<String> missingElements() {
        Gst.init(null);
        var missing = new ArrayList<String>();
        for (var element : REQUIRED_ELEMENTS) {
            if (ElementFactory.find(element[0]) == null) {
                missing.add(element[0] + " (" + element[1] + ")");
            }
        }
        if (ElementFactory.find("hlsdemux2") == null && ElementFactory.find("hlsdemux") == null) {
            missing.add("hlsdemux2 (gst-plugins-good) or hlsdemux (gst-plugins-bad)");
        }
        return missing;
    }

    /**
     * Plays {@code uri} from the start, replacing whatever was playing, and prerolls it (state PAUSED).
     * The URI may contain credentials and is never logged.
     *
     * @param whileStopped runs after the old track's streaming threads have stopped and before the new URI is set,
     *                     so no about-to-finish of the old track can interleave with it
     */
    public synchronized void playUri(String uri, Runnable whileStopped) {
        cancelFade();
        if (loaded && targetState == State.PLAYING) {
            fadeOut();
        }
        // The new track starts silent; fadeIn() brings it up once it is audibly playing, which hides decoder
        // start-up transients.
        setGain(0.0);
        // A manual switch goes all the way to NULL: that disposes the audio sink together with its sound server
        // stream, and with it the audio of the old track that was buffered there but not yet played. At READY the
        // stream survives (a flushing seek didn't reliably clear it either), and that audio came out when the new
        // track started. Going to NULL also flushes the bus, so messages the old track left behind (stream-start,
        // eos, buffering) are dropped.
        playbin.setState(State.NULL);
        // The only moment the audio filter can be swapped safely: the pipeline is in NULL.
        playbin.setProperty("audio-filter", replayGainEnabled ? replayGainFilter : null);
        whileStopped.run();
        playbin.setProperty("uri", uri);
        setState(State.PAUSED);
        loaded = true;
    }

    /** Ramps the old track down so cutting it off doesn't click. Blocks for about 30 ms. */
    private void fadeOut() {
        double start;
        synchronized (volumeLock) {
            start = gain;
        }
        for (int i = FADE_OUT_STEPS - 1; i >= 0; i--) {
            setGain(start * i / FADE_OUT_STEPS);
            try {
                Thread.sleep(FADE_OUT_STEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Ramps the volume back up after {@link #playUri}. Call it once the new track is audibly playing; thread-safe.
     */
    public void fadeIn() {
        synchronized (volumeLock) {
            cancelFade();
            var steps = new int[]{0};
            fadeTask = fader.scheduleAtFixedRate(() -> {
                synchronized (volumeLock) {
                    steps[0]++;
                    gain = Math.min(1.0, (double) steps[0] / FADE_IN_STEPS);
                    applyVolume();
                    if (steps[0] >= FADE_IN_STEPS && fadeTask != null) {
                        fadeTask.cancel(false);
                        fadeTask = null;
                    }
                }
            }, 0L, FADE_IN_STEP_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void cancelFade() {
        synchronized (volumeLock) {
            if (fadeTask != null) {
                fadeTask.cancel(false);
                fadeTask = null;
            }
        }
    }

    private void setGain(double value) {
        synchronized (volumeLock) {
            gain = value;
            applyVolume();
        }
    }

    /** Must hold {@link #volumeLock}. */
    private void applyVolume() {
        playbin.setProperty("volume", userVolume * gain);
        playbin.setProperty("mute", userMuted);
    }

    /** Stops playback; the pipeline stays ready for the next {@link #playUri}. */
    public synchronized void stop() {
        setState(State.READY);
        loaded = false;
        // Nothing will fade in after a stop; the next track starts at full volume again.
        cancelFade();
        setGain(1.0);
    }

    public StateChangeReturn setState(State state) {
        targetState = state;
        return playbin.setState(state);
    }

    /** @param timestampUs position in microseconds */
    public boolean seek(long timestampUs) {
        return playbin.seek(1.0, Format.TIME, Set.of(SeekFlags.FLUSH, SeekFlags.ACCURATE), SeekType.SET, timestampUs * 1000, SeekType.NONE,
                CLOCK_TIME_NONE.getValue());
    }

    /**
     * Sets volume and mute through playbin3's own properties, so no second proxy for the native object is needed.
     *
     * @param volume cubic volume as used by GTK's media controls, 0.0 to 1.0
     */
    public void setVolume(double volume, boolean muted) {
        var linear = StreamVolume.convertVolume(StreamVolumeFormat.CUBIC, StreamVolumeFormat.LINEAR, volume);
        synchronized (volumeLock) {
            userVolume = linear;
            userMuted = muted;
            applyVolume();
        }
    }

    /** @return position within the current track in microseconds, or -1 when unknown */
    public long queryPosition() {
        // The ticker (scheduler thread) and the main thread both ask; a Query object isn't thread-safe.
        synchronized (posQuery) {
            if (!playbin.query(posQuery)) {
                return -1L;
            }
            var pos = new Out<Long>();
            posQuery.parsePosition(null, pos);
            return pos.get() / 1000L;
        }
    }

    public Bus getBus() {
        return bus;
    }

    public void quit() {
        fader.shutdownNow();
        if (playbin != null) {
            playbin.setState(State.NULL);
        }
        loop.quit();
    }

    public void run() {
        loop.run();
    }
}

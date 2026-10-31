package io.github.Nyeksenn.grooveboat.audio;

import org.freedesktop.gstreamer.audio.StreamVolume;
import org.freedesktop.gstreamer.audio.StreamVolumeFormat;
import org.freedesktop.gstreamer.gst.*;
import org.gnome.glib.MainContext;
import org.gnome.glib.MainLoop;
import org.javagi.base.Out;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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

    public AppPipeline() {
        Gst.init(null);
        playbin = ElementFactory.make("playbin3", "player");
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

    /** Called on a GStreamer streaming thread. Setting the URI here makes playbin3 continue gaplessly. */
    private void onAboutToFinish() {
        String next;
        try {
            next = nextUriSupplier.get();
        } catch (RuntimeException e) {
            logger.warn("Couldn't get next track: {}", e.getMessage());
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
     */
    public synchronized void playUri(String uri) {
        playbin.setState(State.READY);
        playbin.setProperty("uri", uri);
        playbin.setState(State.PAUSED);
    }

    /** Stops playback; the pipeline stays ready for the next {@link #playUri}. */
    public synchronized void stop() {
        playbin.setState(State.READY);
    }

    public StateChangeReturn setState(State state) {
        return playbin.setState(state);
    }

    /** @param timestampUs position in microseconds */
    public boolean seek(long timestampUs) {
        return playbin.seek(1.0, Format.TIME, SeekFlags.FLUSH, SeekType.SET, timestampUs * 1000, SeekType.NONE,
                CLOCK_TIME_NONE.getValue());
    }

    /**
     * Sets volume and mute through playbin3's own properties, so no second proxy for the native object is needed.
     *
     * @param volume cubic volume as used by GTK's media controls, 0.0 to 1.0
     */
    public void setVolume(double volume, boolean muted) {
        var linear = StreamVolume.convertVolume(StreamVolumeFormat.CUBIC, StreamVolumeFormat.LINEAR, volume);
        playbin.setProperty("volume", linear);
        playbin.setProperty("mute", muted);
    }

    /** @return position within the current track in microseconds, or -1 when unknown */
    public long queryPosition() {
        var pos = new Out<Long>();
        var success = playbin.query(posQuery);
        posQuery.parsePosition(null, pos);
        if (success) {
            return pos.get() / 1000L;
        } else {
            return -1L;
        }
    }

    public Bus getBus() {
        return bus;
    }

    public void quit() {
        if (playbin != null) {
            playbin.setState(State.NULL);
        }
        loop.quit();
    }

    public void run() {
        loop.run();
    }
}

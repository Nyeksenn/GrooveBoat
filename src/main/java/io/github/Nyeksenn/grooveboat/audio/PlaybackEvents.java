package io.github.Nyeksenn.grooveboat.audio;

import static org.javagi.util.Intl.i18n;

import io.github.Nyeksenn.grooveboat.gobjects.RemoteMediaStream;
import org.freedesktop.gstreamer.gst.Bus;
import org.freedesktop.gstreamer.gst.Message;
import org.freedesktop.gstreamer.gst.State;
import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import org.gnome.glib.GError;
import org.gnome.glib.GLib;
import org.javagi.base.Out;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Reacts to the pipeline's bus messages: end of stream, a new track reaching the sink, playback errors and network
 * buffering. Everything here runs on the GTK main thread.
 */
public final class PlaybackEvents {
    private static final Logger logger = LoggerFactory.getLogger(PlaybackEvents.class);

    private final AppPipeline pipeline;
    private final RemoteMediaStream stream;
    private final QueueStore queue;
    private final Consumer<String> showToast;
    private boolean buffering = false;
    /** Songs skipped in a row; reset when a song starts. Stops a queue of only unsupported songs from looping. */
    private int consecutiveSkips = 0;

    /**
     * @param showToast shows a short message in the active window
     */
    public PlaybackEvents(AppPipeline pipeline, RemoteMediaStream stream, QueueStore queue,
                          Consumer<String> showToast) {
        this.pipeline = pipeline;
        this.stream = stream;
        this.queue = queue;
        this.showToast = showToast;
    }

    /**
     * Subscribes to the bus messages the app handles, one detailed signal per type.
     * <p>
     * Deliberately not a catch-all bus watch: decoding a message type in java-gi 0.14.1 ({@code Message.readType()})
     * never returns for "extended" types (bit 31 set, e.g. stream-collection and streams-selected, which
     * {@code playbin3} posts for every stream), because its flag conversion uses a signed shift. With detailed
     * signals, other message types never reach Java code.
     */
    public void watch(Bus bus) {
        bus.addSignalWatch();
        bus.onMessage("eos", this::onEndOfStream);
        bus.onMessage("stream-start", this::onStreamStart);
        bus.onMessage("error", this::onError);
        bus.onMessage("buffering", this::onBuffering);
    }

    /** With gapless playback, EOS only arrives when no follow-up track was handed to the pipeline in time. */
    private void onEndOfStream(Message msg) {
        logger.info("End of Stream");
        if (!queue.advance(true)) {
            stream.endOfQueue();
        }
    }

    /** A new track reached the sink. After a gapless switch, the queue follows the stream. */
    private void onStreamStart(Message msg) {
        // Deliberately keeps the buffering flag: stream-start can arrive while paused for buffering, and the
        // following 100% message must still resume playback.
        consecutiveSkips = 0;
        var item = stream.onStreamStart();
        if (item != null) {
            queue.followGaplessSwitch(item);
        }
    }

    /**
     * The stream skipped a song whose format this device can't decode (and transcoding it is off). Warns and moves on
     * to the next song, unless every song in the queue was just skipped.
     */
    public void onSongSkipped(SongListItem item, String codec) {
        showToast.accept(String.format(i18n("Skipped “%s”: its format (%s) isn't supported on this device"),
                item.getSongName(), codec));
        consecutiveSkips++;
        if (consecutiveSkips >= Math.max(1, queue.getModel().getNItems())) {
            logger.warn("Every song in the queue was skipped, stopping");
            consecutiveSkips = 0;
            return;
        }
        // Not inside the queue's selection handler that called us.
        GLib.idleAddOnce(() -> {
            if (!queue.advance(false)) {
                stream.endOfQueue();
            }
        });
    }

    private void onError(Message msg) {
        Out<GError> error = new Out<>();
        Out<String> debug = new Out<>();
        msg.parseError(error, debug);
        // The debug string can contain the stream URI with credentials, so only the message is logged.
        logger.warn("Playback error: {}", error.get().readMessage());
        handlePlaybackError();
    }

    private void onBuffering(Message msg) {
        Out<Integer> percent = new Out<>();
        msg.parseBuffering(percent);
        handleBuffering(percent.get());
    }

    /** Network streams: hold playback while the buffer refills, then resume if the user still wants to play. */
    private void handleBuffering(int percent) {
        if (stream.getCurrentItem() == null) {
            return;
        }
        // The follow-up track starts buffering while the current one still plays out; pausing would cause a gap.
        if (percent < 100 && stream.isSwitching()) {
            return;
        }
        if (percent < 100) {
            if (!buffering && stream.getPlaying()) {
                logger.debug("Buffering ({}%), pausing", percent);
                buffering = true;
                pipeline.setState(State.PAUSED);
            }
        } else if (buffering) {
            buffering = false;
            if (stream.getPlaying()) {
                logger.debug("Buffering done, resuming");
                pipeline.setState(State.PLAYING);
            }
        }
    }

    private void handlePlaybackError() {
        buffering = false;
        var current = stream.getCurrentItem();
        if (current == null) {
            return;
        }
        stream.fail();
        showToast.accept(String.format(i18n("Couldn't play “%s”"), current.getSongName()));
        queue.advance(false);
    }
}

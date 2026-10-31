package io.github.Nyeksenn.grooveboat.ui;

import static org.javagi.util.Intl.i18n;

import org.gnome.glib.GLib;
import org.gnome.gtk.*;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.Property;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/music-controls.ui", name = "MusicControls")
public class MusicControls extends Box {
    private static final Logger logger = LoggerFactory.getLogger(MusicControls.class);
    private final Runnable modeListener = this::syncModeButtons;
    private MediaStream ms;
    private GrooveboatApp app;
    private Adjustment adjustment;
    private final static String[] volumeIcons = new String[]{"speaker-0-symbolic", "speaker-3-symbolic", "speaker-1-symbolic", "speaker-2-symbolic"};

    // Debounce
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;
    private final long DEBOUNCE_DELAY = 1000; // milliseconds

    @GtkChild(name = "playback-position-scale")
    public Scale playbackPositionScale;

    @GtkChild(name = "loop-button")
    public Button loopButton;

    @GtkChild(name = "shuffle-button")
    public ToggleButton shuffleButton;

    @GtkChild(name = "skip-back-button")
    public Button skipBackButton;

    @GtkChild(name = "play-button")
    public Button playButton;

    @GtkChild(name = "skip-forward-button")
    public Button skipForwardButton;

    @GtkChild(name = "volume-button")
    public ScaleButton volumeButton;

    @GtkChild(name = "timestamp-label")
    public Label timestampLabel;

    @GtkChild(name = "duration-label")
    public Label durationLabel;

    @Property
    public MediaStream getMediaStream() {
        return ms;
    }

    @Property
    public void setMediaStream(MediaStream mediaStream) {
        if (mediaStream != null) {
            // The app has one media stream for its lifetime: bind it once. The duration changes per track, so it
            // drives the adjustment's upper bound through a binding.
            if (mediaStream == this.ms) {
                return;
            }
            this.ms = mediaStream;
            adjustment = new Adjustment(0.0, 0.0, Math.max(1.0, (double) mediaStream.getDuration()), 1.0, 0.0, 0.0);
            playbackPositionScale.setAdjustment(adjustment);
            setupBindings();
        } else {
            this.ms = null;
            playbackPositionScale.setSensitive(false);
        }
    }

    @GtkCallback(name = "position-scale-change-value")
    public boolean positionScaleChangeValue(ScrollType scroll, double value) {
        ms.pause();
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        try {
            scheduledTask = scheduler.schedule(() -> {
                GLib.idleAddOnce(() -> ms.seek((long) value));
            }, DEBOUNCE_DELAY, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            logger.warn(e.getMessage());
        }

        return false;
    }

    @GtkCallback(name = "volume-changed")
    public void volumeChanged(double value) {
        ms.setMuted(value == 0.0);
        ms.setVolume(value);
    }

    @GtkCallback(name = "loop-clicked")
    public void loopClicked() {
        assert app != null;
        app.queueStore.setRepeat(app.queueStore.getRepeat().cycle());
    }

    @GtkCallback(name = "shuffle-toggled")
    public void shuffleToggled() {
        assert app != null;
        app.queueStore.setShuffle(shuffleButton.getActive());
    }

    /** Shows the queue's repeat and shuffle state; also called when MPRIS changes them. */
    private void syncModeButtons() {
        var queue = app.queueStore;
        loopButton.setIconName(switch (queue.getRepeat()) {
            case NONE -> "arrows-loop-tall-disabled-symbolic";
            case PLAYLIST -> "arrows-loop-tall-symbolic";
            case TRACK -> "media-playlist-repeat-song-symbolic";
        });
        loopButton.setTooltipText(switch (queue.getRepeat()) {
            case NONE -> i18n("Repeat: Off");
            case PLAYLIST -> i18n("Repeat: All");
            case TRACK -> i18n("Repeat: Current Song");
        });
        if (shuffleButton.getActive() != queue.isShuffle()) {
            shuffleButton.setActive(queue.isShuffle());
        }
    }

    private void setupBindings() {
        // Volume changes from outside the slider (MPRIS) show up on it too.
        ms.bindProperty("volume", volumeButton, "value").syncCreate().build();
        ms.<Boolean, String>bindProperty("playing", playButton, "icon-name").transformTo((playing) -> {
            if (playing) {
                return "pause-symbolic";
            } else {
                return "play-symbolic";
            }
        }).transformFrom(null).syncCreate().build();
        ms.<Long, String>bindProperty("timestamp", timestampLabel, "label")
                .transformTo((timestamp) -> timeFormat(timestamp / 1000000L))
                .transformFrom(null)
                .syncCreate().build();
        ms.<Long, String>bindProperty("duration", durationLabel, "label")
                .transformTo((timestamp) -> timeFormat(timestamp / 1000000L))
                .transformFrom(null)
                .syncCreate().build();
        ms.bindProperty("prepared", playbackPositionScale, "sensitive").syncCreate().build();
        ms.<Boolean, Boolean>bindProperty("seeking", playbackPositionScale, "sensitive")
                .transformTo((seeking) -> !seeking).transformFrom(null).syncCreate().build();
        ms.<Long, Double>bindProperty("duration", adjustment, "upper")
                .transformTo((duration) -> Math.max(1.0, (double) duration))
                .transformFrom(null)
                .syncCreate().build();
        ms.<Long, Double>bindProperty("timestamp", adjustment, "value")
                .transformTo((timestamp) -> (double) timestamp)
                .transformFrom(null)
                .syncCreate().build();
    }

    private static String timeFormat(long seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    @InstanceInit
    public void init() {
        onRealize(() -> {
            var win = (GrooveboatWindow) getRoot();
            this.app = (GrooveboatApp) win.getApplication();
            this.scheduler = app.scheduler;
            this.volumeButton.setIcons(volumeIcons);
            app.queueStore.addModeListener(modeListener);
            syncModeButtons();
        });
        onUnrealize(() -> {
            if (app != null) {
                app.queueStore.removeModeListener(modeListener);
            }
        });
    }

    public MusicControls() {
    }

    public MusicControls(MemorySegment address) {
        super(address);
    }
}

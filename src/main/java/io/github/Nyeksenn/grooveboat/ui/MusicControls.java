package io.github.Nyeksenn.grooveboat.ui;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/music-controls.ui", name = "MusicControls")
public class MusicControls extends Box {
    private static final Logger logger = LoggerFactory.getLogger(MusicControls.class);
    private MediaStream ms;
    private Adjustment adjustment;
    private final static String[] volumeIcons = new String[]{"speaker-0-symbolic", "speaker-3-symbolic", "speaker-1-symbolic", "speaker-2-symbolic"};

    // Debounce
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> scheduledTask;
    private final long DEBOUNCE_DELAY = 1000; // milliseconds

    @GtkChild(name = "playback-position-scale")
    public Scale playbackPositionScale;

    @GtkChild(name = "loop-button")
    public Button loopButton;

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
            this.ms = new MediaStream.MediaStream$Impl(mediaStream.handle());
            adjustment = new Adjustment(0.0, 0.0, (double) mediaStream.getDuration(), 1.0, 0.0, 0.0);
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
        if (scheduledTask != null && !scheduledTask.isDone()) {
            scheduledTask.cancel(false);
        }
        scheduledTask = scheduler.schedule(() -> {
            GLib.idleAddOnce(() -> ms.seek((long) value));
        }, DEBOUNCE_DELAY, TimeUnit.MILLISECONDS);

        return false;
    }

    @GtkCallback(name = "volume-changed")
    public void volumeChanged(double value) {
        ms.setMuted(value == 0.0);
        ms.setVolume(value);
    }

    private void setupBindings() {
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
        onRealize(() -> this.volumeButton.setIcons(volumeIcons));
        onUnrealize(this.scheduler::shutdownNow);
    }

    public MusicControls() {
    }

    public MusicControls(MemorySegment address) {
        super(address);
    }
}

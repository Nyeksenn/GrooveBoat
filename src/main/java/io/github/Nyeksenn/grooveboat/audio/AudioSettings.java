package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.utils.AppSettings;
import io.github.Nyeksenn.grooveboat.backend.TranscodeFormat;
import io.github.Nyeksenn.grooveboat.backend.TranscodeProfile;
import org.gnome.gio.NetworkMonitor;
import org.gnome.gio.Settings;
import org.gnome.gobject.GObject;

import java.util.function.Supplier;

/**
 * Keeps the {@link AppPipeline}'s audio options in step with their GSettings keys: applies the stored values once
 * and follows every later change (made by the preferences dialog or from outside, e.g. {@code gsettings set}).
 * GSettings is the only place the values are stored.
 */
public final class AudioSettings {
    private AudioSettings() {
    }

    /** Whether a {@code replay-gain-mode} value selects album gain; anything but "track" counts as album. */
    static boolean isAlbumMode(String mode) {
        return !AppSettings.REPLAY_GAIN_MODE_TRACK.equals(mode);
    }

    /**
     * Applies the current settings to {@code pipeline} and keeps following them for the process lifetime.
     *
     * @param onStreamingChanged run on the main thread after the streaming quality that applies changed, e.g. so the
     *                           prepared follow-up song is rebuilt
     * @return the streaming preferences that apply right now (the metered or unmetered profile, depending on the
     * connection); safe to read from any thread
     */
    public static Supplier<TranscodePolicy.Snapshot> bind(Settings settings, AppPipeline pipeline,
                                                          Runnable onStreamingChanged) {
        applyGapless(settings, pipeline);
        applyReplayGain(settings, pipeline);
        settings.onChanged(AppSettings.GAPLESS_PLAYBACK, _ -> applyGapless(settings, pipeline));
        settings.onChanged(AppSettings.REPLAY_GAIN_ENABLED, _ -> applyReplayGain(settings, pipeline));
        settings.onChanged(AppSettings.REPLAY_GAIN_MODE, _ -> applyReplayGain(settings, pipeline));
        return bindStreaming(settings, onStreamingChanged);
    }

    private static Supplier<TranscodePolicy.Snapshot> bindStreaming(Settings settings, Runnable onChanged) {
        var holder = new TranscodeHolder();
        var monitor = NetworkMonitor.getDefault();
        Runnable refresh = () -> {
            holder.snapshot = readStreaming(settings, monitor.getNetworkMetered());
            onChanged.run();
        };
        holder.snapshot = readStreaming(settings, monitor.getNetworkMetered());
        for (var key : new String[]{AppSettings.TRANSCODE_PROFILE_UNMETERED, AppSettings.TRANSCODE_PROFILE_METERED,
                AppSettings.TRANSCODE_FORMAT, AppSettings.TRANSCODE_UNSUPPORTED}) {
            settings.onChanged(key, _ -> refresh.run());
        }
        // The metered flag is a property of the monitor; "network-changed" is the fallback for other changes.
        if (monitor instanceof GObject object) {
            object.onNotify("network-metered", _ -> refresh.run());
        }
        monitor.onNetworkChanged(_ -> refresh.run());
        return () -> holder.snapshot;
    }

    private static final class TranscodeHolder {
        volatile TranscodePolicy.Snapshot snapshot = TranscodePolicy.Snapshot.DEFAULT;
    }

    static TranscodePolicy.Snapshot readStreaming(Settings settings, boolean metered) {
        var profile = TranscodeProfile.fromKey(settings.getString(
                metered ? AppSettings.TRANSCODE_PROFILE_METERED : AppSettings.TRANSCODE_PROFILE_UNMETERED));
        return new TranscodePolicy.Snapshot(profile,
                TranscodeFormat.fromKey(settings.getString(AppSettings.TRANSCODE_FORMAT)),
                settings.getBoolean(AppSettings.TRANSCODE_UNSUPPORTED));
    }

    private static void applyGapless(Settings settings, AppPipeline pipeline) {
        pipeline.setGapless(settings.getBoolean(AppSettings.GAPLESS_PLAYBACK));
    }

    private static void applyReplayGain(Settings settings, AppPipeline pipeline) {
        pipeline.setReplayGain(settings.getBoolean(AppSettings.REPLAY_GAIN_ENABLED),
                isAlbumMode(settings.getString(AppSettings.REPLAY_GAIN_MODE)));
    }
}

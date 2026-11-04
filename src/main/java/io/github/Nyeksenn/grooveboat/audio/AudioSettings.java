package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.utils.AppSettings;
import org.gnome.gio.Settings;

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

    /** Applies the current settings to {@code pipeline} and keeps following them for the process lifetime. */
    public static void bind(Settings settings, AppPipeline pipeline) {
        applyGapless(settings, pipeline);
        applyReplayGain(settings, pipeline);
        settings.onChanged(AppSettings.GAPLESS_PLAYBACK, _ -> applyGapless(settings, pipeline));
        settings.onChanged(AppSettings.REPLAY_GAIN_ENABLED, _ -> applyReplayGain(settings, pipeline));
        settings.onChanged(AppSettings.REPLAY_GAIN_MODE, _ -> applyReplayGain(settings, pipeline));
    }

    private static void applyGapless(Settings settings, AppPipeline pipeline) {
        pipeline.setGapless(settings.getBoolean(AppSettings.GAPLESS_PLAYBACK));
    }

    private static void applyReplayGain(Settings settings, AppPipeline pipeline) {
        pipeline.setReplayGain(settings.getBoolean(AppSettings.REPLAY_GAIN_ENABLED),
                isAlbumMode(settings.getString(AppSettings.REPLAY_GAIN_MODE)));
    }
}

package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.backend.TranscodeFormat;
import io.github.Nyeksenn.grooveboat.backend.TranscodeProfile;
import io.github.Nyeksenn.grooveboat.utils.AppSettings;
import org.gnome.adw.ComboRow;
import org.gnome.adw.SwitchRow;
import org.gnome.adw.PreferencesDialog;
import org.gnome.gio.Settings;
import org.gnome.gio.SettingsBindFlags;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;


import java.util.Arrays;
import java.util.List;

import static org.javagi.util.Intl.i18n;

/** The device preferences (currently audio). Account and server details live in {@link AccountDialog}. */
@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/prefs.ui", name = "GrooveboatPrefs")
public class GrooveboatPrefs extends PreferencesDialog {
    @GtkChild(name = "row-gapless")
    public SwitchRow rowGapless;

    @GtkChild(name = "row-replay-gain")
    public SwitchRow rowReplayGain;

    @GtkChild(name = "row-replay-gain-mode")
    public ComboRow rowReplayGainMode;

    @GtkChild(name = "row-transcode-unmetered")
    public ComboRow rowTranscodeUnmetered;

    @GtkChild(name = "row-transcode-metered")
    public ComboRow rowTranscodeMetered;

    @GtkChild(name = "row-transcode-format")
    public ComboRow rowTranscodeFormat;

    @GtkChild(name = "row-transcode-unsupported")
    public SwitchRow rowTranscodeUnsupported;

    public GrooveboatPrefs(boolean replayGainAvailable) {
        bindAudioSettings(replayGainAvailable);
        bindStreamingSettings();
    }

    /** Binds a combo row to a string key; the row's items are in the order of {@code keys}. */
    private static void bindChoice(Settings settings, String key, ComboRow row, String[] keys, Runnable changed) {
        row.setSelected(Math.max(0, List.of(keys).indexOf(settings.getString(key))));
        row.onNotify("selected", _ -> {
            settings.setString(key, keys[row.getSelected()]);
            changed.run();
        });
    }

    private void bindStreamingSettings() {
        var settings = AppSettings.get();
        var profiles = Arrays.stream(TranscodeProfile.values()).map(TranscodeProfile::key).toArray(String[]::new);
        var formats = Arrays.stream(TranscodeFormat.values()).map(TranscodeFormat::key).toArray(String[]::new);
        // The format only matters while some profile (or an unsupported codec) transcodes; it stays editable anyway
        // when "Transcode Unsupported Formats" is on, because that path uses it too.
        Runnable updateFormatRow = () -> rowTranscodeFormat.setSensitive(
                rowTranscodeUnmetered.getSelected() != 0 || rowTranscodeMetered.getSelected() != 0
                        || rowTranscodeUnsupported.getActive());
        bindChoice(settings, AppSettings.TRANSCODE_PROFILE_UNMETERED, rowTranscodeUnmetered, profiles, updateFormatRow);
        bindChoice(settings, AppSettings.TRANSCODE_PROFILE_METERED, rowTranscodeMetered, profiles, updateFormatRow);
        bindChoice(settings, AppSettings.TRANSCODE_FORMAT, rowTranscodeFormat, formats, () -> {
        });
        settings.bind(AppSettings.TRANSCODE_UNSUPPORTED, rowTranscodeUnsupported, "active", SettingsBindFlags.DEFAULT);
        rowTranscodeUnsupported.onNotify("active", _ -> updateFormatRow.run());
        updateFormatRow.run();
    }

    /**
     * The audio rows read and write GSettings directly; the pipeline follows the keys (see {@code AudioSettings}).
     * The mode is a string key shown as a list, so it is mapped by hand: index 0 is track, index 1 album.
     */
    private void bindAudioSettings(boolean replayGainAvailable) {
        var settings = AppSettings.get();
        settings.bind(AppSettings.GAPLESS_PLAYBACK, rowGapless, "active", SettingsBindFlags.DEFAULT);
        settings.bind(AppSettings.REPLAY_GAIN_ENABLED, rowReplayGain, "active", SettingsBindFlags.DEFAULT);

        rowReplayGainMode.setSelected(
                AppSettings.REPLAY_GAIN_MODE_TRACK.equals(settings.getString(AppSettings.REPLAY_GAIN_MODE)) ? 0 : 1);
        rowReplayGainMode.onNotify("selected", _ -> settings.setString(AppSettings.REPLAY_GAIN_MODE,
                rowReplayGainMode.getSelected() == 0
                        ? AppSettings.REPLAY_GAIN_MODE_TRACK : AppSettings.REPLAY_GAIN_MODE_ALBUM));

        if (!replayGainAvailable) {
            // The GStreamer ReplayGain plugin is missing; the switch would do nothing.
            rowReplayGain.setSensitive(false);
            rowReplayGain.setSubtitle(i18n("Requires the GStreamer ReplayGain elements (gst-plugins-good)."));
            rowReplayGainMode.setSensitive(false);
        }
    }
}

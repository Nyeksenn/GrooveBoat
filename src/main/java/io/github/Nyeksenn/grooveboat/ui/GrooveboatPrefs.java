package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.backend.MusicBackend;
import io.github.Nyeksenn.grooveboat.backend.ServerInfo;
import io.github.Nyeksenn.grooveboat.utils.AppSettings;
import org.gnome.adw.ActionRow;
import org.gnome.adw.ComboRow;
import org.gnome.adw.SwitchRow;
import org.gnome.adw.PreferencesDialog;
import org.gnome.gio.SettingsBindFlags;
import org.gnome.glib.GLib;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import static org.javagi.util.Intl.i18n;

/**
 * Shows the current session's server and account read-only. The connection can't be edited here: a session's
 * backend and server are fixed, and changing them means logging out.
 */
@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/prefs.ui", name = "GrooveboatPrefs")
public class GrooveboatPrefs extends PreferencesDialog {
    private static final Logger logger = LoggerFactory.getLogger(GrooveboatPrefs.class);

    @GtkChild(name = "row-backend")
    public ActionRow rowBackend;

    @GtkChild(name = "row-url")
    public ActionRow rowUrl;

    @GtkChild(name = "row-version")
    public ActionRow rowVersion;

    @GtkChild(name = "row-username")
    public ActionRow rowUsername;

    @GtkChild(name = "row-gapless")
    public SwitchRow rowGapless;

    @GtkChild(name = "row-replay-gain")
    public SwitchRow rowReplayGain;

    @GtkChild(name = "row-replay-gain-mode")
    public ComboRow rowReplayGainMode;

    @GtkCallback(name = "logout-clicked")
    public void logoutClicked() {
        // Activate while still attached to the window, so the action resolves; then get out of the way.
        activateActionVariant("app.logout", null);
        close();
    }

    public GrooveboatPrefs(MusicBackend backend, ExecutorService exec, boolean replayGainAvailable) {
        bindAudioSettings(replayGainAvailable);
        rowBackend.setSubtitle(backend.type().displayName());
        rowUrl.setSubtitle(GLib.markupEscapeText(backend.baseUrl(), -1));
        rowUsername.setSubtitle(GLib.markupEscapeText(AppSettings.get().getString(AppSettings.USERNAME), -1));

        UiAsync.background(exec, () -> {
            String version;
            try {
                version = switch (backend.probe()) {
                    case ServerInfo.Compatible(var name, var v) -> name + " " + v;
                    case ServerInfo.Incompatible _ -> "Unknown";
                };
            } catch (IOException e) {
                logger.warn("Couldn't query server version: {}", e.getMessage());
                version = "Unreachable";
            }
            final var text = GLib.markupEscapeText(version, -1);
            GLib.idleAddOnce(() -> rowVersion.setSubtitle(text));
        });
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

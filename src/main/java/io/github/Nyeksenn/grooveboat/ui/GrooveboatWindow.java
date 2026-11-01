package io.github.Nyeksenn.grooveboat.ui;

import static org.javagi.util.Intl.i18n;
import io.github.Nyeksenn.grooveboat.audio.AppPipeline;
import io.github.Nyeksenn.grooveboat.backend.AuthenticationException;
import io.github.Nyeksenn.grooveboat.backend.BackendFactory;
import io.github.Nyeksenn.grooveboat.backend.Session;
import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.gobjects.SecretKeeper;
import io.github.Nyeksenn.grooveboat.utils.AppSettings;
import org.gnome.adw.AlertDialog;
import org.gnome.adw.ApplicationWindow;
import org.gnome.adw.ResponseAppearance;
import org.gnome.adw.StatusPage;
import org.gnome.adw.Toast;
import org.gnome.adw.ToastOverlay;
import org.gnome.gio.SimpleAction;
import org.gnome.glib.GLib;
import org.gnome.gtk.Button;
import org.gnome.gtk.Stack;
import org.javagi.base.Out;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/window.ui", name = "GrooveboatWindow")
public class GrooveboatWindow extends ApplicationWindow {
    private static final Logger logger = LoggerFactory.getLogger(GrooveboatWindow.class);
    private final SecretKeeper secrets = SecretKeeper.getInstance();
    private final GrooveboatApp app;

    @GtkChild(name = "music-chooser")
    public MusicChooser musicChooser;

    @GtkChild(name = "main-stack")
    public Stack mainStack;

    @GtkChild(name = "toast-overlay")
    public ToastOverlay toastOverlay;

    @GtkChild(name = "loading-status")
    public StatusPage loadingStatus;

    @GtkChild(name = "error-status")
    public StatusPage errorStatus;

    @GtkChild(name = "error-retry-button")
    public Button errorRetryButton;

    // ---- Pages

    public void showLoading(String title) {
        loadingStatus.setTitle(title);
        mainStack.setVisibleChildName("loading-page");
    }

    public void showError(String title, String description, boolean canRetry) {
        errorStatus.setTitle(title);
        errorStatus.setDescription(description);
        errorRetryButton.setVisible(canRetry);
        mainStack.setVisibleChildName("error-page");
    }

    /** Shows the library and restores the queue. */
    public void showContent() {
        mainStack.setVisibleChildName("content-page");
        app.queueStore.loadState();
    }

    public void showToast(String message) {
        var toast = new Toast(message);
        // Messages can contain song titles, which may include markup characters.
        toast.setUseMarkup(false);
        toastOverlay.addToast(toast);
    }

    private static boolean hasCachedLibrary() {
        return new QAlbumModel().setMaxRows(1).exists();
    }

    // ---- Session

    /** Restores the stored session, or opens the setup dialog when logged out. */
    public void initializeClient() {
        var missing = AppPipeline.missingElements();
        if (!missing.isEmpty()) {
            showError(i18n("Missing GStreamer Plugins"),
                    String.format(i18n("GrooveBoat needs these GStreamer elements to play music: %s. "
                            + "Install the matching packages and restart the app."), String.join(", ", missing)),
                    false);
            return;
        }

        if (app.hasSession()) {
            syncAndShowContent();
            return;
        }

        var type = AppSettings.backend();
        if (type.isEmpty()) {
            showSetup();
            return;
        }

        showLoading(i18n("Connecting"));
        var settings = AppSettings.get();
        final String username = settings.getString(AppSettings.USERNAME);
        final String url = settings.getString(AppSettings.URL);
        final String userId = settings.getString(AppSettings.USER_ID);

        app.executors.io.submit(() -> {
            try {
                var backend = BackendFactory.create(type.get(), url);
                var credential = secrets.lookupPassword(username, backend.scheme(), backend.host()).join().orElse(null);
                var session = new Session(username, credential, userId);
                var offline = false;
                try {
                    backend.restore(session);
                } catch (AuthenticationException e) {
                    throw e;
                } catch (IOException e) {
                    // Server unreachable: start from the cache if there is one, instead of a dead end.
                    if (!hasCachedLibrary()) {
                        throw e;
                    }
                    logger.warn("Server unreachable, starting from the cached library: {}", e.getMessage());
                    backend.restoreUnverified(session);
                    offline = true;
                }
                final var startedOffline = offline;
                GLib.idleAddOnce(() -> {
                    if (app.hasSession()) {
                        // A concurrent retry got there first.
                        backend.close();
                    } else {
                        app.startSession(backend);
                    }
                    syncAndShowContent();
                    if (startedOffline) {
                        showToast(i18n("Can't reach the server. Showing your cached library."));
                    }
                });
            } catch (AuthenticationException e) {
                logger.warn("Stored session rejected: {}", e.getMessage());
                GLib.idleAddOnce(() -> showError(i18n("Session Expired"),
                        String.format(i18n("The server no longer accepts the stored login for %s. "
                                + "Log out and sign in again."), GLib.markupEscapeText(username, -1)), false));
            } catch (IOException | RuntimeException e) {
                logger.warn("Connection failed: {}", e.getMessage());
                GLib.idleAddOnce(() -> showError(i18n("Connection Error"),
                        String.format(i18n("Unable to reach the %s server. Please check your internet "
                                + "connection and try again."), type.get().displayName()), true));
            }
        });
    }

    private void syncAndShowContent() {
        showLoading(i18n("Loading Library"));
        var library = app.getLibrary();
        app.executors.io.submit(() -> {
            library.refresh(false);
            GLib.idleAddOnce(this::showContent);
        });
    }

    private void showSetup() {
        var setupDialog = new SetupDialog();
        var width = new Out<Integer>();
        var height = new Out<Integer>();
        this.getDefaultSize(width, height);
        setupDialog.setContentHeight(height.get());
        setupDialog.setContentWidth(width.get());
        setupDialog.present(this);
    }

    /** Called by the setup dialog once a session was started. */
    public void finishSetup() {
        syncAndShowContent();
    }

    /** Asks for confirmation, then logs out. */
    public void confirmLogout() {
        var backend = app.getBackend();
        var body = backend != null
                ? String.format(i18n("This clears the local library cache, the queue and the stored login. "
                        + "The music on your %s server is not affected. "
                        + "You can then sign in to the same or another server."), backend.type().displayName())
                : i18n("This clears the local library cache, the queue and the stored login. "
                        + "The music on your server is not affected. "
                        + "You can then sign in to the same or another server.");
        var dialog = new AlertDialog(i18n("Log Out?"), body);
        dialog.addResponse("cancel", i18n("Cancel"));
        dialog.addResponse("logout", i18n("Log Out"));
        dialog.setResponseAppearance("logout", ResponseAppearance.DESTRUCTIVE);
        dialog.setDefaultResponse("cancel");
        dialog.setCloseResponse("cancel");
        dialog.onResponse(null, response -> {
            if ("logout".equals(response)) {
                app.logout(this);
            }
        });
        dialog.present(this);
    }

    @InstanceInit
    public void init() {
        onRealize(() -> {
            var retry = new SimpleAction("retry-connection", null);
            retry.onActivate((_) -> initializeClient());
            addAction(retry);

            this.initializeClient();
        });
    }

    public GrooveboatWindow(GrooveboatApp app) {
        this.app = app;
        setApplication(app);
    }

    @GtkCallback(name = "breakpoint-apply")
    public void handleBreakpointApply() {
        musicChooser.miniPlayer.setVisible(true);
        musicChooser.musicSwitcherBar.setReveal(true);
        musicChooser.musicChooserHeader.setTitleWidget(null);
    }

    @GtkCallback(name = "breakpoint-unapply")
    public void handleBreakpointUnapply() {
        musicChooser.miniPlayer.setVisible(false);
        musicChooser.musicSwitcherBar.setReveal(false);
        musicChooser.musicChooserHeader.setTitleWidget(musicChooser.musicSwitcherTitle);
    }
}

package io.github.Nyeksenn.grooveboat.ui;

import static org.javagi.util.Intl.i18n;
import io.github.Nyeksenn.grooveboat.backend.AuthenticationException;
import io.github.Nyeksenn.grooveboat.backend.BackendType;
import io.github.Nyeksenn.grooveboat.gobjects.SecretKeeper;
import io.github.Nyeksenn.grooveboat.utils.AppSettings;
import org.gnome.adw.EntryRow;
import org.gnome.adw.NavigationPage;
import org.gnome.adw.Toast;
import org.gnome.adw.ToastOverlay;
import org.gnome.glib.GLib;
import org.gnome.gtk.Button;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.ExecutorService;

/**
 * Last setup step: logs in with the chosen backend, then stores the session. Settings are written only here, after
 * the login succeeded and the credential is in the keyring.
 */
@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/credentials-page.ui", name = "CredentialsPage")
public class CredentialsPage extends NavigationPage {
    private static final Logger logger = LoggerFactory.getLogger(CredentialsPage.class);
    private final SecretKeeper secrets = SecretKeeper.getInstance();
    private ExecutorService exec;
    private GrooveboatApp app;

    @GtkChild(name = "credentials-overlay")
    public ToastOverlay credentialsOverlay;

    @GtkChild(name = "entry-username")
    public EntryRow entryUsername;

    @GtkChild(name = "entry-password")
    public EntryRow entryPassword;

    @GtkChild(name = "finish-button")
    public Button finishButton;

    @GtkCallback(name = "password-changed")
    public void passwordChanged() {
        entryUsername.removeCssClass("error");
        entryPassword.removeCssClass("error");
    }

    private void fail(String message, boolean credentialsWrong) {
        GLib.idleAddOnce(() -> {
            finishButton.setSensitive(true);
            credentialsOverlay.addToast(new Toast(message));
            if (credentialsWrong) {
                entryUsername.addCssClass("error");
                entryPassword.addCssClass("error");
            }
        });
    }

    private static String keyringLabel(BackendType type) {
        return switch (type) {
            case OPENSUBSONIC -> i18n("GrooveBoat OpenSubsonic Password");
            case JELLYFIN -> i18n("GrooveBoat Jellyfin Access Token");
        };
    }

    @GtkCallback(name = "credential-validation")
    public void credentialValidation() {
        final var username = entryUsername.getText().trim();
        final var password = entryPassword.getText();
        final var backend = SetupDialog.of(this).getCandidate();
        if (backend == null) {
            fail(i18n("Go back and enter the server address first"), false);
            return;
        }
        if (username.isEmpty()) {
            fail(i18n("Enter a username"), true);
            return;
        }

        finishButton.setSensitive(false);
        exec.submit(() -> {
            try {
                var session = backend.authenticate(username, password);
                var stored = secrets.storePassword(username, backend.scheme(), backend.host(),
                        session.credential(), keyringLabel(backend.type())).join();
                if (!stored) {
                    logger.error("Couldn't store credential in the keyring");
                    fail(i18n("Couldn't save the login to the keyring"), false);
                    return;
                }
                AppSettings.saveSession(backend.type(), backend.baseUrl(), username, session.userId());
                GLib.idleAddOnce(() -> {
                    app.startSession(backend);
                    activateActionVariant("win.close-setup", null);
                });
            } catch (AuthenticationException e) {
                fail(i18n("Username or password wrong"), true);
            } catch (IOException e) {
                logger.warn("Login failed: {}", e.getMessage());
                fail(i18n("Unable to reach server"), false);
            } catch (RuntimeException e) {
                logger.error("Login failed: {}", e.getMessage());
                fail(i18n("Login failed"), false);
            }
        });
    }

    @InstanceInit
    public void init() {
        onMap(() -> entryUsername.grabFocus());
        onRealize(() -> {
            var win = (GrooveboatWindow) getRoot();
            this.app = (GrooveboatApp) win.getApplication();
            this.exec = app.executor;
        });
    }

    public CredentialsPage(MemorySegment address) {
        super(address);
    }
}

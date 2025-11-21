package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.Nyeksenn.grooveboat.gobjects.SecretKeeper;
import org.gnome.adw.EntryRow;
import org.gnome.adw.NavigationPage;
import org.gnome.adw.Toast;
import org.gnome.adw.ToastOverlay;
import org.gnome.gio.Settings;
import org.gnome.glib.GLib;
import org.gnome.gtk.Button;
import org.javagi.base.GErrorException;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.ExecutorService;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/credentials-page.ui", name = "CredentialsPage")
public class CredentialsPage extends NavigationPage {
    private static final Logger logger = LoggerFactory.getLogger(CredentialsPage.class);
    private final SecretKeeper secrets = SecretKeeper.getInstance();
    private final Settings settings = new Settings("io.github.Nyeksenn.grooveboat");
    private ExecutorService exec;
    private Subsonic subsonic;

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
        //finishButton.grabFocus();
    }

    @GtkCallback(name = "credential-validation")
    public void credentialValidation() {
        final var username = entryUsername.getText();
        final var password = entryPassword.getText();
        exec.submit(() -> {
            assert subsonic.isClientInitialized() : "Client is not initialized in credential page";
            subsonic.setCredentials(username, password);
            try {
                var resp = subsonic.checkCredentials(username);
                if (resp.status().equals("ok")) {
                    try {
                        var host = subsonic.getHost();
                        var scheme = subsonic.getScheme();
                        secrets.storePassword(username, scheme, host, password).thenAccept((saved) -> {
                            if (saved) {
                                settings.setString("username", username);
                                settings.setString("url", scheme + "://" + host);
                            }
                            GLib.idleAddOnce(() -> activateActionIfExists("win.close-setup", null));
                        });
                    } catch (GErrorException e) {
                        logger.error("Couldn't store password");
                    }
                } else {
                    GLib.idleAddOnce(() -> {
                        credentialsOverlay.addToast(new Toast("Username or password wrong"));
                        entryUsername.addCssClass("error");
                        entryPassword.addCssClass("error");
                    });
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @InstanceInit
    public void init() {
        onRealize(() -> {
            var win = (GrooveboatWindow) getRoot();
            this.exec = win.executor;
            this.subsonic = win.subsonic;
            entryUsername.grabFocus();
        });
    }

    public CredentialsPage(MemorySegment address) {
        super(address);
    }
}

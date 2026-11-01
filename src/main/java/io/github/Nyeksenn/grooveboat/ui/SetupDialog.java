package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.backend.BackendType;
import io.github.Nyeksenn.grooveboat.backend.MusicBackend;
import org.gnome.adw.Dialog;
import org.gnome.adw.NavigationView;
import org.gnome.gio.SimpleAction;
import org.gnome.gtk.Widget;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

/**
 * First-run setup: welcome → server type → connection → credentials.
 * <p>
 * Holds the choices made so far. Nothing is written to settings or the keyring until the credentials page succeeds,
 * so abandoning setup halfway leaves no partial configuration behind.
 */
@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/setup.ui", name = "SetupWindow")
public class SetupDialog extends Dialog {
    @GtkChild(name = "setup-nav-view")
    public NavigationView setupNavView;

    private BackendType backendType;
    private MusicBackend candidate;

    /** The setup dialog a page belongs to, found by walking up the widget tree. */
    public static SetupDialog of(Widget widget) {
        for (var w = widget; w != null; w = w.getParent()) {
            if (w instanceof SetupDialog dialog) {
                return dialog;
            }
        }
        throw new IllegalStateException("Widget is not inside the setup dialog");
    }

    public BackendType getBackendType() {
        return backendType;
    }

    public void setBackendType(BackendType backendType) {
        if (backendType != this.backendType) {
            discardCandidate();
        }
        this.backendType = backendType;
    }

    /** A backend whose URL was probed successfully but which has no session yet. */
    public MusicBackend getCandidate() {
        return candidate;
    }

    public void setCandidate(MusicBackend candidate) {
        if (this.candidate != null && this.candidate != candidate) {
            this.candidate.close();
        }
        this.candidate = candidate;
    }

    private void discardCandidate() {
        setCandidate(null);
    }

    @InstanceInit
    public void init() {
        onRealize(() -> {
            var win = AppWidgets.window(this);
            var action = new SimpleAction("close-setup", null);
            action.onActivate((_) -> {
                // The candidate now belongs to the app's session.
                candidate = null;
                forceClose();
                win.finishSetup();
            });
            win.addAction(action);
        });
    }

    public SetupDialog() {
    }
}

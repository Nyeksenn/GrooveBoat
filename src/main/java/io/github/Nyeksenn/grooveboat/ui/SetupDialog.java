package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.adw.Dialog;
import org.gnome.adw.NavigationView;
import org.gnome.gio.SimpleAction;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/setup.ui", name = "SetupWindow")
public class SetupDialog extends Dialog {
    @GtkChild(name = "setup-nav-view")
    public NavigationView setupNavView;

    @InstanceInit
    public void init() {
        onRealize(() -> {
            var win = (GrooveboatWindow) getRoot();
            var action = new SimpleAction("close-setup", null);
            action.onActivate((_) -> {
                win.mainStack.setVisibleChildName("content-page");
                forceClose();
            });
            win.addAction(action);
        });
    }

    public SetupDialog() {
    }
}

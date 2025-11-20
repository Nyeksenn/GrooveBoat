package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.adw.Dialog;
import org.gnome.adw.NavigationView;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/setup.ui", name = "SetupWindow")
public class SetupDialog extends Dialog {
    @GtkChild(name = "setup-nav-view")
    public NavigationView setupNavView;

    public SetupDialog() {

    }
}

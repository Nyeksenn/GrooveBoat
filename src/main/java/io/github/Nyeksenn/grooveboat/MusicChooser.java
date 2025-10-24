package io.github.Nyeksenn.grooveboat;

import io.github.jwharm.javagi.gobject.annotations.InstanceInit;
import io.github.jwharm.javagi.gtk.annotations.GtkChild;
import io.github.jwharm.javagi.gtk.annotations.GtkTemplate;
import org.gnome.adw.NavigationPage;
import org.gnome.gtk.MenuButton;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/music-chooser.ui", name = "MusicChooser")
public class MusicChooser extends NavigationPage {

    @GtkChild(name = "headerbar_menu_button")
    public MenuButton headerbarMenuButton;

    @InstanceInit
    public void init() {
    }

    public MusicChooser() {
    }
}

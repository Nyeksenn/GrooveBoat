package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.client.ConnDetails;
import org.gnome.adw.Window;
import org.gnome.gio.Settings;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/window.ui", name = "GrooveboatWindow")
public class GrooveboatWindow extends Window {

    @GtkChild(name = "music-chooser")
    public MusicChooser musicChooser;

    public Settings settings;

    public ConnDetails connDetails;

    public GrooveboatWindow(GrooveboatApp app) {
        setApplication(app);
    }

    @GtkCallback(name = "breakpoint-apply")
    public void handleBreakpointApply() {
//        musicChooser.sidebarToggle.setVisible(true);
        musicChooser.musicSwitcherBar.setReveal(true);
        musicChooser.musicChooserHeader.setTitleWidget(null);
    }

    @GtkCallback(name = "breakpoint-unapply")
    public void handleBreakpointUnapply() {
//        musicChooser.sidebarToggle.setVisible(false);
        musicChooser.musicSwitcherBar.setReveal(false);
        musicChooser.musicChooserHeader.setTitleWidget(musicChooser.musicSwitcherTitle);
    }

    @InstanceInit
    public void init() {
        connDetails = new ConnDetails();
    }
}

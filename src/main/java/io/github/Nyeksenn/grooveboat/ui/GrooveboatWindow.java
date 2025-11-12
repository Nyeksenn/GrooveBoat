package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.client.ConnDetails;
import org.gnome.adw.ApplicationWindow;
import org.gnome.gio.Settings;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/window.ui", name = "GrooveboatWindow")
public class GrooveboatWindow extends ApplicationWindow {
    private static final Logger logger = LoggerFactory.getLogger(GrooveboatWindow.class);

    @GtkChild(name = "music-chooser")
    public MusicChooser musicChooser;

    public Settings settings;

    public ConnDetails connDetails;

    private final ExecutorService exec;

    public GrooveboatWindow(GrooveboatApp app) {
        setApplication(app);
        exec = app.executor;
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

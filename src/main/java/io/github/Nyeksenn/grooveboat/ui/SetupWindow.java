package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.adw.Window;
import org.javagi.gtk.annotations.GtkTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/setup.ui", name = "SetupWindow")
public class SetupWindow extends Window {

    private final ExecutorService exec = Executors.newFixedThreadPool(4);

    public SetupWindow(GrooveboatApp app) {
        setApplication(app);
    }
}

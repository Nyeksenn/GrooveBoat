package io.github.Nyeksenn.grooveboat;

import org.gnome.adw.Application;
import org.gnome.gio.ApplicationFlags;
import org.gnome.gio.SimpleAction;
import org.gnome.glib.Variant;

public class GrooveboatApp extends Application {
    @Override
    public void activate() {
        MusicChooser mc = new MusicChooser();
        GrooveboatWindow win = new GrooveboatWindow(this);
        win.present();
    }

    public void preferencesActivated(Variant parameter) {
        GrooveboatWindow win = (GrooveboatWindow) getActiveWindow();
        GrooveboatPrefs prefs = new GrooveboatPrefs();
        prefs.present(win);
    }

    public void quitActivated(Variant parameter) {
        super.quit();
    }

    @Override
    public void startup() {
        super.startup();

        var preferences = new SimpleAction("preferences", null);
        preferences.onActivate(this::preferencesActivated);
        addAction(preferences);

        var quit = new SimpleAction("quit", null);
        quit.onActivate(this::quitActivated);
        addAction(quit);

        String[] quitAccels = new String[]{"<Ctrl>q"};
        setAccelsForAction("app.quit", quitAccels);
    }

    public GrooveboatApp() {
        setApplicationId("org.gtk.grooveboat");
        setFlags(ApplicationFlags.HANDLES_OPEN);
    }
}

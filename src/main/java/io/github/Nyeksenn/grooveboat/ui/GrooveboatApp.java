package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.Nyeksenn.grooveboat.client.SubsonicClient;
import io.github.Nyeksenn.grooveboat.gobjects.SecretKeeper;
import org.gnome.adw.Application;
import org.gnome.gdk.Display;
import org.gnome.gio.ApplicationFlags;
import org.gnome.gio.Settings;
import org.gnome.gio.SimpleAction;
import org.gnome.glib.Variant;
import org.gnome.gtk.CssProvider;
import org.gnome.gtk.Gtk;
import org.gnome.gtk.IconTheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GrooveboatApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(GrooveboatApp.class);

    public Subsonic subsonic;
    public final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final SecretKeeper secrets = SecretKeeper.getInstance();

    @Override
    public void activate() {
        Settings settings = new Settings("io.github.Nyeksenn.groovebox");
        
        var display = Display.getDefault();

        // Setup Icons
        var theme = IconTheme.getForDisplay(display);
        theme.addResourcePath("/io/github/Nyeksenn/grooveboat/icons/");

        // Setup Styles
        loadStyles(display);

        // Setup Client on Startup
        var username = settings.getString("username");
        var url = settings.getString("url");
        var split = url.split("://");
        var password = secrets.lookupPassword(username, split[0], split[1]).join();
        var client = new SubsonicClient(url, username, password);
        this.subsonic = new Subsonic(client);

        GrooveboatWindow win = new GrooveboatWindow(this);
        win.present();
    }

    private void loadStyles(Display display) {
        var cssProvider = new CssProvider();
        cssProvider.loadFromResource("/io/github/Nyeksenn/grooveboat/main.css");

        Gtk.styleContextAddProviderForDisplay(display, cssProvider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION);
    }

    public void preferencesActivated(Variant parameter) {
        GrooveboatWindow win = (GrooveboatWindow) getActiveWindow();
        GrooveboatPrefs prefs = new GrooveboatPrefs(executor, subsonic);
        prefs.present(win);
    }

    public void quitActivated(Variant parameter) {
        super.quit();
    }

    @Override
    public void startup() {
        super.startup();

        var preferences = new SimpleAction("account-settings", null);
        preferences.onActivate(this::preferencesActivated);
        addAction(preferences);

        var quit = new SimpleAction("quit", null);
        quit.onActivate(this::quitActivated);
        addAction(quit);

        String[] quitAccels = new String[]{"<Ctrl>q"};
        setAccelsForAction("app.quit", quitAccels);
    }

    @Override
    protected void shutdown() {
        super.shutdown();
        try {
            subsonic.close();
        } catch (Exception e) {
            logger.warn("Unable to cleanup client");
        }
        executor.shutdown();
        SecretKeeper.getInstance().close();
    }

    public GrooveboatApp() {
        setApplicationId("io.github.Nyeksenn.grooveboat");
        setFlags(ApplicationFlags.DEFAULT_FLAGS);
    }
}

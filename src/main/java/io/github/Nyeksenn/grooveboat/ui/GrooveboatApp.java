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
    public ExecutorService executor;
    private final SecretKeeper secrets = SecretKeeper.getInstance();

    @Override
    public void activate() {
        Settings settings = new Settings("io.github.Nyeksenn.groovebox");

        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        var display = Display.getDefault();
        var gtkSettings = org.gnome.gtk.Settings.getForDisplay(display);

        // Setup Icons
        var theme = IconTheme.getForDisplay(display);
        theme.addResourcePath("/io/github/Nyeksenn/grooveboat/icons/");

        // Setup Styles

        gtkSettings.onNotify("gtk-application-prefer-dark-theme", (pspec) -> loadStyles(display, gtkSettings));
        gtkSettings.onNotify("gtk-theme-name", (pspec) -> loadStyles(display, gtkSettings));
        loadStyles(display, gtkSettings);

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

    private void loadStyles(Display display, org.gnome.gtk.Settings settings) {
        var cssProvider = new CssProvider();
        var themeName = settings.getProperty("gtk-theme-name").toString();
        var preferDarkTheme = (Boolean) settings.getProperty("gtk-application-prefer-dark-theme");

        if (themeName.toLowerCase().contains("dark") || preferDarkTheme) {
            cssProvider.loadFromResource("/io/github/Nyeksenn/grooveboat/dark-styles.css");
        } else {
            cssProvider.loadFromResource("/io/github/Nyeksenn/grooveboat/light-styles.css");
        }

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

        var preferences = new SimpleAction("preferences", null);
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
        setApplicationId("org.gtk.grooveboat");
        setFlags(ApplicationFlags.HANDLES_OPEN);
    }
}

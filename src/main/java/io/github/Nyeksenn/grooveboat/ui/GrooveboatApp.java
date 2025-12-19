package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.audio.AppPipeline;
import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.Nyeksenn.grooveboat.gobjects.MediaSubsonic;
import io.github.Nyeksenn.grooveboat.gobjects.SecretKeeper;
import org.gnome.adw.Application;
import org.gnome.gdk.Display;
import org.gnome.gio.ApplicationFlags;
import org.gnome.gio.SimpleAction;
import org.gnome.glib.Variant;
import org.gnome.gtk.CssProvider;
import org.gnome.gtk.Gtk;
import org.gnome.gtk.IconTheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class GrooveboatApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(GrooveboatApp.class);
    private static final ExecutorService gstExec = Executors.newSingleThreadExecutor();
    public final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final ScheduledExecutorService streamScheduler = Executors.newScheduledThreadPool(2);

    public AppPipeline pipeline;
    public final Subsonic subsonic = new Subsonic();
    private MediaSubsonic stream;

    public MediaSubsonic getStream() {
        return stream;
    }

    public MediaSubsonic createStream(String songId, long duration, String contentType) throws IllegalArgumentException {
        if (subsonic.isClientInitialized()) {
            this.stream = new MediaSubsonic(
                    songId,
                    duration,
                    subsonic,
                    pipeline,
                    executor,
                    contentType,
                    streamScheduler
            );

            return this.stream;
        } else {
            throw new IllegalArgumentException("Subsonic client not initialized. Stream creation impossible.");
        }
    }

    public boolean closeStream() {
        if (stream != null) {
            stream.destroyStream();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void activate() {
        var display = Display.getDefault();

        // Setup Icons
        var theme = IconTheme.getForDisplay(display);
        theme.addResourcePath("/io/github/Nyeksenn/grooveboat/icons/");

        // Setup Styles
        loadStyles(display);

        var win = new GrooveboatWindow(this);
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
        var win = getActiveWindow();
        win.destroy();
        removeWindow(win);
        quit();
    }

    @Override
    public void startup() {
        super.startup();

        gstExec.submit(() -> {
            pipeline = new AppPipeline();
            pipeline.run();
        });

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
            logger.warn("Couldn't close subsonic");
        }
        closeStream();
        executor.shutdownNow();
        streamScheduler.shutdownNow();
        assert pipeline != null;
        pipeline.quit();
        gstExec.shutdownNow();
        SecretKeeper.getInstance().close();
    }

    public GrooveboatApp() {
        setApplicationId("io.github.Nyeksenn.grooveboat");
        setFlags(ApplicationFlags.DEFAULT_FLAGS);
    }
}

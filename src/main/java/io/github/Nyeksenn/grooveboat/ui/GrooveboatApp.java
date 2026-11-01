package io.github.Nyeksenn.grooveboat.ui;

import static org.javagi.util.Intl.i18n;
import io.ebean.DB;
import io.github.Nyeksenn.grooveboat.audio.AppPipeline;
import io.github.Nyeksenn.grooveboat.audio.PlaybackEvents;
import io.github.Nyeksenn.grooveboat.audio.QueueStore;
import io.github.Nyeksenn.grooveboat.backend.BackendSupport;
import io.github.Nyeksenn.grooveboat.backend.MusicBackend;
import io.github.Nyeksenn.grooveboat.gobjects.RemoteMediaStream;
import io.github.Nyeksenn.grooveboat.gobjects.SecretKeeper;
import io.github.Nyeksenn.grooveboat.library.LibrarySync;
import io.github.Nyeksenn.grooveboat.library.SessionSlot;
import io.github.Nyeksenn.grooveboat.mpris.MprisService;
import io.github.Nyeksenn.grooveboat.utils.AppSettings;
import io.github.Nyeksenn.grooveboat.utils.Helpers;
import org.gnome.adw.Application;
import org.gnome.gdk.Display;
import org.gnome.gio.ApplicationFlags;
import org.gnome.gio.SimpleAction;
import org.gnome.glib.GLib;
import org.gnome.glib.Variant;
import org.gnome.gtk.CssProvider;
import org.gnome.gtk.Gtk;
import org.gnome.gtk.IconTheme;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;

public final class GrooveboatApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(GrooveboatApp.class);

    // Declared before queueStore: the queue registers its actions, which use the pools, when it is constructed.
    public final AppExecutors executors = new AppExecutors();
    private final SessionSlot session = new SessionSlot();

    public AppPipeline pipeline;
    public final QueueStore queueStore = new QueueStore(this);
    private RemoteMediaStream stream;
    private PlaybackEvents playback;
    private boolean loggingOut = false;
    private MprisService mpris;

    // ---- Session

    /**
     * Fills the session slot. A session's backend is never swapped: switching servers or backends requires
     * {@link #logout}.
     *
     * @throws IllegalStateException when a session is already active
     */
    public void startSession(MusicBackend newBackend) {
        session.start(newBackend);
    }

    public boolean hasSession() {
        return session.isActive();
    }

    /** The current session's backend, or {@code null} when logged out. */
    public @Nullable MusicBackend getBackend() {
        return session.backend();
    }

    /** The current session's library sync, or {@code null} when logged out. */
    public @Nullable LibrarySync getLibrary() {
        return session.library();
    }

    /**
     * Ends the session and resets the app to a clean first-run state, then shows the setup dialog in a fresh window.
     * Every step runs even if earlier ones failed.
     */
    public void logout(GrooveboatWindow win) {
        if (loggingOut) {
            return;
        }
        loggingOut = true;
        logger.info("Logging out");

        // On the main thread: block the UI and stop playback.
        if (win != null) {
            win.showLoading(i18n("Logging Out"));
        }
        stream.stop();
        queueStore.clear();

        final var ending = session.current();

        // Off the main thread: network, keyring, database and disk. A dedicated thread, so a busy pool can't block it.
        Thread.ofVirtual().name("grooveboat-logout").start(() -> {
            wipeLocalState(ending);
            GLib.idleAddOnce(() -> finishLogout(win));
        });
    }

    /** Stops the sync, revokes the server session and clears everything the session left on this machine. */
    private void wipeLocalState(SessionSlot.@Nullable Active ending) {
        if (ending != null) {
            if (!ending.library().cancelAndAwait(Duration.ofSeconds(10))) {
                logger.warn("Library sync didn't stop in time; continuing logout");
            }
            ending.backend().logout();
        }
        clearCredential();
        try {
            Helpers.truncateDB(DB.getDefault());
        } catch (SQLException | RuntimeException e) {
            logger.warn("Couldn't clear cache tables", e);
        }
        BackendSupport.clearCoverCache();
        CoverLoader.getInstance().clear();
        AppSettings.resetSession();
    }

    /** On the main thread: drops the session and replaces the window with a fresh one. */
    private void finishLogout(GrooveboatWindow oldWindow) {
        session.end();
        // A fresh widget tree guarantees nothing from the old session survives. The new window is created
        // first so the application never has zero windows (which would make it quit).
        loggingOut = false;
        logger.info("Logged out");
        new GrooveboatWindow(this).present();
        if (oldWindow != null) {
            oldWindow.destroy();
        }
    }

    private void clearCredential() {
        var settings = AppSettings.get();
        var username = settings.getString(AppSettings.USERNAME);
        var url = settings.getString(AppSettings.URL);
        var split = BackendSupport.normalizeBaseUrl(url).split("://", 2);
        if (username.isBlank() || split.length != 2) {
            return;
        }
        try {
            var removed = SecretKeeper.getInstance().clearPassword(username, split[0], split[1]);
            logger.info("Keyring entry removed: {}", removed);
        } catch (RuntimeException e) {
            logger.warn("Couldn't remove keyring entry", e);
        }
    }

    public RemoteMediaStream getStream() {
        return stream;
    }

    // ---- Application

    @Override
    public void activate() {
        var existing = getActiveWindow();
        if (existing != null) {
            existing.present();
            return;
        }

        var display = Display.getDefault();

        // Setup Icons
        var theme = IconTheme.getForDisplay(display);
        theme.addResourcePath("/io/github/Nyeksenn/grooveboat/icons/");

        // Setup Styles
        loadStyles(display);

        var win = new GrooveboatWindow(this);

        var togglePlayingAction = new SimpleAction("toggle-play", null);
        togglePlayingAction.onActivate((_) -> {
            if (stream.isPrepared()) {
                stream.setPlaying(!stream.getPlaying());
            }
        });
        addAction(togglePlayingAction);

        var logoutAction = new SimpleAction("logout", null);
        logoutAction.onActivate((_) -> {
            if (getActiveWindow() instanceof GrooveboatWindow gw) {
                gw.confirmLogout();
            }
        });
        addAction(logoutAction);

        playback.watch(pipeline.getBus());

        win.present();
    }

    private void loadStyles(Display display) {
        var cssProvider = new CssProvider();
        cssProvider.loadFromResource("/io/github/Nyeksenn/grooveboat/main.css");

        Gtk.styleContextAddProviderForDisplay(display, cssProvider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION);
    }

    public void preferencesActivated(Variant parameter) {
        var backend = session.backend();
        if (!(getActiveWindow() instanceof GrooveboatWindow win) || backend == null) {
            return;
        }
        var prefs = new GrooveboatPrefs(backend, executors.io);
        prefs.present(win);
    }

    public void quitActivated(Variant parameter) {
        var win = getActiveWindow();
        if (win != null) {
            win.destroy();
            removeWindow(win);
        }
        quit();
    }

    @Override
    public void startup() {
        super.startup();

        // Created synchronously so it exists before activate() watches its bus; its main loop runs on the GStreamer executor.
        pipeline = new AppPipeline();
        executors.gst.submit(pipeline::run);
        // One media stream for the app's lifetime; tracks are loaded into it.
        stream = new RemoteMediaStream(this::getBackend, pipeline, executors.io, executors.scheduler);
        playback = new PlaybackEvents(pipeline, stream, queueStore, message -> {
            if (getActiveWindow() instanceof GrooveboatWindow gw) {
                gw.showToast(message);
            }
        });

        mpris = MprisService.start(this);

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
        if (mpris != null) {
            mpris.close();
        }
        if (stream != null) {
            stream.stop();
        }
        session.end();
        executors.shutdownWorkers();
        if (pipeline != null) {
            pipeline.quit();
        }
        executors.shutdownGst();
    }

    public GrooveboatApp() {
        setApplicationId("io.github.Nyeksenn.grooveboat");
        setFlags(ApplicationFlags.DEFAULT_FLAGS);
    }
}

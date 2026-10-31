package io.github.Nyeksenn.grooveboat.ui;

import static org.javagi.util.Intl.i18n;
import io.ebean.DB;
import io.github.Nyeksenn.grooveboat.audio.AppPipeline;
import io.github.Nyeksenn.grooveboat.audio.QueueStore;
import io.github.Nyeksenn.grooveboat.backend.BackendSupport;
import io.github.Nyeksenn.grooveboat.backend.MusicBackend;
import io.github.Nyeksenn.grooveboat.gobjects.RemoteMediaStream;
import io.github.Nyeksenn.grooveboat.gobjects.SecretKeeper;
import io.github.Nyeksenn.grooveboat.library.LibrarySync;
import io.github.Nyeksenn.grooveboat.utils.AppSettings;
import io.github.Nyeksenn.grooveboat.utils.Helpers;
import org.freedesktop.gstreamer.gst.Bus;
import org.freedesktop.gstreamer.gst.Message;
import org.freedesktop.gstreamer.gst.State;
import org.gnome.adw.Application;
import org.gnome.gdk.Display;
import org.gnome.gio.ApplicationFlags;
import org.gnome.gio.SimpleAction;
import org.gnome.glib.GError;
import org.gnome.glib.GLib;
import org.gnome.glib.Variant;
import org.gnome.gtk.CssProvider;
import org.gnome.gtk.Gtk;
import org.gnome.gtk.IconTheme;
import org.javagi.base.Out;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class GrooveboatApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(GrooveboatApp.class);
    private static final ExecutorService gstExec = Executors.newSingleThreadExecutor();
    public final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    public final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    public AppPipeline pipeline;
    public final QueueStore queueStore = new QueueStore(this);
    private RemoteMediaStream stream;
    public boolean shouldLoop = false;
    private boolean buffering = false;
    private boolean loggingOut = false;

    /** The backend of the current session. Filled once per session, emptied only by logout. */
    private volatile MusicBackend backend;
    private volatile LibrarySync library;

    // ---- Session

    /**
     * Fills the session's backend slot. A session's backend is never swapped: switching servers or backends
     * requires {@link #logout}.
     *
     * @throws IllegalStateException when a session is already active
     */
    public synchronized void startSession(MusicBackend newBackend) {
        if (backend != null) {
            throw new IllegalStateException("A session is already active; log out first");
        }
        logger.info("Starting {} session for {}", newBackend.type().displayName(), newBackend.host());
        this.backend = newBackend;
        this.library = new LibrarySync(newBackend);
    }

    public boolean hasSession() {
        return backend != null;
    }

    /** The current session's backend, or {@code null} when logged out. */
    public MusicBackend getBackend() {
        return backend;
    }

    /** The current session's library sync, or {@code null} when logged out. */
    public LibrarySync getLibrary() {
        return library;
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

        final var oldBackend = backend;
        final var oldLibrary = library;

        // Off the main thread: network, keyring, database and disk. A dedicated thread, so a busy pool can't block it.
        Thread.ofVirtual().name("grooveboat-logout").start(() -> {
            if (oldLibrary != null) {
                if (!oldLibrary.cancelAndAwait(Duration.ofSeconds(10))) {
                    logger.warn("Library sync didn't stop in time; continuing logout");
                }
            }
            if (oldBackend != null) {
                oldBackend.logout();
            }
            clearCredential();
            try {
                Helpers.truncateDB(DB.getDefault());
            } catch (SQLException | RuntimeException e) {
                logger.warn("Couldn't clear cache tables: {}", e.getMessage());
            }
            BackendSupport.clearCoverCache();
            CoverLoader.getInstance().clear();
            AppSettings.resetSession();

            GLib.idleAddOnce(() -> {
                synchronized (this) {
                    if (oldLibrary != null) {
                        oldLibrary.close();
                    }
                    if (oldBackend != null) {
                        oldBackend.close();
                    }
                    backend = null;
                    library = null;
                }
                // A fresh widget tree guarantees nothing from the old session survives. The new window is created
                // first so the application never has zero windows (which would make it quit).
                loggingOut = false;
                logger.info("Logged out");
                new GrooveboatWindow(this).present();
                if (win != null) {
                    win.destroy();
                }
            });
        });
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
            var removed = SecretKeeper.getInstance().clearPassword(username, split[0], split[1]).join();
            logger.info("Keyring entry removed: {}", removed);
        } catch (RuntimeException e) {
            logger.warn("Couldn't remove keyring entry: {}", e.getMessage());
        }
    }

    // ---- Playback

    /**
     * Subscribes to the bus messages the app handles, one detailed signal per type.
     * <p>
     * Deliberately not a catch-all bus watch: decoding a message type in java-gi 0.14.1 ({@code Message.readType()})
     * never returns for "extended" types (bit 31 set, e.g. stream-collection and streams-selected, which
     * {@code playbin3} posts for every stream), because its flag conversion uses a signed shift. With detailed
     * signals, other message types never reach Java code.
     */
    private void watchBus(Bus bus) {
        bus.addSignalWatch();
        bus.onMessage("eos", this::onEndOfStream);
        bus.onMessage("stream-start", this::onStreamStart);
        bus.onMessage("error", this::onError);
        bus.onMessage("buffering", this::onBuffering);
    }

    /** With gapless playback, EOS only arrives when no follow-up track was handed to the pipeline in time. */
    private void onEndOfStream(Message msg) {
        logger.info("End of Stream");
        if (!queueStore.advance(shouldLoop)) {
            stream.endOfQueue();
        }
    }

    /** A new track reached the sink. After a gapless switch, the queue follows the stream. */
    private void onStreamStart(Message msg) {
        // Deliberately keeps the buffering flag: stream-start can arrive while paused for buffering, and the
        // following 100% message must still resume playback.
        var item = stream.onStreamStart();
        if (item != null) {
            queueStore.followGaplessSwitch(item);
        }
    }

    private void onError(Message msg) {
        Out<GError> error = new Out<>();
        Out<String> debug = new Out<>();
        msg.parseError(error, debug);
        // The debug string can contain the stream URI with credentials, so only the message is logged.
        logger.warn("Playback error: {}", error.get().readMessage());
        handlePlaybackError();
    }

    private void onBuffering(Message msg) {
        Out<Integer> percent = new Out<>();
        msg.parseBuffering(percent);
        handleBuffering(percent.get());
    }

    /** Network streams: hold playback while the buffer refills, then resume if the user still wants to play. */
    private void handleBuffering(int percent) {
        if (stream.getCurrentItem() == null) {
            return;
        }
        // The follow-up track starts buffering while the current one still plays out; pausing would cause a gap.
        if (percent < 100 && stream.isSwitching()) {
            return;
        }
        if (percent < 100) {
            if (!buffering && stream.getPlaying()) {
                logger.debug("Buffering ({}%), pausing", percent);
                buffering = true;
                pipeline.setState(State.PAUSED);
            }
        } else if (buffering) {
            buffering = false;
            if (stream.getPlaying()) {
                logger.debug("Buffering done, resuming");
                pipeline.setState(State.PLAYING);
            }
        }
    }

    private void handlePlaybackError() {
        buffering = false;
        var current = stream.getCurrentItem();
        if (current == null) {
            return;
        }
        stream.fail();
        if (getActiveWindow() instanceof GrooveboatWindow gw) {
            gw.showToast(String.format(i18n("Couldn't play \u201c%s\u201d"), current.getSongName()));
        }
        queueStore.advance(false);
    }

    public RemoteMediaStream getStream() {
        return stream;
    }

    /** Turns loop mode on or off; the prepared follow-up track changes accordingly. */
    public void setLoop(boolean loop) {
        shouldLoop = loop;
        queueStore.refreshNext();
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

        watchBus(pipeline.getBus());

        win.present();
    }

    private void loadStyles(Display display) {
        var cssProvider = new CssProvider();
        cssProvider.loadFromResource("/io/github/Nyeksenn/grooveboat/main.css");

        Gtk.styleContextAddProviderForDisplay(display, cssProvider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION);
    }

    public void preferencesActivated(Variant parameter) {
        if (!(getActiveWindow() instanceof GrooveboatWindow win) || backend == null) {
            return;
        }
        var prefs = new GrooveboatPrefs(backend, executor);
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

        // Created synchronously so it exists before activate() watches its bus; its main loop runs on gstExec.
        pipeline = new AppPipeline();
        gstExec.submit(pipeline::run);
        // One media stream for the app's lifetime; tracks are loaded into it.
        stream = new RemoteMediaStream(this::getBackend, pipeline, executor, scheduler);

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
        if (stream != null) {
            stream.stop();
        }
        var currentLibrary = library;
        if (currentLibrary != null) {
            currentLibrary.close();
        }
        var currentBackend = backend;
        if (currentBackend != null) {
            currentBackend.close();
        }
        executor.shutdownNow();
        scheduler.shutdownNow();
        if (pipeline != null) {
            pipeline.quit();
        }
        gstExec.shutdownNow();
        SecretKeeper.getInstance().close();
    }

    public GrooveboatApp() {
        setApplicationId("io.github.Nyeksenn.grooveboat");
        setFlags(ApplicationFlags.DEFAULT_FLAGS);
    }
}

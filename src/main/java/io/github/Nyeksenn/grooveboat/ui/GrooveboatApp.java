package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.audio.AppPipeline;
import io.github.Nyeksenn.grooveboat.audio.QueueStore;
import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.Nyeksenn.grooveboat.gobjects.MediaSubsonic;
import io.github.Nyeksenn.grooveboat.gobjects.SecretKeeper;
import org.freedesktop.gstreamer.gst.Bus;
import org.freedesktop.gstreamer.gst.Message;
import org.freedesktop.gstreamer.gst.MessageType;
import org.freedesktop.gstreamer.gst.State;
import org.gnome.adw.Application;
import org.gnome.gdk.Display;
import org.gnome.gio.ApplicationFlags;
import org.gnome.gio.SimpleAction;
import org.gnome.glib.GError;
import org.gnome.glib.Variant;
import org.gnome.gtk.CssProvider;
import org.gnome.gtk.Gtk;
import org.gnome.gtk.IconTheme;
import org.javagi.base.Out;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class GrooveboatApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(GrooveboatApp.class);
    private static final ExecutorService gstExec = Executors.newSingleThreadExecutor();
    public final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    public final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    public AppPipeline pipeline;
    public final Subsonic subsonic = new Subsonic();
    public final QueueStore queueStore = new QueueStore(this);
    private MediaSubsonic stream;
    public boolean shouldLoop = false;

    public boolean handleBusMessage(Bus bus, Message msg) {
        if (msg.readType().contains(MessageType.EOS)) {
            logger.info("End of Stream");
            var action = lookupAction("play-next");
            action.activate(Variant.boolean_(shouldLoop));
        } else if (msg.readType().contains(MessageType.ERROR)) {
            Out<GError> error = new Out<>();
            Out<String> debug = new Out<>();
            msg.parseError(error, debug);

            logger.warn("Error: {}", error.get().readMessage());

            pipeline.setState(State.NULL);
            pipeline.setState(State.PAUSED);
        } else if (msg.readType().contains(MessageType.STATE_CHANGED)) {
            Out<State> prev = new Out<>();
            Out<State> curr = new Out<>();
            msg.parseStateChanged(prev, curr, null);
            if (prev.get().equals(State.PAUSED) && curr.get().equals(State.PLAYING)) {
                if (!stream.timestampUpdateRunning()) {
                    stream.startTimestampUpdate();
                }
            } else if (curr.get().equals(State.PAUSED) && prev.get().equals(State.PLAYING)) {
                stream.cancelTimestampUpdate();
            }
        }

        return true;
    }

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
                    scheduler
            );

            stream.initializeStream();
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

        var togglePlayingAction = new SimpleAction("toggle-play", null);
        togglePlayingAction.onActivate((_) -> {
            if (stream != null) {
                stream.setPlaying(!stream.getPlaying());
            }
        });
        addAction(togglePlayingAction);

        var bus = pipeline.getBus();
        bus.addWatch(0, this::handleBusMessage);

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
        scheduler.shutdownNow();
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

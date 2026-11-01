package io.github.Nyeksenn.grooveboat.mpris;

import org.jspecify.annotations.Nullable;
import io.github.Nyeksenn.grooveboat.audio.QueueStore;
import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import io.github.Nyeksenn.grooveboat.ui.GrooveboatApp;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.Variant;
import org.javagi.gobject.SignalConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes the app on the session bus as an MPRIS player and keeps it in sync: on the main thread it re-captures an
 * {@link MprisState} whenever playback or the queue changes and sends {@code PropertiesChanged} for the keys that
 * differ. Failure to connect disables MPRIS, nothing more.
 */
public final class MprisService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MprisService.class);
    public static final String BUS_NAME = "org.mpris.MediaPlayer2.io.github.Nyeksenn.grooveboat";
    public static final String OBJECT_PATH = "/org/mpris/MediaPlayer2";
    private static final String PLAYER = "org.mpris.MediaPlayer2.Player";
    private static final String TRACKLIST = "org.mpris.MediaPlayer2.TrackList";

    private final DBusConnection connection;
    private final GrooveboatApp app;
    private volatile MprisState state = MprisState.empty();
    private final List<SignalConnection<?>> connections = new java.util.ArrayList<>();
    private final Runnable modeListener = this::refresh;

    private MprisService(DBusConnection connection, GrooveboatApp app) {
        this.connection = connection;
        this.app = app;
    }

    /** Connects and claims the name; returns {@code null} (after logging) when that isn't possible. Main thread. */
    public static @Nullable MprisService start(GrooveboatApp app) {
        DBusConnection connection = null;
        try {
            connection = DBusConnectionBuilder.forSessionBus().build();
            var service = new MprisService(connection, app);
            connection.exportObject(OBJECT_PATH, new MprisObject(app, () -> service.state));
            connection.requestBusName(BUS_NAME);
            service.state = MprisState.capture(app.getStream(), app.queueStore);
            service.listen();
            logger.info("MPRIS available as {}", BUS_NAME);
            return service;
        } catch (Exception | LinkageError e) {
            logger.warn("MPRIS disabled: {}", e.getMessage());
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception _) {
                    // Already failing.
                }
            }
            return null;
        }
    }

    private void listen() {
        var stream = app.getStream();
        for (var prop : new String[]{"playing", "prepared", "volume", "muted", "duration"}) {
            connections.add(stream.onNotify(prop, _ -> refresh()));
        }
        connections.add(app.queueStore.connect("initialize-song", (QueueStore.InitializeSong) _ -> refresh()));
        connections.add(app.queueStore.getSelection().onNotify("selected", _ -> refresh()));
        connections.add(app.queueStore.getModel().onItemsChanged(this::itemsChanged));
        app.queueStore.addModeListener(modeListener);
        stream.setSeekListener(positionUs -> sendSignal(() -> new MediaPlayer2Player.Seeked(OBJECT_PATH, positionUs)));
    }

    private void send(DBusSignal signal) {
        try {
            connection.sendMessage(signal);
        } catch (RuntimeException e) {
            logger.debug("Couldn't send MPRIS signal: {}", e.getMessage());
        }
    }

    private void sendSignal(SignalFactory factory) {
        try {
            send(factory.create());
        } catch (Exception e) {
            logger.debug("Couldn't create MPRIS signal: {}", e.getMessage());
        }
    }

    @FunctionalInterface
    private interface SignalFactory {
        DBusSignal create() throws Exception;
    }

    /** Queue entries were added or removed: TrackList signals, then the property diff. */
    private void itemsChanged(int position, int removed, int added) {
        var old = state;
        var model = app.queueStore.getModel();
        if (removed == 0 && added == 1) {
            var item = model.getItem(position);
            var after = position > 0 ? model.getItem(position - 1).getTrackId() : MprisState.NO_TRACK;
            sendSignal(() -> new MediaPlayer2TrackList.TrackAdded(OBJECT_PATH, MprisState.metadataOf(item), new DBusPath(after)));
        } else if (removed == 1 && added == 0 && position < old.trackIds().size()) {
            var gone = old.trackIds().get(position);
            sendSignal(() -> new MediaPlayer2TrackList.TrackRemoved(OBJECT_PATH, new DBusPath(gone)));
        } else {
            var fresh = MprisState.capture(app.getStream(), app.queueStore);
            sendSignal(() -> new MediaPlayer2TrackList.TrackListReplaced(OBJECT_PATH,
                    fresh.trackIds().stream().map(DBusPath::new).toList(), new DBusPath(fresh.currentTrackId())));
        }
        refresh();
    }

    /** Captures the current state and announces what changed. Main thread. */
    private void refresh() {
        var old = state;
        var fresh = MprisState.capture(app.getStream(), app.queueStore);
        state = fresh;
        announce(PLAYER, old.playerProperties(), fresh.playerProperties());
        announce(TRACKLIST, old.trackListProperties(), fresh.trackListProperties());
    }

    private void announce(String iface, Map<String, Variant<?>> before, Map<String, Variant<?>> after) {
        var changed = new HashMap<String, Variant<?>>();
        after.forEach((key, value) -> {
            if (!value.equals(before.get(key))) {
                changed.put(key, value);
            }
        });
        if (changed.isEmpty()) {
            return;
        }
        sendSignal(() -> new Properties.PropertiesChanged(OBJECT_PATH, iface, changed, List.of()));
    }

    @Override
    public void close() {
        connections.forEach(SignalConnection::disconnect);
        connections.clear();
        app.queueStore.removeModeListener(modeListener);
        app.getStream().setSeekListener(null);
        try {
            connection.releaseBusName(BUS_NAME);
            connection.close();
        } catch (Exception e) {
            logger.debug("Closing MPRIS connection: {}", e.getMessage());
        }
    }
}

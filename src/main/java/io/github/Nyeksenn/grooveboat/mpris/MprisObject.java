package io.github.Nyeksenn.grooveboat.mpris;

import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import io.github.Nyeksenn.grooveboat.ui.GrooveboatApp;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.types.Variant;
import org.gnome.glib.GLib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The exported MPRIS object. Methods run on D-Bus handler threads: they read the {@link MprisState} snapshot and post
 * every action to the GTK main thread; they never touch GTK objects themselves.
 */
final class MprisObject implements MediaPlayer2, MediaPlayer2Player, MediaPlayer2TrackList, Properties {
    private static final Logger logger = LoggerFactory.getLogger(MprisObject.class);
    private static final String ROOT = "org.mpris.MediaPlayer2";
    private static final String PLAYER = "org.mpris.MediaPlayer2.Player";
    private static final String TRACKLIST = "org.mpris.MediaPlayer2.TrackList";
    private static final String SONG_URI_PREFIX = "grooveboat:song:";

    private final GrooveboatApp app;
    private final Supplier<MprisState> state;

    MprisObject(GrooveboatApp app, Supplier<MprisState> state) {
        this.app = app;
        this.state = state;
    }

    private static void onMain(Runnable r) {
        GLib.idleAddOnce(() -> {
            try {
                r.run();
            } catch (RuntimeException e) {
                logger.warn("MPRIS action failed", e);
            }
        });
    }

    @Override
    public String getObjectPath() {
        return MprisService.OBJECT_PATH;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    // ---- org.mpris.MediaPlayer2

    @Override
    public void Raise() {
        onMain(() -> {
            var win = app.getActiveWindow();
            if (win != null) {
                win.present();
            } else {
                app.activate();
            }
        });
    }

    @Override
    public void Quit() {
        onMain(() -> app.activateAction("quit", null));
    }

    // ---- org.mpris.MediaPlayer2.Player

    @Override
    public void Next() {
        onMain(() -> app.queueStore.advance(false));
    }

    @Override
    public void Previous() {
        onMain(app.queueStore::previous);
    }

    @Override
    public void Pause() {
        onMain(() -> {
            if (app.getStream().isPrepared()) {
                app.getStream().setPlaying(false);
            }
        });
    }

    @Override
    public void Play() {
        onMain(() -> {
            if (app.getStream().isPrepared()) {
                app.getStream().setPlaying(true);
            }
        });
    }

    @Override
    public void PlayPause() {
        onMain(() -> {
            var stream = app.getStream();
            if (stream.isPrepared()) {
                stream.setPlaying(!stream.getPlaying());
            }
        });
    }

    @Override
    public void Stop() {
        onMain(() -> {
            var stream = app.getStream();
            if (stream.isPrepared()) {
                stream.setPlaying(false);
                stream.seek(0L);
            }
        });
    }

    @Override
    public void Seek(long offsetMicros) {
        onMain(() -> {
            var stream = app.getStream();
            if (!stream.isPrepared()) {
                return;
            }
            long target = Math.max(0L, app.pipeline.queryPosition()) + offsetMicros;
            if (target >= stream.getDuration()) {
                if (!app.queueStore.advance(false)) {
                    stream.endOfQueue();
                }
            } else {
                stream.seek(Math.max(0L, target));
            }
        });
    }

    @Override
    public void SetPosition(DBusPath trackId, long positionMicros) {
        onMain(() -> {
            var stream = app.getStream();
            var current = stream.getCurrentItem();
            if (current == null || !stream.isPrepared() || !current.getTrackId().equals(trackId.getPath())
                    || positionMicros < 0 || positionMicros > stream.getDuration()) {
                return;
            }
            stream.seek(positionMicros);
        });
    }

    @Override
    public void OpenUri(String uri) {
        // No URI schemes are supported.
    }

    // ---- org.mpris.MediaPlayer2.TrackList

    @Override
    public List<Map<String, Variant<?>>> GetTracksMetadata(List<DBusPath> trackIds) {
        var tracks = state.get().trackMetadata();
        var result = new ArrayList<Map<String, Variant<?>>>();
        for (var id : trackIds) {
            var meta = tracks.get(id.getPath());
            if (meta != null) {
                result.add(meta);
            }
        }
        return result;
    }

    /** Appends the song named by a {@code grooveboat:song:<id>} URI to the queue; other URIs are ignored. */
    @Override
    public void AddTrack(String uri, DBusPath afterTrack, boolean setAsCurrent) {
        if (uri == null || !uri.startsWith(SONG_URI_PREFIX)) {
            return;
        }
        var songId = uri.substring(SONG_URI_PREFIX.length());
        onMain(() -> app.activateAction("queue-song", org.gnome.glib.Variant.string(songId)));
    }

    @Override
    public void RemoveTrack(DBusPath trackId) {
        onMain(() -> {
            var model = app.queueStore.getModel();
            for (int i = 0; i < model.getNItems(); i++) {
                if (model.getItem(i).getTrackId().equals(trackId.getPath())) {
                    model.removeAt(i);
                    return;
                }
            }
        });
    }

    @Override
    public void GoTo(DBusPath trackId) {
        onMain(() -> {
            var model = app.queueStore.getModel();
            for (int i = 0; i < model.getNItems(); i++) {
                SongListItem item = model.getItem(i);
                if (item.getTrackId().equals(trackId.getPath())) {
                    app.queueStore.select(item);
                    return;
                }
            }
        });
    }

    // ---- org.freedesktop.DBus.Properties

    private Map<String, Variant<?>> properties(String iface) {
        var s = state.get();
        return switch (iface) {
            case ROOT -> s.rootProperties();
            case PLAYER -> {
                var m = new HashMap<>(s.playerProperties());
                m.put("Position", new Variant<>(position()));
                yield m;
            }
            case TRACKLIST -> s.trackListProperties();
            default -> throw new DBusExecutionException("Unknown interface " + iface);
        };
    }

    private long position() {
        return Math.max(0L, app.pipeline.queryPosition());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <A> A Get(String iface, String property) {
        var value = properties(iface).get(property);
        if (value == null) {
            throw new DBusExecutionException("Unknown property " + iface + "." + property);
        }
        return (A) value;
    }

    @Override
    public <A> void Set(String iface, String property, A value) {
        Object raw = value instanceof Variant<?> v ? v.getValue() : value;
        if (!PLAYER.equals(iface)) {
            throw new DBusExecutionException("Property is read-only: " + property);
        }
        switch (property) {
            case "Volume" -> {
                if (!(raw instanceof Number number)) {
                    throw invalidType(property);
                }
                double volume = Math.max(0.0, Math.min(1.0, number.doubleValue()));
                onMain(() -> {
                    app.getStream().setMuted(volume == 0.0);
                    app.getStream().setVolume(volume);
                });
            }
            case "Shuffle" -> {
                if (!(raw instanceof Boolean shuffle)) {
                    throw invalidType(property);
                }
                onMain(() -> app.queueStore.setShuffle(shuffle));
            }
            case "LoopStatus" -> {
                if (!(raw instanceof String status)) {
                    throw invalidType(property);
                }
                var mode = MprisState.repeatMode(status)
                        .orElseThrow(() -> new DBusExecutionException("Invalid LoopStatus"));
                onMain(() -> app.queueStore.setRepeat(mode));
            }
            case "Rate" -> {
                // Only 1.0 is supported; ignore.
            }
            default -> throw new DBusExecutionException("Property is read-only: " + property);
        }
    }

    private static DBusExecutionException invalidType(String property) {
        return new DBusExecutionException("Invalid value type for " + property);
    }

    @Override
    public Map<String, Variant<?>> GetAll(String iface) {
        return properties(iface);
    }
}

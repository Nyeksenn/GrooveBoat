package io.github.Nyeksenn.grooveboat.mpris;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusProperties;
import org.freedesktop.dbus.annotations.DBusProperty;
import org.freedesktop.dbus.annotations.DBusProperty.Access;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.Variant;

import java.util.List;
import java.util.Map;

/** {@code org.mpris.MediaPlayer2.TrackList}: the play queue. */
@DBusProperties({
    @DBusProperty(name = "Tracks", type = MediaPlayer2TrackList.TracksType.class, access = Access.READ),
    @DBusProperty(name = "CanEditTracks", type = Boolean.class, access = Access.READ)
})
@DBusInterfaceName("org.mpris.MediaPlayer2.TrackList")
public interface MediaPlayer2TrackList extends DBusInterface {
    /** Type of the Tracks property (ao). */
    interface TracksType extends org.freedesktop.dbus.TypeRef<List<DBusPath>> {
    }

    List<Map<String, Variant<?>>> GetTracksMetadata(List<DBusPath> trackIds);

    void AddTrack(String uri, DBusPath afterTrack, boolean setAsCurrent);

    void RemoveTrack(DBusPath trackId);

    void GoTo(DBusPath trackId);

    class TrackListReplaced extends DBusSignal {
        public TrackListReplaced(String path, List<DBusPath> tracks, DBusPath currentTrack) throws DBusException {
            super(path, tracks, currentTrack);
        }
    }

    class TrackAdded extends DBusSignal {
        public TrackAdded(String path, Map<String, Variant<?>> metadata, DBusPath afterTrack) throws DBusException {
            super(path, metadata, afterTrack);
        }
    }

    class TrackRemoved extends DBusSignal {
        public TrackRemoved(String path, DBusPath trackId) throws DBusException {
            super(path, trackId);
        }
    }
}

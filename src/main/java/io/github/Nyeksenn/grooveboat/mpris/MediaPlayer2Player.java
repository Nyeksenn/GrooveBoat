package io.github.Nyeksenn.grooveboat.mpris;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusProperties;
import org.freedesktop.dbus.annotations.DBusProperty;
import org.freedesktop.dbus.annotations.DBusProperty.Access;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.DBusPath;

/** {@code org.mpris.MediaPlayer2.Player}. Properties are served through the Properties interface. */
@DBusProperties({
    @DBusProperty(name = "PlaybackStatus", type = String.class, access = Access.READ),
    @DBusProperty(name = "LoopStatus", type = String.class, access = Access.READ_WRITE),
    @DBusProperty(name = "Rate", type = Double.class, access = Access.READ_WRITE),
    @DBusProperty(name = "MinimumRate", type = Double.class, access = Access.READ),
    @DBusProperty(name = "MaximumRate", type = Double.class, access = Access.READ),
    @DBusProperty(name = "Shuffle", type = Boolean.class, access = Access.READ_WRITE),
    @DBusProperty(name = "Metadata", type = MediaPlayer2Player.MetadataType.class, access = Access.READ),
    @DBusProperty(name = "Volume", type = Double.class, access = Access.READ_WRITE),
    @DBusProperty(name = "Position", type = Long.class, access = Access.READ, emitChangeSignal = org.freedesktop.dbus.annotations.PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
    @DBusProperty(name = "CanGoNext", type = Boolean.class, access = Access.READ),
    @DBusProperty(name = "CanGoPrevious", type = Boolean.class, access = Access.READ),
    @DBusProperty(name = "CanPlay", type = Boolean.class, access = Access.READ),
    @DBusProperty(name = "CanPause", type = Boolean.class, access = Access.READ),
    @DBusProperty(name = "CanSeek", type = Boolean.class, access = Access.READ),
    @DBusProperty(name = "CanControl", type = Boolean.class, access = Access.READ)
})
@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
public interface MediaPlayer2Player extends DBusInterface {
    /** Type of the Metadata property (a{sv}). */
    interface MetadataType extends org.freedesktop.dbus.TypeRef<java.util.Map<String, org.freedesktop.dbus.types.Variant<?>>> {
    }

    void Next();

    void Previous();

    void Pause();

    void PlayPause();

    void Stop();

    void Play();

    void Seek(long offsetMicros);

    void SetPosition(DBusPath trackId, long positionMicros);

    void OpenUri(String uri);

    class Seeked extends DBusSignal {
        public final long position;

        public Seeked(String path, long position) throws DBusException {
            super(path, position);
            this.position = position;
        }
    }
}

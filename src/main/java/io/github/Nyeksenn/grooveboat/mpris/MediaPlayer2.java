package io.github.Nyeksenn.grooveboat.mpris;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusProperties;
import org.freedesktop.dbus.annotations.DBusProperty;
import org.freedesktop.dbus.annotations.DBusProperty.Access;
import org.freedesktop.dbus.interfaces.DBusInterface;

/** The MPRIS root interface. Properties are served through the Properties interface. */
@DBusProperties({
    @DBusProperty(name = "CanQuit", type = Boolean.class, access = Access.READ),
    @DBusProperty(name = "CanRaise", type = Boolean.class, access = Access.READ),
    @DBusProperty(name = "HasTrackList", type = Boolean.class, access = Access.READ),
    @DBusProperty(name = "CanSetFullscreen", type = Boolean.class, access = Access.READ),
    @DBusProperty(name = "Fullscreen", type = Boolean.class, access = Access.READ),
    @DBusProperty(name = "Identity", type = String.class, access = Access.READ),
    @DBusProperty(name = "DesktopEntry", type = String.class, access = Access.READ),
    @DBusProperty(name = "SupportedUriSchemes", type = String[].class, access = Access.READ),
    @DBusProperty(name = "SupportedMimeTypes", type = String[].class, access = Access.READ)
})
@DBusInterfaceName("org.mpris.MediaPlayer2")
public interface MediaPlayer2 extends DBusInterface {
    void Raise();

    void Quit();
}

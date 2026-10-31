# MPRIS (desktop media controls)

GrooveBoat exports an [MPRIS 2.2](https://specifications.freedesktop.org/mpris-spec/latest/) player on the session bus as `org.mpris.MediaPlayer2.io.github.Nyeksenn.grooveboat`, at `/org/mpris/MediaPlayer2`. Media keys, the GNOME/KDE media widgets, lock-screen controls and `playerctl` use it. It uses [dbus-java](https://github.com/hypfvieh/dbus-java) directly with the JDK unix-socket transport, not Gio's D-Bus. If the session bus is unavailable or the name is taken, MPRIS is skipped with a warning and the app runs normally.

## Pieces (`mpris/`)

- `MprisService`: owns the connection, claims the name and exports the object. Listens (main thread) to the media stream's `playing`/`prepared`/`volume`/`muted`/`duration` notifications, the queue selection, `initialize-song`, the queue's item changes and its repeat/shuffle changes. Each time it re-captures an `MprisState` and sends `PropertiesChanged` only for the keys that differ. Queue changes also send `TrackAdded`/`TrackRemoved`/`TrackListReplaced`; successful seeks send `Seeked`.
- `MprisState`: immutable snapshot of all exposed properties (except `Position`) and the track metadata. Built on the GTK main thread, published through a `volatile` field.
- `MprisObject`: the exported object, implementing the root, `Player` and `TrackList` interfaces and `org.freedesktop.DBus.Properties` by hand. D-Bus handler threads only read the snapshot and post every action to the main thread with `GLib.idleAddOnce`; they never touch GTK objects. `Position` is queried from the pipeline on demand and never announced.

## Behaviour

- **Metadata**: `mpris:trackid` (unique per queue entry, `SongListItem.getTrackId()`, so a song queued twice has two ids), `mpris:length`, `mpris:artUrl` (`file://` URL into the cover cache), `xesam:title`/`album`/`artist`. There is deliberately no `xesam:url`: playback URLs carry credentials.
- **Status**: `Stopped` without a loaded song, otherwise `Playing`/`Paused` from the media stream. `Stop` pauses and rewinds.
- **LoopStatus / Shuffle**: map to `QueueStore.RepeatMode` (`NONE`/`TRACK`/`PLAYLIST`) and the shuffle flag. Shuffle keeps a separate play order (the visible queue is not reordered). Repeat-track only applies when a song ends on its own; a manual skip moves on.
- **TrackList**: the queue. `AddTrack` accepts `grooveboat:song:<id>` URIs and appends; `GoTo` plays an entry; `RemoveTrack` removes it.
- **Volume** follows the media stream (and the volume button follows it back).

## Packaging

The Flatpak manifest needs `--own-name=org.mpris.MediaPlayer2.io.github.Nyeksenn.grooveboat`.

# GrooveBoat Documentation

GrooveBoat is a GTK4 / libadwaita desktop music player for Jellyfin and OpenSubsonic servers. It is written in Java 27 and talks to the native GNOME stack (GTK, libadwaita, GStreamer, libsecret, GSettings) through the java-gi bindings. Library metadata is cached locally in an embedded H2 database. GStreamer plays the original audio straight from server URLs: HLS for Jellyfin, progressive HTTP for OpenSubsonic.

## Architecture

1. [Overview](architecture/overview.md): the layers, the packages, how they depend on each other, and the backend boundary.
2. [Startup and Lifecycle](architecture/lifecycle.md): what happens from launch to the first visible window, session restore, first-run setup, logout, and shutdown.
3. [Data and Synchronisation](architecture/data.md): the two backends, library sync, the local cache database, and cover-art caching.
4. [Playback](architecture/playback.md): the GStreamer pipeline, playback sources, the media stream object, the play queue, and how the player widgets follow along.
5. [User Interface](architecture/ui.md): the window structure, dialogs, the templated widgets, the resource bundle, actions, adaptive layout, and widget lifetime.
6. [Threading Model](architecture/threading.md): which work runs on which thread, and how results get back to the UI.
7. [Configuration and Storage](architecture/storage.md): settings, secrets, and the files written to disk.
8. [MPRIS](architecture/mpris.md): the desktop media-control service, its threading rule, and how repeat/shuffle map onto the queue.

## Concepts

- [Jellyfin Backend](concepts/jellyfin-backend.md): Jellyfin as a second backend chosen in setup, with no live switching, a logout that fully resets the app, and playback switched from raw input streams to HLS.

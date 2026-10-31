# GrooveBoat Documentation

GrooveBoat is a GTK4 / libadwaita desktop music player for Subsonic-compatible servers. It is written in Java 25 and talks to the native GNOME stack (GTK, libadwaita, GStreamer, libsecret, GSettings) through the java-gi bindings. Library metadata is cached locally in an embedded H2 database, and audio is streamed over HTTP straight into a GStreamer pipeline.

## Architecture

1. [Overview](architecture/overview.md): the layers, the packages, and how they depend on each other.
2. [Startup and Lifecycle](architecture/lifecycle.md): what happens from launch to the first visible window, first-run setup, and shutdown.
3. [Data and Synchronisation](architecture/data.md): the server client, the local cache database, the sync strategy, and cover-art caching.
4. [Playback](architecture/playback.md): the GStreamer pipeline, the media stream object, the play queue, and how the player widgets follow along.
5. [User Interface](architecture/ui.md): the window structure, the templated widgets, the resource bundle, actions, and adaptive layout.
6. [Threading Model](architecture/threading.md): which work runs on which thread, and how results get back to the UI.
7. [Configuration and Storage](architecture/storage.md): settings, secrets, and the files written to disk.

## Concepts

- [Jellyfin Backend](concepts/jellyfin-backend.md): Jellyfin as a second backend chosen in setup, with no live switching, a logout that fully resets the app, and playback switched from raw input streams to HLS.

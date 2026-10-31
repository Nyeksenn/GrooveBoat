# Architecture Overview

GrooveBoat is a single-process desktop application organised in layers. Each layer only reaches into the one below it, with one exception: `GrooveboatApp` is the shared hub, and nearly every component reaches it.

## Layers

| Layer | Packages | Responsibility |
|---|---|---|
| Presentation | `ui` | GTK/libadwaita widgets, each backed by a `.ui` template. They react to user input and to signals and property bindings from the layers below. |
| Application hub | `ui.GrooveboatApp` | Owns the long-lived services (audio pipeline, play queue, thread pools), the **session slot** (the current backend and its library sync), the current media stream, and logout. Registers the application-wide actions. |
| Playback | `audio`, `gobjects.RemoteMediaStream` | The playbin3 player, the one media stream the widgets bind to (it loads songs and prepares the next one for gapless playback), and the play queue model. |
| Library sync | `library.LibrarySync` | Keeps the local cache in sync with the session's server: incremental and full sync, lazy song loading, artist refresh. The only code that writes server data to the database. |
| Backends | `backend`, `backend.subsonic`, `backend.jellyfin` | A protocol-independent interface (`MusicBackend`) with one implementation per server type. Handles probing, login, library listing, image download, playback URLs and server-side logout. |
| Persistence | `entities`, `resources/dbmigration` | Ebean ORM entities stored in an embedded H2 database, plus versioned schema migrations. |
| GObject glue | `gobjects` | Plain data wrapped as GObjects so it can live in GTK list models, plus custom GObject types (rounded cover image, media stream) and the keyring wrapper. |
| Utilities | `utils` | Settings keys and session helpers (`AppSettings`), platform data directory lookup and database truncation (`Helpers`). |

## Four representations of the same data

An album, artist or song exists in up to four forms. Each has its own job, and you should not use one where another belongs.

1. **Protocol data**: whatever the server sends. The OpenSubsonic backend binds it to Java records in its own `dto` package. The Jellyfin backend reads the JSON tree directly. Neither form leaves its backend package.
2. **Neutral records** (`backend`, the `Remote*` records): the protocol-independent shape every backend returns, with durations as durations, bitrates in kbit/s and content types as MIME types. They exist only while a sync step is running.
3. **Entities** (`entities`, the `*Model` classes): the persistent, relational form stored in H2. The rest of the app queries these through the generated type-safe query beans.
4. **List items** (`gobjects`, the `*ListItem` classes): lightweight GObjects holding exactly what a widget row or card needs to display, including the decoded cover texture. Only these can go into GTK list models.

The flow always runs protocol data → neutral record → entity (saved in the database) → list item (shown in the UI). The UI never sees server responses, and nothing outside a backend knows which protocol filled the cache.

## The backend boundary

Everything specific to a server protocol lives in `backend.subsonic` or `backend.jellyfin`: endpoints, authentication, response formats, how playback URLs are built, and how server sessions end. The rest of the app depends only on the `MusicBackend` interface and the neutral records. The setup pages are the one place that pick a backend type, and they do it through `BackendFactory`.

A session's backend never changes while the session runs. The application fills its session slot once, either when a stored login is restored at startup or when setup finishes, and only logout empties it. Switching server or server type therefore always means logging out, which resets the app (see [Startup and Lifecycle](lifecycle.md#logout)). This is why the cache needs no "which server" column: it only ever holds one server's library.

## Native interop

All GNOME libraries are reached through java-gi, which uses the Java Foreign Function & Memory API. This shapes the code in a few ways:

- Custom widgets and GObjects are real GObject subclasses. They must be registered with the GObject type system before they are first used, and they keep a constructor that takes a native memory address so that native code can create them.
- Callbacks handed to native code (signal handlers, GAction handlers, main-loop callbacks) live as long as their connection. Handlers attached to objects that outlive a window must be disconnected explicitly (see [User Interface](ui.md#widget-lifetime)).
- Arguments passed to a callback are only valid while it runs. Work handed to another thread or a later main-loop iteration must copy what it needs first.
- Every proxy java-gi creates for a native object has to be cleaned up later. Code avoids creating proxies needlessly: no second hand-made proxy over an existing object, and no proxies for short-lived GStreamer internals (see [Playback](playback.md#bus-messages)).
- The JVM has to be started with native access enabled.

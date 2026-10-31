# Threading Model

GTK widgets may only be touched from the GTK main thread. Network, database, keyring and decoding work must never run there. GrooveBoat splits the work as follows.

| Thread / pool | Owner | Used for |
|---|---|---|
| GTK main thread | GLib default main loop | All widget creation and changes, signal emission to widgets, list-store changes, action activation from the UI, pipeline bus messages, starting and ending sessions. |
| GStreamer thread | `GrooveboatApp` (a single-thread executor) | Runs the pipeline's own GLib main loop, separate from GTK's. |
| GStreamer streaming threads | GStreamer | Downloading playlists, segments and files, demuxing, decoding and output. Created and managed by GStreamer. The only app code that runs on them is the gapless next-song supplier, which just takes the prepared URL. |
| Application pool | `GrooveboatApp.executor` (fixed size, one thread per CPU core) | Session restore and the startup sync, setup probes and login, refresh actions, queue lookups, album and song list loading, releasing play sessions, the server version query in preferences. |
| Scheduler | `GrooveboatApp.scheduler` | The periodic playback position ticker. |
| Library sync virtual threads | `LibrarySync` | Fan-out during sync: one task per album (artist resolution, cover download) and one per song when loading an album. |
| Logout thread | `GrooveboatApp.logout` (a virtual thread) | The blocking part of logout: waiting for sync to stop, server logout, keyring, database and cover cache. A thread of its own, so a busy application pool can't hold up logout. |
| Keyring thread | `SecretKeeper` (a single-thread executor) | Blocking keyring lookups, stores and removals, returned as futures. |
| Short-lived local pools | Individual widgets | Parallel texture loading while filling the album grid. |

## Returning to the UI thread

Background tasks never change widgets directly. When a result is ready they schedule a one-shot idle callback on the GTK main loop, and that callback appends to a list store, switches a stack page, shows a toast or reports the new position. This pattern is used throughout the code and is the expected way to cross back. Periodic UI updates, such as the refresh progress pulse, use main-loop timers rather than background threads.

## Shared state and synchronisation

- **Session slot.** Starting a session is synchronised and refuses to replace an existing one. The backend and library sync references are volatile, so background tasks see them as soon as they are set. The slot is only emptied on the main thread, at the end of logout.
- **Backends.** Each backend keeps its session credential in volatile fields and is otherwise stateless per request, so library calls from many threads at once are safe.
- **Library sync.** A sync holds a lock for its whole run. Logout sets a volatile cancellation flag, which sync tasks check between steps, and then waits for the lock with a timeout.
- **Player.** Starting and stopping a song are synchronised. The next-song supplier is read from a streaming thread, so the stream keeps the prepared next song and the pending switch in atomic references. Everything else about the stream is only touched on the main thread.
- **Texture cache.** A concurrent map that loads each entry once, so several loaders asking for the same album decode its cover only once.
- **Artist creation.** Concurrent sync tasks can try to insert the same artist. The database's unique constraint decides the winner, and the loser re-reads the existing row.
- **Database.** Ebean manages connections per thread. Bulk writes collect their results first and then save them in one batch.

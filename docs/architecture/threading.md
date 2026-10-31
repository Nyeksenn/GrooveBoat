# Threading Model

GTK widgets may only be touched from the GTK main thread. Network, database and decoding work must never run there. GrooveBoat splits the work as follows.

| Thread / pool | Owner | Used for |
|---|---|---|
| GTK main thread | GLib default main loop | All widget creation and changes, signal emission to widgets, list-store changes, action activation from the UI. |
| GStreamer thread | `GrooveboatApp` (a single-thread executor) | Runs the pipeline's own GLib main loop, separate from GTK's. |
| Application pool | `GrooveboatApp.executor` (fixed size, one thread per CPU core) | Startup connection check and sync, refresh actions, queue lookups, album and song list loading, feeding audio data into the app source. |
| Scheduler | `GrooveboatApp.scheduler` | The periodic playback position ticker. |
| Facade virtual threads | `Subsonic` | Fan-out during sync: one task per album (artist resolution, cover download) and one per song when loading an album. |
| Secret store thread | `SecretKeeper` (a single-thread executor) | Blocking keyring lookups and stores, returned as futures. |
| Short-lived local pools | Individual widgets | Parallel texture loading while filling the album grid. |

## Returning to the UI thread

Background tasks never change widgets directly. When a result is ready they schedule a one-shot idle callback on the GTK main loop, and that callback appends to a list store, switches a stack page, updates a progress bar or reports the new position. This pattern is used throughout the code and is the expected way to cross back.

## Shared state and synchronisation

- **Media stream.** The data-feeding task, the enough-data callback and the seek handler all touch the same HTTP response and flags. A lock serialises them, and the flags are volatile so every thread sees the latest value.
- **Texture cache.** A concurrent map that loads each entry once, so several loaders asking for the same album decode its cover only once.
- **Artist creation.** Concurrent sync tasks can try to insert the same artist. The database's unique constraint decides the winner, and the loser re-reads the existing row.
- **Database.** Ebean manages connections per thread. Bulk writes collect their results first and then save them in one batch.

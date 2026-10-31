# Data and Synchronisation

## Backends

A backend is bound to one server URL and becomes usable for library calls once it holds a session, from a login or a restored credential. The `MusicBackend` interface covers:

- **Probe:** is a compatible server of this type at the URL? Needs no credentials and returns the server's name and version, or a reason.
- **Authenticate:** log in with username and password and return the session to keep.
- **Restore:** re-establish a stored session and verify it with the server.
- **Library:** albums newer than a timestamp (newest first, paged), the songs of an album, artist details.
- **Images:** download a cover or artist image into the cover cache.
- **Playback:** build a playback URL for a song, and release a play session afterwards (see [Playback](playback.md)).
- **Logout:** end the server-side session, best effort.

All results come back as neutral records. Rejected credentials and expired sessions are reported as a dedicated authentication error, so the UI can tell "wrong login" apart from "server unreachable".

`BackendFactory` creates a backend for a server type and URL. For Jellyfin it also supplies the stable device ID, which it creates on first use.

### OpenSubsonic (`SubsonicBackend`)

- **Authentication.** Every request carries the username, API version, client name and JSON format, plus a salted token: a new random salt is hashed together with the password each time. The plain password is never sent. The password itself is the session credential.
- **Probe.** A ping with placeholder credentials. The server answers with its API version and OpenSubsonic flag even when authentication fails.
- **Endpoints used.** Ping and user lookup (probe and credential check), the newest-albums list, artist lookup, the music directory (an album's songs), cover art, and the stream endpoint.
- **Response handling.** Responses are parsed with Jackson 3 (with the Blackbird module for faster binding). The backend checks the status and error code of the Subsonic envelope, then binds only the fragment it needs to its private records.

### Jellyfin (`JellyfinBackend`)

- **Authentication.** Every request sends Jellyfin's authorisation header with client name, device name, device ID, app version and, once logged in, the access token. Login by username and password returns an access token and a user ID. The token is the session credential, and the password is not kept. Restoring a session means fetching the user's own record with the stored token.
- **Probe.** The public system info endpoint, which needs no login. It must identify the product as Jellyfin and report a version at or above the minimum.
- **Library queries.** Albums are queried as music-album items across the whole library, sorted by creation date newest first and paged. Songs are audio items whose parent is the album, sorted by disc and track, with media source details. Artists come from the item endpoint.
- **Conversions.** Runtimes in 100 ns ticks become durations. Bitrates in bit/s become kbit/s. The audio codec becomes a MIME type, using a fixed codec-to-MIME table that can be mapped back to the codec at playback time. Items without a primary image get no image ID, so no download is attempted.
- **Logout.** Calls the server's session logout endpoint to revoke the token.

## Library sync (`LibrarySync`)

There is one `LibrarySync` per session, created together with the session and closed at logout. It works only with neutral records, so both backends share all the caching logic.

- **Incremental album sync.** It reads the last-sync timestamp from settings and asks the backend for albums created after it. For each new album, a virtual-thread task resolves the album's artists and caches the cover. The albums are then saved together. Last-sync moves to the time the sync *started*, so albums added while it was running are picked up next time. Finally, artist details and images are refreshed with an upsert.
- **Full refresh.** Sets the timestamp back to zero and truncates all cache tables (with referential integrity briefly turned off) before running the same sync.
- **Artist resolution.** Artists are created on demand when an album or song refers to them. If two concurrent tasks try to create the same artist, the duplicate-key error is caught and the existing row is re-read.
- **Lazy song loading.** An album's songs are not fetched during sync, but when the album page is opened. If the cached song count already matches the server's, the cached songs are returned. Otherwise each song is compared with any cached version. New songs are inserted, and changed ones are updated in place on the loaded row.
- **Cancellation.** A sync holds a lock for its whole run and checks a cancellation flag between steps. Logout sets the flag and waits, with a timeout, for the lock to be released. Once cancelled, a library sync stays cancelled; the next session gets a new one.
- **Field limits.** Titles and names are cut to the column lengths of the schema before saving.

## Local cache (`entities`)

The cache is an embedded H2 database managed by Ebean.

- **Entities.** Album, artist and song. Albums and songs each have a many-to-many link to artists (separate join tables for album artists and song artists). Songs have a many-to-one link to their album. Primary keys are the server's own IDs, so sync is idempotent.
- **Common base.** Every entity inherits an optimistic-locking version column and created/modified timestamps.
- **File references.** Albums and artists store the local file path of their cached image, not the image itself. An album without a cover keeps an empty path and still appears in the library.
- **Content type.** Each song stores its MIME type. It is shown to the user and, for Jellyfin, decides how the song is streamed.
- **Queries.** Type-safe query beans are generated at compile time by Ebean's annotation processor and used everywhere instead of string queries. The one exception is the artist upsert, which uses a native merge statement.
- **Migrations.** Schema changes go through versioned migration files in the resources (a SQL script plus Ebean's model description). They are generated from the entity classes by a separate Gradle task and applied automatically at startup.

## Cover textures (`AlbumTextureLoader`)

Decoding images into GTK textures is the expensive step, so a process-wide singleton keeps a concurrent map from album ID to texture. The first request reads the album's cover path from the database and decodes the file. Later requests for the same album (in the album grid, song lists, queue entries and player views) reuse the same texture. Albums without a cover get no texture, and the image widget draws nothing. Logout clears the map.

## Refresh from the UI

The main chooser view registers two application actions: a normal refresh and a full refresh. Each one shows a progress bar that pulses from a main-loop timer, runs the matching library sync on the background pool, and when it finishes asks the album grid (through a window action) to reload from the database. A refresh that is already running is not started twice.

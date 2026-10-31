# Data and Synchronisation

## Remote client (`SubsonicClient`)

The client is a thin, stateless-per-request wrapper around the Subsonic REST API, built on the JDK HTTP client.

- **Authentication.** Every request carries the username, API version, client name and JSON format, plus a salted token: a new random salt is generated each time and hashed together with the password. The plain password is never sent over the wire.
- **Endpoints used.** Ping and user lookup (connection and credential checks), the newest-albums list (sync), the artist lookup, the music directory (an album's songs), cover art, and the audio stream.
- **Response handling.** Responses are parsed with Jackson 3 (with the Blackbird module for faster binding). The client walks into the Subsonic response envelope and binds only the fragment it needs to the matching record type.
- **Cover art.** Images are downloaded at a fixed size and written to a cover cache directory. The client returns the file path so it can be stored on the entity.
- **Streaming.** A stream request returns the HTTP response body as an input stream rather than buffering it. For seeking, a time offset is passed so the server starts sending from that point.

## Facade (`Subsonic`)

The rest of the application holds one `Subsonic` instance, owned by `GrooveboatApp`. It hides the client behind domain operations and is the only place that writes server data into the database.

- **Client management.** It can be given a new client (when the URL changes) or new credentials. Until a client exists, playback cannot start.
- **Incremental album sync.** It reads the last-sync timestamp from GSettings and pages through the server's "newest albums" list until it reaches an album created before that timestamp. For each new album, a virtual-thread task resolves its artists and caches its cover art. The albums are then saved together, the timestamp moves forward, and artist details and images are refreshed with an upsert.
- **Full refresh.** It sets the timestamp back to zero and truncates all cache tables (with referential integrity briefly turned off) before running the same sync. This rebuilds the cache from scratch.
- **Artist resolution.** Artists are created on demand when an album or song refers to them. If two concurrent tasks try to create the same artist, the duplicate-key error is caught and the existing row is re-read.
- **Lazy song loading.** An album's songs are not fetched during sync. They are fetched when the album page is opened. If the cached song count already matches the server's, the cached songs are returned. Otherwise each song is built in parallel, compared with any cached version, and inserted or updated as needed.

## Local cache (`entities`)

The cache is an embedded H2 database managed by Ebean.

- **Entities.** Album, artist and song. Albums and songs each have a many-to-many link to artists (separate join tables for album artists and song artists). Songs have a many-to-one link to their album. Primary keys are the server's own IDs, so sync is idempotent.
- **Common base.** Every entity inherits an optimistic-locking version column and created/modified timestamps.
- **File references.** Albums and artists store the local file path of their cached image, not the image itself.
- **Queries.** Type-safe query beans are generated at compile time by Ebean's annotation processor and used everywhere instead of string queries. The one exception is the artist upsert, which uses a native merge statement.
- **Migrations.** Schema changes go through versioned migration files in the resources (a SQL script plus Ebean's model description). They are generated from the entity classes by a separate Gradle task and applied automatically at startup.

## Cover textures (`AlbumTextureLoader`)

Decoding images into GTK textures is the expensive step, so a process-wide singleton keeps a concurrent map from album ID to texture. The first request reads the album's cover path from the database and decodes the file. Later requests for the same album (in the album grid, song lists, queue entries and player views) reuse the same texture.

## Refresh from the UI

The main chooser view registers two application actions: a normal refresh and a full refresh. Each one shows a pulsing progress bar, runs the matching facade operation on the background pool, and when it finishes asks the album grid (through a window action) to reload from the database.

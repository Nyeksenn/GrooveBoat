# Configuration and Storage

## GSettings

Non-secret configuration lives in the GSettings schema `io.github.Nyeksenn.grooveboat`. The key names and the session helpers are in `AppSettings`.

| Key | Type | Meaning |
|---|---|---|
| `backend` | string: `none`, `opensubsonic` or `jellyfin` | Server type of the current session. `none` means logged out and opens the setup dialog. |
| `url` | string | Base URL of the server, including the scheme, without trailing slash. |
| `username` | string | The account name used to authenticate. |
| `user-id` | string | Server-side user ID (Jellyfin). Empty for OpenSubsonic. |
| `device-id` | string | Stable client device ID sent to Jellyfin. Created on first use and **kept across logouts**, so the server sees the same device logging in again. |
| `last-sync` | unsigned 64-bit | Unix timestamp of the last successful metadata sync. Zero forces a full sync. |

A completed setup writes the session keys and resets `last-sync`. It writes `backend` last, because a value other than `none` is what marks setup as complete. Logout resets every key except `device-id`.

The schema has to be compiled where GLib can find it; without it, creating a settings object aborts the process. `./gradlew run` compiles it into the build directory and points the app there. Running the app any other way needs the schema installed system-wide with the helper script in the repository.

## Secrets

The session credential is never written to GSettings or the database. For OpenSubsonic it is the password; for Jellyfin it is the access token from login, and the password is not stored at all. It goes into the user's default keyring through libsecret, using the standard network-password schema. Entries are keyed by username, protocol (the URL scheme) and server (the rest of the URL), and labelled with the server type, so they are recognisable in a keyring manager. A process-wide `SecretKeeper` singleton handles all keyring access on its own thread and returns futures. Logout removes the entry.

## Files on disk

Everything goes under the platform's user data directory: the XDG data home on Linux (falling back to the local share folder in the home directory), Application Support on macOS, and roaming AppData on Windows. Inside it, in a `grooveboat` folder:

| Path | Contents |
|---|---|
| `cache` (H2 database files) | The cached library of the current server: albums, artists, songs and their links. |
| `covers` | Downloaded album and artist images, referred to by path from the database. |

Logout empties both. Deleting them by hand is also safe: a full refresh rebuilds everything from the server.

For development and testing, pointing the XDG data home at a scratch directory and using GLib's in-memory settings backend keeps a run away from the real library cache and settings.

## Logging

Logging goes through SLF4J with Logback as the backend, configured by the Logback file in the resources. It writes to the console and to a rolling log file in a `logs` folder in the working directory. Playback URLs carry credentials, so they are only ever logged with the query string removed, and GStreamer debug strings are not logged.

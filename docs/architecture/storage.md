# Configuration and Storage

## GSettings

Non-secret configuration lives in the GSettings schema `io.github.Nyeksenn.grooveboat`:

| Key | Type | Meaning |
|---|---|---|
| `url` | string | Base URL of the server, including the scheme. |
| `username` | string | The account name used to authenticate. |
| `last-sync` | unsigned 64-bit | Unix timestamp of the last successful metadata sync. Zero forces a full sync. |

The schema has to be installed and compiled on the system (the repository includes a helper script for this). Without it, creating a settings object aborts the process. Empty `url` or `username` values are what tell the app this is a first run.

## Secrets

The password is never written to GSettings or the database. It is stored in the user's default keyring through libsecret, using the standard network-password schema. Entries are keyed by username, protocol (the URL scheme) and server (the host part of the URL). A process-wide `SecretKeeper` singleton handles all keyring access on its own thread and returns futures.

## Files on disk

Everything goes under the platform's user data directory: the XDG data home on Linux (falling back to the local share folder in the home directory), Application Support on macOS, and roaming AppData on Windows. Inside it, in a `grooveboat` folder:

| Path | Contents |
|---|---|
| `cache` (H2 database files) | The cached library: albums, artists, songs and their links. |
| `covers` | Downloaded album and artist images, referred to by path from the database. |

Deleting these is safe. A full refresh (or removing the database and resetting `last-sync`) rebuilds everything from the server.

## Logging

Logging goes through SLF4J with Logback as the backend. It is configured by the Logback file in the resources.

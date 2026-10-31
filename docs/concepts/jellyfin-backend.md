# Concept: Jellyfin Backend

Status: draft
Scope: add Jellyfin as a second server backend next to OpenSubsonic. The user picks the backend once, in the setup dialog. Switching backend requires logging out, and logging out resets the app to a clean first-run state. As part of the same work, playback moves from pushing a raw HTTP byte stream by hand to GStreamer playing HLS playlists directly.

## Goals

- During first-run setup the user chooses between **OpenSubsonic** and **Jellyfin**.
- Everything that reads the local cache keeps working unchanged, whichever backend filled it: the album grid, song lists, queue, player and search.
- The backend is **fixed** for as long as the user is logged in. Nothing in the UI can switch backend, or point the app at a different server, while a session is active.
- A new **Log Out** action wipes all local state for the session (cache, covers, credentials, settings, queue, playback). The app goes back to the setup dialog, where a backend can be chosen again.
- Audio is streamed over **HLS**. The pipeline receives a playlist URL and GStreamer does the downloading, demuxing and seeking. The Java code that read the HTTP response and pushed buffers into the pipeline is removed.

## Non-goals

- Live switching between backends or servers, or several accounts or servers at once.
- Merging libraries from more than one server.
- Features specific to Jellyfin that have no equivalent in the current app (playlists, favourites, playback reporting). The design should allow them later but does not include them.
- **Transcoding, for now.** The client always asks for the original audio, never a re-encoded version, and has no codec, bitrate or quality settings. The local GStreamer pipeline is assumed to decode every audio format in the library. Servers may still *repackage* the original audio into HLS segments (copying the stream without re-encoding), because that keeps the original quality. Transcoding can be added later as an option, through the same playback URL operation, without changing the pipeline.

## Current state

The app is tied to Subsonic in these places (see [Overview: backend coupling](../architecture/overview.md#backend-coupling)):

- `SubsonicClient` and the `deserializer` records know the Subsonic protocol.
- The `Subsonic` facade mixes two jobs: talking to the server, and writing to the database (sync, artist resolution, upserting songs). Widgets, the setup pages and the preferences dialog hold a reference to its concrete type.
- `MediaSubsonic` asks the facade for byte streams and offset-based seeks. It reads the HTTP response in fixed-size chunks on a worker thread and pushes them into an app source. When GStreamer asks to seek, it closes the response and opens a new one at a time offset. Doing this by hand needs a lock, volatile flags and arena-bound callbacks, and the stream has to be told its caps up front from the song's MIME type.
- The connection page checks for OpenSubsonic with a minimum version. The credentials page checks credentials the Subsonic way.
- The preferences dialog can change the URL, username and password of a running session and re-run client setup. **This is live server switching, and this concept removes it.**
- There is no logout. The only way to reset is to delete files and settings by hand.

## Target architecture

### 1. Backend abstraction

Introduce a **music backend** interface that describes what the app needs from a server, in terms that are independent of the protocol. It has two implementations, *OpenSubsonic* and *Jellyfin*. The interface covers:

| Capability | Purpose |
|---|---|
| Probe server | Given a URL, say whether a compatible server of this backend type is there. Return server name and version, or a reason it is incompatible. No credentials needed. |
| Authenticate | Log in with username and password. Return a *session credential* to keep: the password for Subsonic, an access token plus user ID for Jellyfin. |
| Restore session | Rebuild a ready client from the stored URL, username and session credential at startup, and check it is still valid. |
| List albums newer than a timestamp | Paged, newest first. Feeds incremental sync. |
| List songs of an album | Feeds lazy song loading. |
| Get artist details | Feeds the artist refresh after sync. |
| Fetch image | Download a cover or artist image at a given size. Returns bytes or a cached file path. |
| Build playback URL | Given a song ID, return a ready-to-play URL for the original audio, with authentication included. That is an HLS playlist where the backend and the song's codec allow segments without re-encoding, and a progressive stream URL otherwise (see [Streaming via HLS](#streaming-via-hls)). The backend does no network I/O for this; it only builds the URL. |
| Release playback | Tell the server that a playback session has ended, so it can stop any job it started to segment the stream (it does nothing for Subsonic). |
| Log out | Best-effort server-side session end (it does nothing for Subsonic). |

Backends return **neutral records** (remote album, remote song, remote artist) instead of their own JSON types. The protocol-specific records stay private to each implementation's package. Suggested layout: a `backend` package for the interface and neutral records, with `backend.subsonic` and `backend.jellyfin` beneath it. The existing `client` and `deserializer` code moves into `backend.subsonic`.

### 2. Separate sync from transport

Move the database work out of the current facade into a **library sync service** that works with any backend:

- incremental and full album sync, driven by the `last-sync` setting;
- artist resolution, including the existing duplicate-key recovery;
- lazy song loading with compare-and-upsert;
- the artist image refresh.

This service takes a backend and only ever sees neutral records, so both backends share all the caching logic. The entity layer and migrations do **not** change: both servers use string IDs, and since logout always clears the cache, rows from two backends never sit side by side. That means no "backend" column is needed.

### 3. Backend-agnostic media stream

Rename `MediaSubsonic` to a neutral name (for example a "remote media stream"). It no longer handles any bytes itself: it asks the backend for a playback URL and hands that URL to the pipeline. The queue and the player widgets don't change, because the stream object keeps its GTK media stream properties (playing, timestamp, duration, prepared, seeking). See [Streaming via HLS](#streaming-via-hls) for the details.

### 4. Backend lifetime: one per session, never swapped

- `GrooveboatApp` keeps **one optional backend slot** instead of a final Subsonic field.
- The slot is filled exactly once per session: either by startup restore (credentials stored) or by the setup dialog finishing successfully.
- Once filled, the slot is read-only until logout. No widget, dialog or action can replace it.
- Widgets get the backend (and the sync service) from the application when they are realised, as they do today. Logout destroys the window (see below), so no widget can be left holding a backend from an earlier session.

## Setup dialog changes

The navigation flow becomes:

1. **Welcome**: unchanged.
2. **Server type** (new page): two rows, *OpenSubsonic* and *Jellyfin*, each with a short description. Picking one pushes the connection page with the backend type attached.
3. **Connection**: the URL is checked with the chosen backend's *probe*.
   - OpenSubsonic keeps its current rule: it must be an OpenSubsonic server at or above the minimum version.
   - Jellyfin checks the server's public system info endpoint, which needs no login, and applies a Jellyfin minimum version.
   - If the probe fails but the *other* backend's probe succeeds, the toast says so ("This looks like a Jellyfin server"), so the user can go back and pick the right type.
4. **Credentials**: calls the chosen backend's *authenticate*. On success it saves the backend type, URL, username and any backend-specific IDs to settings, puts the session credential in the keyring, fills the application's backend slot, and closes the dialog.
5. **After setup**: run an initial sync before showing the content page. Today the dialog jumps straight to the content page without syncing, so a first run starts with an empty grid.

Nothing goes into GSettings until the credentials page succeeds. Today the connection page writes the URL early. That should go, so that leaving setup halfway never leaves a partial configuration behind.

## Jellyfin specifics

| Topic | Approach |
|---|---|
| Client identity | Every request carries Jellyfin's authorisation header with client name, device name, a device ID and the app version. The device ID is made once and kept in settings. It is **not** reset on logout, so the server sees the same device logging in again rather than a new device each time. |
| Login | Authenticate by username and password. Keep the returned access token and user ID. The password itself is **not** stored. |
| Session credential | The access token goes in the keyring. The user ID goes in settings. |
| Albums | Query items of type music album, recursively, sorted by creation date newest first, in pages, asking for creation date, album artists, child count and runtime. Stop paging at the first album older than `last-sync`, the same way as the Subsonic implementation. |
| Songs | Query audio items whose parent is the album, sorted by disc and track, asking for media source information (container, bitrate). |
| Artists | Album and song artist references come with the items as name and ID pairs. Artist details and images come from the item endpoint for the artist's ID. |
| Durations | Jellyfin reports runtimes in ticks of 100 ns. Convert these to the app's duration type inside the backend. |
| Images | Download the primary image of an item with a maximum width, into the existing cover cache directory. |
| Streaming | Use the audio HLS master playlist endpoint for the song. Request the song's **own** codec and fMP4 segments, so the server only copies the stream into segments without re-encoding. Pass the media source ID, a device ID and a new play-session ID for each track. Songs whose codec fMP4 segments can't carry go through the static (original file) stream endpoint instead. Authentication goes in the URL as an API key parameter (see the authentication point under [Streaming via HLS](#streaming-via-hls)). |
| Stopping segment jobs | Even without re-encoding, Jellyfin runs a server-side job per HLS session to cut the segments. When a track is replaced or playback stops, end that job for this device and play session, or it keeps running until its idle timeout. |
| Logout | Call the server's session logout endpoint so the token is revoked. Errors are ignored, so logging out works offline too. |

## Streaming via HLS

### Why

The current playback path does by hand what GStreamer already does well. A worker thread pulls fixed-size chunks out of an HTTP response and pushes them into an app source. Every seek closes the connection and opens a new one at a time offset, which only works while the server is transcoding. Getting this right needs locks, volatile flags, callbacks that must outlive their memory arena, and caps guessed up front from the song's MIME type. Problems with any of these show up as stalls, races or garbled starts.

HLS is a better fit for both servers:

- **Seeking is built in.** A finished (video-on-demand) HLS playlist lists every segment and its length. GStreamer's HLS demuxer turns a time seek into "fetch segment N", with no special server support and no new connections opened by the app.
- **Same pipeline for every source.** HLS playlists and plain HTTP files both go in as a URI. GStreamer works out the container and codec itself, so the original audio of every song plays through one code path.
- **No byte handling in Java.** Downloading, buffering, retries and demuxing all happen inside GStreamer, on its own threads.

### Pipeline changes (`AppPipeline`)

- Replace the *app source → decode bin* front end with a **URI decode bin** (the third-generation one, which uses the adaptive streaming demuxers). Its decoded audio output links to the existing converter → resampler → audio sink chain as before, through the pad-added handler.
- The pipeline gets one new operation, *play this URI*: set the pipeline to ready, set the decode bin's URI, set it to paused. It no longer creates, links or removes app sources.
- Seeking stays a time-based pipeline seek. The HLS demuxer picks the right segment, so a seek from the app still uses the flushing time-seek that is already there.
- Position and duration come from pipeline queries as before. The duration from the playlist takes priority over the database value once the stream is prepared. The database value is used only until then.
- The bus handler also gets:
  - **Buffering messages:** pause while the buffer is below full, and go back to the state the user wants when it fills. This is the standard pattern for network streams, and was never needed with the hand-fed source.
  - **Error messages:** report them on the media stream (the GTK media stream error state) so the UI can show a toast, instead of the current silent reset to paused.
- The MIME-to-caps mapping in the helpers is not needed for playback any more, because type detection now happens inside GStreamer. The song's content type stays in the database for display.

### Media stream changes

The renamed media stream gets much simpler:

- **When created**, it asks the backend for the playback URL and tells the pipeline to play that URI. It is marked as prepared once the pipeline reaches paused, using the pipeline's duration.
- **Play, pause and seek** go straight to pipeline state changes and seeks, exactly as now.
- **Position updates** use the scheduled pipeline-position query that already exists.
- **When destroyed**, it returns the pipeline to ready and calls the backend's *release playback*.
- **Removed:** the need-data, enough-data and seek-data callbacks, the worker thread that pushed data, the input stream, the buffer, the lock and flags, and the per-stream arena.

### What each backend provides

| Backend | HLS source | Notes |
|---|---|---|
| Jellyfin | The audio master playlist endpoint, asking for the song's own codec in fMP4 segments (copied, not re-encoded) | Works for codecs that fMP4 can carry, such as FLAC, ALAC, AAC, MP3 and Opus. Other formats (for example WAV/PCM, WMA, APE or WavPack) use the static stream endpoint as a progressive download. The backend chooses per song, from the codec recorded when the song was synced. |
| OpenSubsonic | The stream endpoint, asking for the raw original format | No HLS. The Subsonic API's `hls.m3u8` endpoint segments the audio by transcoding to a target bitrate, which this concept rules out. It is also optional, and several popular servers (Navidrome, for example) don't implement it. The URI decode bin plays the original file as a progressive HTTP download, and seeking uses HTTP range requests. |

Both backends end up on the same URI-based pipeline, so the raw input-stream path is gone either way. If transcoding is brought in later, Subsonic HLS can be added behind the same operation.

The choice between HLS and progressive is made per song, from data already in the cache. Building a playback URL never needs an extra request to the server.

### Authentication on segment requests

HLS clients fetch the playlist and then each segment as separate HTTP requests, and GStreamer's HLS demuxer does not reliably send custom headers to segment URLs. So authentication has to travel **in the URL**:

- **Jellyfin:** the access token goes in as the API key query parameter. Jellyfin carries it into the variant playlist and segment URLs it generates.
- **OpenSubsonic:** the usual salted-token query parameters are already in the URL. Whether a given server repeats them in its segment URLs needs checking against each target server.

Because these URLs contain credentials, they must never be logged in full. Logging of playback URLs keeps the host and path and drops the query string.

### Runtime dependencies

HLS playback needs GStreamer plugins that the current pipeline didn't: the HTTP source (soup), the HLS demuxer (the newer one in plugins-good, with the older one from plugins-bad as a fallback), and an MP4 demuxer for fMP4 segments. Because the audio is never transcoded, the pipeline also needs a demuxer and decoder for **every** format in the library. In practice that means having plugins-good, plugins-bad, plugins-ugly and libav installed. At startup the pipeline checks that the needed element factories exist. If they don't, the app shows an error page naming the missing plugins instead of failing silently on the first track. If a single song's format has no decoder, it shows up as a pipeline error on that track, which is reported as a toast, and the queue moves on. The app does not fall back to transcoding.

### Risks to check with a prototype

Before implementation, a short prototype should confirm, for each backend:

- that Jellyfin really copies the stream (no re-encoding) when the requested codec matches the source, including for users whose server policy forbids audio transcoding;
- how long it takes to start playing, and to seek, while Jellyfin creates segments on demand;
- that seeking to the far end of a long track works without the server first creating every segment before it;
- that progressive playback seeks correctly by HTTP range for formats without a seek index (for example some MP3 and WAV files);
- that authentication in the URL reaches every segment request;
- that end-of-stream arrives cleanly at the end of the playlist, so play-next still fires;
- which codecs the target Jellyfin version accepts for stream copy into fMP4 segments, so the per-song choice between HLS and progressive is correct.

## Settings and storage

GSettings schema additions:

| Key | Type | Meaning |
|---|---|---|
| `backend` | enum: none, opensubsonic, jellyfin | The backend of the current session. *none* means logged out, and triggers the setup dialog. |
| `user-id` | string | Server-side user ID (Jellyfin). Empty for OpenSubsonic. |
| `device-id` | string | Stable client device ID sent to Jellyfin. Made on first use and kept after logout. |

Existing keys (`url`, `username`, `last-sync`) keep their meaning but are described without reference to Subsonic.

First-run detection changes from "URL or username empty" to "**backend is none**". Existing installs have no `backend` value, so they get a one-time migration: if `url` and `username` are set, `backend` is set to *opensubsonic*. These users don't have to set the app up again.

Keyring entries keep the current attributes (user, protocol, server). The stored secret is the *session credential*: the password for OpenSubsonic, the access token for Jellyfin. The entry label names the backend so it is clear to the user in their keyring manager.

The schema change means the schema has to be reinstalled with the install script.

## Logout

### Entry points

- A **Log Out** item in the header bar menu.
- A **Log Out** button in the preferences dialog.
- A **Log Out** button on the error page, so a user whose server is gone or whose token was revoked can recover without editing files.

All three ask for confirmation with an alert dialog. The dialog says that the local library cache and queue will be cleared and that the server's library is not touched.

### Reset sequence

Logout runs in this order. Each step after the first must succeed even if earlier steps failed. Failures are logged, not shown, because the aim is always to reach a clean state.

1. Show a blocking "logging out" state on the window so nothing else can be clicked.
2. Stop playback: destroy the current media stream, which also releases the server-side playback session, and return the pipeline to ready.
3. Clear the play queue model.
4. Cancel any sync or list-loading tasks that are still running, and wait briefly for the workers to finish.
5. Call the backend's *log out*. This is best effort and has a short timeout.
6. Remove the session credential from the keyring.
7. Truncate all cache tables (the existing full-refresh truncation).
8. Delete the contents of the cover cache directory.
9. Clear the in-memory album texture cache.
10. Reset settings: `backend` to *none*, and `url`, `username`, `user-id` and `last-sync` to their defaults. `device-id` stays.
11. Empty the application's backend slot.
12. Destroy the main window and create a new one. The new window finds `backend` set to *none* and opens the setup dialog.

Creating a new window, rather than resetting widgets one at a time, is deliberate. Today several widgets hold references to the facade and the executors, and register window-scope actions when they are realised. Throwing the whole widget tree away guarantees that nothing from the old session survives, without each widget needing its own reset logic.

### Preferences dialog after the change

The preferences dialog no longer edits the connection. It shows the backend type, server URL, server version and username as read-only rows, and offers Log Out. Pointing the app at another server or account always means logging out first. That is how the "no live switching" rule is enforced.

Changing the password of the *same* account (for example after it was changed on the server) is also handled by logging out and back in. A dedicated re-authenticate flow can be added later if that turns out to be annoying.

## Implementation phases

1. **Refactor without changing behaviour.** Bring in the backend interface and neutral records, and move the Subsonic code behind it. Split out the library sync service. Rename the media stream. The app should behave exactly as before.
2. **HLS prototype.** Check the risks in [Streaming via HLS](#risks-to-check-with-a-prototype) against a Jellyfin server, and check progressive playback and seeking against at least one OpenSubsonic server.
3. **URI-based playback.** Switch the pipeline to the URI decode bin. Simplify the media stream. Add buffering and error handling on the bus and the plugin check at startup. Make the Subsonic backend return progressive URLs for the raw original stream. Remove the app-source code. OpenSubsonic playback should work the same as before, or better.
4. **Backend lifetime and settings.** Add the new schema keys and the migration for existing installs. Replace the final facade field with the session-scoped backend slot, and change first-run detection.
5. **Logout.** Add the reset sequence, the confirmation dialog, the three entry points, and recreating the window. Make the preferences dialog read-only.
6. **Setup flow.** Add the server type page, probing per backend with the cross-backend hint, settings written only at the end, and the initial sync after setup.
7. **Jellyfin backend.** Probe, authenticate, restore, album, song and artist listing, images, playback URLs (HLS stream copy or progressive), releasing segment jobs, and logout.

Phases 1 and 3 to 6 are worth doing even without Jellyfin. They untangle the facade, replace the fragile hand-written stream feeding, fix the half-written setup state and the missing first sync, and give users a supported way to reset the app.

## Manual test checklist

The project has no automated tests, so each phase is checked by hand against:

- a fresh install with each backend: setup, first sync, browsing, playing, seeking, end-of-track advance, loop;
- an existing OpenSubsonic install upgrading: no setup prompt, library still there;
- logout while a song is playing and a sync is running: ends in setup, no errors, no leftover covers or cache rows, keyring entry gone;
- logout while the server is unreachable: still ends in setup;
- logging in with the other backend straight after logout: only the new server's library appears;
- entering a Jellyfin URL after picking OpenSubsonic (and the reverse): the cross-backend hint appears;
- a revoked Jellyfin token at startup: error page, and its Log Out button recovers.
- seeking repeatedly, including to the very start and very end of a track, over HLS (both backends) and over progressive streaming (an OpenSubsonic server without HLS);
- skipping tracks quickly on Jellyfin: no segment jobs left running on the server afterwards;
- a library with mixed formats (FLAC, ALAC, MP3, AAC, Opus, Vorbis, WAV, and anything less common in the test library): every track plays in its original format, and the server dashboard shows no transcoding;
- a slow or briefly dropped network connection: playback pauses while buffering and then carries on;
- missing GStreamer HLS plugins: the error page names them;
- the logs contain no access tokens or auth parameters.

## Open questions

- The minimum Jellyfin server version to support.
- Whether Jellyfin's "Quick Connect" (log in by approving a code on another device) should be offered as well as username and password.
- Whether logout should also remove the H2 database files rather than only truncating tables, in case a later migration needs a truly fresh database.
- Whether to switch from the URI decode bin to a full playbin later. A playbin would make gapless playback possible, using its "about to finish" signal to queue the next track's URL, but it would replace the custom converter → resampler → sink chain.

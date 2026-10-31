# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

GrooveBoat is a GTK4/libadwaita desktop music player for Jellyfin and OpenSubsonic servers, written in Java 25 using [java-gi](https://github.com/jwharm/java-gi) bindings (GTK, Adw, GStreamer, libsecret). Metadata is cached locally in an H2 database via Ebean ORM; GStreamer plays audio straight from server URLs (HLS for Jellyfin, progressive HTTP for OpenSubsonic), never transcoded.

## Commands

```sh
./gradlew run                 # build + run; uses the schema compiled into build/schemas (no install needed)
./gradlew build               # compile everything
./gradlew generateMigrations  # generate a new Ebean DB migration from entity changes
./install_settings.sh         # install the GSettings schema system-wide (sudo; needed for running outside `gradlew run`)
```

- `classes` depends on `compileResources`, which first runs `compileSass` (`sass` CLI: `scss/main.scss` → `css/main.css`) and then `glib-compile-resources` to produce `src/main/resources/grooveboat.gresource`. Both `sass` and `glib-compile-resources` must be on PATH.
- `Main` loads the gresource via the relative path `src/main/resources/grooveboat.gresource`, so the app must be run from the repo root.
- Without the GSettings schema `io.github.Nyeksenn.grooveboat` (keys in `utils.AppSettings`), `new Settings(...)` aborts the app. `gradlew run` sets `GSETTINGS_SCHEMA_DIR=build/schemas`.
- To run or test without touching the real user state, set `XDG_DATA_HOME=<scratch dir>` (DB and cover cache) and `GSETTINGS_BACKEND=memory`. Build first without it, then run with `-x compileSass -x compileResources`: `sass` may be a distrobox wrapper, and rootless podman keeps its containers under `XDG_DATA_HOME`, so an overridden value makes distrobox create a second container. Never run `podman system reset` or similar with an overridden `XDG_DATA_HOME`; the runtime state under `XDG_RUNTIME_DIR` is shared with the real containers. For a headless GUI start, run `gtk4-broadwayd :7` and launch with `GDK_BACKEND=broadway BROADWAY_DISPLAY=:7`. Logback writes `logs/` into the working directory.
- There is no test suite; `src/test` only holds `MigrationGenerator` (used by `generateMigrations`).

## Jellyfin test server

A git-ignored `.env` in the repo root holds credentials for a Jellyfin test server: `JELLYFIN_URL`, `JELLYFIN_USER`, `JELLYFIN_API_KEY`. Use them to explore or verify the Jellyfin API (e.g. `curl` with `Authorization: MediaBrowser Client="GrooveBoat", Device="dev", DeviceId="dev", Version="1.0", Token="$JELLYFIN_API_KEY"`; this server, Jellyfin 12.x, rejects `X-Emby-Token`, and URLs take `ApiKey=` but not `api_key=`). It is an API key, not a user password, so the app's username/password login can't be exercised with it; restore a `Session(user, apiKey, userId)` instead. Don't call `/Sessions/Logout` with it. Load the values from the environment without printing them, never commit them or copy them into code, docs or logs, and keep them out of command output (no `cat .env`, no echoing a request URL that has the key in it). The app itself does not read `.env`.

## Architecture

Detailed architecture docs live in `docs/` (start at `docs/README.md`). Summary:

**Startup (`Main`)**: configures the default Ebean `Database` (H2 file at `$XDG_DATA_HOME/grooveboat/cache`, migrations auto-run from `resources/dbmigration/h2`), registers the gresource bundle, registers every templated widget / custom GObject type, then runs `GrooveboatApp`.

**Adding a UI component** requires touching three places: a `.ui` template in `resources/templates/`, an entry in `grooveboat.gresource.xml` (alias = path under `/io/github/Nyeksenn/grooveboat/`), and a `TemplateTypes.register(...)` (or `Types.register(...)` for non-template GObjects) call in `Main`. Widget classes use `@GtkTemplate`, `@RegisteredType`, `@GtkChild(name=...)`, `@GtkCallback`, `@InstanceInit`, and must keep a `(MemorySegment address)` constructor. New icons likewise need a gresource entry under the `icons/scalable/actions/` prefix.

**Backends** (`backend/`): `MusicBackend` is the protocol-independent interface (probe, authenticate/restore, album/song/artist listing, image download, playback URL, release, logout) returning neutral records (`RemoteAlbum`, `RemoteSong`, …). Implementations: `backend.subsonic.SubsonicBackend` (salted-token auth; its JSON records live in `backend.subsonic.dto`) and `backend.jellyfin.JellyfinBackend` (`Authorization: MediaBrowser` header; reads JSON as trees). `BackendFactory` creates them. No code outside `backend/` may depend on a specific protocol.

**`GrooveboatApp`** (Adw `Application`) is the central hub that widgets reach via `getRoot()` → window → `getApplication()`. It owns:
- the **session slot**: one `MusicBackend` plus its `library.LibrarySync`, filled once via `startSession` (by startup restore in `GrooveboatWindow.initializeClient` or by setup) and emptied only by `logout`. There is deliberately no way to swap backend or server in a running session; `startSession` throws if one is active. Widgets get it via `getBackend()` / `getLibrary()`, which are `null` while logged out.
- `LibrarySync` is the only code that writes server data to the DB: incremental album sync keyed on `last-sync` (full refresh truncates tables via `Helpers.truncateDB`), lazy per-album song upserts, artist refresh. Cancellable for logout.
- `pipeline` (`audio.AppPipeline`) — one `playbin3` for the process; only its URI changes. Gapless: playbin3's `about-to-finish` (on a streaming thread) takes the prepared next URI. Its `MainLoop` runs on `gstExec`. Bus messages are handled per type via `bus.onMessage("eos"|"stream-start"|"error"|"buffering", …)` in `GrooveboatApp`. **Never call `Message.readType()` or add a catch-all bus watch**: java-gi (0.14.1 and 1.0.0-RC3) loops forever converting extended message types such as stream-collection. Also don't subscribe to `state-changed` or call `readSrc()` on playbin3's messages: the per-element proxies caused java-gi toggle-ref crashes.
- `stream` (`gobjects.RemoteMediaStream`, a GTK `MediaStream`) — **one for the app's lifetime**, bound to widgets once. `load(item)` starts a song, `setNext(item)` prepares the gapless follow-up, `onStreamStart()` follows a switch. Never create a second proxy over an existing object (e.g. `new MediaStream$Impl(handle)`). Playback URIs carry credentials: log only `PlaybackSource.toString()`/`redact()`, never the URI or GStreamer debug strings.
- `queueStore` (`audio.QueueStore`, a GObject) — the play queue (`ListStore<SongListItem>` + `SingleSelection`). A selection change loads the song into the stream and emits `initialize-song` (player widgets only update labels on it); `refreshNext()` keeps the stream's next song in sync; `followGaplessSwitch` moves the selection without reloading. Queue manipulation is exposed as GActions (e.g. `queue-song`, `play-next`, `toggle-play`). GAction/callback arguments (`Variant`s) are only valid during the callback: read them before handing work to another thread.
- `executor` / `scheduler` thread pools; network, DB and keyring work must stay off the GTK main thread (return via `GLib.idleAddOnce`).

**Logout** (`GrooveboatApp.logout`) wipes everything for the session: it stops playback, clears the queue, cancels sync and revokes the server session (best effort), then clears the keyring entry, the DB tables, the cover cache, the texture cache and the session settings, and finally replaces the window with a new one. Because of that, widgets that connect to app-lifetime objects (`queueStore` signals, its selection model) must disconnect in `onUnrealize`. Create the new window before destroying the old one, or the app quits.

**Data layers** (keep them distinct): protocol records stay private to each backend; `entities/` are Ebean entities (`*Model`, extending `BaseModel`) persisted in H2, with query beans (`entities.query.Q*Model`) generated by the `querybean-generator` annotation processor (after changing entities, run `generateMigrations` and commit the new files in `resources/dbmigration/`); `gobjects/` holds the GObject list items (`AlbumListItem`, `SongListItem`, `QueueListItem`) used in GTK list models.

**Settings and credentials**: `utils.AppSettings` wraps GSettings. `backend` (`none`/`opensubsonic`/`jellyfin`; `none` = logged out → setup dialog), `url`, `username`, `user-id`, `last-sync`, and `device-id` (kept across logouts). The session credential (the OpenSubsonic password or the Jellyfin access token) goes to the keyring via `SecretKeeper` (`COMPAT_NETWORK` schema, keyed by user/scheme/host). Setup (`SetupDialog`: `WelcomePage` → `ServerTypePage` → `ConnectionPage` → `CredentialsPage`) writes settings only after a successful login. `AppSettings.migrate()` maps pre-backend installs to `opensubsonic`.

**Styling**: edit `resources/scss/main.scss`, not the generated `css/main.css`; it's loaded from the gresource at `/io/github/Nyeksenn/grooveboat/main.css`.

# Startup and Lifecycle

## Process start (`Main`)

Startup happens in this order. The order matters because later steps depend on earlier ones.

1. **Database.** The default Ebean database is set up to point at an H2 file in the user's data directory. Ebean's automatic DDL generation is turned off, and the versioned migrations from the classpath run instead, so the schema is always exactly what the migration files say. It is registered as the *default* database, which is how the rest of the code reaches it without being handed a reference.
2. **Native type initialisation.** The GStreamer audio library is initialised up front, so its GObject types resolve correctly the first time they are looked up.
3. **Settings migration.** Installs from before backend selection existed have a URL and username but no backend. They are marked as OpenSubsonic sessions, so these users don't have to set the app up again.
4. **Resources.** The compiled gresource bundle is loaded and registered: from `build/generated` in the devel profile (so the app has to be started from the repository root), from `/app/share/io.github.Nyeksenn.grooveboat` in the Flatpak. It holds the widget templates, the stylesheet and the icons. Gettext is bound to the installed locale directory before that, except in the devel profile.
5. **Type registration.** Every templated widget that appears inside another template, and every custom GObject type, is registered with the GObject type system. A widget that is missing from this list fails when a template refers to it.
6. **Application run.** `GrooveboatApp` is created and handed control.

## Application startup and activation

`GrooveboatApp` builds the play queue (which registers its queue and playback actions on the application right away) and the thread pools when it is created. It has no backend yet; that only arrives with a session.

At **startup**:

- The playbin3 player is created, and its own main loop is started on a dedicated thread.
- The application's single media stream is created on top of it.
- The application-wide actions for account settings and quit are registered, and quit is bound to Ctrl+Q.

At **activate**:

- If a window already exists (the app was launched a second time), it is brought to the front and nothing else happens.
- Otherwise the bundled icon path is added to the icon theme, and the stylesheet is installed for the display.
- The main window is created.
- The toggle-play and logout actions are registered.
- The app starts watching the pipeline's message bus.
- The window is presented.

## Window initialisation

When the main window is realised it registers a retry action and then works out what to show:

1. **Plugin check.** If GStreamer elements needed for playback are missing (playbin3, the HTTP source, an HLS demuxer, the MP4 demuxer, the automatic audio sink), an error page names them and the packages that provide them. Nothing else happens.
2. **Existing session.** If the application already holds a session (for example after a retry or just after setup), the library is shown as described under *Showing the library* below.
3. **Logged out.** If the stored backend is *none*, the setup dialog opens.
4. **Restore.** Otherwise a background task:
   - creates a backend of the stored type for the stored URL;
   - looks up the session credential in the keyring;
   - **with a cached library and a stored credential**, adopts the stored session without contacting the server and starts it at once (the "Connecting" page is not shown);
   - **otherwise** the window shows "Connecting" and the task asks the backend to restore and verify the session first. If the server rejects the stored login, a "Session Expired" page offers Log Out. If the server can't be reached, a "Connection Error" page offers Retry and Log Out; with a cached library it starts from the cache instead and shows a toast.

### Showing the library

Once a session is started the window shows the library:

- **Cached library present:** the content page is shown at once and the queue is restored. In the background (`library.StartupSync`) the stored login is verified with the server, if it hasn't been just now, and an incremental sync runs. When the sync found new albums, the album and artist grids reload. If the server can't be reached, a toast says so and the cached library stays. If it rejects the login, a dialog over the library offers Log Out. A result that arrives after the session ended (logout) is ignored.
- **Empty cache** (first sync after setup or login): the window shows "Loading Library" until the first sync has finished, then the content page.

## First-run setup

The setup dialog is a navigation view with four pages. It holds the choices made so far; nothing is written to settings or the keyring until the last step succeeds, so leaving setup halfway leaves no partial configuration behind.

1. **Welcome**: introduction.
2. **Server type**: the user picks OpenSubsonic or Jellyfin.
3. **Connection**: the user enters the server URL. A backend of the chosen type probes it without credentials.
   - OpenSubsonic must report OpenSubsonic support and at least the minimum API version.
   - Jellyfin must identify itself as Jellyfin at or above the minimum server version.
   - If the probe fails but the other server type answers, the toast says which type it looks like. The probed backend is kept as the dialog's *candidate*.
4. **Credentials**: the candidate logs in with username and password. The resulting session credential (the password for OpenSubsonic, an access token for Jellyfin) is stored in the keyring. Then the backend type, URL, username and server user ID are saved to settings, the application's session is started with the candidate, and the dialog closes. The window then runs the first sync before showing the library.

## Logout

Logout is the only way to end a session, and so the only way to switch server or server type. It is offered from the main menu, the preferences dialog and the error page, always behind a confirmation dialog. It runs in this order, and each step runs even if earlier ones failed:

1. The window shows a "Logging Out" page, so nothing else can be clicked.
2. Playback stops, the media stream releases its play sessions and forgets the prepared next song, and the queue is cleared.
3. On a dedicated thread:
   - running sync work is cancelled, with a bounded wait for it to unwind;
   - the backend ends the server-side session where the protocol has one (best effort, short timeout);
   - the keyring entry is removed;
   - the cache tables are truncated and the cover cache directory is emptied;
   - the in-memory cover and artist texture caches are cleared;
   - the session settings are reset (the device ID is kept on purpose).
4. Back on the main thread, the library sync and backend are closed and the session slot is emptied. A new window is created and presented, and only then is the old one destroyed: an application with no windows would quit. The new window finds the backend set to *none* and opens setup.

Replacing the whole window, rather than resetting widgets one by one, guarantees that nothing from the old session survives in the UI.

## Shutdown

On shutdown the application, in this order:

1. Stops the media stream, which releases its play sessions.
2. Closes the library sync and the backend, if a session is active.
3. Stops the thread pools.
4. Sets the pipeline to its null state, quits its main loop and stops its thread.

The quit action destroys and removes the active window before the application quits.

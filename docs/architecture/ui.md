# User Interface

## Templated widgets

Every non-trivial widget is a Java class paired with a GtkBuilder `.ui` template:

- **Template.** The class is annotated with the resource path of its template and, if it appears inside other templates, a registered type name.
- **Children.** Named children of the template are injected into annotated fields.
- **Callbacks.** Signal handlers named in the template are linked to annotated methods.
- **Initialisation.** An instance-init method runs when the object is built. Most widgets put their real setup in a realise handler instead, because only then can they reach the window, and through it the application. Anything that depends on the current session, such as the username shown in the avatar or the server type named on the connection page, is read when the widget is mapped, because the window can be built before setup has finished.

Adding a widget touches three places: the template file, an entry in the gresource manifest, and (for widgets used inside other templates) a type registration at startup.

## Resource bundle

Templates, the compiled stylesheet and the symbolic icons are packed into one gresource bundle by Gradle (`gradlew run`) or Meson (Flatpak) from `data/`. Styles are written in SCSS (`data/scss`) and compiled to the committed `data/css/main.css`. Icons are registered under the application's resource path so they can be referred to by icon name. The stylesheet is installed at application priority so it overrides the theme defaults.

## Window structure

The main window is a libadwaita application window. Its content is a toast overlay, used for playback errors and similar messages, around a stack with three pages:

- **Loading**: shown while the app connects, syncs or logs out. Its title says which.
- **Error**: shown for missing GStreamer plugins, an expired session or an unreachable server. Title and description are set for the case. It has Retry (only where retrying can help) and Log Out buttons.
- **Content**: the main interface.

The content page is a navigation split, with the *music chooser* on one side and the full *music player* on the other.

**Music chooser.** A header bar with the account avatar, the main menu, a view switcher, a search bar and a refresh progress bar. The main menu holds Refresh, Full Refresh, Preferences and Log Out. Below it are the library views. The album view is a grid of album cards with its own navigation view: clicking a card pushes a song list page for that album, which loads its songs lazily through the library sync and offers play-album and queue-album. The artist view is a grid of the library's album artists (artists with at least one album), sorted by name. Each card is an avatar with the artist's image, or initials if there is none. It has its own navigation view: clicking a card pushes the artist page, which lists the artist's songs grouped by album, newest album first. Each album is a section with a header (cover, title, year, play and queue album) above a boxed list of its songs, and the sections are spaced apart. Songs come through the library sync, so albums that were never opened are fetched from the server, and sections appear one by one as their songs arrive. Both grids reload from the database after a refresh, through window actions.

**Music player.** The large cover, the song title and artist, the transport controls and the queue list.

**Mini player.** A compact bar at the bottom of the chooser. Clicking it, anywhere except on its buttons, navigates to the full player.

## Dialogs

- **Setup** (`SetupDialog`): a non-closable dialog with a navigation view through welcome, server type, connection and credentials pages. The pages find the dialog by walking up the widget tree, and share the chosen server type and the probed backend through it. See [Startup and Lifecycle](lifecycle.md#first-run-setup).
- **Preferences** (`GrooveboatPrefs`): opened from the avatar or the main menu. It shows the session's server type, address, server version (queried in the background) and username, all read-only, and a Log Out button. Connection details can't be edited, because a session's server is fixed. A second page, **Audio**, has a gapless playback switch, a ReplayGain switch and a ReplayGain mode row (track or album, only enabled while ReplayGain is on). The rows are bound to the `gapless-playback`, `replay-gain-enabled` and `replay-gain-mode` GSettings keys; `audio.AudioSettings` forwards the keys to the pipeline.
- **Log out confirmation**: an alert dialog explaining that the local cache, queue and stored login will be cleared and that the server's library is not affected.

## Adaptive layout

The window defines a breakpoint for narrow widths:

- **Narrow.** The mini player and a bottom view-switcher bar are shown, and the switcher is removed from the header.
- **Wide.** The switcher returns to the header and the mini player is hidden.

Crossing the breakpoint collapses or expands the split view, which unrealizes and realizes its content again. Widgets that do one-time setup on realize (the album grid loading its albums, the chooser adding its actions and bindings, the mini player binding the stream) guard it so it runs only once. Handlers that are disconnected on unrealize are connected again on each realize.

## Lists and models

Collections are always shown through GTK list models built from GObject list items:

- **Album grid.** A list store → a sort model ordered by title → while a search is active, a filter model that matches the search text → a no-selection wrapper.
- **Song list and queue.** List views built by signal-based factories, whose setup and bind handlers create and fill the row widgets from list items.
- **Queue view.** Uses the queue store's selection model directly, so selecting a row changes the current song.

## Actions

The UI and the domain layers mostly talk through GActions rather than direct method calls:

- **Application scope.** Queue and playback actions (play, queue, remove, next, previous, toggle play), refresh and full refresh, account settings, logout, quit.
- **Window scope.** Reloading the album grid, retrying the connection, closing the setup dialog.
- **Widget scope.** Navigation pushes inside navigation views.

Menu items and buttons in templates refer to these actions by name, with the target value (a song or album ID) attached to the item.

## Widget lifetime

Logout replaces the main window with a new one, so widgets must not leave anything behind that outlives their window. The queue store, its selection model, the media stream and the application live for the whole process. Property bindings from the stream to a widget end on their own when the widget is destroyed. Widgets that connect handlers to them (the player and mini player to the *initialize-song* signal, the queue view to the selection) keep the connection and disconnect it when they are unrealised. Without that, the old window's widgets would keep reacting to the new session after they were destroyed.

Widgets also get realised again whenever the window crosses the size breakpoint (the split view collapses or expands), so two helpers in `ui/AppWidgets` cover the two cases:

- `whileRealized(widget, scope -> …)` runs its setup on every realise and releases what was registered in the scope (`scope.track(connection)` for signal handlers, `scope.onRelease(cleanup)` for listeners) on every unrealise. Use it for anything connected to the queue store, its selection or the stream.
- `onFirstRealize(widget, setup)` runs one-time setup (loading the grid, adding actions) only the first time. Pages that must wait for a session before loading keep their own guard.

`AppWidgets.app(widget)` and `window(widget)` reach the application and main window above a realised widget; they are only valid while it is realised, so code that can run while a widget is unrealised (a reload action) keeps what it needs from setup.

## Loading data off the main thread

`ui/UiAsync` is the one way widgets do work on the pool and show the result: `run(executor, work, onUi)` runs `work` on the pool and `onUi` on the main loop, `background(executor, task)` is for tasks that update the UI themselves, and both log failures instead of losing them. `UiAsync.Latest` is for loads that replace each other (the album and artist grids): only the newest result is applied, so overlapping reloads can't add items twice. The work runs on the given executor, which keeps custom GObjects off the ForkJoin common pool.

Application actions registered by a widget (refresh and full refresh, from the music chooser) are registered again by the new window's widget, which replaces the old ones.

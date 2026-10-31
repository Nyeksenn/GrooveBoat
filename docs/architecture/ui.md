# User Interface

## Templated widgets

Every non-trivial widget is a Java class paired with a GtkBuilder `.ui` template:

- **Template.** The class is annotated with the resource path of its template and, if it appears inside other templates, a registered type name.
- **Children.** Named children of the template are injected into annotated fields.
- **Callbacks.** Signal handlers named in the template are linked to annotated methods.
- **Initialisation.** An instance-init method runs when the object is built. Most widgets put their real setup in a realise handler instead, because only then can they reach the window, and through it the application.

Adding a widget touches three places: the template file, an entry in the gresource manifest, and (for widgets used inside other templates) a type registration at startup.

## Resource bundle

Templates, the compiled stylesheet and the symbolic icons are packed into one gresource bundle during the Gradle build. Styles are written in SCSS and compiled to CSS before the bundle is built. Icons are registered under the application's resource path so they can be referred to by icon name. The stylesheet is installed at application priority so it overrides the theme defaults.

## Window structure

The main window is a libadwaita application window whose top level is a stack with three pages:

- **Loading**: shown while the app connects and syncs at startup.
- **Error**: shown when the stored connection details fail.
- **Content**: the main interface.

The content page is a navigation split, with the *music chooser* on one side and the full *music player* on the other.

**Music chooser.** A header bar with a view switcher, a search bar and a refresh progress bar. Below it are the library views. The album view is a grid of album cards with its own navigation view: clicking a card pushes a song list page for that album, which loads its songs lazily through the server facade and offers play-album and queue-album.

**Music player.** The large cover, the song title and artist, the transport controls and the queue list.

**Mini player.** A compact bar at the bottom of the chooser. Clicking it, anywhere except on its buttons, navigates to the full player.

## Adaptive layout

The window defines a breakpoint for narrow widths:

- **Narrow.** The mini player and a bottom view-switcher bar are shown, and the switcher is removed from the header.
- **Wide.** The switcher returns to the header and the mini player is hidden.

## Lists and models

Collections are always shown through GTK list models built from GObject list items:

- **Album grid.** A list store → a sort model ordered by title → while a search is active, a filter model that matches the search text → a no-selection wrapper.
- **Song list and queue.** List views built by signal-based factories, whose setup and bind handlers create and fill the row widgets from list items.
- **Queue view.** Uses the queue store's selection model directly, so selecting a row changes the current song.

## Actions

The UI and the domain layers mostly talk through GActions rather than direct method calls:

- **Application scope.** Queue and playback actions (play, queue, remove, next, previous, toggle play), refresh and full refresh, account settings, quit.
- **Window scope.** Reloading the album grid; closing the setup dialog.
- **Widget scope.** Navigation pushes inside navigation views.

Menu items and buttons in templates refer to these actions by name, with the target value (a song or album ID) attached to the item.

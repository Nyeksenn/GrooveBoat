# Startup and Lifecycle

## Process start (`Main`)

Startup happens in this order. The order matters because later steps depend on earlier ones.

1. **Database.** The default Ebean database is set up to point at an H2 file in the user's data directory. Ebean's automatic DDL generation is turned off, and the versioned migrations from the classpath run instead, so the schema is always exactly what the migration files say. It is registered as the *default* database, which is how the rest of the code reaches it without being handed a reference.
2. **Native type initialisation.** The GStreamer audio library and the client class are initialised up front, so their GObject types resolve correctly the first time they are looked up.
3. **Resources.** The compiled gresource bundle is loaded from the source tree by a relative path and registered. It holds the widget templates, the stylesheet and the icons. Because of the relative path, the app has to be started from the repository root.
4. **Type registration.** Every templated widget that appears inside another template, and every custom GObject type, is registered with the GObject type system. A widget that is missing from this list fails when a template refers to it.
5. **Application run.** `GrooveboatApp` is created and handed control.

## Application startup and activation

`GrooveboatApp` builds its services when it is created: the server facade, the play queue (which registers its queue and playback actions on the application right away), and the thread pools.

At **startup**:

- The GStreamer pipeline is created and its main loop is started on a dedicated thread.
- The application-wide actions for preferences and quit are registered, and quit is bound to Ctrl+Q.

At **activate**:

- The bundled icon path is added to the icon theme, and the stylesheet is installed for the display.
- The main window is created.
- The toggle-play action is registered.
- The app starts watching the pipeline's message bus.
- The window is presented.

## Window initialisation

When the main window is realised it does two things:

1. **Connect to the server.** The window shows a loading page and reads the server URL and username from GSettings.
   - If either is empty, this is a first run, and the setup dialog opens.
   - If both are set, a background task looks up the password in the system keyring, builds a client and checks the credentials against the server. On success it runs an incremental metadata sync and then switches to the content page. On failure it switches to an error page.
2. **Restore the queue.** The play queue is asked to load its initial state, which queues a song from the local cache.

## First-run setup

The setup dialog is a navigation view with three pages:

1. **Welcome**: introduction.
2. **Connection**: the user enters a server URL. A client is built with placeholder credentials and the server is pinged. If the server answers, the URL is saved to GSettings and the next page is pushed.
3. **Credentials**: the user enters a username and password. These are checked against the server, the password is stored in the keyring, the username and URL are saved to GSettings, and a window action closes the dialog and shows the content page.

The preferences dialog (the "account settings" action) does the same validate-store-save steps for changing connection details later.

## Shutdown

On shutdown the application, in this order:

1. Closes the server facade's executors.
2. Destroys the current media stream.
3. Stops the thread pools.
4. Quits the GStreamer main loop and stops its thread.
5. Closes the secret store's executor.

The quit action destroys and removes the active window before the application quits.

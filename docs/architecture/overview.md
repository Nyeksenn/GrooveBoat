# Architecture Overview

GrooveBoat is a single-process desktop application organised in layers. Each layer only reaches into the one below it, with one exception: `GrooveboatApp` is the shared hub, and nearly every component reaches it.

## Layers

| Layer | Packages | Responsibility |
|---|---|---|
| Presentation | `ui` | GTK/libadwaita widgets, each backed by a `.ui` template. They react to user input and to signals and property bindings from the layers below. |
| Application hub | `ui.GrooveboatApp` | Owns the long-lived services (server facade, audio pipeline, current media stream, play queue, thread pools) and registers the application-wide actions. |
| Playback | `audio`, `gobjects.MediaSubsonic` | The GStreamer pipeline, the per-song media stream that feeds it, and the play queue model. |
| Domain / sync | `client.Subsonic` | Coordinates the remote server and the local cache: incremental sync, lazy song loading, stream requests, credential checks. |
| Remote access | `client.SubsonicClient`, `deserializer` | Raw HTTP calls to the Subsonic REST API, token authentication, and JSON-to-record mapping. |
| Persistence | `entities`, `resources/dbmigration` | Ebean ORM entities stored in an embedded H2 database, plus versioned schema migrations. |
| GObject glue | `gobjects` | Plain data wrapped as GObjects so it can live in GTK list models, plus custom GObject types (rounded cover image, secret storage). |
| Utilities | `utils.Helpers` | Platform data directory lookup, MIME type to GStreamer caps mapping, database truncation. |

## Three representations of the same data

An album, artist or song exists in up to three forms. Each form has its own job, and you should not use one where another belongs.

1. **Deserializer records** (`deserializer`): immutable Java records that mirror the server's JSON. They exist only while a response is being processed.
2. **Entities** (`entities`, the `*Model` classes): the persistent, relational form stored in H2. The rest of the app queries these, through the generated type-safe query beans.
3. **List items** (`gobjects`, the `*ListItem` classes): lightweight GObjects holding exactly what a widget row or card needs to display, including the decoded cover texture. Only these can go into GTK list models.

The flow always runs server JSON → record → entity (saved in the database) → list item (shown in the UI). The UI never shows server responses directly.

## Native interop

All GNOME libraries are reached through java-gi, which uses the Java Foreign Function & Memory API. This shapes the code in a few ways:

- Custom widgets and GObjects are real GObject subclasses. They must be registered with the GObject type system before they are first used, and they keep a constructor that takes a native memory address so that native code can create them.
- Callbacks handed to native code (for example the GStreamer app-source callbacks or GAction handlers) are bound to a memory arena whose lifetime has to outlast the callback.
- The JVM has to be started with native access enabled.

## Backend coupling

Subsonic is currently the only supported server type. The places that are tied to Subsonic specifically:

- `SubsonicClient` (endpoints, salted-token auth, response envelope) and the `deserializer` records.
- `Subsonic`, the facade that the UI and playback layers hold a reference to by its concrete type.
- `MediaSubsonic`, which asks the facade for byte streams and offset-based seeks.
- The setup pages and the preferences dialog, which build clients and check credentials through the facade.
- The GSettings schema descriptions, which describe the keys as Subsonic settings.

The entity layer, the play queue, the pipeline and the widgets do not depend on the server protocol. They only see entities, list items and the media stream.

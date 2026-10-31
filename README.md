# GrooveBoat

A music player for your own server. GrooveBoat is a native GNOME app (GTK4 and libadwaita) that plays your library from a **Jellyfin** or **OpenSubsonic** server, such as Navidrome, Gonic or Airsonic-Advanced.

## Features

- Browse albums and artists, with cover art
- Play queue with gapless playback
- Original audio is streamed directly from your server and never transcoded
- Works with Jellyfin 10.9 or newer and OpenSubsonic servers
- Your login is stored in the system keyring, and your library is cached locally for fast startup
- Adaptive layout with a mini player for narrow windows

## Install

GrooveBoat is packaged as a Flatpak. There is no published release yet, so build it from source.

Install the runtimes once:

```sh
flatpak --user install org.gnome.Platform//50 org.gnome.Sdk//50
flatpak --user install org.freedesktop.Sdk.Extension.openjdk//25.08
```

Build and install:

```sh
flatpak-builder --force-clean --user --install --state-dir=/tmp/flatpak-builder /tmp/builddir io.github.Nyeksenn.grooveboat.yaml
```

Run it:

```sh
flatpak run io.github.Nyeksenn.grooveboat
```

Remove it:

```sh
flatpak uninstall io.github.Nyeksenn.grooveboat
```

## First run

On first start a setup dialog asks you to:

1. Choose your server type (Jellyfin or OpenSubsonic)
2. Enter the server address, for example `https://music.example.com`
3. Sign in with your username and password

GrooveBoat then syncs your library. To use another server or account, open the preferences and choose **Log Out**. This clears the local cache, the queue and the stored login, but doesn't touch anything on your server.

Playback needs a few GStreamer plugins. If any are missing, the app tells you which.

## Where your data goes

When installed as a Flatpak, everything lives under `~/.var/app/io.github.Nyeksenn.grooveboat/`: the library cache, cover art and logs. The login is kept in your keyring, not on disk.

## Building for development

You need JDK 25 or newer, GTK4, libadwaita, GStreamer (with the base, good and bad plugin sets), libsecret, `glib-compile-resources`, `glib-compile-schemas` and `sass` on your system.

```sh
./gradlew run     # build and start the app from the repository root
./gradlew build   # compile everything
```

`run` uses the development profile: resources are loaded from `build/generated` and translations are not loaded. To try the app without touching your real data, start it with a scratch data directory and an in-memory settings backend:

```sh
XDG_DATA_HOME=/tmp/grooveboat-dev GSETTINGS_BACKEND=memory ./gradlew run
```

After changing Gradle dependencies, regenerate the offline dependency list used by the Flatpak build:

```sh
./gradlew flatpakGradleGenerator --no-configuration-cache
```

Edit styles in `data/scss/main.scss` (the generated `data/css/main.css` is committed). User-visible strings are translatable: use `translatable="yes"` in `.ui` files and `i18n(...)` in Java, and list the file in `po/POTFILES.in`.

For how the app is put together, see the [documentation](docs/README.md).

## Project layout

| Path | Contents |
|---|---|
| `src/main/java` | Application code |
| `data/` | UI templates, styles, icons, GSettings schema, desktop and metainfo files |
| `po/` | Translations |
| `io.github.Nyeksenn.grooveboat.yaml` | Flatpak manifest |
| `docs/` | Architecture documentation |

The packaging setup follows the [java-gi app template](https://codeberg.org/java-gi/app-template).

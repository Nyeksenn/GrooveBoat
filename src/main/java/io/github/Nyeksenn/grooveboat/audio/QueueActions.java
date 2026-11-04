package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import io.github.Nyeksenn.grooveboat.ui.GrooveboatApp;
import org.gnome.gio.ActionEntry;
import org.gnome.glib.GLib;
import org.javagi.glib.types.VariantTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The app-level GActions that manipulate the play queue ({@code play-song}, {@code queue-song}, {@code play-album},
 * {@code queue-album}, {@code remove-song}) and drive playback ({@code play-next}, {@code play-previous}).
 * <p>
 * Action arguments are only valid during the callback, so they are read there before any work moves to a pool.
 * Database and network work runs on the app's I/O pool; queue changes go back to the main thread.
 */
final class QueueActions {
    private static final Logger logger = LoggerFactory.getLogger(QueueActions.class);

    private final GrooveboatApp app;
    private final QueueStore queue;

    QueueActions(GrooveboatApp app, QueueStore queue) {
        this.app = app;
        this.queue = queue;
    }

    /** Registers all actions on the application. */
    void register() {
        var exec = app.executors.io;
        var model = queue.getModel();

        var entries = new ActionEntry[]{
                stringAction("play-song", songId -> exec.submit(() -> {
                    logger.info("Playing this song:");
                    var item = songItem(songId);
                    if (item != null) {
                        GLib.idleAddOnce(() -> {
                            model.removeAll();
                            model.append(item);
                        });
                    }
                })),
                stringAction("queue-song", songId -> exec.submit(() -> {
                    logger.info("Queueing this song:");
                    var item = songItem(songId);
                    if (item != null) {
                        GLib.idleAddOnce(() -> model.append(item));
                    }
                })),
                stringAction("play-album", albumId -> exec.submit(() -> {
                    var items = albumItems(albumId);
                    if (!items.isEmpty()) {
                        GLib.idleAddOnce(() -> {
                            // Empty first, so the selection starts over at the album's first song.
                            model.removeAll();
                            items.forEach(model::append);
                        });
                    }
                })),
                stringAction("queue-album", albumId -> exec.submit(() -> {
                    var items = albumItems(albumId);
                    if (!items.isEmpty()) {
                        GLib.idleAddOnce(() -> items.forEach(model::append));
                    }
                })),
                new ActionEntry(
                        "remove-song",
                        (_, parameter) -> {
                            if (parameter != null) {
                                final var index = parameter.getInt32();
                                GLib.idleAddOnce(() -> model.removeAt(index));
                            }
                        },
                        VariantTypes.INT32.dupString(),
                        null,
                        null,
                        Arena.global()
                ),
                new ActionEntry(
                        "play-next",
                        (_, parameter) -> queue.advance(parameter != null && parameter.getBoolean()),
                        VariantTypes.BOOLEAN.dupString(),
                        null,
                        null,
                        Arena.global()
                ),
                new ActionEntry(
                        "play-previous",
                        (_, _) -> queue.previous(),
                        null,
                        null,
                        null,
                        Arena.global()
                )
        };
        app.addActionEntries(entries, null);
    }

    /** An action that takes a string (a song or album id). */
    private static ActionEntry stringAction(String name, Consumer<String> handler) {
        return new ActionEntry(
                name,
                (_, parameter) -> {
                    if (parameter != null) {
                        // Read now: callback arguments are only valid during the callback.
                        final var value = parameter.getString(null);
                        handler.accept(value);
                    }
                },
                VariantTypes.STRING.dupString(),
                null,
                null,
                Arena.global()
        );
    }

    /** The queue item for a cached song, or {@code null} if the song is unknown. Runs off the main thread. */
    private static SongListItem songItem(String songId) {
        var song = new QSongModel().id.equalTo(songId).songArtists.fetchQuery().album.fetch().findOne();
        if (song == null) {
            return null;
        }
        return SongListItem.of(song, song.getAlbum());
    }

    /**
     * Queue items for all songs of an album, fetched from the server when the cache doesn't have them all yet.
     * Runs off the main thread.
     */
    private List<SongListItem> albumItems(String albumId) {
        var library = app.getLibrary();
        var album = new QAlbumModel().id.equalTo(albumId).findOne();
        if (library == null || album == null) {
            return List.of();
        }
        var items = new ArrayList<SongListItem>();
        for (var song : library.getSongs(albumId)) {
            items.add(SongListItem.of(song, album));
        }
        return items;
    }
}

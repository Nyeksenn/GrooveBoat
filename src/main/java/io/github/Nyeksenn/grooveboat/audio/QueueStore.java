package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import io.github.Nyeksenn.grooveboat.ui.AlbumTextureLoader;
import io.github.Nyeksenn.grooveboat.ui.GrooveboatApp;
import org.gnome.gio.ActionEntry;
import org.gnome.gio.ListStore;
import org.gnome.glib.GLib;
import org.gnome.glib.Variant;
import org.gnome.gobject.GObject;
import org.gnome.gobject.ParamSpec;
import org.gnome.gtk.SingleSelection;
import org.javagi.glib.types.VariantTypes;
import org.javagi.gobject.annotations.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;

public class QueueStore extends GObject {
    private final GrooveboatApp app;
    private static final Logger logger = LoggerFactory.getLogger(QueueStore.class);
    private static final AlbumTextureLoader loader = AlbumTextureLoader.getInstance();
    private boolean initialized;
    private final ListStore<SongListItem> model = new ListStore<>();
    private final SingleSelection<SongListItem> selection = new SingleSelection<>(model);
    private static final int GTK_INVALID_LIST_POSITION = -1;
    /** Set while the selection follows a gapless switch, so the already playing song isn't reloaded. */
    private boolean followingSwitch = false;

    @Signal
    public interface InitializeSong {
        void run(SongListItem item);
    }

    public SingleSelection<SongListItem> getSelection() {
        return this.selection;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void itemSelectionChanged(ParamSpec pspec) {
        if (followingSwitch) {
            return;
        }
        // Prepare the follow-up first: the pipeline may ask for it as soon as the new song starts downloading.
        refreshNext();
        var selectedItem = model.getItem(selection.getSelected());
        if (selectedItem != null && app.hasSession()) {
            app.getStream().load(selectedItem);
            emit("initialize-song", selectedItem);
            if (!initialized) {
                initialized = true;
            } else {
                app.getStream().setPlaying(true);
            }
        }
    }

    /**
     * Tells the stream which song follows the current one, for gapless playback: the same song in loop mode,
     * otherwise the next queue entry (or none at the end of the queue).
     */
    public void refreshNext() {
        var stream = app.getStream();
        if (stream == null || !app.hasSession()) {
            return;
        }
        var idx = selection.getSelected();
        SongListItem nextItem = null;
        if (idx != GTK_INVALID_LIST_POSITION) {
            if (app.shouldLoop) {
                nextItem = model.getItem(idx);
            } else if (idx + 1 < model.getNItems()) {
                nextItem = model.getItem(idx + 1);
            }
        }
        stream.setNext(nextItem);
    }

    /** Moves the selection to a song the stream already switched to gaplessly, without reloading it. */
    public void followGaplessSwitch(SongListItem item) {
        for (int i = 0; i < model.getNItems(); i++) {
            if (model.getItem(i) == item) {
                followingSwitch = true;
                try {
                    selection.setSelected(i);
                } finally {
                    followingSwitch = false;
                }
                break;
            }
        }
        emit("initialize-song", item);
        refreshNext();
    }

    /**
     * Manual or end-of-stream advance: restarts the current song in loop mode, otherwise selects the next one.
     *
     * @return {@code false} when the queue has no next song
     */
    public boolean advance(boolean loop) {
        var idx = selection.getSelected();
        if (idx == GTK_INVALID_LIST_POSITION) {
            return false;
        }
        if (loop) {
            selection.emitNotify("selected", null);
            return true;
        }
        if (idx + 1 < model.getNItems()) {
            selection.selectItem(idx + 1, true);
            return true;
        }
        return false;
    }

    public void loadState() {
        if (!model.isEmpty()) {
            return;
        }
        var initSong = new QSongModel().setMaxRows(1).findOne();
        if (initSong == null) {
            return;
        }
        var action = app.lookupAction("queue-song");
        action.activate(Variant.string(initSong.getId()));
    }

    /** Empties the queue; the next queued song will be prepared without auto-playing, as after startup. */
    public void clear() {
        model.removeAll();
        initialized = false;
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
        var texture = loader.load(album.getId());
        var items = new ArrayList<SongListItem>();
        for (var song : library.getSongs(albumId)) {
            items.add(new SongListItem(
                    song.getTitle(),
                    song.getId(),
                    song.getDisplayArtist(),
                    album.getTitle(),
                    song.getDuration(),
                    song.getContentType(),
                    texture
            ));
        }
        return items;
    }

    private void setupActions() {
        var exec = app.executor;

        var entries = new ActionEntry[7];

        // These are queue manipulation action
        entries[0] = new ActionEntry(
                "play-song",
                (_, parameter) -> {
                    if (parameter != null) {
                        // Read now: callback arguments are only valid during the callback.
                        final var songId = parameter.getString(null);
                        exec.submit(() -> {
                            logger.info("Playing this song:");
                            var song = new QSongModel().id.equalTo(songId).findOne();
                            if (song != null) {
                                var texture = loader.load(song.getAlbum().getId());
                                var item = new SongListItem(
                                        song.getTitle(),
                                        song.getId(),
                                        song.getDisplayArtist(),
                                        song.getAlbum().getTitle(),
                                        song.getDuration(),
                                        song.getContentType(),
                                        texture
                                );
                                GLib.idleAddOnce(() -> {
                                    model.removeAll();
                                    model.append(item);
                                });
                            }
                        });
                    }
                },
                VariantTypes.STRING.dupString(),
                null,
                null,
                Arena.global()
        );
        entries[1] = new ActionEntry(
                "queue-song",
                (_, parameter) -> {
                    if (parameter != null) {
                        // Read now: callback arguments are only valid during the callback.
                        final var songId = parameter.getString(null);
                        exec.submit(() -> {
                            logger.info("Queueing this song:");
                            var song = new QSongModel().id.equalTo(songId).findOne();
                            if (song != null) {
                                var texture = loader.load(song.getAlbum().getId());
                                var item = new SongListItem(
                                        song.getTitle(),
                                        song.getId(),
                                        song.getDisplayArtist(),
                                        song.getAlbum().getTitle(),
                                        song.getDuration(),
                                        song.getContentType(),
                                        texture
                                );
                                GLib.idleAddOnce(() -> model.append(item));
                            }
                        });
                    }
                },
                VariantTypes.STRING.dupString(),
                null,
                null,
                Arena.global()
        );
        entries[2] = new ActionEntry(
                "play-album",
                (_, parameter) -> {
                    if (parameter != null) {
                        // Read now: callback arguments are only valid during the callback.
                        final var albumId = parameter.getString(null);
                        exec.submit(() -> {
                            var items = albumItems(albumId);
                            if (!items.isEmpty()) {
                                GLib.idleAddOnce(() -> {
                                    // Empty first, so the selection starts over at the album's first song.
                                    model.removeAll();
                                    items.forEach(model::append);
                                });
                            }
                        });
                    }
                },
                VariantTypes.STRING.dupString(),
                null,
                null,
                Arena.global()
        );
        entries[3] = new ActionEntry(
                "queue-album",
                (_, parameter) -> {
                    if (parameter != null) {
                        // Read now: callback arguments are only valid during the callback.
                        final var albumId = parameter.getString(null);
                        exec.submit(() -> {
                            var items = albumItems(albumId);
                            if (!items.isEmpty()) {
                                GLib.idleAddOnce(() -> items.forEach(model::append));
                            }
                        });
                    }
                },
                VariantTypes.STRING.dupString(),
                null,
                null,
                Arena.global()
        );
        entries[4] = new ActionEntry(
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
        );

        // These are play actions
        entries[5] = new ActionEntry(
                "play-next",
                (_, parameter) -> advance(parameter.getBoolean()),
                VariantTypes.BOOLEAN.dupString(),
                null,
                null,
                Arena.global()
        );
        entries[6] = new ActionEntry(
                "play-previous",
                (_, _) -> {
                    var selectedIdx = selection.getSelected();
                    if (selectedIdx > 0) {
                        selection.selectItem(selectedIdx - 1, true);
                    }
                },
                null,
                null,
                null,
                Arena.global()
        );
        app.addActionEntries(entries, null);
    }

    public QueueStore(GrooveboatApp app) {
        this.app = app;
        selection.onNotify("selected", this::itemSelectionChanged);
        model.onItemsChanged((position, removed, added) -> refreshNext());
        setupActions();
    }
}

package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import io.github.Nyeksenn.grooveboat.ui.CoverLoader;
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
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class QueueStore extends GObject {
    private final GrooveboatApp app;
    private static final Logger logger = LoggerFactory.getLogger(QueueStore.class);
    private static final CoverLoader loader = CoverLoader.getInstance();
    private boolean initialized;
    private final ListStore<SongListItem> model = new ListStore<>();
    private final SingleSelection<SongListItem> selection = new SingleSelection<>(model);
    private static final int GTK_INVALID_LIST_POSITION = -1;
    /** Set while the selection follows a gapless switch, so the already playing song isn't reloaded. */
    private boolean followingSwitch = false;
    /** Set while restarting the current song on purpose (loop mode), which re-notifies an unchanged selection. */
    private boolean forceReload = false;
    private RepeatMode repeat = RepeatMode.NONE;
    private boolean shuffle = false;
    /** Shuffled play order; only maintained while shuffle is on. */
    private final PlayOrder<SongListItem> order = new PlayOrder<>(new Random());
    private final List<Runnable> modeListeners = new CopyOnWriteArrayList<>();

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
        // Removing an earlier queue entry shifts the selected index without changing the song: keep playing.
        var stream = app.getStream();
        if (!forceReload && selectedItem != null && stream != null && selectedItem == stream.getCurrentItem()) {
            return;
        }
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

    public ListStore<SongListItem> getModel() {
        return model;
    }

    public RepeatMode getRepeat() {
        return repeat;
    }

    public boolean isShuffle() {
        return shuffle;
    }

    /** Called (on the main thread) after repeat or shuffle changed. */
    public void addModeListener(Runnable listener) {
        modeListeners.add(listener);
    }

    public void removeModeListener(Runnable listener) {
        modeListeners.remove(listener);
    }

    public void setRepeat(RepeatMode mode) {
        if (mode == repeat) {
            return;
        }
        repeat = mode;
        refreshNext();
        modeListeners.forEach(Runnable::run);
    }

    public void setShuffle(boolean enabled) {
        if (enabled == shuffle) {
            return;
        }
        shuffle = enabled;
        order.reset();
        refreshNext();
        modeListeners.forEach(Runnable::run);
    }

    /** The selected song, or {@code null}. */
    public SongListItem currentItem() {
        var idx = selection.getSelected();
        return idx == GTK_INVALID_LIST_POSITION ? null : model.getItem(idx);
    }

    /** Queue entries in play order: the list order, or the shuffled order while shuffle is on. */
    private List<SongListItem> playOrder() {
        var list = new ArrayList<SongListItem>();
        for (int i = 0; i < model.getNItems(); i++) {
            list.add(model.getItem(i));
        }
        return order.resolve(list, currentItem(), shuffle);
    }

    /** The song a skip-forward would play (wrapping in repeat-playlist mode), or {@code null}. */
    private SongListItem itemAfterCurrent() {
        var current = currentItem();
        return current == null ? null : PlayOrder.after(playOrder(), current, repeat);
    }

    private SongListItem itemBeforeCurrent() {
        var current = currentItem();
        return current == null ? null : PlayOrder.before(playOrder(), current, repeat);
    }

    public boolean canGoNext() {
        return itemAfterCurrent() != null;
    }

    public boolean canGoPrevious() {
        return itemBeforeCurrent() != null;
    }

    /** The song that follows when the current one ends on its own: repeated, next, or none. */
    private SongListItem followingItem() {
        return repeat == RepeatMode.TRACK ? currentItem() : itemAfterCurrent();
    }

    /** Selects a queue entry (and thereby plays it). */
    public void select(SongListItem item) {
        for (int i = 0; i < model.getNItems(); i++) {
            if (model.getItem(i) == item) {
                if (item == currentItem()) {
                    restartCurrent();
                } else {
                    selection.selectItem(i, true);
                }
                return;
            }
        }
    }

    /**
     * Tells the stream which song follows the current one, for gapless playback: the same song in repeat-track
     * mode, otherwise the next entry (wrapping in repeat-playlist mode, none at the end of the queue).
     */
    public void refreshNext() {
        var stream = app.getStream();
        if (stream == null || !app.hasSession()) {
            return;
        }
        stream.setNext(followingItem());
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

    private void restartCurrent() {
        forceReload = true;
        try {
            selection.emitNotify("selected", null);
        } finally {
            forceReload = false;
        }
    }

    /**
     * Advances to the next song.
     *
     * @param endOfSong {@code true} when the current song ended on its own, which in repeat-track mode restarts it;
     *                  a manual skip passes {@code false} and moves on
     * @return {@code false} when there is no next song
     */
    public boolean advance(boolean endOfSong) {
        var current = currentItem();
        if (current == null) {
            return false;
        }
        if (endOfSong && repeat == RepeatMode.TRACK) {
            restartCurrent();
            return true;
        }
        var next = itemAfterCurrent();
        if (next == null) {
            return false;
        }
        select(next);
        return true;
    }

    /** Skips back; returns {@code false} when there is no previous song. */
    public boolean previous() {
        var prev = itemBeforeCurrent();
        if (prev == null) {
            return false;
        }
        select(prev);
        return true;
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
                    texture,
                    album.getId(),
                    album.getCoverPath()
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
                                        texture,
                                        song.getAlbum().getId(),
                                        song.getAlbum().getCoverPath()
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
                                        texture,
                                        song.getAlbum().getId(),
                                        song.getAlbum().getCoverPath()
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
                (_, parameter) -> advance(parameter != null && parameter.getBoolean()),
                VariantTypes.BOOLEAN.dupString(),
                null,
                null,
                Arena.global()
        );
        entries[6] = new ActionEntry(
                "play-previous",
                (_, _) -> previous(),
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

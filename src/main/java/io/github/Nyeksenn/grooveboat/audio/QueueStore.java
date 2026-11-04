package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.entities.SongModel;
import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import io.github.Nyeksenn.grooveboat.ui.GrooveboatApp;
import org.gnome.gio.ListStore;
import org.gnome.glib.GLib;
import org.gnome.glib.Variant;
import org.gnome.gobject.GObject;
import org.gnome.gobject.ParamSpec;
import org.gnome.gtk.SingleSelection;
import org.javagi.gobject.annotations.Signal;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class QueueStore extends GObject {
    private static final Logger logger = LoggerFactory.getLogger(QueueStore.class);

    private final GrooveboatApp app;
    private boolean initialized;
    /** Set while the saved queue is being looked up, so a second request doesn't restore it twice. */
    private boolean restoring;
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
            if (!app.getStream().load(selectedItem)) {
                // Skipped (unsupported format): the playback events move on; whatever comes next should autoplay.
                initialized = true;
                return;
            }
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
    public @Nullable SongListItem currentItem() {
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
    private @Nullable SongListItem itemAfterCurrent() {
        var current = currentItem();
        return current == null ? null : PlayOrder.after(playOrder(), current, repeat);
    }

    private @Nullable SongListItem itemBeforeCurrent() {
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

    /** The queue as it is now, for saving; empty when nothing is queued. */
    public QueueSnapshot snapshot() {
        var ids = new ArrayList<String>();
        for (int i = 0; i < model.getNItems(); i++) {
            ids.add(model.getItem(i).getSongId());
        }
        return new QueueSnapshot(ids, Math.max(selection.getSelected(), 0));
    }

    /**
     * Saves the queue for the next run (an empty queue removes the saved one). Does nothing without a session: the
     * queue is empty then because the app never got to restore it, and that must not erase the saved one.
     */
    public void save() {
        if (!app.hasSession()) {
            return;
        }
        try {
            QueueSnapshot.write(QueueSnapshot.defaultPath(), snapshot());
        } catch (IOException | RuntimeException e) {
            logger.warn("Couldn't save the queue: {}", e.getMessage());
        }
    }

    /** Forgets the saved queue, e.g. on logout (its song ids belong to one server). */
    public static void deleteSaved() {
        try {
            QueueSnapshot.delete(QueueSnapshot.defaultPath());
        } catch (IOException e) {
            logger.warn("Couldn't delete the saved queue: {}", e.getMessage());
        }
    }

    /**
     * Fills an empty queue after startup: the queue of the last run if there is one, otherwise the first song of the
     * library. Nothing starts playing; the current song is only prepared.
     */
    public void loadState() {
        if (!model.isEmpty() || restoring) {
            return;
        }
        restoring = true;
        var saved = QueueSnapshot.read(QueueSnapshot.defaultPath());
        app.executors.io.submit(() -> {
            List<SongListItem> items = List.of();
            var index = 0;
            try {
                if (saved.isPresent()) {
                    var restored = resolve(saved.get());
                    items = restored.items();
                    index = restored.index();
                }
            } catch (RuntimeException e) {
                logger.error("Couldn't restore the saved queue", e);
            }
            final var restoredItems = items;
            final var restoredIndex = index;
            GLib.idleAddOnce(() -> {
                restoring = false;
                if (!model.isEmpty() || !app.hasSession()) {
                    return;
                }
                if (restoredItems.isEmpty()) {
                    queueFirstSong();
                } else {
                    restore(restoredItems, restoredIndex);
                }
            });
        });
    }

    /** Fallback for a first run: queues one song so the player isn't empty. */
    private void queueFirstSong() {
        var initSong = new QSongModel().setMaxRows(1).findOne();
        if (initSong == null) {
            return;
        }
        var action = app.lookupAction("queue-song");
        action.activate(Variant.string(initSong.getId()));
    }

    /** The saved songs that the library still has (in saved order), and where the current song ended up. */
    record Restored(List<SongListItem> items, int index) {
    }

    /** Runs off the main thread. Songs that are gone from the library are dropped from the queue. */
    private static Restored resolve(QueueSnapshot snapshot) {
        var songs = new HashMap<String, SongModel>();
        new QSongModel().id.in(snapshot.songIds()).songArtists.fetchQuery().album.fetch().findList().stream()
                .filter(song -> song.getAlbum() != null)
                .forEach(song -> songs.put(song.getId(), song));
        var kept = snapshot.retain(songs.keySet());
        var items = new ArrayList<SongListItem>();
        for (var id : kept.songIds()) {
            var song = songs.get(id);
            items.add(SongListItem.of(song, song.getAlbum()));
        }
        return new Restored(items, kept.index());
    }

    /**
     * Puts a restored queue in place and prepares {@code index} as the current song, without playing it. The
     * selection changes are handled here, not by the usual handler: it would load the first entry as it is
     * appended, and treat the later selection of the saved entry as a manual change that starts playing.
     */
    private void restore(List<SongListItem> items, int index) {
        followingSwitch = true;
        try {
            items.forEach(model::append);
            selection.setSelected(index);
        } finally {
            followingSwitch = false;
        }
        var current = currentItem();
        if (current != null && app.getStream().load(current)) {
            emit("initialize-song", current);
        }
        // As after startup: whatever is queued next is prepared, not played.
        initialized = true;
        refreshNext();
    }

    /** Empties the queue; the next queued song will be prepared without auto-playing, as after startup. */
    public void clear() {
        model.removeAll();
        initialized = false;
    }

    public QueueStore(GrooveboatApp app) {
        this.app = app;
        selection.onNotify("selected", this::itemSelectionChanged);
        model.onItemsChanged((position, removed, added) -> refreshNext());
        new QueueActions(app, this).register();
    }
}

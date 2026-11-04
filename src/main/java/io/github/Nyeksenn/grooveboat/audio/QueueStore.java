package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import io.github.Nyeksenn.grooveboat.ui.GrooveboatApp;
import org.gnome.gio.ListStore;
import org.gnome.glib.Variant;
import org.gnome.gobject.GObject;
import org.gnome.gobject.ParamSpec;
import org.gnome.gtk.SingleSelection;
import org.javagi.gobject.annotations.Signal;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class QueueStore extends GObject {
    private final GrooveboatApp app;
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

    public QueueStore(GrooveboatApp app) {
        this.app = app;
        selection.onNotify("selected", this::itemSelectionChanged);
        model.onItemsChanged((position, removed, added) -> refreshNext());
        new QueueActions(app, this).register();
    }
}

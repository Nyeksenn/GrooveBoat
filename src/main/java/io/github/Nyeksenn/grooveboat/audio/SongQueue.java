package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.entities.SongModel;
import org.gnome.gobject.GObject;
import org.javagi.gobject.annotations.RegisteredType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

@RegisteredType(name = "SongQueue")
public final class SongQueue extends GObject {
    private final Deque<SongModel> queue = new ArrayDeque<>();

    public SongModel peekSong() {
        return queue.peek();
    }

    public void addSongList(List<SongModel> songs) {
        queue.addAll(songs);
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void replace(List<SongModel> songs) {
        queue.clear();
        queue.addAll(songs);
    }

    public SongModel next() {
        queue.removeFirst();
        return queue.peek();
    }

    public Iterator<SongModel> iterate() {
        return queue.iterator();
    }

    public SongQueue() {
    }
}

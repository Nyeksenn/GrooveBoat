package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.entities.SongModel;
import org.gnome.gobject.GObject;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gobject.annotations.Signal;

import java.lang.foreign.MemorySegment;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@RegisteredType(name = "SongQueue")
public final class SongQueue extends GObject {
    @Signal(name = "queue-changed")
    public interface QueueChanged {
        void run();
    }

    @Signal(name = "queue-replaced")
    public interface QueueReplaced {
        void run();
    }

    @Signal(name = "queue-added")
    public interface QueueAdded {
        void run(String songId);
    }

    private final Deque<SongModel> queue = new ConcurrentLinkedDeque<>();

    public SongModel peekSong() {
        return queue.peek();
    }

    public void addSong(SongModel song) {
        queue.add(song);
        emit("queue-added", song.getSongId());
        emit("queue-changed");
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public synchronized void replace(List<SongModel> songs) {
        queue.clear();
        queue.addAll(songs);
        emit("queue-replaced");
        emit("queue-changed");
    }

    public Iterator<SongModel> iterate() {
        return queue.iterator();
    }

    public SongQueue() {
    }

    public SongQueue(MemorySegment address) {
        super(address);
    }
}

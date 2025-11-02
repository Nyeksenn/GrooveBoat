package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.entities.SongModel;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.List;

public final class SongQueue {
    private final Deque<SongModel> queue = new ConcurrentLinkedDeque<>();

    private SongQueue() {
        // Private constructor prevents instantiation
    }

    private static class Holder {
        private static final SongQueue INSTANCE = new SongQueue();
    }

    public static SongQueue getInstance() {
        return Holder.INSTANCE;
    }

    public void addSong(SongModel song) {
        queue.add(song);
    }

    public SongModel getNextSong() {
        return queue.poll();
    }

    public SongModel peekNext() {
        return queue.peek();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public synchronized void replace(List<SongModel> songs) {
        queue.clear();
        queue.addAll(songs);
    }
}

package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.entities.SongModel;
import org.gnome.gobject.GObject;
import org.javagi.gobject.annotations.RegisteredType;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@RegisteredType(name = "SongQueue")
public final class SongQueue extends GObject {
    private final List<SongModel> list = new LinkedList<>();


    public SongModel takeSong() {
        return list.removeFirst();
    }

    public void addSongList(List<SongModel> songs) {
        list.addAll(songs);
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public boolean lengthOne() {
        return list.size() == 1;
    }

    public void replace(List<SongModel> songs) {
        list.clear();
        list.addAll(songs);
    }

    public SongModel getAt(int idx) {
        return list.get(idx);
    }

    public Iterator<SongModel> iterate() {
        return list.iterator();
    }

    public int length() {
        return list.size();
    }

    public SongQueue() {
    }
}

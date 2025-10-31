package io.github.Nyeksenn.grooveboat.gobjects;

import org.gnome.gdk.Texture;
import org.gnome.gobject.GObject;

import java.time.Duration;

public class SongListItem extends GObject {
    private final String songName;
    private final String artistName;
    private final String albumTitle;
    private final Duration duration;
    private final Texture coverArt;

    public String getSongName() {
        return songName;
    }

    public String getArtistName() {
        return artistName;
    }

    public String getAlbumTitle() {
        return albumTitle;
    }

    public Duration getDuration() {
        return duration;
    }

    public Texture getCoverArt() {
        return coverArt;
    }

    public SongListItem(String songName, String artistName, String albumTitle, Duration duration, Texture coverArt) {
        this.songName = songName;
        this.artistName = artistName;
        this.albumTitle = albumTitle;
        this.duration = duration;
        this.coverArt = coverArt;
    }
}

package io.github.Nyeksenn.grooveboat.gobjects;

import org.gnome.gdk.Texture;
import org.gnome.gobject.GObject;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

public final class SongListItem extends GObject {
    private final String songId;
    private final String songName;
    private final String artistName;
    private final String albumTitle;
    private final Duration duration;
    private final String contentType;
    private final Texture coverArt;
    private final String albumId;
    private final Path coverPath;
    private final String trackId;

    private static final AtomicLong TRACK_COUNTER = new AtomicLong();

    /** Album the song belongs to, or {@code null} when unknown. */
    public String getAlbumId() {
        return albumId;
    }

    /** Cover image file in the cover cache, or {@code null} when unknown or missing. */
    public Path getCoverPath() {
        return coverPath;
    }

    /** Unique D-Bus object path of this queue entry (a song queued twice gets two ids). */
    public String getTrackId() {
        return trackId;
    }

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

    public String getSongId() {
        return songId;
    }

    public String getDurationString() {
        long seconds = duration.getSeconds();
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    public String getContentType() {
        return contentType;
    }

    public SongListItem(String songName, String songId, String artistName, String albumTitle, Duration duration, String contentType, Texture coverArt) {
        this(songName, songId, artistName, albumTitle, duration, contentType, coverArt, null, null);
    }

    public SongListItem(String songName, String songId, String artistName, String albumTitle, Duration duration, String contentType, Texture coverArt, String albumId, Path coverPath) {
        this.albumId = albumId;
        this.coverPath = coverPath;
        this.trackId = "/io/github/Nyeksenn/grooveboat/track/" + TRACK_COUNTER.incrementAndGet();
        this.songName = songName;
        this.songId = songId;
        this.artistName = artistName;
        this.albumTitle = albumTitle;
        this.duration = duration;
        this.contentType = contentType;
        this.coverArt = coverArt;
    }
}

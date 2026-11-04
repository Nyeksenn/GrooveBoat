package io.github.Nyeksenn.grooveboat.gobjects;

import io.github.Nyeksenn.grooveboat.entities.AlbumModel;
import io.github.Nyeksenn.grooveboat.entities.SongModel;
import org.gnome.gdk.Texture;
import org.gnome.gobject.GObject;
import org.jspecify.annotations.Nullable;

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
    private final int bitRate;
    private final Texture coverArt;
    private final String albumId;
    private final Path coverPath;
    private final String trackId;
    private final int trackNumber;

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

    /** Position of the song on its album; 0 when unknown. */
    public int getTrackNumber() {
        return trackNumber;
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

    /** Bitrate in kbit/s as reported by the server; 0 when unknown. */
    public int getBitRate() {
        return bitRate;
    }

    /** A queue/list entry for {@code song} of {@code album}. {@code cover} is the album's decoded cover, if any. */
    public static SongListItem of(SongModel song, AlbumModel album, @Nullable Texture cover) {
        return new SongListItem(song.getTitle(), song.getId(), song.getDisplayArtist(), album.getTitle(),
                song.getDuration(), song.getContentType(), song.getBitRate(), cover, album.getId(), album.getCoverPath(),
                song.getTrack());
    }

    private SongListItem(String songName, String songId, String artistName, String albumTitle, Duration duration, String contentType, int bitRate, Texture coverArt, String albumId, Path coverPath, int trackNumber) {
        this.albumId = albumId;
        this.trackNumber = trackNumber;
        this.coverPath = coverPath;
        this.trackId = "/io/github/Nyeksenn/grooveboat/track/" + TRACK_COUNTER.incrementAndGet();
        this.songName = songName;
        this.songId = songId;
        this.artistName = artistName;
        this.albumTitle = albumTitle;
        this.duration = duration;
        this.contentType = contentType;
        this.bitRate = bitRate;
        this.coverArt = coverArt;
    }
}

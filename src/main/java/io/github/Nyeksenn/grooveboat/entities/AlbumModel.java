package io.github.Nyeksenn.grooveboat.entities;

import io.ebean.annotation.Index;
import io.ebean.annotation.Length;
import io.ebean.annotation.NotNull;
import jakarta.persistence.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

@Entity
@Table(name = "album")
@Index(unique = true, columnNames = {"album_id"})
public class AlbumModel extends BaseModel {
    @NotNull
    @Length(100)
    String albumId;

    @NotNull
    @Length(100)
    String title;

    @NotNull
    int songCount;

    @NotNull
    @Length(100)
    String artist;

    @NotNull
    @Length(100)
    String artistId;

    int release_year;

    Duration duration;

    @Lob
    byte[] coverArt;

    @ManyToOne
    @JoinColumn(name = "id")
    ArtistModel albumArtist;

    public String getAlbumId() {
        return albumId;
    }

    public void setAlbumId(String albumId) {
        this.albumId = albumId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getSongCount() {
        return songCount;
    }

    public void setSongCount(int songCount) {
        this.songCount = songCount;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getArtistId() {
        return artistId;
    }

    public void setArtistId(String artistId) {
        this.artistId = artistId;
    }

    public int getRelease_year() {
        return release_year;
    }

    public void setRelease_year(int release_year) {
        this.release_year = release_year;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public byte[] getCoverArt() {
        return coverArt;
    }

    public void setCoverArt(byte[] coverArt) {
        this.coverArt = coverArt;
    }

    public ArtistModel getAlbumArtist() {
        return albumArtist;
    }

    public void setAlbumArtist(ArtistModel albumArtist) {
        this.albumArtist = albumArtist;
    }

    public AlbumModel(String albumId, String title, int songCount, String artist, String artistId, int release_year, Duration duration, byte[] coverArt, ArtistModel albumArtist) {
        this.albumId = albumId;
        this.title = title;
        this.songCount = songCount;
        this.artist = artist;
        this.artistId = artistId;
        this.release_year = release_year;
        this.duration = duration;
        this.coverArt = coverArt;
        this.albumArtist = albumArtist;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AlbumModel that = (AlbumModel) o;
        return songCount == that.songCount && release_year == that.release_year && Objects.equals(albumId, that.albumId) && Objects.equals(title, that.title) && Objects.equals(artist, that.artist) && Objects.equals(artistId, that.artistId) && Objects.equals(duration, that.duration) && Arrays.equals(coverArt, that.coverArt) && Objects.equals(albumArtist, that.albumArtist);
    }

    @Override
    public int hashCode() {
        return Objects.hash(albumId, title, songCount, artist, artistId, release_year, duration, Arrays.hashCode(coverArt), albumArtist);
    }
}

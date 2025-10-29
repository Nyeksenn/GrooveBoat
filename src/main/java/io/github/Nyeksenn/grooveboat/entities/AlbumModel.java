package io.github.Nyeksenn.grooveboat.entities;

import io.ebean.annotation.Index;
import io.ebean.annotation.Length;
import io.ebean.annotation.NotNull;
import jakarta.persistence.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
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

    int release_year;

    Duration duration;

    @Column(length = 100000000)
    byte[] coverArt;

    @ManyToMany
    @JoinTable(name = "album_artists")
    List<ArtistModel> albumArtists;

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

    public List<ArtistModel> getAlbumArtists() {
        return albumArtists;
    }

    public String getDisplayArtist() {
        var sb = new StringBuilder();
        var artists = getAlbumArtists();
        var numArtists = artists.size();
        for (int i = 0; i < numArtists; i++) {
            sb.append(artists.get(i).getName());
            if (!(i == (numArtists - 1))) {
                sb.append("; ");
            }
        }

        return sb.toString();
    }

    public void setAlbumArtists(List<ArtistModel> albumArtists) {
        this.albumArtists = albumArtists;
    }

    public AlbumModel(String albumId, String title, int songCount, int release_year, Duration duration, byte[] coverArt, List<ArtistModel> albumArtists) {
        this.albumId = albumId;
        this.title = title;
        this.songCount = songCount;
        this.release_year = release_year;
        this.duration = duration;
        this.coverArt = coverArt;
        this.albumArtists = albumArtists;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AlbumModel that = (AlbumModel) o;
        return getSongCount() == that.getSongCount() && getRelease_year() == that.getRelease_year() && Objects.equals(getAlbumId(), that.getAlbumId()) && Objects.equals(getTitle(), that.getTitle()) && Objects.equals(getDuration(), that.getDuration()) && Arrays.equals(getCoverArt(), that.getCoverArt()) && Objects.equals(getAlbumArtists(), that.getAlbumArtists());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAlbumId(), getTitle(), getSongCount(), getRelease_year(), getDuration(), Arrays.hashCode(getCoverArt()), getAlbumArtists());
    }
}

package io.github.Nyeksenn.grooveboat.entities;

import io.ebean.annotation.Index;
import io.ebean.annotation.Length;
import io.ebean.annotation.NotNull;
import jakarta.persistence.*;

import java.nio.file.Path;
import java.time.Duration;
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

    Path coverPath;

    @ManyToMany
    @JoinTable(name = "album_artists")
    List<ArtistModel> albumArtists;

    @OneToMany(mappedBy = "album")
    List<SongModel> songs;

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

    public Path getCoverPath() {
        return coverPath;
    }

    public void setCoverPath(Path coverPath) {
        this.coverPath = coverPath;
    }

    public List<ArtistModel> getAlbumArtists() {
        return albumArtists;
    }

    public List<SongModel> getSongs() {
        return songs;
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

    public AlbumModel(String albumId, String title, int songCount, int release_year, Duration duration, Path coverPath, List<ArtistModel> albumArtists) {
        this.albumId = albumId;
        this.title = title;
        this.songCount = songCount;
        this.release_year = release_year;
        this.duration = duration;
        this.coverPath = coverPath;
        this.albumArtists = albumArtists;
    }

    public void setAllFields(AlbumModel src) {
        this.setTitle(src.getTitle());
        this.setSongCount(src.getSongCount());
        this.setRelease_year(src.getRelease_year());
        this.setDuration(src.getDuration());
        this.setCoverPath(src.getCoverPath());
        this.setAlbumArtists(src.getAlbumArtists());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AlbumModel that = (AlbumModel) o;
        return songCount == that.songCount && release_year == that.release_year && Objects.equals(albumId, that.albumId) && Objects.equals(title, that.title) && Objects.equals(duration, that.duration) && Objects.equals(coverPath, that.coverPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(albumId, title, songCount, release_year, duration, coverPath);
    }
}

package io.github.Nyeksenn.grooveboat.entities;

import io.ebean.annotation.Index;
import io.ebean.annotation.Length;
import io.ebean.annotation.NotNull;
import jakarta.persistence.*;

import io.github.Nyeksenn.grooveboat.backend.RemoteAlbum;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "album")
public class AlbumModel extends BaseModel {
    /** Size of the {@code title} column. */
    public static final int TITLE_LENGTH = 100;

    @Id
    String id;

    @NotNull
    @Length(TITLE_LENGTH)
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
        return ArtistModel.joinNames(getAlbumArtists(), "; ");
    }

    public void setAlbumArtists(List<ArtistModel> albumArtists) {
        this.albumArtists = albumArtists;
    }

    /** A new album from the server's data. Not saved. */
    public static AlbumModel from(RemoteAlbum album, @Nullable Path coverPath, List<ArtistModel> artists) {
        return new AlbumModel(album.id(), truncate(album.title(), TITLE_LENGTH), album.songCount(), album.year(),
                album.duration(), coverPath, artists);
    }

    public AlbumModel(String id, String title, int songCount, int release_year, Duration duration, Path coverPath, List<ArtistModel> albumArtists) {
        this.id = id;
        this.title = title;
        this.songCount = songCount;
        this.release_year = release_year;
        this.duration = duration;
        this.coverPath = coverPath;
        this.albumArtists = albumArtists;
    }

    /** Entities are the same when their ids are: the other fields change while the row stays the same album. */
    @Override
    public boolean equals(Object o) {
        return this == o || (o != null && getClass() == o.getClass() && Objects.equals(id, ((AlbumModel) o).id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

package io.github.Nyeksenn.grooveboat.entities;

import io.ebean.annotation.Index;
import io.ebean.annotation.Length;
import io.ebean.annotation.NotNull;
import jakarta.persistence.*;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "artist")
public class ArtistModel extends BaseModel {
    @Id
    String id;

    @NotNull
    @Length(200)
    String name;

    Path artistImagePath;

    @ManyToMany(mappedBy = "albumArtists")
    Set<AlbumModel> albums;

    @ManyToMany(mappedBy = "songArtists")
    Set<SongModel> songs;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Path getArtistImagePath() {
        return artistImagePath;
    }

    public void setArtistImagePath(Path artistImagePath) {
        this.artistImagePath = artistImagePath;
    }

    public Set<AlbumModel> getAlbums() {
        return albums;
    }

    public Set<SongModel> getSongs() {
        return songs;
    }

    public ArtistModel(String artistId, String name) {
        this.id = artistId;
        this.name = name;
    }

    public ArtistModel(String artistId, String name, Path path) {
        this.id = artistId;
        this.name = name;
        this.artistImagePath = path;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ArtistModel that = (ArtistModel) o;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name) && Objects.equals(artistImagePath, that.artistImagePath) && Objects.equals(albums, that.albums) && Objects.equals(songs, that.songs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, artistImagePath, albums, songs);
    }
}

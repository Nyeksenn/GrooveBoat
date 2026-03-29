package io.github.Nyeksenn.grooveboat.entities;

import io.ebean.annotation.Index;
import io.ebean.annotation.Length;
import io.ebean.annotation.NotNull;
import jakarta.persistence.*;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "artist")
@Index(unique = true, columnNames = {"artist_id"})
public class ArtistModel extends BaseModel {
    @Id
    String id;

    @NotNull
    @Length(200)
    String name;

    @Column(length = 100000000)
    byte[] image;

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

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
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

    public ArtistModel(String artistId, String name, byte[] image) {
        this.id = artistId;
        this.name = name;
        this.image = image;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ArtistModel that = (ArtistModel) o;
        return Objects.equals(getId(), that.getId()) && Objects.equals(getName(), that.getName()) && Arrays.equals(getImage(), that.getImage());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName(), Arrays.hashCode(getImage()));
    }
}

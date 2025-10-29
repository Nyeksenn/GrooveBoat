package io.github.Nyeksenn.grooveboat.entities;

import io.ebean.annotation.Index;
import io.ebean.annotation.Length;
import io.ebean.annotation.NotNull;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "artist")
@Index(unique = true, columnNames = {"artist_id"})
public class ArtistModel extends BaseModel {
    @NotNull
    @Length(100)
    String artistId;

    @NotNull
    @Length(200)
    String name;

    @Column(length = 100000000)
    byte[] image;

    @ManyToMany(mappedBy = "albumArtists")
    Set<AlbumModel> albums;

    public String getArtistId() {
        return artistId;
    }

    public void setArtistId(String artistId) {
        this.artistId = artistId;
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


    public ArtistModel(String artistId, String name, byte[] image) {
        this.artistId = artistId;
        this.name = name;
        this.image = image;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ArtistModel that = (ArtistModel) o;
        return Objects.equals(getArtistId(), that.getArtistId()) && Objects.equals(getName(), that.getName()) && Arrays.equals(getImage(), that.getImage());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getArtistId(), getName(), Arrays.hashCode(getImage()));
    }
}

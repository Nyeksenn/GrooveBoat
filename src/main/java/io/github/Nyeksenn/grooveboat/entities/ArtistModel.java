package io.github.Nyeksenn.grooveboat.entities;

import io.ebean.annotation.Index;
import io.ebean.annotation.Length;
import io.ebean.annotation.NotNull;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.Objects;

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

    public String getArtistId() {
        return artistId;
    }

    public String getName() {
        return name;
    }

    public byte[] getImage() {
        return image;
    }

    public ArtistModel(String artistId, String name, byte[] image) {
        this.artistId = artistId;
        this.name = name;
        this.image = image;
    }

    @Override
    public String toString() {
        return "ArtistModel{" +
                "artistId='" + artistId + '\'' +
                ", name='" + name + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ArtistModel that = (ArtistModel) o;
        return Objects.equals(artistId, that.artistId) && Objects.equals(name, that.name) && Arrays.equals(image, that.image);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artistId, name, Arrays.hashCode(image));
    }
}

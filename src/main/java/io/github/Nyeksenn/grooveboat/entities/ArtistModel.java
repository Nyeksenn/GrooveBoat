package io.github.Nyeksenn.grooveboat.entities;

import io.ebean.annotation.Length;
import io.ebean.annotation.NotNull;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "artist")
public class ArtistModel extends BaseModel {
    @NotNull @Length(50)
    String artistId;

    @NotNull @Length(200)
    String name;

    @Lob
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
}

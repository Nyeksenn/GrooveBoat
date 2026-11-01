package io.github.Nyeksenn.grooveboat.entities;

import io.ebean.annotation.Index;
import io.ebean.annotation.Length;
import io.ebean.annotation.NotNull;
import jakarta.persistence.*;

import io.github.Nyeksenn.grooveboat.backend.RemoteArtistRef;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "artist")
public class ArtistModel extends BaseModel {
    /** Size of the {@code name} column. */
    public static final int NAME_LENGTH = 200;

    @Id
    String id;

    @NotNull
    @Length(NAME_LENGTH)
    String name;

    Path artistImagePath;

    /** The server was asked for this artist's image: it has none, or it is cached. Failed downloads stay unchecked. */
    boolean imageChecked;

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

    public boolean isImageChecked() {
        return imageChecked;
    }

    public void setImageChecked(boolean imageChecked) {
        this.imageChecked = imageChecked;
    }

    public Set<AlbumModel> getAlbums() {
        return albums;
    }

    public Set<SongModel> getSongs() {
        return songs;
    }

    /** A new artist from a server reference. Not saved. */
    public static ArtistModel from(RemoteArtistRef ref) {
        return new ArtistModel(ref.id(), clampName(ref.name()));
    }

    /** {@code name}, cut to what the {@code name} column holds. */
    public static String clampName(String name) {
        return truncate(name, NAME_LENGTH);
    }

    /** The artists' names joined with {@code separator}, in list order. */
    public static String joinNames(List<ArtistModel> artists, String separator) {
        return artists.stream().map(ArtistModel::getName).collect(Collectors.joining(separator));
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

    /** Entities are the same when their ids are: the other fields change while the row stays the same artist. */
    @Override
    public boolean equals(Object o) {
        return this == o || (o != null && getClass() == o.getClass() && Objects.equals(id, ((ArtistModel) o).id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

package io.github.Nyeksenn.grooveboat.entities;

import io.ebean.annotation.Index;
import io.ebean.annotation.Length;
import io.ebean.annotation.NotNull;
import jakarta.persistence.*;

import io.github.Nyeksenn.grooveboat.backend.RemoteSong;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "song")
public class SongModel extends BaseModel {
    /** Size of the {@code title} column. */
    public static final int TITLE_LENGTH = 200;
    /** Size of the {@code content_type} column. */
    public static final int CONTENT_TYPE_LENGTH = 50;

    @Id
    String id;

    @NotNull
    @Length(TITLE_LENGTH)
    String title;

    @NotNull
    int track;

    int releaseYear;

    @NotNull
    Duration duration;

    int bitRate;

    @Length(CONTENT_TYPE_LENGTH)
    String contentType;

    int discNumber;

    @ManyToMany
    @JoinTable(name = "song_artists")
    List<ArtistModel> songArtists;

    @ManyToOne
    @JoinColumn(name = "album_id")
    AlbumModel album;

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

    public int getTrack() {
        return track;
    }

    public void setTrack(int track) {
        this.track = track;
    }

    public int getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(int releaseYear) {
        this.releaseYear = releaseYear;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public int getBitRate() {
        return bitRate;
    }

    public void setBitRate(int bitRate) {
        this.bitRate = bitRate;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public int getDiscNumber() {
        return discNumber;
    }

    public void setDiscNumber(int discNumber) {
        this.discNumber = discNumber;
    }

    public List<ArtistModel> getSongArtists() {
        return songArtists;
    }

    public void setSongArtists(List<ArtistModel> songArtists) {
        this.songArtists = songArtists;
    }

    public AlbumModel getAlbum() {
        return album;
    }

    public void setAlbum(AlbumModel album) {
        this.album = album;
    }

    public String getDisplayArtist() {
        return ArtistModel.joinNames(songArtists, " & ");
    }

    /** A new song from the server's data. Not saved. */
    public static SongModel from(RemoteSong song, List<ArtistModel> artists, @Nullable AlbumModel album) {
        return new SongModel(song.id(), truncate(song.title(), TITLE_LENGTH), song.track(), song.year(),
                song.duration(), song.bitRate(), truncate(song.contentType(), CONTENT_TYPE_LENGTH),
                song.discNumber(), artists, album);
    }

    public SongModel(String songId, String title, int track, int year, Duration duration, int bitRate, String contentType, int discNumber, List<ArtistModel> songArtists, AlbumModel album) {
        this.id = songId;
        this.title = title;
        this.track = track;
        this.releaseYear = year;
        this.duration = duration;
        this.bitRate = bitRate;
        this.contentType = contentType;
        this.discNumber = discNumber;
        this.songArtists = songArtists;
        this.album = album;
    }

    public void setAllFields(SongModel src) {
        this.setTitle(src.getTitle());
        this.setTrack(src.getTrack());
        this.setReleaseYear(src.getReleaseYear());
        this.setDuration(src.getDuration());
        this.setBitRate(src.getBitRate());
        this.setContentType(src.getContentType());
        this.setDiscNumber(src.getDiscNumber());
        this.setSongArtists(src.getSongArtists());
        this.setAlbum(src.getAlbum());
    }

    /**
     * Whether every synced field matches {@code other}'s. Artists (in order) and the album are compared by id, so
     * this never walks into their own collections.
     */
    public boolean hasSameContent(SongModel other) {
        return track == other.track && releaseYear == other.releaseYear && bitRate == other.bitRate
                && discNumber == other.discNumber
                && Objects.equals(title, other.title)
                && Objects.equals(duration, other.duration)
                && Objects.equals(contentType, other.contentType)
                && Objects.equals(artistIds(), other.artistIds())
                && Objects.equals(albumId(), other.albumId());
    }

    private List<String> artistIds() {
        return songArtists == null ? List.of() : songArtists.stream().map(ArtistModel::getId).toList();
    }

    private @Nullable String albumId() {
        return album == null ? null : album.getId();
    }

    /** Entities are the same when their ids are: the other fields change while the row stays the same song. */
    @Override
    public boolean equals(Object o) {
        return this == o || (o != null && getClass() == o.getClass() && Objects.equals(id, ((SongModel) o).id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

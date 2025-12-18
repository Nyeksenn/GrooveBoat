package io.github.Nyeksenn.grooveboat.entities;

import io.ebean.annotation.Index;
import io.ebean.annotation.Length;
import io.ebean.annotation.NotNull;
import jakarta.persistence.*;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "song")
@Index(unique = true, columnNames = {"song_id"})
public class SongModel extends BaseModel {
    @NotNull
    @Length(100)
    String songId;

    @NotNull
    @Length(200)
    String title;

    @NotNull
    int track;

    int releaseYear;

    @NotNull
    Duration duration;

    int bitRate;

    @Length(50)
    String contentType;

    int discNumber;

    @ManyToMany
    @JoinTable(name = "song_artists")
    List<ArtistModel> songArtists;

    @ManyToOne
    @JoinColumn(name = "album_id")
    AlbumModel album;

    public String getSongId() {
        return songId;
    }

    public void setSongId(String songId) {
        this.songId = songId;
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
        var sb = new StringBuilder();
        var numSongArtists = songArtists.size();
        for (int i = 0; i < numSongArtists; i++) {
            sb.append(songArtists.get(i).getName());
            if (!(i == (numSongArtists - 1))) {
                sb.append(" & ");
            }
        }

        return sb.toString();
    }

    public String getDisplayArtistPango() {
        var artist = getDisplayArtist();
        return artist.replace("&", "&amp;");
    }

    public SongModel(String songId, String title, int track, int year, Duration duration, int bitRate, String contentType, int discNumber, List<ArtistModel> songArtists, AlbumModel album) {
        this.songId = songId;
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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SongModel songModel = (SongModel) o;
        return getTrack() == songModel.getTrack() && getReleaseYear() == songModel.getReleaseYear() && getBitRate() == songModel.getBitRate() && getDiscNumber() == songModel.getDiscNumber() && Objects.equals(getSongId(), songModel.getSongId()) && Objects.equals(getTitle(), songModel.getTitle()) && Objects.equals(getDuration(), songModel.getDuration()) && Objects.equals(getContentType(), songModel.getContentType()) && Objects.equals(getSongArtists(), songModel.getSongArtists()) && Objects.equals(getAlbum(), songModel.getAlbum());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSongId(), getTitle(), getTrack(), getReleaseYear(), getDuration(), getBitRate(), getContentType(), getDiscNumber(), getSongArtists(), getAlbum());
    }
}

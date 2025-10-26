package io.github.Nyeksenn.grooveboat.entities;

import io.ebean.annotation.Length;
import io.ebean.annotation.NotNull;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;

import java.time.Duration;

@Entity
public class Album extends BaseModel {
    @NotNull @Length(50)
    String albumId;

    @NotNull @Length(100)
    String title;

    @NotNull
    int songCount;

    String genre;

    @NotNull @Length(100)
    String artist;

    @NotNull @Length(50)
    String artistId;

    int year;

    Duration duration;

    @Lob
    byte[] coverArt;

    public String getAlbumId() {
        return albumId;
    }

    public String getTitle() {
        return title;
    }

    public int getSongCount() {
        return songCount;
    }

    public String getGenre() {
        return genre;
    }

    public String getArtist() {
        return artist;
    }

    public String getArtistId() {
        return artistId;
    }

    public int getYear() {
        return year;
    }

    public Duration getDuration() {
        return duration;
    }

    public byte[] getCoverArt() {
        return coverArt;
    }

    public Album(String albumId, String title, int songCount, String genre, String artist, String artistId, int year, Duration duration, byte[] coverArt) {
        this.albumId = albumId;
        this.title = title;
        this.songCount = songCount;
        this.genre = genre;
        this.artist = artist;
        this.artistId = artistId;
        this.year = year;
        this.duration = duration;
        this.coverArt = coverArt;
    }
}

package io.github.Nyeksenn.grooveboat.models;

import com.jsoniter.annotation.JsonCreator;
import com.jsoniter.annotation.JsonProperty;
import org.gnome.gobject.GObject;

public class Album extends GObject {
    private final String id;
    private final String album;
    private final String title;
    private final String name;
    private final String coverArt;
    private final int songCount;
    private final String created;
    private final int duration;
    private final int playCount;
    private final String artistId;
    private final String artist;
    private final int year;
    private final String genre;

    public String getId() {
        return id;
    }

    public String getAlbum() {
        return album;
    }

    public String getTitle() {
        return title;
    }

    public String getName() {
        return name;
    }

    public String getCoverArt() {
        return coverArt;
    }

    public int getSongCount() {
        return songCount;
    }

    public String getCreated() {
        return created;
    }

    public int getDuration() {
        return duration;
    }

    public int getPlayCount() {
        return playCount;
    }

    public String getArtistId() {
        return artistId;
    }

    public String getArtist() {
        return artist;
    }

    public int getYear() {
        return year;
    }

    public String getGenre() {
        return genre;
    }

    @JsonCreator
    public Album(
            @JsonProperty("id") String id,
            @JsonProperty("album") String album,
            @JsonProperty("title") String title,
            @JsonProperty("name") String name,
            @JsonProperty("coverArt") String coverArt,
            @JsonProperty("songCount") int songCount,
            @JsonProperty("created") String created,
            @JsonProperty("duration") int duration,
            @JsonProperty("playCount") int playCount,
            @JsonProperty("artistId") String artistId,
            @JsonProperty("artist") String artist,
            @JsonProperty("year") int year,
            @JsonProperty("genre") String genre) {
        this.id = id;
        this.album = album;
        this.title = title;
        this.name = name;
        this.coverArt = coverArt;
        this.songCount = songCount;
        this.created = created;
        this.duration = duration;
        this.playCount = playCount;
        this.artistId = artistId;
        this.artist = artist;
        this.year = year;
        this.genre = genre;
    }
}

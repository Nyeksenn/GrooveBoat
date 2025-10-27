package io.github.Nyeksenn.grooveboat.models;

import com.jsoniter.annotation.JsonCreator;
import com.jsoniter.annotation.JsonProperty;

public class Artist {
    private final String id;
    private final String name;
    private final String artistImageUrl;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getArtistImageUrl() {
        return artistImageUrl;
    }

    @JsonCreator
    public Artist(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("artistImageUrl") String artistImageUrl
    ) {
        this.id = id;
        this.name = name;
        this.artistImageUrl = artistImageUrl;
    }
}

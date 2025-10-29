package io.github.Nyeksenn.grooveboat.deserializer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Album(String id, String name, String coverArt, int songCount, String created,
                    int duration, List<AlbumArtist> artists, int year) {
}

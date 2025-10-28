package io.github.Nyeksenn.grooveboat.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Album(String id, String album, String title, String name, String coverArt, int songCount, String created,
                    int duration, String artistId, String artist, int year) {
}

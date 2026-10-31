package io.github.Nyeksenn.grooveboat.backend.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Album(String id, String name, String coverArt, int songCount, String created,
                    int duration, List<ShortArtist> artists, int year) {
}

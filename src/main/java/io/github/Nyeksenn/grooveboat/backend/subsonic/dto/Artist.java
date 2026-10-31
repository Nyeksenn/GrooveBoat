package io.github.Nyeksenn.grooveboat.backend.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Artist(String id, String name, String coverArt) {
}

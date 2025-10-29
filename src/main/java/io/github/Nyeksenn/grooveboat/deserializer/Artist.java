package io.github.Nyeksenn.grooveboat.deserializer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Artist(String id, String name, String artistImageUrl) {
}

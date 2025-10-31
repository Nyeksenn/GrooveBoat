package io.github.Nyeksenn.grooveboat.deserializer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Song(String id, String albumId, String title, int track, int year, int duration, int bitRate,
                   String contentType, int discNumber, List<ShortArtist> artists) {
}
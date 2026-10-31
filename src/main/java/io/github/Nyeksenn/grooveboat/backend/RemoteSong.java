package io.github.Nyeksenn.grooveboat.backend;

import java.time.Duration;
import java.util.List;

/**
 * A song as reported by the server.
 *
 * @param bitRate     in kbit/s
 * @param contentType MIME type of the original file
 */
public record RemoteSong(String id, String albumId, String title, int track, int year, Duration duration, int bitRate,
                         String contentType, int discNumber, List<RemoteArtistRef> artists) {
}

package io.github.Nyeksenn.grooveboat.backend;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * An album as reported by the server.
 *
 * @param imageId backend image id for {@link MusicBackend#cacheImage}, or {@code null} if the album has no cover
 */
public record RemoteAlbum(String id, String title, int songCount, int year, Duration duration, String imageId,
                          List<RemoteArtistRef> artists, Instant created) {
}

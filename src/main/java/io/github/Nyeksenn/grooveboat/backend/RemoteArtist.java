package io.github.Nyeksenn.grooveboat.backend;

/**
 * Artist details.
 *
 * @param imageId backend image id for {@link MusicBackend#cacheImage}, or {@code null} if the artist has no image
 */
public record RemoteArtist(String id, String name, String imageId) {
}

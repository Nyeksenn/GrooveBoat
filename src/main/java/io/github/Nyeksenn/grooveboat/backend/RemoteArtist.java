package io.github.Nyeksenn.grooveboat.backend;

import org.jspecify.annotations.Nullable;

/**
 * Artist details.
 *
 * @param imageId backend image id for {@link MusicBackend#cacheImage}, or {@code null} if the artist has no image
 */
public record RemoteArtist(String id, String name, @Nullable String imageId) {
}

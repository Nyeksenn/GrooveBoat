package io.github.Nyeksenn.grooveboat.backend;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Everything GrooveBoat needs from a music server, independent of the protocol.
 * <p>
 * An instance is bound to one server URL. It becomes usable for library calls once a {@link Session} has been
 * established through {@link #authenticate} or {@link #restore}. Implementations are thread-safe for concurrent
 * library calls.
 */
public interface MusicBackend extends AutoCloseable {

    BackendType type();

    /** Base URL including scheme, without trailing slash. */
    String baseUrl();

    /** URL scheme, used as the keyring "protocol" attribute. */
    String scheme();

    /** Host (plus optional path) part of the URL, used as the keyring "server" attribute. */
    String host();

    /** Checks whether a compatible server of this backend type answers at {@link #baseUrl()}. Needs no credentials. */
    ServerInfo probe() throws IOException;

    /**
     * Logs in with username and password and keeps the resulting session.
     *
     * @return the session to persist; its credential goes to the keyring
     * @throws AuthenticationException when the server rejects the credentials
     */
    Session authenticate(String username, String password) throws IOException;

    /**
     * Re-establishes a stored session and verifies it with the server.
     *
     * @throws AuthenticationException when the stored credential is no longer valid
     */
    void restore(Session session) throws IOException;

    /**
     * Takes over a stored session without contacting the server, for starting while the server can't be reached.
     * Library calls fail with an {@link IOException} until the server answers again.
     *
     * @throws AuthenticationException when there is no stored credential
     */
    void restoreUnverified(Session session) throws AuthenticationException;

    /** Albums created on the server after {@code since}, newest first. */
    List<RemoteAlbum> albumsNewerThan(Instant since) throws IOException;

    /** All songs of an album, in disc/track order. */
    List<RemoteSong> songsOfAlbum(String albumId) throws IOException;

    RemoteArtist artist(String artistId) throws IOException;

    /** Downloads the image with the given backend image id into the cover cache and returns its path. */
    Path cacheImage(String imageId, int size) throws IOException;

    /**
     * Builds a ready-to-play URL for a song, as the original audio or transcoded by the server. Does no network I/O.
     *
     * @param contentType the song's MIME type as stored in the cache
     * @param transcode   the quality to ask the server for
     */
    PlaybackSource playbackSource(String songId, String contentType, TranscodeSettings transcode);

    /** Tells the server that a playback session has ended. Best effort; may do network I/O. */
    void releasePlayback(PlaybackSource source);

    /** Ends the server-side session. Best effort with a short timeout; never throws. */
    void logout();

    @Override
    void close();
}

package io.github.Nyeksenn.grooveboat.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

/**
 * Plumbing shared by the HTTP-based {@link MusicBackend} implementations: the parsed server URL, the HTTP client, a
 * {@code send} that restores the interrupt flag, the cover download and the template for restoring a stored session.
 * Subclasses only provide the protocol: request building, authentication and response parsing.
 */
public abstract class AbstractHttpBackend implements MusicBackend {
    private static final Logger logger = LoggerFactory.getLogger(AbstractHttpBackend.class);

    private final String baseUrl;
    private final String scheme;
    private final String host;
    private final HttpClient http;

    /**
     * @param baseUrl server URL including scheme; trailing slashes are dropped
     * @param http    the client for all requests; tests pass one pointing at a local server
     * @throws IllegalArgumentException when {@code baseUrl} has no scheme
     */
    protected AbstractHttpBackend(String baseUrl, HttpClient http) {
        this.baseUrl = BackendSupport.normalizeBaseUrl(baseUrl);
        var split = this.baseUrl.split("://", 2);
        if (split.length != 2) {
            throw new IllegalArgumentException("URL has no scheme");
        }
        this.scheme = split[0];
        this.host = split[1];
        this.http = http;
    }

    @Override
    public final String baseUrl() {
        return baseUrl;
    }

    @Override
    public final String scheme() {
        return scheme;
    }

    @Override
    public final String host() {
        return host;
    }

    /** Sends a request. An interrupt is reported as an {@link IOException} with the interrupt flag kept set. */
    protected final <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException {
        try {
            return http.send(request, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    /** Runs an image request and stores the result in the cover cache. */
    protected final Path downloadImage(String imageId, HttpRequest request) throws IOException {
        return BackendSupport.writeImage(imageId, send(request, HttpResponse.BodyHandlers.ofByteArray()));
    }

    // ---- Restoring a stored session

    @Override
    public final void restore(Session session) throws IOException {
        requireStoredSession(session);
        verifySession(session);
        adoptSession(session);
    }

    @Override
    public final void restoreUnverified(Session session) throws AuthenticationException {
        requireStoredSession(session);
        adoptSession(session);
    }

    /** Fails when {@code session} lacks what this protocol needs to log in again (e.g. the stored credential). */
    protected abstract void requireStoredSession(Session session) throws AuthenticationException;

    /** Asks the server whether {@code session} is still valid. Does not change the backend's state. */
    protected abstract void verifySession(Session session) throws IOException;

    /** Makes {@code session} the one used for library calls. Does no I/O. */
    protected abstract void adoptSession(Session session);

    @Override
    public void close() {
        logger.debug("Closing {} backend", type().displayName());
    }
}

package io.github.Nyeksenn.grooveboat.backend.subsonic;

import static org.javagi.util.Intl.i18n;
import io.github.Nyeksenn.grooveboat.backend.*;
import io.github.Nyeksenn.grooveboat.backend.subsonic.dto.Album;
import io.github.Nyeksenn.grooveboat.backend.subsonic.dto.Artist;
import io.github.Nyeksenn.grooveboat.backend.subsonic.dto.ShortArtist;
import io.github.Nyeksenn.grooveboat.backend.subsonic.dto.Song;
import io.github.Nyeksenn.grooveboat.backend.subsonic.dto.SubsonicResponse;
import org.semver4j.Semver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

import static io.github.Nyeksenn.grooveboat.backend.BackendSupport.HTTP;
import static io.github.Nyeksenn.grooveboat.backend.BackendSupport.JSON;
import static org.gnome.glib.GLib.getHostName;

/** OpenSubsonic implementation of {@link MusicBackend}, using salted-token authentication. */
public final class SubsonicBackend implements MusicBackend {
    private static final Logger logger = LoggerFactory.getLogger(SubsonicBackend.class);
    private static final Semver MIN_API_VERSION = new Semver("1.16.1");
    private static final String API_VERSION = "1.16.1";
    private static final int ALBUM_PAGE_SIZE = 200;
    private static final SecureRandom rng = new SecureRandom();

    private static final ObjectReader albumReader = JSON.readerFor(new TypeReference<List<Album>>() {
    });
    private static final ObjectReader songReader = JSON.readerFor(new TypeReference<List<Song>>() {
    });

    private final String baseUrl;
    private final String scheme;
    private final String host;
    private final String clientName = "GrooveBoat-" + getHostName();

    private volatile String username = "";
    private volatile String password = "";

    public SubsonicBackend(String baseUrl) {
        this.baseUrl = BackendSupport.normalizeBaseUrl(baseUrl);
        var split = this.baseUrl.split("://", 2);
        if (split.length != 2) {
            throw new IllegalArgumentException("URL has no scheme");
        }
        this.scheme = split[0];
        this.host = split[1];
    }

    @Override
    public BackendType type() {
        return BackendType.OPENSUBSONIC;
    }

    @Override
    public String baseUrl() {
        return baseUrl;
    }

    @Override
    public String scheme() {
        return scheme;
    }

    @Override
    public String host() {
        return host;
    }

    // ---- Requests

    private String generateSalt() {
        final int leftLimit = 48; // numeral '0'
        final int rightLimit = 122; // letter 'z'
        return rng.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(10)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    /** Builds an authenticated URI. The result contains auth parameters: never log it. */
    private URI uri(String endpoint, Map<String, String> params, String user, String pwd) {
        var all = new LinkedHashMap<String, String>();
        if (params != null) {
            all.putAll(params);
        }
        all.put("u", user);
        all.put("f", "json");
        all.put("v", API_VERSION);
        all.put("c", clientName);
        try {
            var md = MessageDigest.getInstance("MD5");
            var salt = generateSalt();
            md.update((pwd + salt).getBytes(StandardCharsets.UTF_8));
            all.put("t", HexFormat.of().formatHex(md.digest()));
            all.put("s", salt);
        } catch (NoSuchAlgorithmException e) {
            all.put("p", pwd);
        }
        return URI.create(baseUrl + endpoint + "?" + BackendSupport.queryString(all));
    }

    private URI uri(String endpoint, Map<String, String> params) {
        return uri(endpoint, params, username, password);
    }

    private <T> HttpResponse<T> send(URI uri, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            var request = HttpRequest.newBuilder().GET().timeout(Duration.ofSeconds(30)).uri(uri).build();
            return HTTP.send(request, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    /** Executes a request and returns the {@code subsonic-response} node. */
    private JsonNode call(URI uri) throws IOException {
        var response = send(uri, HttpResponse.BodyHandlers.ofString());
        try {
            var node = JSON.readTree(response.body()).path("subsonic-response");
            if (node.isMissingNode()) {
                throw new IOException("Not a Subsonic response (HTTP " + response.statusCode() + ")");
            }
            return node;
        } catch (JacksonException e) {
            throw new IOException("Couldn't parse Subsonic response (HTTP " + response.statusCode() + ")");
        }
    }

    private static SubsonicResponse envelope(JsonNode node) {
        return JSON.treeToValue(node, SubsonicResponse.class);
    }

    private static void requireOk(JsonNode node) throws IOException {
        var status = node.path("status").asString("");
        if (!status.equals("ok")) {
            var code = node.path("error").path("code").asInt(0);
            var message = node.path("error").path("message").asString("request failed");
            // 40: wrong credentials, 41: token auth not supported, 44: invalid API key
            if (code == 40 || code == 41 || code == 44) {
                throw new AuthenticationException(message);
            }
            throw new IOException("Subsonic error " + code + ": " + message);
        }
    }

    // ---- Session

    @Override
    public ServerInfo probe() throws IOException {
        // Ping with dummy credentials: the server answers with its API version even when auth fails.
        var node = call(uri("/rest/ping", null, "grooveboat-probe", ""));
        try {
            var resp = envelope(node);
            if (resp.version() == null) {
                return ServerInfo.incompatible(i18n("Not a Subsonic server"));
            }
            if (!resp.openSubsonic()) {
                return ServerInfo.incompatible(i18n("Server is not OpenSubsonic compatible"));
            }
            if (new Semver(resp.version()).isLowerThan(MIN_API_VERSION)) {
                return ServerInfo.incompatible(String.format(i18n("Server API version %s is too old"), resp.version()));
            }
            var name = resp.type() != null ? resp.type() : "OpenSubsonic";
            var version = resp.serverVersion() != null ? resp.serverVersion() : resp.version();
            return ServerInfo.compatible(name, version);
        } catch (JacksonException | IllegalArgumentException e) {
            return ServerInfo.incompatible(i18n("Not a Subsonic server"));
        }
    }

    private void checkCredentials(String user, String pwd) throws IOException {
        var node = call(uri("/rest/getUser", Map.of("username", user), user, pwd));
        requireOk(node);
    }

    @Override
    public Session authenticate(String username, String password) throws IOException {
        checkCredentials(username, password);
        this.username = username;
        this.password = password;
        return new Session(username, password, "");
    }

    @Override
    public void restore(Session session) throws IOException {
        if (session.credential() == null) {
            throw new AuthenticationException("No stored password");
        }
        checkCredentials(session.username(), session.credential());
        this.username = session.username();
        this.password = session.credential();
    }

    @Override
    public void restoreUnverified(Session session) throws AuthenticationException {
        if (session.credential() == null) {
            throw new AuthenticationException("No stored password");
        }
        this.username = session.username();
        this.password = session.credential();
    }

    @Override
    public void logout() {
        // Subsonic has no server-side sessions.
    }

    // ---- Library

    private static List<RemoteArtistRef> refs(List<ShortArtist> artists) {
        if (artists == null) {
            return List.of();
        }
        return artists.stream().map(a -> new RemoteArtistRef(a.id(), a.name())).toList();
    }

    private static Instant parseCreated(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @Override
    public List<RemoteAlbum> albumsNewerThan(Instant since) throws IOException {
        var albums = new ArrayList<RemoteAlbum>(ALBUM_PAGE_SIZE);
        var offset = 0;
        while (true) {
            var node = call(uri("/rest/getAlbumList2", Map.of(
                    "size", String.valueOf(ALBUM_PAGE_SIZE),
                    "offset", String.valueOf(offset),
                    "type", "newest"
            )));
            requireOk(node);
            var fragment = node.path("albumList2").path("album");
            if (fragment.isMissingNode()) {
                return albums;
            }
            List<Album> page;
            try {
                page = albumReader.readValue(fragment);
            } catch (JacksonException e) {
                throw new IOException("Couldn't parse album list: " + e.getMessage());
            }
            for (var album : page) {
                var created = parseCreated(album.created());
                // Albums without a usable timestamp are always included; the sync skips ones it already has.
                if (created != null && !created.isAfter(since)) {
                    return albums;
                }
                albums.add(new RemoteAlbum(album.id(), album.name(), album.songCount(), album.year(),
                        Duration.ofSeconds(album.duration()), album.coverArt(), refs(album.artists()),
                        created == null ? Instant.EPOCH : created));
            }
            if (page.size() < ALBUM_PAGE_SIZE) {
                return albums;
            }
            offset += ALBUM_PAGE_SIZE;
        }
    }

    @Override
    public List<RemoteSong> songsOfAlbum(String albumId) throws IOException {
        var node = call(uri("/rest/getMusicDirectory", Map.of("id", albumId)));
        requireOk(node);
        try {
            List<Song> songs = songReader.readValue(node.path("directory").path("child"));
            return songs.stream().map(s -> new RemoteSong(s.id(), s.albumId(), s.title(), s.track(), s.year(),
                    Duration.ofSeconds(s.duration()), s.bitRate(), s.contentType(), s.discNumber(),
                    refs(s.artists()))).toList();
        } catch (JacksonException e) {
            throw new IOException("Couldn't parse song list: " + e.getMessage());
        }
    }

    @Override
    public RemoteArtist artist(String artistId) throws IOException {
        var node = call(uri("/rest/getArtist", Map.of("id", artistId)));
        requireOk(node);
        try {
            var artist = JSON.treeToValue(node.path("artist"), Artist.class);
            var imageId = artist.coverArt() != null ? artist.coverArt() : artist.id();
            return new RemoteArtist(artist.id(), artist.name(), imageId);
        } catch (JacksonException e) {
            throw new IOException("Couldn't parse artist: " + e.getMessage());
        }
    }

    @Override
    public Path cacheImage(String imageId, int size) throws IOException {
        var response = send(uri("/rest/getCoverArt", Map.of("id", imageId, "size", String.valueOf(size))),
                HttpResponse.BodyHandlers.ofByteArray());
        return BackendSupport.writeImage(imageId, response);
    }

    // ---- Playback

    @Override
    public PlaybackSource playbackSource(String songId, String contentType) {
        // OpenSubsonic's hls.m3u8 transcodes by design, so play the original file progressively.
        var uri = uri("/rest/stream", Map.of("id", songId, "format", "raw"));
        return new PlaybackSource(uri.toString(), false, null);
    }

    @Override
    public void releasePlayback(PlaybackSource source) {
        // Nothing to release for progressive streams.
    }

    @Override
    public void close() {
        logger.debug("Closing OpenSubsonic backend");
    }
}

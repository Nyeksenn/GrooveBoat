package io.github.Nyeksenn.grooveboat.backend.jellyfin;

import org.jspecify.annotations.Nullable;
import static org.javagi.util.Intl.i18n;
import io.github.Nyeksenn.grooveboat.backend.*;
import org.semver4j.Semver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static io.github.Nyeksenn.grooveboat.backend.BackendSupport.HTTP;
import static io.github.Nyeksenn.grooveboat.backend.BackendSupport.JSON;

/**
 * Jellyfin implementation of {@link MusicBackend}.
 * <p>
 * Requests authenticate with the {@code Authorization: MediaBrowser ...} header. Playback URLs carry the access token
 * as {@code ApiKey} query parameter instead, because GStreamer's HLS demuxer fetches playlists and segments itself and
 * Jellyfin propagates that parameter into every variant and segment URL.
 */
public final class JellyfinBackend extends AbstractHttpBackend {
    private static final Logger logger = LoggerFactory.getLogger(JellyfinBackend.class);
    private static final Semver MIN_VERSION = new Semver("10.9.0");
    private static final String CLIENT_NAME = "GrooveBoat";
    private static final int ALBUM_PAGE_SIZE = 200;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(5);
    /** Jellyfin durations are in ticks of 100 ns. */
    private static final long NANOS_PER_TICK = 100L;

    private final String deviceId;
    private final String deviceName;
    private final String appVersion;

    private volatile @Nullable String token;
    private volatile @Nullable String userId;

    public JellyfinBackend(String baseUrl, String deviceId, String deviceName, String appVersion) {
        this(baseUrl, deviceId, deviceName, appVersion, HTTP);
    }

    public JellyfinBackend(String baseUrl, String deviceId, String deviceName, String appVersion, HttpClient http) {
        super(baseUrl, http);
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.appVersion = appVersion;
    }

    @Override
    public BackendType type() {
        return BackendType.JELLYFIN;
    }

    // ---- Requests

    private static String quote(String value) {
        return value.replace("\\", "").replace("\"", "'");
    }

    private String authorizationHeader(String withToken) {
        var header = new StringBuilder("MediaBrowser ")
                .append("Client=\"").append(CLIENT_NAME).append("\", ")
                .append("Device=\"").append(quote(deviceName)).append("\", ")
                .append("DeviceId=\"").append(quote(deviceId)).append("\", ")
                .append("Version=\"").append(quote(appVersion)).append('"');
        if (withToken != null) {
            header.append(", Token=\"").append(withToken).append('"');
        }
        return header.toString();
    }

    private URI uri(String path, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return URI.create(baseUrl() + path);
        }
        return URI.create(baseUrl() + path + "?" + BackendSupport.queryString(params));
    }

    private HttpRequest.Builder request(URI uri, String withToken, Duration timeout) {
        return HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Authorization", authorizationHeader(withToken))
                .header("Accept", "application/json");
    }

    /** Authenticated GET returning parsed JSON. */
    private JsonNode get(String path, Map<String, String> params) throws IOException {
        var response = send(request(uri(path, params), token, REQUEST_TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return parse(response);
    }

    private static JsonNode parse(HttpResponse<String> response) throws IOException {
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new AuthenticationException("Jellyfin rejected the session (HTTP " + response.statusCode() + ")");
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Jellyfin request failed with HTTP " + response.statusCode());
        }
        try {
            return JSON.readTree(response.body());
        } catch (JacksonException e) {
            throw new IOException("Couldn't parse Jellyfin response");
        }
    }

    private String requireUserId() {
        if (userId == null || token == null) {
            throw new IllegalStateException("Jellyfin backend has no session");
        }
        return userId;
    }

    // ---- Session

    @Override
    public ServerInfo probe() throws IOException {
        var response = send(HttpRequest.newBuilder(uri("/System/Info/Public", null))
                        .timeout(REQUEST_TIMEOUT).header("Accept", "application/json").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return ServerInfo.incompatible(i18n("Not a Jellyfin server"));
        }
        JsonNode info;
        try {
            info = JSON.readTree(response.body());
        } catch (JacksonException e) {
            return ServerInfo.incompatible(i18n("Not a Jellyfin server"));
        }
        var product = info.path("ProductName").asString("");
        var version = info.path("Version").asString("");
        if (!product.toLowerCase(Locale.ROOT).contains("jellyfin") || version.isEmpty()) {
            return ServerInfo.incompatible(i18n("Not a Jellyfin server"));
        }
        var semver = Semver.coerce(version);
        if (semver == null || semver.isLowerThan(MIN_VERSION)) {
            return ServerInfo.incompatible(String.format(i18n("Jellyfin %s is too old, %s or newer is required"), version, MIN_VERSION));
        }
        return ServerInfo.compatible(info.path("ServerName").asString(product), version);
    }

    @Override
    public Session authenticate(String username, String password) throws IOException {
        var body = JSON.createObjectNode().put("Username", username).put("Pw", password);
        var request = request(uri("/Users/AuthenticateByName", null), null, REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        var result = parse(send(request, HttpResponse.BodyHandlers.ofString()));
        var accessToken = result.path("AccessToken").asString("");
        var id = result.path("User").path("Id").asString("");
        if (accessToken.isEmpty() || id.isEmpty()) {
            throw new IOException("Jellyfin login response is incomplete");
        }
        this.token = accessToken;
        this.userId = id;
        return new Session(username, accessToken, id);
    }

    @Override
    protected void requireStoredSession(Session session) throws AuthenticationException {
        if (session.credential() == null || session.userId() == null || session.userId().isEmpty()) {
            throw new AuthenticationException("No stored Jellyfin session");
        }
    }

    @Override
    protected void verifySession(Session session) throws IOException {
        var response = send(request(uri("/Users/" + session.userId(), null), session.credential(), REQUEST_TIMEOUT)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        parse(response);
    }

    @Override
    protected void adoptSession(Session session) {
        this.token = session.credential();
        this.userId = session.userId();
    }

    @Override
    public void logout() {
        if (token == null) {
            return;
        }
        try {
            var response = send(request(uri("/Sessions/Logout", null), token, SHORT_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
            logger.info("Jellyfin logout returned HTTP {}", response.statusCode());
        } catch (IOException e) {
            logger.warn("Jellyfin logout failed: {}", e.getMessage());
        }
    }

    // ---- Library

    private static List<RemoteArtistRef> refs(JsonNode artists) {
        var result = new ArrayList<RemoteArtistRef>();
        for (var artist : artists) {
            var id = artist.path("Id").asString("");
            if (!id.isEmpty()) {
                result.add(new RemoteArtistRef(id, artist.path("Name").asString("Unknown Artist")));
            }
        }
        return result;
    }

    private static Duration ticks(JsonNode node) {
        return Duration.ofNanos(node.asLong(0L) * NANOS_PER_TICK);
    }

    private static boolean hasPrimaryImage(JsonNode item) {
        return !item.path("ImageTags").path("Primary").isMissingNode();
    }

    private static Instant created(JsonNode item) {
        try {
            return Instant.parse(item.path("DateCreated").asString(""));
        } catch (DateTimeParseException e) {
            return Instant.EPOCH;
        }
    }

    @Override
    public List<RemoteAlbum> albumsNewerThan(Instant since) throws IOException {
        var user = requireUserId();
        var albums = new ArrayList<RemoteAlbum>(ALBUM_PAGE_SIZE);
        var startIndex = 0;
        while (true) {
            var params = new LinkedHashMap<String, String>();
            params.put("userId", user);
            params.put("IncludeItemTypes", "MusicAlbum");
            params.put("Recursive", "true");
            params.put("SortBy", "DateCreated,SortName");
            params.put("SortOrder", "Descending");
            params.put("Fields", "DateCreated,ChildCount");
            params.put("EnableImageTypes", "Primary");
            params.put("ImageTypeLimit", "1");
            params.put("StartIndex", String.valueOf(startIndex));
            params.put("Limit", String.valueOf(ALBUM_PAGE_SIZE));
            var items = get("/Items", params).path("Items");
            for (var item : items) {
                var created = created(item);
                if (!created.isAfter(since)) {
                    return albums;
                }
                var id = item.path("Id").asString();
                albums.add(new RemoteAlbum(
                        id,
                        item.path("Name").asString("Unknown Album"),
                        item.path("ChildCount").asInt(0),
                        item.path("ProductionYear").asInt(0),
                        ticks(item.path("RunTimeTicks")),
                        hasPrimaryImage(item) ? id : null,
                        refs(item.path("AlbumArtists")),
                        created));
            }
            if (items.size() < ALBUM_PAGE_SIZE) {
                return albums;
            }
            startIndex += ALBUM_PAGE_SIZE;
        }
    }

    private static String codecOf(JsonNode item) {
        var source = item.path("MediaSources").path(0);
        for (var stream : source.path("MediaStreams")) {
            if (stream.path("Type").asString("").equals("Audio")) {
                var codec = stream.path("Codec").asString("");
                if (!codec.isEmpty()) {
                    return codec;
                }
            }
        }
        var container = source.path("Container").asString(item.path("Container").asString(""));
        return container.isEmpty() ? null : container.split(",")[0];
    }

    @Override
    public List<RemoteSong> songsOfAlbum(String albumId) throws IOException {
        var params = new LinkedHashMap<String, String>();
        params.put("userId", requireUserId());
        params.put("ParentId", albumId);
        params.put("IncludeItemTypes", "Audio");
        params.put("Recursive", "true");
        params.put("SortBy", "ParentIndexNumber,IndexNumber,SortName");
        params.put("Fields", "MediaSources");
        var songs = new ArrayList<RemoteSong>();
        for (var item : get("/Items", params).path("Items")) {
            var artists = refs(item.path("ArtistItems"));
            if (artists.isEmpty()) {
                artists = refs(item.path("AlbumArtists"));
            }
            var bitrate = item.path("MediaSources").path(0).path("Bitrate").asInt(0);
            songs.add(new RemoteSong(
                    item.path("Id").asString(),
                    item.path("AlbumId").asString(albumId),
                    item.path("Name").asString("Unknown Title"),
                    item.path("IndexNumber").asInt(0),
                    item.path("ProductionYear").asInt(0),
                    ticks(item.path("RunTimeTicks")),
                    bitrate / 1000,
                    AudioFormats.mimeForCodec(codecOf(item)),
                    item.path("ParentIndexNumber").asInt(1),
                    artists));
        }
        return songs;
    }

    @Override
    public RemoteArtist artist(String artistId) throws IOException {
        var item = get("/Items/" + artistId, Map.of("userId", requireUserId()));
        return new RemoteArtist(artistId, item.path("Name").asString("Unknown Artist"),
                hasPrimaryImage(item) ? artistId : null);
    }

    @Override
    public Path cacheImage(String imageId, int size) throws IOException {
        var params = Map.of("maxWidth", String.valueOf(size), "maxHeight", String.valueOf(size));
        return downloadImage(imageId, request(uri("/Items/" + imageId + "/Images/Primary", params), token,
                REQUEST_TIMEOUT).header("Accept", "image/*").GET().build());
    }

    // ---- Playback

    @Override
    public PlaybackSource playbackSource(String songId, String contentType, TranscodeSettings transcode) {
        if (token == null) {
            throw new IllegalStateException("Jellyfin backend has no session");
        }
        var codec = AudioFormats.codecForMime(contentType);
        var params = new LinkedHashMap<String, String>();
        params.put("mediaSourceId", songId);
        params.put("deviceId", deviceId);
        if (transcode.profile().transcodes()) {
            // Transcoding is always HLS. The server copies the original codec when it is allowed and already fits
            // under the cap, and re-encodes to the target codec otherwise.
            var target = transcode.format().codec(TranscodeFormat.OPUS);
            var bitrate = String.valueOf(transcode.profile().maxBitrateKbps() * 1000);
            var copyable = transcode.allowCopy() && AudioFormats.isFmp4Copyable(codec) && !target.equals(codec);
            var playSessionId = UUID.randomUUID().toString().replace("-", "");
            params.put("audioCodec", copyable ? codec + "," + target : target);
            params.put("audioBitRate", bitrate);
            params.put("maxStreamingBitrate", bitrate);
            params.put("segmentContainer", "mp4");
            params.put("allowAudioStreamCopy", String.valueOf(transcode.allowCopy()));
            params.put("playSessionId", playSessionId);
            params.put("ApiKey", token);
            return new PlaybackSource(uri("/Audio/" + songId + "/master.m3u8", params).toString(), true,
                    playSessionId);
        }
        if (AudioFormats.isFmp4Copyable(codec)) {
            // Ask for the original codec so the server only copies the stream into fMP4 segments.
            var playSessionId = UUID.randomUUID().toString().replace("-", "");
            params.put("audioCodec", codec);
            params.put("segmentContainer", "mp4");
            params.put("allowAudioStreamCopy", "true");
            params.put("playSessionId", playSessionId);
            params.put("ApiKey", token);
            return new PlaybackSource(uri("/Audio/" + songId + "/master.m3u8", params).toString(), true,
                    playSessionId);
        }
        // Formats fMP4 can't carry: original file as progressive download.
        params.put("static", "true");
        params.put("ApiKey", token);
        return new PlaybackSource(uri("/Audio/" + songId + "/stream", params).toString(), false, null);
    }

    @Override
    public void releasePlayback(PlaybackSource source) {
        if (source == null || !source.hls() || source.playSessionId() == null || token == null) {
            return;
        }
        try {
            var params = Map.of("deviceId", deviceId, "playSessionId", source.playSessionId());
            var response = send(request(uri("/Videos/ActiveEncodings", params), token, SHORT_TIMEOUT)
                    .DELETE().build(), HttpResponse.BodyHandlers.discarding());
            logger.debug("Released Jellyfin play session, HTTP {}", response.statusCode());
        } catch (IOException e) {
            logger.warn("Couldn't release Jellyfin play session: {}", e.getMessage());
        }
    }
}

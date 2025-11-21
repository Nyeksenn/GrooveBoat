package io.github.Nyeksenn.grooveboat.client;

import io.github.Nyeksenn.grooveboat.deserializer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdModule;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

import static org.gnome.glib.GLib.getHostName;

public class SubsonicClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(SubsonicClient.class);
    private static final JsonMapper om = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .addModule(new BlackbirdModule())
            .build();
    private static final ObjectReader albumReader = om.readerFor(new TypeReference<List<Album>>() {
    });
    private static final ObjectReader indexReader = om.readerFor(new TypeReference<List<Index>>() {
    });
    private static final ObjectReader songReader = om.readerFor(new TypeReference<List<Song>>() {
    });

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(150))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final HashMap<String, String> baseQueryParams = new HashMap<>();

    private String scheme;
    private String host;
    private String username;
    private String password;
    private static final SecureRandom rng = new SecureRandom();

    public void setBaseUrl(String baseUrl) {
        var splitResults = baseUrl.split("://");
        this.scheme = splitResults[0];
        this.host = splitResults[1];
    }

    public String getHost() {
        return this.host;
    }

    public String getScheme() {
        return this.scheme;
    }

    public void setUsername(String username) {
        this.username = username;
        this.baseQueryParams.replace("u", username);
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public SubsonicClient(String baseUrl, String username, String password) {
        this.username = username;
        this.password = password;

        baseQueryParams.put("u", this.username);
        baseQueryParams.put("f", "json");
        baseQueryParams.put("v", "1.16.1");
        baseQueryParams.put("c", "GrooveBoat-" + getHostName());
        var splitResults = baseUrl.split("://");
        this.scheme = splitResults[0];
        this.host = splitResults[1];
    }

    private String generateSalt() {
        final int leftLimit = 48; // numeral '0'
        final int rightLimit = 122; // letter 'z'
        final int targetStringLength = 10;

        return rng.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private String getQueryString(Map<String, String> params) {
        var querySB = new StringBuilder();
        params.forEach((key, value) -> querySB
                .append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8)).append('&'));
        querySB.deleteCharAt(querySB.length() - 1);

        return querySB.toString();
    }

    private URI getUri(String endpoint, Map<String, String> params) throws URISyntaxException {
        if (params != null) {
            params.putAll(baseQueryParams);
        } else {
            params = baseQueryParams;
        }

        try {
            var md = MessageDigest.getInstance("MD5");
            var salt = generateSalt();
            var saltedPwd = this.password + salt;
            md.update(saltedPwd.getBytes(StandardCharsets.UTF_8));
            var digest = md.digest();
            var hf = HexFormat.of();
            var token = hf.formatHex(digest);

            params.put("t", token);
            params.put("s", salt);
        } catch (NoSuchAlgorithmException e) {
            params.put("p", password);
        }

        var queryString = getQueryString(params);

        return new URI(
                scheme,
                host,
                endpoint,
                queryString,
                null
        );
    }

    private String executeGetString(String endpoint, Map<String, String> params) throws IOException {
        try {
            URI uri = getUri(endpoint, params);

            var retryCounter = 5;
            var request = HttpRequest.newBuilder().GET().uri(uri).build();

            while (retryCounter > 0) {
                try {
                    return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
                } catch (HttpConnectTimeoutException | InterruptedException e) {
                    logger.warn("Execution of string request failed");
                    retryCounter--;
                }
            }
            throw new IOException("Couldn't connect after" + retryCounter + "retries");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + e.getMessage(), e);
        }
    }

    private byte[] executeGetBinary(Endpoint endpoint, Map<String, String> params) throws IOException {
        try {
            URI uri;

            switch (endpoint) {
                case AbsoluteEndpoint ep -> uri = URI.create(ep.url());
                case RelativeEndpoint ep -> uri = getUri(ep.url(), params);
            }

            var retryCounter = 5;

            var request = HttpRequest.newBuilder().GET().uri(uri).build();
            while (retryCounter > 0) {
                try {
                    return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
                } catch (HttpConnectTimeoutException | InterruptedException e) {
                    logger.warn("Execution of binary request failed");
                    retryCounter--;
                }
            }

            throw new IOException("Couldn't connect after" + retryCounter + "retries");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + e.getMessage(), e);
        }
    }

    public HttpResponse<InputStream> stream(String songId, boolean seek, long position) throws IOException {
        Map<String, String> params;

        if (seek) {
            params = new HashMap<>(Map.of(
                    "id", songId,
                    "timeOffset", String.valueOf(position)
            ));
        } else {
            params = new HashMap<>(Map.of(
                    "id", songId
            ));
        }

        var retryCounter = 5;

        try {
            URI uri = getUri("/rest/stream", params);
            var request = HttpRequest.newBuilder().GET().timeout(Duration.ofHours(3)).uri(uri).build();
            while (retryCounter > 0) {
                try {
                    return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                } catch (HttpConnectTimeoutException | InterruptedException e) {
                    logger.warn("Execution of streaming request failed");
                    retryCounter--;
                }
            }
            throw new IOException("Couldn't connect after" + retryCounter + "retries");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + e.getMessage(), e);
        }
    }

    public byte[] getArtistImage(String url) throws IOException {
        return executeGetBinary(new AbsoluteEndpoint(url), null);
    }

    public byte[] getCoverArt(String id, int size) throws IOException {
        var params = new HashMap<>(Map.of(
                "id", id,
                "size", String.valueOf(size)
        ));

        return executeGetBinary(new RelativeEndpoint("/rest/getCoverArt"), params);
    }

    public List<Album> getAlbumList2() throws IOException {
        var moreToGo = true;
        List<Album> albums = new ArrayList<>(200);
        var offset = 0;
        var params = new HashMap<>(Map.of(
                "size", String.valueOf(500),
                "offset", String.valueOf(offset),
                "type", "alphabeticalByArtist"
        ));

        while (moreToGo) {
            String response;
            response = executeGetString("/rest/getAlbumList2", params);
            var root = om.readTree(response);
            var album_fragment = root.path("subsonic-response").path("albumList2").path("album");

            if (album_fragment.isMissingNode()) {
                moreToGo = false;
            } else {
                try {
                    List<Album> partialAlbumList = albumReader.readValue(album_fragment);
                    albums.addAll(partialAlbumList);
                } catch (IllegalArgumentException | JacksonException e) {
                    logger.warn(e.getMessage());
                    return new ArrayList<>();
                }
            }

            offset += 500;
            params.replace("offset", String.valueOf(offset));
        }

        return albums;
    }

    public Artist getArtist(String id) throws IOException {
        var params = new HashMap<>(Map.of(
                "id", id
        ));

        try {
            var response = executeGetString("/rest/getArtist", params);

            var root = om.readTree(response);
            var subsonicResponse = root.path("subsonic-response").path("artist");

            return om.treeToValue(subsonicResponse, Artist.class);
        } catch (JacksonException e) {
            logger.warn(e.getMessage());
            return null;
        }
    }

    public List<Artist> getArtists() throws IOException {
        var artistList = new ArrayList<Artist>(50);

        List<Index> indices;
        try {
            var response = executeGetString("/rest/getArtists", null);
            var root = om.readTree(response);
            var index_fragment = root.path("subsonic-response").path("artists").path("index");

            indices = indexReader.readValue(index_fragment);
        } catch (IllegalArgumentException | JacksonException e) {
            logger.warn(e.getCause().toString());
            return new ArrayList<>();
        }

        for (var index : indices) {
            artistList.addAll(index.artist());
        }

        return artistList;
    }

    public List<Song> getSongsByAlbum(String albumId) throws IOException {
        var params = new HashMap<>(Map.of(
                "id", albumId
        ));

        try {
            var response = executeGetString("/rest/getMusicDirectory", params);

            var root = om.readTree(response);
            var child_fragment = root.path("subsonic-response").path("directory").path("child");
            return songReader.readValue(child_fragment);
        } catch (IllegalArgumentException | JacksonException e) {
            logger.warn(e.getCause().toString());
            return new ArrayList<>();
        }
    }

    public SubsonicResponse userPing(String username) throws IOException {
        try {
            var params = new HashMap<>(Map.of(
                    "username", username
            ));
            var response = executeGetString("/rest/getUser", params);
            var root = om.readTree(response);
            var subsonicResponse = root.path("subsonic-response");
            return om.treeToValue(subsonicResponse, SubsonicResponse.class);
        } catch (JacksonException e) {
            logger.warn(e.getCause().toString());
            return null;
        }
    }

    public SubsonicResponse ping() throws IOException {
        try {
            var response = executeGetString("/rest/ping", null);
            var root = om.readTree(response);
            var subsonicResponse = root.path("subsonic-response");
            return om.treeToValue(subsonicResponse, SubsonicResponse.class);
        } catch (JacksonException e) {
            logger.warn(e.getCause().toString());
            return null;
        }
    }

    public static void ensureInitialized() {
    }

    public void close() {
        if (httpClient != null) {
            httpClient.shutdownNow();
        }
    }
}
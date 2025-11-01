package io.github.Nyeksenn.grooveboat.client;

import io.github.Nyeksenn.grooveboat.deserializer.*;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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

    private final CloseableHttpClient httpClient;

    private final HashMap<String, String> baseQueryParams;

    private URI baseUrl;
    private String username;
    private String password;
    private final SecureRandom rng;

    public void setBaseUrl(String baseUrl) {
        try {
            this.baseUrl = new URI(baseUrl);
        } catch (URISyntaxException e) {
            logger.warn("Unable to set base url. Base url {} invalid", baseUrl);
        }
    }

    public void setUsername(String username) {
        this.username = username;
        this.baseQueryParams.replace("u", username);
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public SubsonicClient(String baseUrl, String username, String password) {
        this.username = username;
        this.password = password;
        this.rng = new SecureRandom();

        this.baseQueryParams = new HashMap<>();
        baseQueryParams.put("u", this.username);
        baseQueryParams.put("f", "json");
        baseQueryParams.put("v", "1.16.1");
        baseQueryParams.put("c", "GrooveBoat-" + getHostName());
        try {
            this.baseUrl = new URI(baseUrl);
        } catch (URISyntaxException e) {
            logger.warn("Invalid base url");
            this.baseUrl = null;
        }

        // Configure connection pooling for performance
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100); // Maximum total connections
        connectionManager.setDefaultMaxPerRoute(20); // Max connections per route

        this.httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();
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
                .append(key).append('&').append(URLEncoder.encode(value, StandardCharsets.UTF_8)).append('&'));
        querySB.deleteCharAt(querySB.length() - 1);

        return querySB.toString();
    }

    private URI getUri(String endpoint, Map<String, String> params) throws URISyntaxException {
        if (params == null) {
            params = new HashMap<>();
        }

        params.putAll(baseQueryParams);

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
                baseUrl.getScheme(),
                baseUrl.getHost(),
                endpoint,
                queryString,
                null
        );
    }

    private String executeGetString(String endpoint, Map<String, String> params) throws IOException {
        try {
            URI uri = getUri(endpoint, params);

            return this.httpClient.execute(new HttpGet(uri),
                    response -> {
                        int statusCode = response.getCode();

                        if (statusCode >= 200 && statusCode < 300) {
                            return EntityUtils.toString(response.getEntity());
                        } else {
                            String errorBody = EntityUtils.toString(response.getEntity());
                            throw new IOException("Request failed with status " + statusCode + ": " + errorBody);
                        }
                    }
            );

        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI: " + e.getMessage(), e);
        }
    }

    private byte[] executeGetBinary(String endpoint, Map<String, String> params) throws IOException {
        try {
            URI uri = getUri(endpoint, params);

            return this.httpClient.execute(new HttpGet(uri),
                    response -> {
                        int statusCode = response.getCode();
                        if (statusCode >= 200 && statusCode < 300) {
                            return EntityUtils.toByteArray(response.getEntity());
                        } else {
                            String errorBody = EntityUtils.toString(response.getEntity());
                            throw new IOException("Request failed with status " + statusCode + ": " + errorBody);
                        }
                    }
            );

        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI: " + e.getMessage(), e);
        }
    }

    public byte[] getCoverArt(String id, int size) {
        var params = new HashMap<>(Map.of(
                "id", id,
                "size", String.valueOf(size)
        ));

        try {
            return executeGetBinary("/rest/getCoverArt", params);
        } catch (IOException e) {
            logger.info("Couldn't fetch album art");
            return null;
        }
    }

    public byte[] getArtistImage(String url) {
        try {
            return this.httpClient.execute(new HttpGet(url),
                    response -> {
                        int statusCode = response.getCode();
                        if (statusCode >= 200 && statusCode < 300) {
                            return EntityUtils.toByteArray(response.getEntity());
                        } else {
                            String errorBody = EntityUtils.toString(response.getEntity());
                            throw new IOException("Request failed with status " + statusCode + ": " + errorBody);
                        }
                    }
            );
        } catch (IOException e) {
            logger.warn("Couldn't fetch artist image");
            return null;
        }
    }

    public List<Album> getAlbumsByArtist(String artistId) throws IOException {
        var params = new HashMap<>(Map.of(
           "id", artistId
        ));

        var response = executeGetString("/rest/getMusicDirectory", params);
        var root = om.readTree(response);
        var album_fragment = root.path("subsonic-response").path("directory").path("child");
        try {
            return albumReader.readValue(album_fragment);
        } catch (IllegalArgumentException | JacksonException e) {
            logger.warn(e.getCause().toString());
            return new ArrayList<>();
        }
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
            var response = executeGetString("/rest/getAlbumList2", params);
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

        var response = executeGetString("/rest/getArtist", params);

        var root = om.readTree(response);
        var subsonicResponse = root.path("subsonic-response").path("artist");
        try {
            return om.treeToValue(subsonicResponse, Artist.class);
        } catch (JacksonException e) {
            logger.warn(e.getCause().toString());
            return null;
        }
    }

    public List<Artist> getArtists() throws IOException {
        var artistList = new ArrayList<Artist>(50);

        var response = executeGetString("/rest/getArtists", null);
        var root = om.readTree(response);
        var index_fragment = root.path("subsonic-response").path("artists").path("index");

        List<Index> indices;
        try {
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

        var response = executeGetString("/rest/getMusicDirectory", params);

        var root = om.readTree(response);
        var child_fragment = root.path("subsonic-response").path("directory").path("child");
        try {
            return songReader.readValue(child_fragment);
        } catch (IllegalArgumentException | JacksonException e) {
            logger.warn(e.getCause().toString());
            return new ArrayList<>();
        }
    }

    public SubsonicResponse ping() throws IOException {
        var response = executeGetString("/rest/ping", null);

        var root = om.readTree(response);
        var subsonicResponse = root.path("subsonic-response");
        try {
            return om.treeToValue(subsonicResponse, SubsonicResponse.class);
        } catch (JacksonException e) {
            logger.warn(e.getCause().toString());
            return null;
        }
    }

    public ClassicHttpResponse stream(String songId) throws IOException {
        var params = new HashMap<>(Map.of(
                "id", songId
        ));

        try {
            URI uri = getUri("stream", params);

            return this.httpClient.executeOpen(null, new HttpGet(uri), null);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI: " + e.getMessage(), e);
        }
    }

    public void close() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }
}

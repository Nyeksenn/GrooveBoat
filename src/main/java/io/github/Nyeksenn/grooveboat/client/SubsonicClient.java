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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.Executors;

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

    private final HttpClient httpClient;

    private final HashMap<String, String> baseQueryParams;

    private String scheme;
    private String host;
    private String username;
    private String password;
    private final SecureRandom rng;

    public void setBaseUrl(String baseUrl) {
        var splitResults = baseUrl.split("://");
        this.scheme = splitResults[0];
        this.host = splitResults[1];
    }

    public String getHost() {
        return this.host;
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
        this.rng = new SecureRandom();

        this.baseQueryParams = new HashMap<>();
        baseQueryParams.put("u", this.username);
        baseQueryParams.put("f", "json");
        baseQueryParams.put("v", "1.16.1");
        baseQueryParams.put("c", "GrooveBoat-" + getHostName());
        var splitResults = baseUrl.split("://");
        this.scheme = splitResults[0];
        this.host = splitResults[1];

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
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

    private String executeGetString(String endpoint, Map<String, String> params) throws IOException, InterruptedException {
        try {
            URI uri = getUri(endpoint, params);

            var request = HttpRequest.newBuilder().GET().uri(uri).build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI: " + e.getMessage(), e);
        }
    }

    private byte[] executeGetBinary(Endpoint endpoint, Map<String, String> params) throws IOException, InterruptedException {
        try {
            URI uri;

            switch (endpoint) {
                case AbsoluteEndpoint ep -> uri = URI.create(ep.url());
                case RelativeEndpoint ep -> uri = getUri(ep.url(), params);
            }

            var request = HttpRequest.newBuilder().GET().uri(uri).build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return response.body();
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI: " + e.getMessage(), e);
        }
    }

    public HttpResponse<InputStream> stream(String songId) throws IOException {
        var params = new HashMap<>(Map.of(
                "id", songId
        ));

        try {
            URI uri = getUri("/rest/stream", params);
            var request = HttpRequest.newBuilder().GET().uri(uri).build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            logger.warn("Interrupted while streaming");
            return null;
        }
    }

    public byte[] getArtistImage(String url) {
        try {
            return executeGetBinary(new AbsoluteEndpoint(url), null);
        } catch (IOException e) {
            logger.warn("Couldn't fetch artist image");
            return null;
        } catch (InterruptedException e) {
            logger.warn("Interrupted while getting artist image");
            return null;
        }
    }

    public byte[] getCoverArt(String id, int size) {
        var params = new HashMap<>(Map.of(
                "id", id,
                "size", String.valueOf(size)
        ));

        try {
            return executeGetBinary(new RelativeEndpoint("/rest/getCoverArt"), params);
        } catch (IOException | InterruptedException e) {
            logger.info("Couldn't fetch album art");
            return null;
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
            String response = null;
            try {
                response = executeGetString("/rest/getAlbumList2", params);
            } catch (InterruptedException e) {
                logger.warn("Was interrupted while getting albums");
            }
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
        } catch (InterruptedException e) {
            logger.warn("Interrupted while getting artist");
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
        } catch (InterruptedException e) {
            logger.warn("Interrupted while getting artist list");
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
        } catch (InterruptedException e) {
            logger.warn("Interrupted while getting songs by album");
            return new ArrayList<>();
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
        } catch (InterruptedException e) {
            logger.warn("Interrupted while pinging the server");
            return null;
        }
    }

    public void close() {
        if (httpClient != null) {
            httpClient.shutdownNow();
        }
    }
}
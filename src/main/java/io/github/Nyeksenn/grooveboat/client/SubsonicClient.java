package io.github.Nyeksenn.grooveboat.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.Nyeksenn.grooveboat.deserializer.*;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.gnome.glib.GLib.getHostName;

public class SubsonicClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(SubsonicClient.class);
    private final CloseableHttpClient httpClient;
    private String baseUrl;
    private String username;
    private String password;
    private final String clientId = "GrooveBoat-" + getHostName();
    private final String apiVersion = "1.16.1";
    private final SecureRandom rng;

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public SubsonicClient(String baseUrl, String username, String password) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
        this.rng = new SecureRandom();

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

    private URI getUri(String endpoint, List<NameValuePair> params) throws URISyntaxException {
        try {
            var md = MessageDigest.getInstance("MD5");
            var salt = generateSalt();
            var saltedPwd = this.password + salt;
            md.update(saltedPwd.getBytes(StandardCharsets.UTF_8));
            var digest = md.digest();
            var hf = HexFormat.of();
            var token = hf.formatHex(digest);

            return new URIBuilder(baseUrl)
                    .setPathSegments("rest", endpoint)
                    .addParameter("u", username)
                    .addParameter("t", token)
                    .addParameter("s", salt)
                    .addParameter("f", "json")
                    .addParameter("c", clientId)
                    .addParameter("v", apiVersion)
                    .addParameters(params)
                    .build();
        } catch (NoSuchAlgorithmException e) {
            logger.warn("Can't create MD5 hash. Using plain password.");
            return new URIBuilder()
                    .setHost(baseUrl)
                    .setPathSegments("rest", endpoint)
                    .addParameter("u", username)
                    .addParameter("p", password)
                    .addParameter("f", "json")
                    .addParameter("c", clientId)
                    .addParameter("v", apiVersion)
                    .addParameters(params)
                    .build();
        }
    }

    private String executeGetString(String endpoint, List<NameValuePair> params) throws IOException {
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

    private byte[] executeGetBinary(String endpoint, List<NameValuePair> params) throws IOException {
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
        var params = new ArrayList<NameValuePair>(2);
        params.add(new BasicNameValuePair("id", id));
        params.add(new BasicNameValuePair("size", String.valueOf(size)));

        try {
            return executeGetBinary("getCoverArt", params);
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
        var params = new ArrayList<NameValuePair>(2);
        params.add(new BasicNameValuePair("id", artistId));

        var response = executeGetString("getMusicDirectory", params);
        var om = new ObjectMapper();
        var root = om.readTree(response);
        var album_fragment = root.path("subsonic-response").path("directory").path("child");
        try {
            return om.convertValue(album_fragment, new TypeReference<List<Album>>() {
            });
        } catch (IllegalArgumentException e) {
            logger.warn(e.getCause().toString());
            return new ArrayList<>();
        }
    }

    public List<Album> getAlbumList2() throws IOException {
        var moreToGo = true;
        List<Album> albums = new ArrayList<>(200);
        var offset = 0;
        var params = new ArrayList<NameValuePair>(2);
        params.add(new BasicNameValuePair("size", String.valueOf(500)));
        params.add(new BasicNameValuePair("offset", String.valueOf(offset)));
        params.add(new BasicNameValuePair("type", "alphabeticalByArtist"));

        while (moreToGo) {
            var response = executeGetString("getAlbumList2", params);
            var om = new ObjectMapper();
            var root = om.readTree(response);
            var album_fragment = root.path("subsonic-response").path("albumList2").path("album");

            if (album_fragment.isMissingNode()) {
                moreToGo = false;
            } else {
                try {
                    var x = om.convertValue(album_fragment, new TypeReference<List<Album>>() {
                    });
                    albums.addAll(x);
                } catch (IllegalArgumentException e) {
                    logger.warn(e.getCause().toString());
                    return new ArrayList<>();
                }
            }

            params.remove(1);
            offset += 500;
            params.add(new BasicNameValuePair("offset", String.valueOf(offset)));
        }

        return albums;
    }

    public Artist getArtist(String id) throws IOException {
        var params = new ArrayList<NameValuePair>(1);
        params.add(new BasicNameValuePair("id", id));

        var response = executeGetString("getArtist", params);

        var om = new ObjectMapper();
        var root = om.readTree(response);
        var subsonicResponse = root.path("subsonic-response").path("artist");
        return om.convertValue(subsonicResponse, Artist.class);
    }

    public List<Artist> getArtists() throws IOException {
        var artistList = new ArrayList<Artist>(50);

        var response = executeGetString("getArtists", null);
        var om = new ObjectMapper();
        var root = om.readTree(response);
        var index_fragment = root.path("subsonic-response").path("artists").path("index");

        List<Index> indices;
        try {
            indices = om.convertValue(index_fragment, new TypeReference<List<Index>>() {
            });
        } catch (IllegalArgumentException e) {
            logger.warn(e.getCause().toString());
            return new ArrayList<>();
        }

        for (var index : indices) {
            artistList.addAll(index.artist());
        }

        return artistList;
    }

    public List<Song> getSongsByAlbum(String albumId) throws IOException {
        var songList = new ArrayList<Song>(20);
        var params = new ArrayList<NameValuePair>(1);
        params.add(new BasicNameValuePair("id", albumId));

        var response = executeGetString("getMusicDirectory", params);

        var om = new ObjectMapper();
        var root = om.readTree(response);
        var child_fragment = root.path("subsonic-response").path("directory").path("child");
        try {
            return om.convertValue(child_fragment, new TypeReference<List<Song>>() {
            });
        } catch (IllegalArgumentException e) {
            logger.warn(e.getCause().toString());
            return new ArrayList<>();
        }
    }

    public SubsonicResponse ping() throws IOException {
        var response = executeGetString("ping", null);

        var om = new ObjectMapper();
        var root = om.readTree(response);
        var subsonicResponse = root.path("subsonic-response");
        return om.convertValue(subsonicResponse, SubsonicResponse.class);
    }

    public ClassicHttpResponse stream(String songId) throws IOException {
        var params = new ArrayList<NameValuePair>(1);
        params.add(new BasicNameValuePair("id", songId));

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

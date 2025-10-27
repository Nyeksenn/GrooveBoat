package io.github.Nyeksenn.grooveboat.client;

import com.jsoniter.JsonIterator;
import io.github.Nyeksenn.grooveboat.models.AlbumList;
import io.github.Nyeksenn.grooveboat.models.Indices;
import io.github.Nyeksenn.grooveboat.models.SubsonicResponse;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
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
import java.util.*;

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
            logger.info("Built the following URI: {}", uri.toString());

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

    public AlbumList getAlbumsByArtist(String artistId) throws IOException {
        var params = new ArrayList<NameValuePair>(2);
        params.add(new BasicNameValuePair("id", artistId));

        var response = executeGetString("getMusicDirectory", params);
        var any = JsonIterator.deserialize(response);
        var album_fragment = any.get("subsonic-response", "directory").toString();

        return JsonIterator.deserialize(album_fragment, AlbumList.class);
    }

    public byte[] getCoverArt(String id) {
        var params = new ArrayList<NameValuePair>(1);

        params.add(new BasicNameValuePair("id", id));
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

    public Indices getArtists() throws IOException {
        var response = executeGetString("getArtists", null);
        var any = JsonIterator.deserialize(response);
        var index_fragment = any.get("subsonic-response", "artists").toString();
        return JsonIterator.deserialize(index_fragment, Indices.class);
    }

    public SubsonicResponse ping() throws IOException {
        var response = executeGetString("ping", null);
        var any = JsonIterator.deserialize(response);
        var subsonicResponse = any.get("subsonic-response").toString();
        return JsonIterator.deserialize(subsonicResponse, SubsonicResponse.class);
    }

    public void close() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }
}

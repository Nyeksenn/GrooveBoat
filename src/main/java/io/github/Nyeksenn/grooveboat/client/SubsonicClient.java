package io.github.Nyeksenn.grooveboat.client;

import com.jsoniter.JsonIterator;
import io.github.Nyeksenn.grooveboat.models.AlbumList2;
import io.github.Nyeksenn.grooveboat.models.CoverArt;
import io.github.Nyeksenn.grooveboat.models.SubsonicResponse;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static org.gnome.glib.GLib.getHostName;

public class SubsonicClient implements AutoCloseable {
    private final CloseableHttpClient httpClient;
    private String baseUrl;
    private String username;
    private String password;
    private final String clientId = "GrooveBoat-" + getHostName();
    private final String apiVersion = "v1.16.1";

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public SubsonicClient(String baseUrl, String username, String password) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;

        // Configure connection pooling for performance
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100); // Maximum total connections
        connectionManager.setDefaultMaxPerRoute(20); // Max connections per route

        this.httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();
    }

    private URI getUri(String endpoint, List<NameValuePair> params) throws URISyntaxException {
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

    public AlbumList2 getAlbumList2() throws IOException {
        var response = executeGetString("getAlbumList2", null);
        var any = JsonIterator.deserialize(response);
        var album_fragment = any.get("subsonic-response", "albumList2", "album").toString();
        return JsonIterator.deserialize(album_fragment, AlbumList2.class);
    }

    public CoverArt getCoverArt(String id) throws IOException {
        var params = new ArrayList<NameValuePair>(1);
        params.add(new BasicNameValuePair("id", id));
        var coverArtData = executeGetBinary("getCoverArt", params);
        return new CoverArt(id, coverArtData);
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

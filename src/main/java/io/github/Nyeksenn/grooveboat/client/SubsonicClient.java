package io.github.Nyeksenn.grooveboat.client;

import com.jsoniter.JsonIterator;
import io.github.Nyeksenn.grooveboat.models.AlbumList2;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class SubsonicClient implements AutoCloseable {
    private final CloseableHttpClient httpClient;
    private final String baseUrl;
    private final String username;
    private final String password;

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

    private String executeGet(String endpoint) throws IOException {
        try {
            URI uri = new URIBuilder()
                    .setHost(baseUrl)
                    .setPathSegments("rest", endpoint)
                    .addParameter("u", username)
                    .addParameter("p", password)
                    .addParameter("f", "json")
                    .build();

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

    public AlbumList2 getAlbumList2() throws IOException {
        try {
            var response = executeGet("getAlbumList2");
            var any = JsonIterator.deserialize(response);
            var album_fragment = any.get("subsonic-response", "albumList2", "album").toString();
            return JsonIterator.deserialize(album_fragment, AlbumList2.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }
}

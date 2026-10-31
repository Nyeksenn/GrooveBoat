package io.github.Nyeksenn.grooveboat.backend;

import io.github.Nyeksenn.grooveboat.utils.Helpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdModule;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

/** Shared plumbing for backend implementations: HTTP client, JSON mapper, query strings and the cover cache. */
public final class BackendSupport {
    private static final Logger logger = LoggerFactory.getLogger(BackendSupport.class);

    public static final Path COVER_CACHE_PATH = Paths.get(Helpers.getUserDataPath(), "grooveboat", "covers");

    public static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .addModule(new BlackbirdModule())
            .build();

    public static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private BackendSupport() {
    }

    public static String queryString(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    /** Removes trailing slashes so endpoint paths can be appended directly. */
    public static String normalizeBaseUrl(String baseUrl) {
        var url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /** Writes a downloaded image into the cover cache, naming it after the image id and its content type. */
    public static Path writeImage(String imageId, HttpResponse<byte[]> response) throws IOException {
        if (response.statusCode() != 200) {
            throw new IOException("Image request failed with HTTP " + response.statusCode());
        }
        var contentType = response.headers().firstValue("Content-Type")
                .orElseThrow(() -> new IOException("Response did not contain Content-Type header"));
        var subtype = contentType.split(";")[0].split("/");
        var extension = subtype.length > 1 ? subtype[1].trim() : "img";
        var safeId = imageId.replaceAll("[^A-Za-z0-9_.-]", "_");
        Files.createDirectories(COVER_CACHE_PATH);
        var imagePath = COVER_CACHE_PATH.resolve(safeId + "." + extension);
        Files.write(imagePath, response.body());
        return imagePath;
    }

    /** Deletes all files in the cover cache. Failures are logged, not thrown. */
    public static void clearCoverCache() {
        if (!Files.isDirectory(COVER_CACHE_PATH)) {
            return;
        }
        try (var files = Files.list(COVER_CACHE_PATH)) {
            files.forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    logger.warn("Couldn't delete cached image {}: {}", file.getFileName(), e.getMessage());
                }
            });
        } catch (IOException e) {
            logger.warn("Couldn't list cover cache: {}", e.getMessage());
        }
    }
}

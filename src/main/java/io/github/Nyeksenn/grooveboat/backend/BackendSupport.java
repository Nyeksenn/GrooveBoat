package io.github.Nyeksenn.grooveboat.backend;

import io.github.Nyeksenn.grooveboat.utils.Helpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdModule;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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

    /** Whether {@code url} is an http(s) URL with a host and a valid port, usable as a server address. */
    public static boolean isValidBaseUrl(String url) {
        try {
            var uri = new URI(url);
            var scheme = uri.getScheme();
            var host = uri.getHost();
            return scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && host != null && !host.isBlank()
                    && uri.getPort() <= 65535;
        } catch (URISyntaxException e) {
            return false;
        }
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
        return writeImage(COVER_CACHE_PATH, imageId, extension, response.body());
    }

    /**
     * Stores {@code body} as {@code <fileStem(imageId)>.<extension>} in {@code directory}. The file appears
     * atomically (written to a temporary file first), because several albums can share one cover and download it at
     * the same time; a reader must never see a half-written image.
     */
    static Path writeImage(Path directory, String imageId, String extension, byte[] body) throws IOException {
        Files.createDirectories(directory);
        var safeExtension = extension.replaceAll("[^A-Za-z0-9]", "");
        var imagePath = directory.resolve(fileStem(imageId) + "." + (safeExtension.isEmpty() ? "img" : safeExtension));
        var temporary = Files.createTempFile(directory, ".download-", ".tmp");
        try {
            Files.write(temporary, body);
            Files.move(temporary, imagePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return imagePath;
    }

    private static final int MAX_STEM_LENGTH = 100;

    /**
     * A file name stem for {@code imageId}. Ids made only of safe characters are used as they are (so existing cache
     * files keep their names). Anything else is sanitized and gets a hash of the original id appended, so ids that
     * differ only in unsafe characters ({@code a/b} and {@code a_b}) don't end up sharing a file. Never starts with a
     * dot, so a cached image is neither hidden nor a path component like {@code ..}.
     */
    static String fileStem(String imageId) {
        if (imageId.length() <= MAX_STEM_LENGTH && imageId.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]*")) {
            return imageId;
        }
        var sanitized = imageId.replaceAll("[^A-Za-z0-9_.-]", "_").replaceFirst("^\\.", "_");
        if (sanitized.length() > MAX_STEM_LENGTH) {
            sanitized = sanitized.substring(0, MAX_STEM_LENGTH);
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(imageId.getBytes(StandardCharsets.UTF_8));
            return sanitized + "-" + HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every Java platform", e);
        }
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

package io.github.Nyeksenn.grooveboat.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BackendSupportTest {
    @Test
    void normalizeStripsWhitespaceAndTrailingSlashes() {
        assertThat(BackendSupport.normalizeBaseUrl(" https://example.org/music// ")).isEqualTo("https://example.org/music");
    }

    @Test
    void queryStringEncodesValuesAndKeepsOrder() {
        var params = new LinkedHashMap<String, String>();
        params.put("u", "a b");
        params.put("q", "x&y=z");
        assertThat(BackendSupport.queryString(params)).isEqualTo("u=a+b&q=x%26y%3Dz");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost", "https://music.example.org", "http://192.168.1.10:8096", "http://nas:4533/sub",
            "HTTPS://Example.org", "http://[::1]:8096"})
    void acceptsServerAddresses(String url) {
        assertThat(BackendSupport.isValidBaseUrl(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "music.example.org", "ftp://music.example.org", "http://", "http:///path", "http://exa mple.org",
            "http://my_host", "http://host:99999", "file:///etc/passwd", "javascript:alert(1)"})
    void rejectsEverythingElse(String url) {
        assertThat(BackendSupport.isValidBaseUrl(url)).isFalse();
    }

    @Test
    void safeImageIdsKeepTheirName() {
        assertThat(BackendSupport.fileStem("0a1b2c3d4e5f")).isEqualTo("0a1b2c3d4e5f");
        assertThat(BackendSupport.fileStem("al-123")).isEqualTo("al-123");
        assertThat(BackendSupport.fileStem("cover_1.2")).isEqualTo("cover_1.2");
    }

    @Test
    void idsThatDifferOnlyInUnsafeCharactersGetDistinctNames() {
        var slash = BackendSupport.fileStem("a/b");
        var underscore = BackendSupport.fileStem("a_b");
        var colon = BackendSupport.fileStem("a:b");

        assertThat(underscore).isEqualTo("a_b");
        assertThat(slash).startsWith("a_b-").isNotEqualTo(colon);
        assertThat(colon).startsWith("a_b-");
        assertThat(slash).isEqualTo(BackendSupport.fileStem("a/b"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", ".", ".hidden", "../../etc/passwd", "a\\b", "", " "})
    void unsafeIdsNeverEscapeOrHideTheCacheFile(String id) {
        var stem = BackendSupport.fileStem(id);

        assertThat(stem).doesNotStartWith(".").doesNotContain("/").doesNotContain("\\").isNotEmpty();
    }

    @Test
    void veryLongIdsAreShortened() {
        var id = "x".repeat(300);

        assertThat(BackendSupport.fileStem(id).length()).isLessThan(120);
        assertThat(BackendSupport.fileStem(id)).isNotEqualTo(BackendSupport.fileStem(id + "y"));
    }

    @Test
    void writeImageReplacesAtomicallyAndLeavesNoTemporaryFiles(@TempDir Path dir) throws IOException {
        var first = BackendSupport.writeImage(dir, "al/1", "jpeg", new byte[]{1, 2, 3});
        var second = BackendSupport.writeImage(dir, "al/1", "jpeg", new byte[]{4, 5});

        assertThat(second).isEqualTo(first).hasParent(dir);
        assertThat(Files.readAllBytes(second)).containsExactly(4, 5);
        try (var files = Files.list(dir)) {
            assertThat(files).containsExactly(second);
        }
    }

    @Test
    void writeImageSanitizesTheExtension(@TempDir Path dir) throws IOException {
        var path = BackendSupport.writeImage(dir, "x", "svg+xml", new byte[]{1});

        assertThat(path.getFileName().toString()).isEqualTo("x.svgxml");
        assertThat(BackendSupport.writeImage(dir, "y", "/../", new byte[]{1}).getFileName().toString())
                .isEqualTo("y.img");
    }
}

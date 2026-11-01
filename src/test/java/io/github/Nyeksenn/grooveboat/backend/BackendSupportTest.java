package io.github.Nyeksenn.grooveboat.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
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
}

package io.github.Nyeksenn.grooveboat.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

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
}

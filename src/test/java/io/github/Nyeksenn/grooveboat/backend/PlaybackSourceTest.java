package io.github.Nyeksenn.grooveboat.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlaybackSourceTest {
    @Test
    void redactStripsQueryString() {
        assertThat(PlaybackSource.redact("https://host/stream?ApiKey=secret&x=1"))
                .isEqualTo("https://host/stream?<redacted>");
    }

    @Test
    void redactKeepsUrisWithoutQuery() {
        assertThat(PlaybackSource.redact("https://host/stream")).isEqualTo("https://host/stream");
        assertThat(PlaybackSource.redact(null)).isNull();
    }

    @Test
    void toStringNeverContainsCredentials() {
        var source = new PlaybackSource("https://host/stream?ApiKey=secret", true, "session");
        assertThat(source.toString()).doesNotContain("secret").contains("hls=true");
    }
}

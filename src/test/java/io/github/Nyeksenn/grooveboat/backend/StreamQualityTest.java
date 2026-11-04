package io.github.Nyeksenn.grooveboat.backend;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreamQualityTest {
    @Test
    void labelShowsCodecAndBitrate() {
        assertThat(new StreamQuality("flac", 987, false).label()).isEqualTo("FLAC · 987 kbps");
        assertThat(new StreamQuality("mp3", 0, false).label()).isEqualTo("MP3");
    }

    @Test
    void originalWithoutCodecHasNoQuality() {
        assertThat(StreamQuality.original(null, 320)).isNull();
    }

    @Test
    void fileUnderTheCapIsCopied() {
        assertThat(StreamQuality.transcoded("mp3", 128, true, "opus", 192))
                .isEqualTo(new StreamQuality("mp3", 128, false));
    }

    @Test
    void fileOverTheCapIsReencodedAtTheCap() {
        assertThat(StreamQuality.transcoded("flac", 987, true, "opus", 192))
                .isEqualTo(new StreamQuality("opus", 192, true));
    }

    @Test
    void forcedTranscodeOrUnknownBitrateIsReencoded() {
        assertThat(StreamQuality.transcoded("ape", 500, false, "aac", 320).transcoded()).isTrue();
        assertThat(StreamQuality.transcoded("mp3", 0, true, "opus", 192).transcoded()).isTrue();
    }
}

package io.github.Nyeksenn.grooveboat.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AudioFormatsTest {
    @Test
    void knownCodecsMapToMime() {
        assertThat(AudioFormats.mimeForCodec("flac")).isEqualTo("audio/flac");
        assertThat(AudioFormats.mimeForCodec(" MP3 ")).isEqualTo("audio/mpeg");
    }

    @Test
    void pcmVariantsAreWav() {
        assertThat(AudioFormats.mimeForCodec("pcm_s16le")).isEqualTo("audio/wav");
    }

    @Test
    void unknownCodecGetsExperimentalMime() {
        assertThat(AudioFormats.mimeForCodec("dsd")).isEqualTo("audio/x-dsd");
    }

    @Test
    void blankOrNullCodecHasNoMime() {
        assertThat(AudioFormats.mimeForCodec(null)).isNull();
        assertThat(AudioFormats.mimeForCodec("  ")).isNull();
    }

    @Test
    void mimeMapsBackToCodec() {
        assertThat(AudioFormats.codecForMime("audio/mpeg")).isEqualTo("mp3");
        assertThat(AudioFormats.codecForMime("audio/x-flac")).isEqualTo("flac");
        assertThat(AudioFormats.codecForMime("audio/mp3")).isEqualTo("mp3");
        assertThat(AudioFormats.codecForMime("audio/x-dsd")).isEqualTo("dsd");
        assertThat(AudioFormats.codecForMime("video/mp4")).isNull();
        assertThat(AudioFormats.codecForMime(null)).isNull();
    }

    @Test
    void fmp4Copyable() {
        assertThat(AudioFormats.isFmp4Copyable("flac")).isTrue();
        assertThat(AudioFormats.isFmp4Copyable("wma")).isFalse();
        assertThat(AudioFormats.isFmp4Copyable(null)).isFalse();
    }
}

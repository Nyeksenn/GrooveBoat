package io.github.Nyeksenn.grooveboat.audio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AudioSettingsTest {
    @Test
    void trackModeIsNotAlbumMode() {
        assertThat(AudioSettings.isAlbumMode("track")).isFalse();
    }

    @Test
    void albumAndUnknownValuesUseAlbumMode() {
        assertThat(AudioSettings.isAlbumMode("album")).isTrue();
        assertThat(AudioSettings.isAlbumMode("")).isTrue();
    }
}

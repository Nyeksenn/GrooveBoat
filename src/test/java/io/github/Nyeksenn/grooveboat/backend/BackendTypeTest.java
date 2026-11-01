package io.github.Nyeksenn.grooveboat.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BackendTypeTest {
    @Test
    void parsesSettingsValues() {
        assertThat(BackendType.fromSettingsValue("jellyfin")).contains(BackendType.JELLYFIN);
        assertThat(BackendType.fromSettingsValue("opensubsonic")).contains(BackendType.OPENSUBSONIC);
    }

    @Test
    void noneAndGarbageAreEmpty() {
        assertThat(BackendType.fromSettingsValue(BackendType.NONE)).isEmpty();
        assertThat(BackendType.fromSettingsValue("nope")).isEmpty();
        assertThat(BackendType.fromSettingsValue(null)).isEmpty();
    }

    @Test
    void otherSwaps() {
        assertThat(BackendType.JELLYFIN.other()).isEqualTo(BackendType.OPENSUBSONIC);
        assertThat(BackendType.OPENSUBSONIC.other()).isEqualTo(BackendType.JELLYFIN);
    }
}

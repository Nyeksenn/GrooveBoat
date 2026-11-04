package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.audio.TranscodePolicy.Decision;
import io.github.Nyeksenn.grooveboat.audio.TranscodePolicy.Snapshot;
import io.github.Nyeksenn.grooveboat.backend.TranscodeFormat;
import io.github.Nyeksenn.grooveboat.backend.TranscodeProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranscodePolicyTest {
    private static final Snapshot BALANCED = new Snapshot(TranscodeProfile.BALANCED, TranscodeFormat.AAC, true);

    @Test
    void decodableCodecUsesTheActiveProfile() {
        var decision = TranscodePolicy.decide("flac", _ -> true, BALANCED);
        assertThat(decision).isInstanceOfSatisfying(Decision.Play.class, play -> {
            assertThat(play.settings().profile()).isEqualTo(TranscodeProfile.BALANCED);
            assertThat(play.settings().format()).isEqualTo(TranscodeFormat.AAC);
            assertThat(play.settings().allowCopy()).isTrue();
        });
    }

    @Test
    void unknownCodecIsAttempted() {
        assertThat(TranscodePolicy.decide(null, _ -> false, Snapshot.DEFAULT)).isInstanceOf(Decision.Play.class);
    }

    @Test
    void unsupportedCodecIsForceTranscodedAtHighWhenProfileIsOriginal() {
        var decision = TranscodePolicy.decide("ape", _ -> false, Snapshot.DEFAULT);
        assertThat(decision).isInstanceOfSatisfying(Decision.Play.class, play -> {
            assertThat(play.settings().profile()).isEqualTo(TranscodeProfile.HIGH);
            assertThat(play.settings().allowCopy()).isFalse();
        });
    }

    @Test
    void unsupportedCodecKeepsAStricterUserProfile() {
        var decision = TranscodePolicy.decide("ape", _ -> false, BALANCED);
        assertThat(decision).isInstanceOfSatisfying(Decision.Play.class,
                play -> assertThat(play.settings().profile()).isEqualTo(TranscodeProfile.BALANCED));
    }

    @Test
    void unsupportedCodecIsSkippedWhenTranscodingUnsupportedIsOff() {
        var off = new Snapshot(TranscodeProfile.ORIGINAL, TranscodeFormat.AUTO, false);
        assertThat(TranscodePolicy.decide("ape", _ -> false, off)).isEqualTo(new Decision.Skip("ape"));
    }
}

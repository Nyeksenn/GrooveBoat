package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.backend.TranscodeFormat;
import io.github.Nyeksenn.grooveboat.backend.TranscodeProfile;
import io.github.Nyeksenn.grooveboat.backend.TranscodeSettings;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/** Decides, per song, how it is requested from the server or whether it is skipped. Pure logic. */
public final class TranscodePolicy {
    /** The user's streaming preferences that currently apply (the profile already reflects the metered state). */
    public record Snapshot(TranscodeProfile profile, TranscodeFormat format, boolean transcodeUnsupported) {
        public static final Snapshot DEFAULT = new Snapshot(TranscodeProfile.ORIGINAL, TranscodeFormat.AUTO, true);
    }

    public sealed interface Decision {
        /** Play the song, requested with these settings. */
        record Play(TranscodeSettings settings) implements Decision {
        }

        /** The device can't decode this codec and transcoding unsupported formats is off. */
        record Skip(String codec) implements Decision {
        }
    }

    private TranscodePolicy() {
    }

    /**
     * @param codec     the song's codec, or {@code null} when unknown (then playback is simply attempted)
     * @param canDecode whether this device can decode a codec
     */
    public static Decision decide(@Nullable String codec, Predicate<String> canDecode, Snapshot snapshot) {
        if (codec == null || canDecode.test(codec)) {
            return new Decision.Play(new TranscodeSettings(snapshot.profile(), snapshot.format(), true));
        }
        if (!snapshot.transcodeUnsupported()) {
            return new Decision.Skip(codec);
        }
        // The original can't be decoded here, so it must be re-encoded; never below the user's own cap.
        var profile = snapshot.profile().transcodes() ? snapshot.profile() : TranscodeProfile.HIGH;
        return new Decision.Play(new TranscodeSettings(profile, snapshot.format(), false));
    }
}

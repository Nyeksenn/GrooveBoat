package io.github.Nyeksenn.grooveboat.backend;

/**
 * What to ask the server for one song.
 *
 * @param profile   quality cap; {@link TranscodeProfile#ORIGINAL} plays the original file
 * @param format    target codec when the server re-encodes
 * @param allowCopy whether the server may pass the original codec through when it already fits the cap; false when
 *                  the original codec can't be decoded on this device and must be re-encoded
 */
public record TranscodeSettings(TranscodeProfile profile, TranscodeFormat format, boolean allowCopy) {
    public static final TranscodeSettings ORIGINAL =
            new TranscodeSettings(TranscodeProfile.ORIGINAL, TranscodeFormat.AUTO, true);
}

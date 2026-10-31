package io.github.Nyeksenn.grooveboat.audio;

/** What happens when the current song ends. */
public enum RepeatMode {
    NONE,
    TRACK,
    PLAYLIST;

    public RepeatMode cycle() {
        return values()[(ordinal() + 1) % values().length];
    }
}

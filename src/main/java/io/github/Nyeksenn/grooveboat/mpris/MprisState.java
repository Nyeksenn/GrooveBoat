package io.github.Nyeksenn.grooveboat.mpris;

import io.github.Nyeksenn.grooveboat.audio.QueueStore;
import io.github.Nyeksenn.grooveboat.audio.RepeatMode;
import io.github.Nyeksenn.grooveboat.gobjects.RemoteMediaStream;
import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.types.Variant;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable snapshot of everything MPRIS exposes. Built on the GTK main thread and read by D-Bus handler threads,
 * which therefore never touch GTK objects. {@code Position} is not part of it: it changes continuously and is queried
 * on demand.
 */
public record MprisState(
        String status,
        String loop,
        boolean shuffle,
        double volume,
        Map<String, Variant<?>> metadata,
        boolean canGoNext,
        boolean canGoPrevious,
        boolean canPlay,
        List<String> trackIds,
        Map<String, Map<String, Variant<?>>> trackMetadata,
        String currentTrackId
) {
    public static final String NO_TRACK = "/org/mpris/MediaPlayer2/TrackList/NoTrack";

    public static MprisState empty() {
        return new MprisState("Stopped", "None", false, 1.0, noTrackMetadata(), false, false, false,
                List.of(), Map.of(), NO_TRACK);
    }

    private static Map<String, Variant<?>> noTrackMetadata() {
        return Map.of("mpris:trackid", new Variant<>(new DBusPath(NO_TRACK), "o"));
    }

    public static Map<String, Variant<?>> metadataOf(SongListItem item) {
        var map = new LinkedHashMap<String, Variant<?>>();
        map.put("mpris:trackid", new Variant<>(new DBusPath(item.getTrackId()), "o"));
        map.put("mpris:length", new Variant<>(item.getDuration().toNanos() / 1000L, "x"));
        var cover = item.getCoverPath();
        if (cover != null && Files.exists(cover)) {
            map.put("mpris:artUrl", new Variant<>(cover.toUri().toString(), "s"));
        }
        map.put("xesam:title", new Variant<>(item.getSongName(), "s"));
        map.put("xesam:album", new Variant<>(item.getAlbumTitle(), "s"));
        var artist = item.getArtistName();
        map.put("xesam:artist", new Variant<>(artist == null || artist.isBlank() ? List.<String>of() : List.of(artist), "as"));
        // No xesam:url: playback URLs carry credentials.
        return Map.copyOf(map);
    }

    /** The MPRIS {@code LoopStatus} string for a repeat mode. */
    public static String loopStatus(RepeatMode mode) {
        return switch (mode) {
            case NONE -> "None";
            case TRACK -> "Track";
            case PLAYLIST -> "Playlist";
        };
    }

    /** The repeat mode for an MPRIS {@code LoopStatus} string, or empty if the string is not a valid status. */
    public static Optional<RepeatMode> repeatMode(String loopStatus) {
        return Arrays.stream(RepeatMode.values()).filter(mode -> loopStatus(mode).equals(loopStatus)).findFirst();
    }

    /** Must run on the main thread. */
    public static MprisState capture(RemoteMediaStream stream, QueueStore queue) {
        var current = stream.getCurrentItem();
        String status = current == null || !stream.isPrepared() ? "Stopped"
                : stream.getPlaying() ? "Playing" : "Paused";
        var model = queue.getModel();
        var ids = new ArrayList<String>();
        var tracks = new LinkedHashMap<String, Map<String, Variant<?>>>();
        for (int i = 0; i < model.getNItems(); i++) {
            var item = model.getItem(i);
            ids.add(item.getTrackId());
            tracks.put(item.getTrackId(), metadataOf(item));
        }
        var loop = loopStatus(queue.getRepeat());
        double volume = stream.getMuted() ? 0.0 : stream.getVolume();
        return new MprisState(status, loop, queue.isShuffle(), volume,
                current == null ? noTrackMetadata() : metadataOf(current),
                queue.canGoNext(), queue.canGoPrevious(), current != null,
                List.copyOf(ids), tracks, current == null ? NO_TRACK : current.getTrackId());
    }

    // ---- Property maps (name -> value), as served and diffed

    public Map<String, Variant<?>> rootProperties() {
        var m = new LinkedHashMap<String, Variant<?>>();
        m.put("CanQuit", new Variant<>(true));
        m.put("CanRaise", new Variant<>(true));
        m.put("HasTrackList", new Variant<>(true));
        m.put("CanSetFullscreen", new Variant<>(false));
        m.put("Fullscreen", new Variant<>(false));
        m.put("Identity", new Variant<>("GrooveBoat"));
        m.put("DesktopEntry", new Variant<>("io.github.Nyeksenn.grooveboat"));
        m.put("SupportedUriSchemes", new Variant<>(List.<String>of(), "as"));
        m.put("SupportedMimeTypes", new Variant<>(List.<String>of(), "as"));
        return m;
    }

    /** Player properties except {@code Position}. */
    public Map<String, Variant<?>> playerProperties() {
        var m = new LinkedHashMap<String, Variant<?>>();
        m.put("PlaybackStatus", new Variant<>(status));
        m.put("LoopStatus", new Variant<>(loop));
        m.put("Rate", new Variant<>(1.0));
        m.put("MinimumRate", new Variant<>(1.0));
        m.put("MaximumRate", new Variant<>(1.0));
        m.put("Shuffle", new Variant<>(shuffle));
        m.put("Metadata", new Variant<>(metadata, "a{sv}"));
        m.put("Volume", new Variant<>(volume));
        m.put("CanGoNext", new Variant<>(canGoNext));
        m.put("CanGoPrevious", new Variant<>(canGoPrevious));
        m.put("CanPlay", new Variant<>(canPlay));
        m.put("CanPause", new Variant<>(canPlay));
        m.put("CanSeek", new Variant<>(canPlay));
        m.put("CanControl", new Variant<>(true));
        return m;
    }

    public Map<String, Variant<?>> trackListProperties() {
        var m = new LinkedHashMap<String, Variant<?>>();
        m.put("Tracks", new Variant<>(trackIds.stream().map(DBusPath::new).toList(), "ao"));
        m.put("CanEditTracks", new Variant<>(true));
        return m;
    }
}

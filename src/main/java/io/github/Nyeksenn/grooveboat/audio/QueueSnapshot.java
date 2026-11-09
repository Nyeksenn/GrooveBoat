package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.backend.BackendSupport;
import io.github.Nyeksenn.grooveboat.utils.Helpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The play queue as stored between runs: the song ids in queue order and the position of the current song. Song ids
 * belong to one server, so the file is deleted on logout.
 *
 * @param songIds the queued songs, in queue order; a song queued twice appears twice
 * @param index   position of the current song in {@code songIds}
 */
public record QueueSnapshot(List<String> songIds, int index) {
    private static final Logger logger = LoggerFactory.getLogger(QueueSnapshot.class);

    /** Where the queue is kept: next to the database and the cover cache. */
    public static Path defaultPath() {
        return Paths.get(Helpers.getUserDataPath(), "grooveboat", "queue.json");
    }

    public QueueSnapshot {
        songIds = List.copyOf(songIds);
        index = songIds.isEmpty() ? 0 : Math.clamp(index, 0, songIds.size() - 1);
    }

    /**
     * The snapshot without the songs the library no longer has. The current song stays current; if it is gone, the
     * entry that followed it takes over (or the last one, at the end of the queue).
     */
    public QueueSnapshot retain(Set<String> available) {
        if (songIds.isEmpty()) {
            return this;
        }
        var kept = new ArrayList<String>();
        var atOrBefore = -1; // last kept entry at or before the current one
        var after = -1; // first kept entry after the current one
        for (var i = 0; i < songIds.size(); i++) {
            if (!available.contains(songIds.get(i))) {
                continue;
            }
            if (i <= index) {
                atOrBefore = kept.size();
            } else if (after < 0) {
                after = kept.size();
            }
            kept.add(songIds.get(i));
        }
        var currentKept = available.contains(songIds.get(index));
        return new QueueSnapshot(kept, currentKept || after < 0 ? Math.max(atOrBefore, 0) : after);
    }

    /** Writes the snapshot, replacing the previous one atomically; an empty queue removes the file. */
    public static void write(Path file, QueueSnapshot snapshot) throws IOException {
        if (snapshot.songIds().isEmpty()) {
            delete(file);
            return;
        }
        var directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        var temporary = Files.createTempFile(directory, ".queue-", ".tmp");
        try {
            BackendSupport.JSON.writeValue(temporary, snapshot);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** The stored queue, or empty when there is none or it can't be read (a broken file is ignored, not fatal). */
    public static Optional<QueueSnapshot> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            var snapshot = BackendSupport.JSON.readValue(file, QueueSnapshot.class);
            return snapshot == null || snapshot.songIds().isEmpty() ? Optional.empty() : Optional.of(snapshot);
        } catch (JacksonException e) {
            logger.warn("Ignoring unreadable saved queue: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public static void delete(Path file) throws IOException {
        Files.deleteIfExists(file);
    }
}

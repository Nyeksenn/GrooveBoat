package io.github.Nyeksenn.grooveboat.library;

import io.github.Nyeksenn.grooveboat.backend.BackendType;
import io.github.Nyeksenn.grooveboat.backend.MusicBackend;
import io.github.Nyeksenn.grooveboat.backend.PlaybackSource;
import io.github.Nyeksenn.grooveboat.backend.RemoteAlbum;
import io.github.Nyeksenn.grooveboat.backend.RemoteArtist;
import io.github.Nyeksenn.grooveboat.backend.RemoteSong;
import io.github.Nyeksenn.grooveboat.backend.ServerInfo;
import io.github.Nyeksenn.grooveboat.backend.Session;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory {@link MusicBackend} for sync tests. Albums, songs and artists are plain mutable fields. */
final class StubBackend implements MusicBackend {
    final List<RemoteAlbum> albums = new ArrayList<>();
    final Map<String, List<RemoteSong>> songs = new HashMap<>();
    final Map<String, RemoteArtist> artists = new HashMap<>();
    final AtomicInteger albumCalls = new AtomicInteger();
    final AtomicInteger songCalls = new AtomicInteger();
    final AtomicInteger artistCalls = new AtomicInteger();
    final AtomicInteger restoreCalls = new AtomicInteger();
    /** When set, thrown by {@link #restore}. */
    volatile IOException restoreFailure;
    volatile boolean offline;
    /** When set, thrown by the album and song calls, like an unexpected bug would. */
    volatile RuntimeException unexpectedFailure;
    final AtomicBoolean closed = new AtomicBoolean();
    /** When set, {@link #cacheImage} announces itself on {@link #imageEntered} and waits here before failing. */
    volatile CountDownLatch imageGate;
    final CountDownLatch imageEntered = new CountDownLatch(1);

    @Override
    public BackendType type() {
        return BackendType.OPENSUBSONIC;
    }

    @Override
    public String baseUrl() {
        return "http://stub";
    }

    @Override
    public String scheme() {
        return "http";
    }

    @Override
    public String host() {
        return "stub";
    }

    @Override
    public ServerInfo probe() {
        return ServerInfo.compatible("stub", "1");
    }

    @Override
    public Session authenticate(String username, String password) {
        return new Session(username, password, "");
    }

    @Override
    public void restore(Session session) throws IOException {
        restoreCalls.incrementAndGet();
        if (restoreFailure != null) {
            throw restoreFailure;
        }
    }

    @Override
    public void restoreUnverified(Session session) {
    }

    @Override
    public List<RemoteAlbum> albumsNewerThan(Instant since) throws IOException {
        albumCalls.incrementAndGet();
        if (unexpectedFailure != null) {
            throw unexpectedFailure;
        }
        if (offline) {
            throw new IOException("offline");
        }
        return albums.stream().filter(a -> a.created().isAfter(since)).toList();
    }

    @Override
    public List<RemoteSong> songsOfAlbum(String albumId) throws IOException {
        songCalls.incrementAndGet();
        if (unexpectedFailure != null) {
            throw unexpectedFailure;
        }
        if (offline) {
            throw new IOException("offline");
        }
        return songs.getOrDefault(albumId, List.of());
    }

    @Override
    public RemoteArtist artist(String artistId) throws IOException {
        artistCalls.incrementAndGet();
        if (offline) {
            throw new IOException("offline");
        }
        var artist = artists.get(artistId);
        if (artist == null) {
            throw new IOException("unknown artist " + artistId);
        }
        return artist;
    }

    @Override
    public Path cacheImage(String imageId, int size) throws IOException {
        var gate = imageGate;
        if (gate != null) {
            imageEntered.countDown();
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new IOException("no images in tests");
    }

    @Override
    public PlaybackSource playbackSource(String songId, String contentType) {
        return new PlaybackSource("http://stub/" + songId, false, null);
    }

    @Override
    public void releasePlayback(PlaybackSource source) {
    }

    @Override
    public void logout() {
    }

    @Override
    public void close() {
        closed.set(true);
    }
}

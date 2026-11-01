package io.github.Nyeksenn.grooveboat.library;

import static org.assertj.core.api.Assertions.assertThat;

import io.ebean.Database;
import io.github.Nyeksenn.grooveboat.backend.AuthenticationException;
import io.github.Nyeksenn.grooveboat.backend.RemoteAlbum;
import io.github.Nyeksenn.grooveboat.backend.RemoteArtist;
import io.github.Nyeksenn.grooveboat.backend.RemoteArtistRef;
import io.github.Nyeksenn.grooveboat.backend.Session;
import io.github.Nyeksenn.grooveboat.utils.AppSettings;
import io.github.Nyeksenn.grooveboat.utils.Helpers;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StartupSyncTest {
    private static final Session SESSION = new Session("alice", "token", "user-1");
    private static final RemoteArtistRef BAND = new RemoteArtistRef("ar1", "Band");

    private static Database db;

    private final StubBackend backend = new StubBackend();
    private LibrarySync library;

    @BeforeAll
    static void startDatabase() {
        db = TestDatabase.start();
    }

    @AfterAll
    static void stopDatabase() {
        db.shutdown();
    }

    @BeforeEach
    void clean() throws Exception {
        Helpers.truncateDB(db);
        AppSettings.get().setUint64(AppSettings.LAST_SYNC, 0L);
        backend.artists.put("ar1", new RemoteArtist("ar1", "Band", null));
        library = new LibrarySync(backend);
    }

    @AfterEach
    void close() {
        library.close();
    }

    private void serverHasAlbums(int n) {
        for (int i = 0; i < n; i++) {
            backend.albums.add(new RemoteAlbum("al" + i, "Album " + i, 2, 2024, Duration.ofMinutes(8), null,
                    List.of(BAND), Instant.parse("2024-01-01T00:00:00Z").plusSeconds(i)));
        }
    }

    @Test
    void aVerifiedSessionIsSyncedAndReportsTheNewAlbums() {
        serverHasAlbums(3);

        var result = StartupSync.run(backend, SESSION, library);

        assertThat(result).isEqualTo(new StartupSync.Synced(3));
        assertThat(backend.restoreCalls.get()).isEqualTo(1);
    }

    @Test
    void nothingNewIsReportedAsZero() {
        serverHasAlbums(2);
        StartupSync.run(backend, SESSION, library);

        assertThat(StartupSync.run(backend, SESSION, library)).isEqualTo(new StartupSync.Synced(0));
    }

    @Test
    void aSessionThatWasJustEstablishedIsNotVerifiedAgain() {
        serverHasAlbums(1);

        var result = StartupSync.run(backend, null, library);

        assertThat(result).isEqualTo(new StartupSync.Synced(1));
        assertThat(backend.restoreCalls.get()).isZero();
    }

    @Test
    void aRejectedLoginIsReportedAndNothingIsSynced() {
        serverHasAlbums(2);
        backend.restoreFailure = new AuthenticationException("revoked");

        var result = StartupSync.run(backend, SESSION, library);

        assertThat(result).isInstanceOf(StartupSync.SessionExpired.class);
        assertThat(backend.albumCalls.get()).isZero();
    }

    @Test
    void anUnreachableServerIsReportedAndNothingIsSynced() {
        serverHasAlbums(2);
        backend.restoreFailure = new IOException("connection refused");

        var result = StartupSync.run(backend, SESSION, library);

        assertThat(result).isInstanceOf(StartupSync.Offline.class);
        assertThat(backend.albumCalls.get()).isZero();
    }
}

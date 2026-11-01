package io.github.Nyeksenn.grooveboat.library;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import io.ebean.Database;
import io.github.Nyeksenn.grooveboat.backend.RemoteAlbum;
import io.github.Nyeksenn.grooveboat.backend.RemoteArtist;
import io.github.Nyeksenn.grooveboat.backend.RemoteArtistRef;
import io.github.Nyeksenn.grooveboat.backend.RemoteSong;
import io.github.Nyeksenn.grooveboat.entities.AlbumModel;
import io.github.Nyeksenn.grooveboat.entities.ArtistModel;
import io.github.Nyeksenn.grooveboat.entities.SongModel;
import io.github.Nyeksenn.grooveboat.utils.AppSettings;
import io.github.Nyeksenn.grooveboat.utils.Helpers;
import io.github.Nyeksenn.grooveboat.utils.LogCapture;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LibrarySyncTest {
    private static final Instant T1 = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2024-02-01T00:00:00Z");
    private static final RemoteArtistRef BAND = new RemoteArtistRef("ar1", "Band");

    private static Database db;

    private final StubBackend backend = new StubBackend();
    private LibrarySync sync;

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
        sync = new LibrarySync(backend);
    }

    @AfterEach
    void close() {
        sync.close();
    }

    private static RemoteAlbum album(String id, String title, Instant created) {
        return new RemoteAlbum(id, title, 2, 2024, Duration.ofMinutes(8), null, List.of(BAND), created);
    }

    private static RemoteSong song(String id, String albumId, String title, int track) {
        return new RemoteSong(id, albumId, title, track, 2024, Duration.ofMinutes(4), 320, "audio/mpeg", 1,
                List.of(BAND));
    }

    private long lastSync() {
        return AppSettings.get().getUint64(AppSettings.LAST_SYNC);
    }

    @Test
    void refreshStoresAlbumsWithTheirArtistsAndRemembersTheNewest() {
        backend.albums.add(album("al2", "Second", T2));
        backend.albums.add(album("al1", "First", T1));

        sync.refresh(false);

        var albums = db.find(AlbumModel.class).findList();
        assertThat(albums).extracting(AlbumModel::getId).containsExactlyInAnyOrder("al1", "al2");
        assertThat(db.find(ArtistModel.class).findList()).extracting(ArtistModel::getId).containsExactly("ar1");
        assertThat(lastSync()).isEqualTo(T2.getEpochSecond());
    }

    @Test
    void secondRefreshOnlyFetchesNewAlbumsAndNeverDuplicates() {
        backend.albums.add(album("al1", "First", T1));
        sync.refresh(false);

        backend.albums.add(album("al2", "Second", T2));
        sync.refresh(false);

        assertThat(db.find(AlbumModel.class).findCount()).isEqualTo(2);
        assertThat(lastSync()).isEqualTo(T2.getEpochSecond());
    }

    @Test
    void refreshingAnUnchangedLibraryChangesNothing() {
        backend.albums.add(album("al1", "First", T1));
        sync.refresh(false);
        sync.refresh(false);

        assertThat(db.find(AlbumModel.class).findCount()).isEqualTo(1);
        assertThat(lastSync()).isEqualTo(T1.getEpochSecond());
    }

    @Test
    void fullRefreshReplacesTheCache() {
        backend.albums.add(album("al1", "First", T1));
        sync.refresh(false);

        backend.albums.clear();
        backend.albums.add(album("al9", "Replacement", T2));
        sync.refresh(true);

        assertThat(db.find(AlbumModel.class).findList()).extracting(AlbumModel::getId).containsExactly("al9");
    }

    @Test
    void failedFullRefreshKeepsTheCache() {
        backend.albums.add(album("al1", "First", T1));
        sync.refresh(false);

        backend.offline = true;
        sync.refresh(true);

        assertThat(db.find(AlbumModel.class).findCount()).isEqualTo(1);
        assertThat(lastSync()).isEqualTo(T1.getEpochSecond());
    }

    @Test
    void overlongTitlesAreTruncatedToTheColumnSize() {
        backend.albums.add(album("al1", "x".repeat(300), T1));
        sync.refresh(false);

        assertThat(db.find(AlbumModel.class).findOne().getTitle()).hasSize(100);
    }

    @Test
    void refreshReportsHowManyAlbumsWereNew() {
        backend.albums.add(album("al1", "First", T1));
        backend.albums.add(album("al2", "Second", T2));

        assertThat(sync.refresh(false)).isEqualTo(2);
        assertThat(sync.refresh(false)).isZero();

        backend.albums.add(album("al3", "Third", T2.plusSeconds(60)));
        assertThat(sync.refresh(false)).isEqualTo(1);
    }

    @Test
    void refreshReportsZeroWhenTheSyncFailsOrIsCancelled() {
        backend.albums.add(album("al1", "First", T1));
        backend.offline = true;
        assertThat(sync.refresh(false)).isZero();

        backend.offline = false;
        sync.cancelAndAwait(Duration.ofSeconds(1));
        assertThat(sync.refresh(false)).isZero();
    }

    @Test
    void anArtistWithoutImageIsNotAskedForAgainOnEveryRefresh() {
        backend.albums.add(album("al1", "First", T1));
        sync.refresh(false);
        assertThat(backend.artistCalls.get()).isEqualTo(1);
        assertThat(db.find(ArtistModel.class).findOne().isImageChecked()).isTrue();

        sync.refresh(false);
        sync.refresh(false);

        assertThat(backend.artistCalls.get()).isEqualTo(1);
    }

    @Test
    void newAlbumsOnlyRefreshTheirOwnArtistsNotTheWholeLibrary() {
        backend.artists.put("ar2", new RemoteArtist("ar2", "Other", null));
        backend.albums.add(album("al1", "First", T1));
        sync.refresh(false);
        assertThat(backend.artistCalls.get()).isEqualTo(1);

        backend.albums.add(new RemoteAlbum("al2", "Second", 2, 2024, Duration.ofMinutes(8), null,
                List.of(new RemoteArtistRef("ar2", "Other")), T2));
        assertThat(sync.refresh(false)).isEqualTo(1);

        // only ar2 was asked for; ar1 was checked before and its album is not new
        assertThat(backend.artistCalls.get()).isEqualTo(2);
        assertThat(db.find(ArtistModel.class).findCount()).isEqualTo(2);
    }

    @Test
    void anArtistWhoseImageFailedToDownloadIsAskedForAgain() {
        backend.artists.put("ar1", new RemoteArtist("ar1", "Band", "img"));   // the stub can't download images
        backend.albums.add(album("al1", "First", T1));
        sync.refresh(false);
        assertThat(backend.artistCalls.get()).isEqualTo(1);
        assertThat(db.find(ArtistModel.class).findOne().isImageChecked()).isFalse();

        sync.refresh(false);

        assertThat(backend.artistCalls.get()).isEqualTo(2);
    }

    @Test
    void cancelledSyncDoesNothing() {
        backend.albums.add(album("al1", "First", T1));
        assertThat(sync.cancelAndAwait(Duration.ofSeconds(1))).isTrue();

        sync.refresh(false);

        assertThat(db.find(AlbumModel.class).findCount()).isZero();
        assertThat(backend.albumCalls.get()).isZero();
    }

    @Test
    void manyAlbumsSharingAnArtistAreSyncedConcurrentlyWithoutDuplicates() {
        for (int i = 0; i < 60; i++) {
            backend.albums.add(album("al" + i, "Album " + i, T1.plusSeconds(i)));
        }

        sync.refresh(false);

        assertThat(db.find(AlbumModel.class).findCount()).isEqualTo(60);
        assertThat(db.find(ArtistModel.class).findCount()).isEqualTo(1);
        assertThat(lastSync()).isEqualTo(T1.plusSeconds(59).getEpochSecond());
    }

    @Test
    void cancellingWhileAlbumTasksAreRunningSavesNothing() throws Exception {
        backend.imageGate = new CountDownLatch(1);
        backend.albums.add(new RemoteAlbum("al1", "First", 2, 2024, Duration.ofMinutes(8), "img", List.of(BAND), T1));
        var refresh = new Thread(() -> sync.refresh(false));
        refresh.start();
        assertThat(backend.imageEntered.await(10, TimeUnit.SECONDS)).isTrue();

        var stopper = new Thread(() -> sync.cancelAndAwait(Duration.ofSeconds(10)));
        stopper.start();
        Thread.sleep(100);
        backend.imageGate.countDown();
        refresh.join(10_000);
        stopper.join(10_000);

        assertThat(refresh.isAlive()).isFalse();
        assertThat(db.find(AlbumModel.class).findCount()).isZero();
        assertThat(lastSync()).isZero();
    }

    @Test
    void getSongsFetchesStoresAndThenServesFromTheCache() {
        backend.albums.add(album("al1", "First", T1));
        backend.songs.put("al1", List.of(song("s2", "al1", "Two", 2), song("s1", "al1", "One", 1)));
        sync.refresh(false);

        var first = sync.getSongs("al1");
        assertThat(first).extracting(SongModel::getId).containsExactlyInAnyOrder("s1", "s2");
        assertThat(db.find(SongModel.class).findCount()).isEqualTo(2);

        var second = sync.getSongs("al1");
        assertThat(second).extracting(SongModel::getTitle).containsExactly("One", "Two");
        assertThat(db.find(SongModel.class).findCount()).isEqualTo(2);
    }

    @Test
    void getSongsUpdatesChangedSongsInPlaceWhenTheServerListGrows() throws Exception {
        backend.albums.add(album("al1", "First", T1));
        backend.songs.put("al1", List.of(song("s1", "al1", "One", 1), song("s2", "al1", "Two", 2)));
        sync.refresh(false);
        sync.getSongs("al1");

        backend.songs.put("al1", List.of(song("s1", "al1", "One (Remastered)", 1), song("s2", "al1", "Two", 2),
                song("s3", "al1", "Three", 3)));
        // The next start: the cache is complete, so the old list comes back at once and the server is checked in the
        // background.
        List<SongModel> updated;
        try (var nextSession = new LibrarySync(backend)) {
            assertThat(nextSession.getSongs("al1")).hasSize(2);
            awaitSongRows(3);
            updated = nextSession.getSongs("al1");
        }
        assertThat(updated).extracting(SongModel::getTitle)
                .containsExactlyInAnyOrder("One (Remastered)", "Two", "Three");
        assertThat(db.find(SongModel.class).findCount()).isEqualTo(3);
        assertThat(db.find(SongModel.class).where().idEq("s1").findOne().getTitle()).isEqualTo("One (Remastered)");
        assertThat(db.find(AlbumModel.class).where().idEq("al1").findOne().getSongCount()).isEqualTo(3);
    }

    private void awaitSongRows(int expected) throws InterruptedException {
        for (int i = 0; i < 200 && db.find(SongModel.class).findCount() != expected; i++) {
            Thread.sleep(25);
        }
        assertThat(db.find(SongModel.class).findCount()).isEqualTo(expected);
    }

    @Test
    void aCompleteCacheIsAnsweredWithoutWaitingForTheServer() throws Exception {
        backend.albums.add(album("al1", "First", T1));
        backend.songs.put("al1", List.of(song("s1", "al1", "One", 1), song("s2", "al1", "Two", 2)));
        sync.refresh(false);
        sync.getSongs("al1");
        assertThat(backend.songCalls.get()).isEqualTo(1);

        backend.offline = true;
        var again = sync.getSongs("al1");

        assertThat(again).extracting(SongModel::getId).containsExactly("s1", "s2");
        assertThat(backend.songCalls.get()).isEqualTo(1);
    }

    @Test
    void anAlbumIsComparedWithTheServerOnlyOncePerSession() throws Exception {
        backend.albums.add(album("al1", "First", T1));
        backend.songs.put("al1", List.of(song("s1", "al1", "One", 1), song("s2", "al1", "Two", 2)));
        sync.refresh(false);
        sync.getSongs("al1");
        // a second sync object: a new session with a complete cache but nothing compared yet
        try (var next = new LibrarySync(backend)) {
            next.getSongs("al1");
            for (int i = 0; i < 200 && backend.songCalls.get() < 2; i++) {
                Thread.sleep(25);
            }
            next.getSongs("al1");
            next.getSongs("al1");
            Thread.sleep(150);
        }
        assertThat(backend.songCalls.get()).isEqualTo(2);
    }

    @Test
    void anIncompleteCacheIsCompletedFromTheServerBeforeReturning() {
        backend.albums.add(album("al1", "First", T1));
        backend.songs.put("al1", List.of(song("s1", "al1", "One", 1), song("s2", "al1", "Two", 2)));
        sync.refresh(false);

        assertThat(sync.getSongs("al1")).hasSize(2);
        assertThat(backend.songCalls.get()).isEqualTo(1);
    }

    @Test
    void getSongsTruncatesOverlongColumns() {
        backend.albums.add(album("al1", "First", T1));
        backend.songs.put("al1", List.of(new RemoteSong("s1", "al1", "t".repeat(300), 1, 2024, Duration.ofMinutes(4),
                320, "c".repeat(80), 1, List.of(BAND))));
        sync.refresh(false);

        var saved = sync.getSongs("al1").getFirst();

        assertThat(saved.getTitle()).hasSize(200);
        assertThat(saved.getContentType()).hasSize(50);
    }

    @Test
    void artistNamesAreTruncatedToTheColumnSize() {
        backend.artists.put("long", new RemoteArtist("long", "n".repeat(300), null));
        backend.albums.add(new RemoteAlbum("al1", "First", 1, 2024, Duration.ofMinutes(4), null,
                List.of(new RemoteArtistRef("long", "n".repeat(300))), T1));

        sync.refresh(false);

        assertThat(db.find(ArtistModel.class).where().idEq("long").findOne().getName()).hasSize(200);
    }

    @Test
    void cancelledGetSongsIsNotLoggedAsAnError() {
        sync.cancelAndAwait(Duration.ofSeconds(1));
        try (var log = new LogCapture(LibrarySync.class)) {
            assertThat(sync.getSongs("al1")).isEmpty();

            assertThat(log.events(Level.ERROR)).isEmpty();
            assertThat(log.events(Level.WARN)).isEmpty();
        }
    }

    @Test
    void anUnexpectedFailureWhileLoadingSongsIsLoggedAsAnErrorWithItsStackTrace() {
        backend.unexpectedFailure = new IllegalStateException("bug");
        try (var log = new LogCapture(LibrarySync.class)) {
            assertThat(sync.getSongs("al1")).isEmpty();

            assertThat(log.events(Level.ERROR)).hasSize(1);
            assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy().getMessage()).isEqualTo("bug");
        }
    }

    @Test
    void anUnexpectedFailureDuringRefreshIsLoggedAsAnErrorAndDoesNotEscape() {
        backend.unexpectedFailure = new IllegalStateException("bug");
        try (var log = new LogCapture(LibrarySync.class)) {
            sync.refresh(false);

            assertThat(log.events(Level.ERROR)).hasSize(1);
            assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy().getMessage()).isEqualTo("bug");
        }
    }

    @Test
    void anUnreachableServerIsOnlyAWarning() {
        backend.offline = true;
        try (var log = new LogCapture(LibrarySync.class)) {
            sync.refresh(false);
            sync.getSongs("al1");

            assertThat(log.events(Level.ERROR)).isEmpty();
            assertThat(log.events(Level.WARN)).hasSize(2);
        }
    }

    @Test
    void getSongsFallsBackToTheCacheWhenOffline() {
        backend.albums.add(album("al1", "First", T1));
        backend.songs.put("al1", List.of(song("s1", "al1", "One", 1)));
        sync.refresh(false);
        sync.getSongs("al1");

        backend.offline = true;

        assertThat(sync.getSongs("al1")).extracting(SongModel::getId).containsExactly("s1");
    }

    @Test
    void getSongsOfAnUnknownAlbumWhileOfflineIsEmpty() {
        backend.offline = true;
        assertThat(sync.getSongs("nope")).isEmpty();
    }
}

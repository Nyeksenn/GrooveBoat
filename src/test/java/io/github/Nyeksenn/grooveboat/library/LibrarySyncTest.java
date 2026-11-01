package io.github.Nyeksenn.grooveboat.library;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    void cancelledSyncDoesNothing() {
        backend.albums.add(album("al1", "First", T1));
        assertThat(sync.cancelAndAwait(Duration.ofSeconds(1))).isTrue();

        sync.refresh(false);

        assertThat(db.find(AlbumModel.class).findCount()).isZero();
        assertThat(backend.albumCalls.get()).isZero();
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

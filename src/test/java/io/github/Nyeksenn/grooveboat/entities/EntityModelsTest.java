package io.github.Nyeksenn.grooveboat.entities;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.Nyeksenn.grooveboat.backend.RemoteAlbum;
import io.github.Nyeksenn.grooveboat.backend.RemoteArtistRef;
import io.github.Nyeksenn.grooveboat.backend.RemoteSong;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntityModelsTest {
    private static final RemoteArtistRef REF_A = new RemoteArtistRef("ar1", "Alpha");
    private static final RemoteArtistRef REF_B = new RemoteArtistRef("ar2", "Beta");

    private static RemoteSong remoteSong(String title) {
        return new RemoteSong("s1", "al1", title, 3, 2020, Duration.ofSeconds(200), 256, "audio/flac", 2,
                List.of(REF_A));
    }

    private static SongModel song(String title, List<ArtistModel> artists, AlbumModel album) {
        return SongModel.from(remoteSong(title), artists, album);
    }

    private final ArtistModel alpha = ArtistModel.from(REF_A);
    private final ArtistModel beta = ArtistModel.from(REF_B);
    private final AlbumModel album = AlbumModel.from(
            new RemoteAlbum("al1", "Album", 5, 2020, Duration.ofMinutes(30), null, List.of(REF_A, REF_B), Instant.EPOCH),
            null, List.of(alpha, beta));

    @Test
    void artistFromRefCopiesIdAndTruncatesTheName() {
        var artist = ArtistModel.from(new RemoteArtistRef("x", "n".repeat(300)));
        assertThat(artist.getId()).isEqualTo("x");
        assertThat(artist.getName()).hasSize(ArtistModel.NAME_LENGTH);
    }

    @Test
    void clampNameKeepsShortNamesAndNull() {
        assertThat(ArtistModel.clampName("Band")).isEqualTo("Band");
        assertThat(ArtistModel.clampName(null)).isNull();
    }

    @Test
    void albumFromRemoteCopiesFieldsAndTruncatesTheTitle() {
        var remote = new RemoteAlbum("al9", "t".repeat(150), 12, 1999, Duration.ofMinutes(45), "img", List.of(REF_A),
                Instant.EPOCH);
        var cover = Path.of("/covers/al9.jpeg");

        var built = AlbumModel.from(remote, cover, List.of(alpha));

        assertThat(built.getId()).isEqualTo("al9");
        assertThat(built.getTitle()).hasSize(AlbumModel.TITLE_LENGTH);
        assertThat(built.getSongCount()).isEqualTo(12);
        assertThat(built.getRelease_year()).isEqualTo(1999);
        assertThat(built.getDuration()).isEqualTo(Duration.ofMinutes(45));
        assertThat(built.getCoverPath()).isEqualTo(cover);
        assertThat(built.getAlbumArtists()).containsExactly(alpha);
    }

    @Test
    void songFromRemoteCopiesFieldsAndTruncatesColumns() {
        var remote = new RemoteSong("s1", "al1", "t".repeat(300), 3, 2020, Duration.ofSeconds(200), 256,
                "c".repeat(80), 2, List.of(REF_A));

        var built = SongModel.from(remote, List.of(alpha), album);

        assertThat(built.getId()).isEqualTo("s1");
        assertThat(built.getTitle()).hasSize(SongModel.TITLE_LENGTH);
        assertThat(built.getContentType()).hasSize(SongModel.CONTENT_TYPE_LENGTH);
        assertThat(built.getTrack()).isEqualTo(3);
        assertThat(built.getReleaseYear()).isEqualTo(2020);
        assertThat(built.getDuration()).isEqualTo(Duration.ofSeconds(200));
        assertThat(built.getBitRate()).isEqualTo(256);
        assertThat(built.getDiscNumber()).isEqualTo(2);
        assertThat(built.getSongArtists()).containsExactly(alpha);
        assertThat(built.getAlbum()).isSameAs(album);
    }

    @Test
    void songWithoutContentTypeStaysWithout() {
        var remote = new RemoteSong("s1", "al1", "T", 1, 2020, Duration.ZERO, 0, null, 1, List.of());
        assertThat(SongModel.from(remote, List.of(), album).getContentType()).isNull();
    }

    @Test
    void displayArtistsUseTheirOwnSeparators() {
        assertThat(song("T", List.of(alpha, beta), album).getDisplayArtist()).isEqualTo("Alpha & Beta");
        assertThat(album.getDisplayArtist()).isEqualTo("Alpha; Beta");
        assertThat(song("T", List.of(), album).getDisplayArtist()).isEmpty();
    }

    @Test
    void sameContentIgnoresArtistObjectIdentity() {
        var first = song("T", List.of(alpha), album);
        var second = song("T", List.of(ArtistModel.from(REF_A)), album);
        assertThat(first.hasSameContent(second)).isTrue();
    }

    @Test
    void sameContentSeesEveryChangedField() {
        var base = song("T", List.of(alpha), album);

        assertThat(base.hasSameContent(song("Other", List.of(alpha), album))).isFalse();
        assertThat(base.hasSameContent(song("T", List.of(alpha, beta), album))).isFalse();
        assertThat(base.hasSameContent(song("T", List.of(beta), album))).isFalse();
        var otherAlbum = AlbumModel.from(
                new RemoteAlbum("al2", "Other", 1, 2020, Duration.ZERO, null, List.of(), Instant.EPOCH), null, List.of());
        assertThat(base.hasSameContent(song("T", List.of(alpha), otherAlbum))).isFalse();
        assertThat(base.hasSameContent(song("T", List.of(alpha), null))).isFalse();

        var moved = song("T", List.of(alpha), album);
        moved.setTrack(4);
        assertThat(base.hasSameContent(moved)).isFalse();
        moved = song("T", List.of(alpha), album);
        moved.setBitRate(128);
        assertThat(base.hasSameContent(moved)).isFalse();
        moved = song("T", List.of(alpha), album);
        moved.setContentType("audio/mpeg");
        assertThat(base.hasSameContent(moved)).isFalse();
    }

    @Test
    void entitiesAreEqualWhenTheirIdsAre() {
        var renamed = song("Renamed", List.of(beta), album);
        var original = song("T", List.of(alpha), album);
        assertThat(renamed).isEqualTo(original).hasSameHashCodeAs(original);

        var other = SongModel.from(new RemoteSong("s2", "al1", "T", 3, 2020, Duration.ZERO, 0, null, 1, List.of()),
                List.of(), album);
        assertThat(original).isNotEqualTo(other);

        assertThat(alpha).isEqualTo(ArtistModel.from(new RemoteArtistRef("ar1", "Different name")));
        assertThat(alpha).isNotEqualTo(beta);
        assertThat(album).isNotEqualTo(alpha);
    }

    @Test
    void hashingDoesNotChangeWhenFieldsDo() {
        var set = new HashSet<SongModel>();
        var s = song("T", List.of(alpha), album);
        set.add(s);
        s.setTitle("Changed");
        s.setTrack(9);
        assertThat(set).contains(s);
    }
}

package io.github.Nyeksenn.grooveboat.gobjects;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.Nyeksenn.grooveboat.backend.RemoteAlbum;
import io.github.Nyeksenn.grooveboat.backend.RemoteArtistRef;
import io.github.Nyeksenn.grooveboat.backend.RemoteSong;
import io.github.Nyeksenn.grooveboat.entities.AlbumModel;
import io.github.Nyeksenn.grooveboat.entities.ArtistModel;
import io.github.Nyeksenn.grooveboat.entities.SongModel;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListItemFactoriesTest {
    private static final RemoteArtistRef REF = new RemoteArtistRef("ar1", "Band");

    private final ArtistModel artist = ArtistModel.from(REF);
    private final AlbumModel album = AlbumModel.from(
            new RemoteAlbum("al1", "The Album", 2, 2020, Duration.ofMinutes(8), null, List.of(REF), Instant.EPOCH),
            Path.of("/covers/al1.jpeg"), List.of(artist));
    private final SongModel song = SongModel.from(
            new RemoteSong("s1", "al1", "The Song", 1, 2020, Duration.ofSeconds(125), 320, "audio/mpeg", 1,
                    List.of(REF)),
            List.of(artist), album);

    @Test
    void songItemCarriesSongAndAlbumData() {
        var item = SongListItem.of(song, album, null);

        assertThat(item.getSongId()).isEqualTo("s1");
        assertThat(item.getSongName()).isEqualTo("The Song");
        assertThat(item.getArtistName()).isEqualTo("Band");
        assertThat(item.getAlbumTitle()).isEqualTo("The Album");
        assertThat(item.getAlbumId()).isEqualTo("al1");
        assertThat(item.getCoverPath()).isEqualTo(Path.of("/covers/al1.jpeg"));
        assertThat(item.getContentType()).isEqualTo("audio/mpeg");
        assertThat(item.getDurationString()).isEqualTo("02:05");
        assertThat(item.getCoverArt()).isNull();
    }

    @Test
    void everySongItemGetsItsOwnTrackId() {
        assertThat(SongListItem.of(song, album, null).getTrackId())
                .isNotEqualTo(SongListItem.of(song, album, null).getTrackId());
    }

    @Test
    void albumItemCarriesAlbumData() {
        var item = AlbumListItem.of(album, null);

        assertThat(item.getAlbumId()).isEqualTo("al1");
        assertThat(item.getAlbumName()).isEqualTo("The Album");
        assertThat(item.getAlbumArtist()).isEqualTo("Band");
        assertThat(item.getPictureData()).isNull();
    }
}

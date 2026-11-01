package io.github.Nyeksenn.grooveboat.backend.jellyfin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.Nyeksenn.grooveboat.backend.AuthenticationException;
import io.github.Nyeksenn.grooveboat.backend.BackendType;
import io.github.Nyeksenn.grooveboat.backend.FakeServer;
import io.github.Nyeksenn.grooveboat.backend.FakeServer.Reply;
import io.github.Nyeksenn.grooveboat.backend.PlaybackSource;
import io.github.Nyeksenn.grooveboat.backend.RemoteArtistRef;
import io.github.Nyeksenn.grooveboat.backend.ServerInfo;
import io.github.Nyeksenn.grooveboat.backend.Session;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class JellyfinBackendTest {
    private static final Session SESSION = new Session("alice", "tok3n", "user-1");

    private static JellyfinBackend backend(String url) {
        return new JellyfinBackend(url, "device-1", "Test Box", "1.0");
    }

    private static JellyfinBackend loggedIn(String url) throws Exception {
        var backend = backend(url);
        backend.restoreUnverified(SESSION);
        return backend;
    }

    @Test
    void exposesUrlParts() {
        var backend = backend("http://jf.example.org:8096/");
        assertThat(backend.type()).isEqualTo(BackendType.JELLYFIN);
        assertThat(backend.baseUrl()).isEqualTo("http://jf.example.org:8096");
        assertThat(backend.scheme()).isEqualTo("http");
        assertThat(backend.host()).isEqualTo("jf.example.org:8096");
    }

    @Test
    void urlWithoutSchemeIsRejected() {
        assertThatThrownBy(() -> backend("jf.example.org")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void probeAcceptsRecentJellyfin() throws Exception {
        var body = "{\"ProductName\":\"Jellyfin Server\",\"Version\":\"10.10.3\",\"ServerName\":\"Home\"}";
        try (var server = new FakeServer((m, p, q) -> Reply.json(body))) {
            assertThat(backend(server.baseUrl()).probe()).isEqualTo(new ServerInfo.Compatible("Home", "10.10.3"));
            assertThat(server.requests()).containsExactly("GET /System/Info/Public");
        }
    }

    @Test
    void probeRejectsOldJellyfin() throws Exception {
        var body = "{\"ProductName\":\"Jellyfin Server\",\"Version\":\"10.8.13\"}";
        try (var server = new FakeServer((m, p, q) -> Reply.json(body))) {
            assertThat(backend(server.baseUrl()).probe()).isInstanceOf(ServerInfo.Incompatible.class);
        }
    }

    @Test
    void probeRejectsOtherProductsAndErrors() throws Exception {
        try (var other = new FakeServer((m, p, q) -> Reply.json("{\"ProductName\":\"Emby\",\"Version\":\"4.8\"}"));
             var missing = new FakeServer((m, p, q) -> Reply.status(404));
             var garbage = new FakeServer((m, p, q) -> Reply.json("<html>"))) {
            assertThat(backend(other.baseUrl()).probe()).isInstanceOf(ServerInfo.Incompatible.class);
            assertThat(backend(missing.baseUrl()).probe()).isInstanceOf(ServerInfo.Incompatible.class);
            assertThat(backend(garbage.baseUrl()).probe()).isInstanceOf(ServerInfo.Incompatible.class);
        }
    }

    @Test
    void authenticateStoresTheTokenAndSendsDeviceHeader() throws Exception {
        var body = "{\"AccessToken\":\"abc\",\"User\":{\"Id\":\"user-9\"}}";
        try (var server = new FakeServer((m, p, q) -> Reply.json(body))) {
            var session = backend(server.baseUrl()).authenticate("alice", "pw");
            assertThat(session).isEqualTo(new Session("alice", "abc", "user-9"));
            assertThat(server.requests()).containsExactly("POST /Users/AuthenticateByName");
            assertThat(server.authorizations().getFirst())
                    .startsWith("MediaBrowser ")
                    .contains("Client=\"GrooveBoat\"", "Device=\"Test Box\"", "DeviceId=\"device-1\"")
                    .doesNotContain("Token=");
        }
    }

    @Test
    void incompleteLoginResponseIsAnIoError() throws Exception {
        try (var server = new FakeServer((m, p, q) -> Reply.json("{\"AccessToken\":\"abc\"}"))) {
            assertThatThrownBy(() -> backend(server.baseUrl()).authenticate("alice", "pw"))
                    .isInstanceOf(IOException.class)
                    .isNotInstanceOf(AuthenticationException.class);
        }
    }

    @Test
    void rejectedCredentialsAreAnAuthenticationError() throws Exception {
        try (var server = new FakeServer((m, p, q) -> Reply.status(401))) {
            assertThatThrownBy(() -> backend(server.baseUrl()).authenticate("alice", "bad"))
                    .isInstanceOf(AuthenticationException.class);
        }
    }

    @Test
    void serverErrorsAreAPlainIoError() throws Exception {
        try (var server = new FakeServer((m, p, q) -> Reply.status(500))) {
            assertThatThrownBy(() -> backend(server.baseUrl()).authenticate("alice", "pw"))
                    .isInstanceOf(IOException.class)
                    .isNotInstanceOf(AuthenticationException.class);
        }
    }

    @Test
    void restoreVerifiesTheStoredSession() throws Exception {
        try (var server = new FakeServer((m, p, q) -> Reply.json("{}"))) {
            backend(server.baseUrl()).restore(SESSION);
            assertThat(server.requests()).containsExactly("GET /Users/user-1");
            assertThat(server.authorizations().getFirst()).contains("Token=\"tok3n\"");
        }
    }

    @Test
    void restoreWithRevokedTokenIsAnAuthenticationError() throws Exception {
        try (var server = new FakeServer((m, p, q) -> Reply.status(401))) {
            assertThatThrownBy(() -> backend(server.baseUrl()).restore(SESSION))
                    .isInstanceOf(AuthenticationException.class);
        }
    }

    @Test
    void failedRestoreDoesNotAdoptTheSession() throws Exception {
        try (var server = new FakeServer((m, p, q) -> Reply.status(401))) {
            var backend = backend(server.baseUrl());
            assertThatThrownBy(() -> backend.restore(SESSION)).isInstanceOf(AuthenticationException.class);
            assertThatThrownBy(() -> backend.albumsNewerThan(Instant.EPOCH)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void interruptedRequestKeepsTheInterruptFlag() throws Exception {
        try (var server = new FakeServer((m, p, q) -> Reply.json("{}"))) {
            var backend = backend(server.baseUrl());
            Thread.currentThread().interrupt();
            try {
                assertThatThrownBy(backend::probe).isInstanceOf(IOException.class);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void restoreNeedsTokenAndUserId() {
        var backend = backend("http://127.0.0.1:1");
        assertThatThrownBy(() -> backend.restoreUnverified(new Session("alice", null, "user-1")))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> backend.restoreUnverified(new Session("alice", "tok", "")))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void libraryCallsNeedASession() {
        assertThatThrownBy(() -> backend("http://127.0.0.1:1").albumsNewerThan(Instant.EPOCH))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mapsAlbumsAndStopsAtAlbumsAlreadySynced() throws Exception {
        var body = "{\"Items\":["
                + "{\"Id\":\"al2\",\"Name\":\"New\",\"ChildCount\":9,\"ProductionYear\":2024,"
                + "\"RunTimeTicks\":24000000000,\"DateCreated\":\"2024-05-01T10:00:00.0000000Z\","
                + "\"ImageTags\":{\"Primary\":\"x\"},\"AlbumArtists\":[{\"Id\":\"ar1\",\"Name\":\"Band\"}]},"
                + "{\"Id\":\"al1\",\"Name\":\"Old\",\"DateCreated\":\"2020-01-01T00:00:00.0000000Z\"}]}";
        try (var server = new FakeServer((m, p, q) -> Reply.json(body))) {
            var result = loggedIn(server.baseUrl()).albumsNewerThan(Instant.parse("2023-01-01T00:00:00Z"));
            assertThat(result).hasSize(1);
            var album = result.getFirst();
            assertThat(album.id()).isEqualTo("al2");
            assertThat(album.title()).isEqualTo("New");
            assertThat(album.songCount()).isEqualTo(9);
            assertThat(album.year()).isEqualTo(2024);
            assertThat(album.duration()).isEqualTo(Duration.ofSeconds(2400));
            assertThat(album.imageId()).isEqualTo("al2");
            assertThat(album.created()).isEqualTo(Instant.parse("2024-05-01T10:00:00Z"));
            assertThat(album.artists()).containsExactly(new RemoteArtistRef("ar1", "Band"));
            assertThat(server.requests().getFirst()).contains("userId=user-1").contains("IncludeItemTypes=MusicAlbum");
        }
    }

    @Test
    void albumWithoutPrimaryImageHasNoImageId() throws Exception {
        var body = "{\"Items\":[{\"Id\":\"al1\",\"Name\":\"Plain\",\"DateCreated\":\"2024-05-01T10:00:00Z\"}]}";
        try (var server = new FakeServer((m, p, q) -> Reply.json(body))) {
            var result = loggedIn(server.baseUrl()).albumsNewerThan(Instant.EPOCH);
            assertThat(result.getFirst().imageId()).isNull();
        }
    }

    @Test
    void fetchesFurtherPagesWhileTheyAreFull() throws Exception {
        var page = IntStream.range(0, 200)
                .mapToObj(i -> "{\"Id\":\"a" + i + "\",\"Name\":\"A\",\"DateCreated\":\"2024-05-01T10:00:00Z\"}")
                .collect(Collectors.joining(",", "{\"Items\":[", "]}"));
        var last = "{\"Items\":[{\"Id\":\"z\",\"Name\":\"Z\",\"DateCreated\":\"2024-05-01T10:00:00Z\"}]}";
        try (var server = new FakeServer((m, p, q) -> Reply.json(q.contains("StartIndex=0") ? page : last))) {
            assertThat(loggedIn(server.baseUrl()).albumsNewerThan(Instant.EPOCH)).hasSize(201);
            assertThat(server.requests()).hasSize(2);
            assertThat(server.requests().get(1)).contains("StartIndex=200");
        }
    }

    @Test
    void mapsSongs() throws Exception {
        var body = "{\"Items\":[{\"Id\":\"s1\",\"AlbumId\":\"al1\",\"Name\":\"Intro\",\"IndexNumber\":3,"
                + "\"ParentIndexNumber\":2,\"ProductionYear\":2001,\"RunTimeTicks\":610000000,"
                + "\"MediaSources\":[{\"Bitrate\":320000,\"Container\":\"flac\","
                + "\"MediaStreams\":[{\"Type\":\"Video\",\"Codec\":\"mjpeg\"},{\"Type\":\"Audio\",\"Codec\":\"flac\"}]}],"
                + "\"ArtistItems\":[],\"AlbumArtists\":[{\"Id\":\"ar1\",\"Name\":\"Band\"}]}]}";
        try (var server = new FakeServer((m, p, q) -> Reply.json(body))) {
            var result = loggedIn(server.baseUrl()).songsOfAlbum("al1");
            assertThat(result).hasSize(1);
            var song = result.getFirst();
            assertThat(song.id()).isEqualTo("s1");
            assertThat(song.albumId()).isEqualTo("al1");
            assertThat(song.title()).isEqualTo("Intro");
            assertThat(song.track()).isEqualTo(3);
            assertThat(song.discNumber()).isEqualTo(2);
            assertThat(song.duration()).isEqualTo(Duration.ofSeconds(61));
            assertThat(song.bitRate()).isEqualTo(320);
            assertThat(song.contentType()).isEqualTo("audio/flac");
            assertThat(song.artists()).containsExactly(new RemoteArtistRef("ar1", "Band"));
        }
    }

    @Test
    void songFallsBackToContainerAndDefaults() throws Exception {
        var body = "{\"Items\":[{\"Id\":\"s1\",\"Container\":\"mp3,wav\"}]}";
        try (var server = new FakeServer((m, p, q) -> Reply.json(body))) {
            var song = loggedIn(server.baseUrl()).songsOfAlbum("al1").getFirst();
            assertThat(song.albumId()).isEqualTo("al1");
            assertThat(song.title()).isEqualTo("Unknown Title");
            assertThat(song.discNumber()).isEqualTo(1);
            assertThat(song.contentType()).isEqualTo("audio/mpeg");
            assertThat(song.artists()).isEmpty();
        }
    }

    @Test
    void artistImageOnlyWhenThereIsAPrimaryImage() throws Exception {
        try (var with = new FakeServer((m, p, q) -> Reply.json("{\"Name\":\"Band\",\"ImageTags\":{\"Primary\":\"x\"}}"));
             var without = new FakeServer((m, p, q) -> Reply.json("{\"Name\":\"Band\"}"))) {
            assertThat(loggedIn(with.baseUrl()).artist("ar1").imageId()).isEqualTo("ar1");
            assertThat(loggedIn(without.baseUrl()).artist("ar1").imageId()).isNull();
        }
    }

    @Test
    void copyableCodecsPlayAsHls() throws Exception {
        var source = loggedIn("http://jf.example.org").playbackSource("s1", "audio/flac");
        assertThat(source.hls()).isTrue();
        assertThat(source.playSessionId()).isNotBlank();
        assertThat(source.uri()).contains("/Audio/s1/master.m3u8").contains("audioCodec=flac")
                .contains("ApiKey=tok3n").contains("playSessionId=" + source.playSessionId());
        assertThat(source.toString()).doesNotContain("tok3n");
    }

    @Test
    void otherCodecsPlayAsProgressiveDownload() throws Exception {
        var source = loggedIn("http://jf.example.org").playbackSource("s1", "audio/x-ms-wma");
        assertThat(source.hls()).isFalse();
        assertThat(source.playSessionId()).isNull();
        assertThat(source.uri()).contains("/Audio/s1/stream").contains("static=true");
    }

    @Test
    void playbackNeedsASession() {
        assertThatThrownBy(() -> backend("http://jf.example.org").playbackSource("s1", "audio/flac"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void releasePlaybackOnlyForHlsSessions() throws Exception {
        try (var server = new FakeServer((m, p, q) -> Reply.status(204))) {
            var backend = loggedIn(server.baseUrl());
            backend.releasePlayback(new PlaybackSource("http://x/stream", false, null));
            backend.releasePlayback(null);
            assertThat(server.requests()).isEmpty();

            backend.releasePlayback(new PlaybackSource("http://x/master.m3u8", true, "ps1"));
            assertThat(server.requests()).hasSize(1);
            assertThat(server.requests().getFirst()).startsWith("DELETE /Videos/ActiveEncodings")
                    .contains("playSessionId=ps1");
        }
    }

    @Test
    void logoutPostsOnlyWhenLoggedIn() throws Exception {
        try (var server = new FakeServer((m, p, q) -> Reply.status(204))) {
            backend(server.baseUrl()).logout();
            assertThat(server.requests()).isEmpty();

            loggedIn(server.baseUrl()).logout();
            assertThat(server.requests()).containsExactly("POST /Sessions/Logout");
        }
    }

    @Test
    void logoutNeverThrowsWhenTheServerIsGone() throws Exception {
        var backend = loggedIn("http://127.0.0.1:1");
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(backend::logout);
    }
}

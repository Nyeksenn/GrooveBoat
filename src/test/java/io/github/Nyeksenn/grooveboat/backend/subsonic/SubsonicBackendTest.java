package io.github.Nyeksenn.grooveboat.backend.subsonic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.Nyeksenn.grooveboat.backend.AuthenticationException;
import io.github.Nyeksenn.grooveboat.backend.BackendType;
import io.github.Nyeksenn.grooveboat.backend.FakeServer;
import io.github.Nyeksenn.grooveboat.backend.FakeServer.Reply;
import io.github.Nyeksenn.grooveboat.backend.RemoteArtistRef;
import io.github.Nyeksenn.grooveboat.backend.Session;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SubsonicBackendTest {
    private static String envelope(String extra) {
        return "{\"subsonic-response\":{\"status\":\"ok\",\"version\":\"1.16.1\",\"type\":\"navidrome\","
                + "\"serverVersion\":\"0.55\",\"openSubsonic\":true" + (extra.isEmpty() ? "" : "," + extra) + "}}";
    }

    private static String error(int code) {
        return "{\"subsonic-response\":{\"status\":\"failed\",\"version\":\"1.16.1\","
                + "\"error\":{\"code\":" + code + ",\"message\":\"Wrong username or password\"}}}";
    }

    @Test
    void exposesUrlParts() {
        var backend = new SubsonicBackend("https://music.example.org/sub/ ");
        assertThat(backend.type()).isEqualTo(BackendType.OPENSUBSONIC);
        assertThat(backend.baseUrl()).isEqualTo("https://music.example.org/sub");
        assertThat(backend.scheme()).isEqualTo("https");
        assertThat(backend.host()).isEqualTo("music.example.org/sub");
    }

    @Test
    void urlWithoutSchemeIsRejected() {
        assertThatThrownBy(() -> new SubsonicBackend("music.example.org")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void probeAcceptsOpenSubsonicServers() throws Exception {
        try (var server = new FakeServer((m, p, q) -> Reply.json(envelope("")))) {
            var info = new SubsonicBackend(server.baseUrl()).probe();
            assertThat(info.compatible()).isTrue();
            assertThat(info.name()).isEqualTo("navidrome");
            assertThat(info.version()).isEqualTo("0.55");
        }
    }

    @Test
    void probeRejectsPlainSubsonic() throws Exception {
        var body = "{\"subsonic-response\":{\"status\":\"ok\",\"version\":\"1.16.1\"}}";
        try (var server = new FakeServer((m, p, q) -> Reply.json(body))) {
            assertThat(new SubsonicBackend(server.baseUrl()).probe().compatible()).isFalse();
        }
    }

    @Test
    void probeRejectsOldApiVersions() throws Exception {
        var body = "{\"subsonic-response\":{\"status\":\"ok\",\"version\":\"1.10.0\",\"openSubsonic\":true}}";
        try (var server = new FakeServer((m, p, q) -> Reply.json(body))) {
            assertThat(new SubsonicBackend(server.baseUrl()).probe().compatible()).isFalse();
        }
    }

    @Test
    void nonSubsonicAnswerIsAnIoError() throws Exception {
        try (var server = new FakeServer((m, p, q) -> Reply.json("{\"hello\":1}"))) {
            assertThatThrownBy(() -> new SubsonicBackend(server.baseUrl()).probe()).isInstanceOf(IOException.class);
        }
    }

    @Test
    void authenticateReturnsSessionAndSendsSaltedToken() throws Exception {
        try (var server = new FakeServer((m, p, q) -> Reply.json(envelope("")))) {
            var session = new SubsonicBackend(server.baseUrl()).authenticate("alice", "secret");
            assertThat(session).isEqualTo(new Session("alice", "secret", ""));
            var request = server.requests().getFirst();
            assertThat(request).contains("/rest/getUser").contains("u=alice").contains("t=").contains("s=")
                    .doesNotContain("secret");
        }
    }

    @Test
    void wrongCredentialsAreAnAuthenticationError() throws Exception {
        try (var server = new FakeServer((m, p, q) -> Reply.json(error(40)))) {
            assertThatThrownBy(() -> new SubsonicBackend(server.baseUrl()).authenticate("alice", "nope"))
                    .isInstanceOf(AuthenticationException.class);
        }
    }

    @Test
    void otherServerErrorsAreAPlainIoError() throws Exception {
        try (var server = new FakeServer((m, p, q) -> Reply.json(error(70)))) {
            assertThatThrownBy(() -> new SubsonicBackend(server.baseUrl()).authenticate("alice", "pw"))
                    .isInstanceOf(IOException.class)
                    .isNotInstanceOf(AuthenticationException.class);
        }
    }

    @Test
    void restoreUnverifiedNeedsACredentialButNoServer() {
        var backend = new SubsonicBackend("http://127.0.0.1:1");
        assertThatThrownBy(() -> backend.restoreUnverified(new Session("alice", null, "")))
                .isInstanceOf(AuthenticationException.class);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> backend.restoreUnverified(new Session("alice", "pw", "")));
    }

    @Test
    void mapsAlbumsAndStopsAtAlbumsAlreadySynced() throws Exception {
        var albums = "\"albumList2\":{\"album\":["
                + "{\"id\":\"a2\",\"name\":\"New\",\"coverArt\":\"al-a2\",\"songCount\":9,\"year\":2024,\"duration\":2400,"
                + "\"created\":\"2024-05-01T10:00:00Z\",\"artists\":[{\"id\":\"ar1\",\"name\":\"Band\"}]},"
                + "{\"id\":\"a1\",\"name\":\"Old\",\"songCount\":3,\"year\":2001,\"duration\":900,"
                + "\"created\":\"2020-01-01T00:00:00Z\"}]}";
        try (var server = new FakeServer((m, p, q) -> Reply.json(envelope(albums)))) {
            var result = new SubsonicBackend(server.baseUrl()).albumsNewerThan(Instant.parse("2023-01-01T00:00:00Z"));
            assertThat(result).hasSize(1);
            var album = result.getFirst();
            assertThat(album.id()).isEqualTo("a2");
            assertThat(album.title()).isEqualTo("New");
            assertThat(album.imageId()).isEqualTo("al-a2");
            assertThat(album.songCount()).isEqualTo(9);
            assertThat(album.year()).isEqualTo(2024);
            assertThat(album.duration()).isEqualTo(Duration.ofSeconds(2400));
            assertThat(album.created()).isEqualTo(Instant.parse("2024-05-01T10:00:00Z"));
            assertThat(album.artists()).containsExactly(new RemoteArtistRef("ar1", "Band"));
        }
    }

    @Test
    void albumsWithoutTimestampAreAlwaysIncluded() throws Exception {
        var albums = "\"albumList2\":{\"album\":[{\"id\":\"a1\",\"name\":\"Undated\"}]}";
        try (var server = new FakeServer((m, p, q) -> Reply.json(envelope(albums)))) {
            var result = new SubsonicBackend(server.baseUrl()).albumsNewerThan(Instant.parse("2023-01-01T00:00:00Z"));
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().created()).isEqualTo(Instant.EPOCH);
            assertThat(result.getFirst().artists()).isEmpty();
        }
    }

    @Test
    void emptyLibraryHasNoAlbums() throws Exception {
        try (var server = new FakeServer((m, p, q) -> Reply.json(envelope("\"albumList2\":{}")))) {
            assertThat(new SubsonicBackend(server.baseUrl()).albumsNewerThan(Instant.EPOCH)).isEmpty();
        }
    }

    @Test
    void mapsSongs() throws Exception {
        var songs = "\"directory\":{\"child\":[{\"id\":\"s1\",\"albumId\":\"a1\",\"title\":\"Intro\",\"track\":1,"
                + "\"year\":2001,\"duration\":61,\"bitRate\":320,\"contentType\":\"audio/mpeg\",\"discNumber\":2,"
                + "\"artists\":[{\"id\":\"ar1\",\"name\":\"Band\"}]}]}";
        try (var server = new FakeServer((m, p, q) -> Reply.json(envelope(songs)))) {
            var result = new SubsonicBackend(server.baseUrl()).songsOfAlbum("a1");
            assertThat(result).hasSize(1);
            var song = result.getFirst();
            assertThat(song.id()).isEqualTo("s1");
            assertThat(song.albumId()).isEqualTo("a1");
            assertThat(song.title()).isEqualTo("Intro");
            assertThat(song.track()).isEqualTo(1);
            assertThat(song.discNumber()).isEqualTo(2);
            assertThat(song.duration()).isEqualTo(Duration.ofSeconds(61));
            assertThat(song.bitRate()).isEqualTo(320);
            assertThat(song.contentType()).isEqualTo("audio/mpeg");
            assertThat(song.artists()).containsExactly(new RemoteArtistRef("ar1", "Band"));
        }
    }

    @Test
    void artistImageFallsBackToTheArtistId() throws Exception {
        var artist = "\"artist\":{\"id\":\"ar1\",\"name\":\"Band\"}";
        try (var server = new FakeServer((m, p, q) -> Reply.json(envelope(artist)))) {
            var result = new SubsonicBackend(server.baseUrl()).artist("ar1");
            assertThat(result.name()).isEqualTo("Band");
            assertThat(result.imageId()).isEqualTo("ar1");
        }
    }

    @Test
    void playbackSourceIsProgressiveAndNeverLogsCredentials() throws Exception {
        var backend = new SubsonicBackend("http://music.example.org");
        backend.restoreUnverified(new Session("alice", "secret", ""));
        var source = backend.playbackSource("s1", "audio/flac");
        assertThat(source.hls()).isFalse();
        assertThat(source.uri()).contains("/rest/stream").contains("id=s1").contains("format=raw");
        assertThat(source.toString()).doesNotContain("secret").doesNotContain("t=");
    }
}

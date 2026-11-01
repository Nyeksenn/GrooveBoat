package io.github.Nyeksenn.grooveboat.mpris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.freedesktop.dbus.DBusPath;
import org.junit.jupiter.api.Test;

class MprisStateTest {
    @Test
    void emptyStateIsStoppedWithNoTrack() {
        var state = MprisState.empty();
        assertThat(state.status()).isEqualTo("Stopped");
        assertThat(state.loop()).isEqualTo("None");
        assertThat(state.shuffle()).isFalse();
        assertThat(state.currentTrackId()).isEqualTo(MprisState.NO_TRACK);
        assertThat(state.metadata().get("mpris:trackid").getValue()).isEqualTo(new DBusPath(MprisState.NO_TRACK));
    }

    @Test
    void playerPropertiesReflectTheState() {
        var props = MprisState.empty().playerProperties();
        assertThat(props.get("PlaybackStatus").getValue()).isEqualTo("Stopped");
        assertThat(props.get("LoopStatus").getValue()).isEqualTo("None");
        assertThat(props.get("Volume").getValue()).isEqualTo(1.0);
        assertThat(props.get("CanPlay").getValue()).isEqualTo(false);
        assertThat(props.get("CanControl").getValue()).isEqualTo(true);
    }

    @Test
    void rootPropertiesNeverAdvertiseUriSchemes() {
        var props = MprisState.empty().rootProperties();
        assertThat(props.get("Identity").getValue()).isEqualTo("GrooveBoat");
        assertThat(props.get("SupportedUriSchemes").getValue()).isEqualTo(List.of());
    }

    @Test
    void trackListPropertiesExposeTrackIdsAsObjectPaths() {
        var state = new MprisState("Playing", "Track", true, 0.5, MprisState.empty().metadata(), true, true, true,
                List.of("/t/1", "/t/2"), java.util.Map.of(), "/t/1");
        assertThat(state.trackListProperties().get("Tracks").getValue())
                .isEqualTo(List.of(new DBusPath("/t/1"), new DBusPath("/t/2")));
        assertThat(state.playerProperties().get("Shuffle").getValue()).isEqualTo(true);
    }
}

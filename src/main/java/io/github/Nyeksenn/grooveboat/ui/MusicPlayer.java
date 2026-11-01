package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.audio.QueueStore;
import io.github.Nyeksenn.grooveboat.gobjects.RoundedImage;
import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import org.gnome.gtk.Box;
import org.gnome.gtk.Label;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/music-player.ui", name = "MusicPlayer")
@RegisteredType(name = "MusicPlayer")
public class MusicPlayer extends Box {

    @GtkChild(name = "player-album-picture")
    public RoundedImage playerAlbumPicture;

    @GtkChild(name = "player-song-name")
    public Label playerSongName;

    @GtkChild(name = "player-album-artist")
    public Label playerAlbumArtist;

    @GtkChild(name = "music-controls")
    public MusicControls mediaControls;

    @GtkChild(name = "song-queue")
    public SongQueue songQueue;

    private GrooveboatApp app;

    private void initializeMediaStream(SongListItem songItem) {
        if (songItem != null) {
            this.playerAlbumArtist.setLabel(songItem.getArtistName());
            this.playerSongName.setLabel(songItem.getSongName());
            this.playerAlbumPicture.setTexture(songItem.getCoverArt());
        }
    }

    private void initializePlayer(SongListItem songItem) {
        if (songItem != null) {
            this.playerAlbumArtist.setLabel(songItem.getArtistName());
            this.playerSongName.setLabel(songItem.getSongName());
            this.playerAlbumPicture.setTexture(songItem.getCoverArt());
            this.mediaControls.setMediaStream(app.getStream());
        }
    }

    @InstanceInit
    public void init() {
        AppWidgets.whileRealized(this, scope -> {
            var app = AppWidgets.app(this);
            this.app = app;
            this.mediaControls.setMediaStream(app.getStream());

            if (!app.queueStore.getSelection().isEmpty()) {
                initializePlayer(app.queueStore.getSelection().getSelectedItem());
            }

            scope.track(app.queueStore.connect("initialize-song", (QueueStore.InitializeSong) this::initializeMediaStream));
        });
    }

    public MusicPlayer() {
    }

    public MusicPlayer(MemorySegment address) {
        super(address);
    }
}

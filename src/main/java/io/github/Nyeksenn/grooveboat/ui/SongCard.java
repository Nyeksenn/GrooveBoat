package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.audio.SongQueue;
import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import io.github.Nyeksenn.grooveboat.gobjects.RoundedImage;
import org.gnome.gtk.Box;
import org.gnome.gtk.Label;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/song-card.ui", name = "SongCard")
public class SongCard extends Box {
    private static final Logger logger = LoggerFactory.getLogger(SongCard.class);
    private SongQueue songQueue;

    private String songId;

    @GtkChild(name = "song-picture")
    public RoundedImage songPicture;

    @GtkChild(name = "song-name")
    public Label songName;

    @GtkChild(name = "song-artist")
    public Label songArtist;

    @GtkChild(name = "song-duration")
    public Label songDuration;

    @GtkCallback(name = "song-clicked")
    public void songClicked(int n_press, double x, double y) {
        var song = new QSongModel().songId.equalTo(songId).findOne();
        assert song != null;
        songQueue.replace(List.of(song));
    }

    public void setSongId(String songId) {
        this.songId = songId;
    }

    public void setSongQueue(SongQueue songQueue) {
        this.songQueue = songQueue;
    }

    public SongCard() {
    }
}

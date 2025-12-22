package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.audio.QueueStore;
import io.github.Nyeksenn.grooveboat.gobjects.MediaSubsonic;
import io.github.Nyeksenn.grooveboat.gobjects.RoundedImage;
import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import org.gnome.glib.Variant;
import org.gnome.graphene.Point;
import org.gnome.gtk.Box;
import org.gnome.gtk.Button;
import org.gnome.gtk.GestureClick;
import org.gnome.gtk.Label;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/mini-player.ui", name = "MiniPlayer")
@RegisteredType(name = "MiniPlayer")
public class MiniPlayer extends Box {
    private MediaSubsonic stream;
    private GrooveboatApp app;

    @GtkChild(name = "mini-album-cover")
    public RoundedImage albumCover;

    @GtkChild(name = "mini-song-title-label")
    public Label songTitleLabel;

    @GtkChild(name = "mini-album-artist-label")
    public Label albumArtistLabel;

    @GtkChild(name = "mini-player-click")
    public GestureClick miniPlayerClick;

    @GtkChild(name = "mini-play-toggle")
    public Button miniPlayToggle;

    @GtkChild(name = "mini-play-previous")
    public Button miniPlayPrevious;

    @GtkChild(name = "mini-play-skip")
    public Button miniPlaySkip;

    @GtkCallback(name = "mini-player-pressed")
    public void miniPlayerPressed(int nPress, double x, double y) {
        var clickPoint = new Point((float) x, (float) y);
        var playPoint = new Point();
        var previousPoint = new Point();
        var nextPoint = new Point();

        computePoint(miniPlayToggle, clickPoint, playPoint);
        computePoint(miniPlayPrevious, clickPoint, previousPoint);
        computePoint(miniPlaySkip, clickPoint, nextPoint);

        if (!(miniPlayToggle.contains(playPoint.readX(), playPoint.readY()) || miniPlayPrevious.contains(previousPoint.readX(), previousPoint.readY()) || miniPlaySkip.contains(nextPoint.readX(), nextPoint.readY()))) {
            activateActionIfExists("navigation.push", Variant.string("sidebar"));
        }
    }

    public void initStream(SongListItem item) {
        if (item != null) {
            this.stream = app.getStream();
            var selectedSong = app.queueStore.getSelection().getSelectedItem();
            albumCover.setTexture(selectedSong.getCoverArt());
            songTitleLabel.setLabel(selectedSong.getSongName());
            albumArtistLabel.setLabel(selectedSong.getArtistName());
            stream.<Boolean, String>bindProperty("playing", miniPlayToggle, "icon-name").transformTo((playing) -> {
                if (playing) {
                    return "pause-symbolic";
                } else {
                    return "play-symbolic";
                }
            }).transformFrom(null).syncCreate().build();
        }
    }

    @InstanceInit
    public void init() {
        onRealize(() -> {
            var win = (GrooveboatWindow) getRoot();
            var app = (GrooveboatApp) win.getApplication();
            this.app = app;
            app.queueStore.connect("initialize-song", (QueueStore.InitializeSong) this::initStream);

            this.stream = app.getStream();

            if (this.stream != null) {
                stream.<Boolean, String>bindProperty("playing", miniPlayToggle, "icon-name").transformTo((playing) -> {
                    if (playing) {
                        return "pause-symbolic";
                    } else {
                        return "play-symbolic";
                    }
                }).transformFrom(null).syncCreate().build();
                var selectedSong = app.queueStore.getSelection().getSelectedItem();
                albumCover.setTexture(selectedSong.getCoverArt());
                songTitleLabel.setLabel(selectedSong.getSongName());
                albumArtistLabel.setLabel(selectedSong.getArtistName());
            }
        });
    }

    public MiniPlayer() {
    }

    public MiniPlayer(MemorySegment address) {
        super(address);
    }
}

package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.audio.SongQueue;
import io.github.Nyeksenn.grooveboat.client.ConnDetails;
import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import org.gnome.adw.ApplicationWindow;
import org.gnome.gio.ActionEntry;
import org.gnome.gio.Settings;
import org.gnome.glib.Variant;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/window.ui", name = "GrooveboatWindow")
public class GrooveboatWindow extends ApplicationWindow {

    @GtkChild(name = "music-chooser")
    public MusicChooser musicChooser;

    public Settings settings;

    public ConnDetails connDetails;

    private final ExecutorService exec;

    public GrooveboatWindow(GrooveboatApp app) {
        setApplication(app);
        songQueue = app.songQueue;
        exec = app.executor;
    }

    private final SongQueue songQueue;

    @GtkCallback(name = "breakpoint-apply")
    public void handleBreakpointApply() {
//        musicChooser.sidebarToggle.setVisible(true);
        musicChooser.musicSwitcherBar.setReveal(true);
        musicChooser.musicChooserHeader.setTitleWidget(null);
    }

    @GtkCallback(name = "breakpoint-unapply")
    public void handleBreakpointUnapply() {
//        musicChooser.sidebarToggle.setVisible(false);
        musicChooser.musicSwitcherBar.setReveal(false);
        musicChooser.musicChooserHeader.setTitleWidget(musicChooser.musicSwitcherTitle);
    }

    private void setupActions() {
        var entries = new ArrayList<ActionEntry>(5);

        entries.add(new ActionEntry(
                "play-song",
                (action, parameter) -> {
                    exec.submit(() -> {
                        var songId = parameter.getString(null);
                        var song = new QSongModel().songId.equalTo(songId).findOne();
                        if (song != null) {
                            songQueue.replace(List.of(song));
                        }
                    });
                },
                Variant.string("").getTypeString(),
                null,
                null
        ));
        entries.add(new ActionEntry(
                "queue-song",
                (action, parameter) -> {
                    var songId = parameter.getString(null);
                    var song = new QSongModel().songId.equalTo(songId).findOne();
                    if (song != null) {
                        songQueue.addSongList(List.of(song));
                    }
                },
                Variant.string("").getTypeString(),
                null,
                null
        ));
        entries.add(new ActionEntry(
                "play-album",
                (action, parameter) -> {
                    var albumId = parameter.getString(null);
                    var album = new QAlbumModel().albumId.equalTo(albumId).findOne();
                    if (album != null) {
                        var songs = album.getSongs();
                        songQueue.replace(songs);
                    }
                },
                Variant.string("").getTypeString(),
                null,
                null
        ));
        entries.add(new ActionEntry(
                "queue-album",
                (action, parameter) -> {
                    var albumId = parameter.getString(null);
                    var album = new QAlbumModel().albumId.equalTo(albumId).findOne();
                    if (album != null) {
                        var songs = album.getSongs();
                        songQueue.addSongList(songs);
                    }
                },
                Variant.string("").getTypeString(),
                null,
                null
        ));

        addActionEntries(entries.toArray(ActionEntry[]::new), null);
    }

    @InstanceInit
    public void init() {
        connDetails = new ConnDetails();
    }
}

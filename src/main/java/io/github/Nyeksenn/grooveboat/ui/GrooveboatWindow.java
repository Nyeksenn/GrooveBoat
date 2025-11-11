package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.audio.SongQueue;
import io.github.Nyeksenn.grooveboat.client.ConnDetails;
import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import org.gnome.adw.ApplicationWindow;
import org.gnome.gio.Settings;
import org.gnome.gio.SimpleAction;
import org.javagi.glib.types.VariantTypes;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/window.ui", name = "GrooveboatWindow")
public class GrooveboatWindow extends ApplicationWindow {
    private static final Logger logger = LoggerFactory.getLogger(GrooveboatWindow.class);

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
        var playSongAction = new SimpleAction("play-song", VariantTypes.STRING);
        playSongAction.onActivate((parameter) -> {
            if (parameter != null) {
                exec.submit(() -> {
                    logger.info("Playing this song:");
                    var songId = parameter.getString(null);
                    var song = new QSongModel().songId.equalTo(songId).findOne();
                    if (song != null) {
                        logger.info("{}", song.getTitle());
                        songQueue.replace(List.of(song));
                    }
                });
            }
        });
        addAction(playSongAction);

        var queueSongAction = new SimpleAction("queue-song", VariantTypes.STRING);
        queueSongAction.onActivate(
                (parameter) -> {
                    if (parameter != null) {
                        exec.submit(() -> {
                            logger.info("Queueing this song:");
                            var songId = parameter.getString(null);
                            var song = new QSongModel().songId.equalTo(songId).findOne();
                            if (song != null) {
                                logger.info("{}", song.getTitle());
                                songQueue.addSongList(List.of(song));
                            }
                        });
                    }
                }
        );
        addAction(queueSongAction);

        var playAlbumAction = new SimpleAction("play-album", VariantTypes.STRING);
        playAlbumAction.onActivate(
                (parameter) -> {
                    if (parameter != null) {
                        exec.submit(() -> {
                            var albumId = parameter.getString(null);
                            var album = new QAlbumModel().albumId.equalTo(albumId).findOne();
                            if (album != null) {
                                var songs = album.getSongs();
                                songQueue.replace(songs);
                            }
                        });
                    }
                }
        );
        addAction(playAlbumAction);

        var queueAlbumAction = new SimpleAction("queue-album", VariantTypes.STRING);
        queueAlbumAction.onActivate(
                (parameter) -> {
                    if (parameter != null) {
                        exec.submit(() -> {
                            var albumId = parameter.getString(null);
                            var album = new QAlbumModel().albumId.equalTo(albumId).findOne();
                            if (album != null) {
                                var songs = album.getSongs();
                                songQueue.addSongList(songs);
                            }
                        });
                    }
                }
        );
        addAction(queueAlbumAction);
    }

    @InstanceInit
    public void init() {
        connDetails = new ConnDetails();
        setupActions();
    }
}

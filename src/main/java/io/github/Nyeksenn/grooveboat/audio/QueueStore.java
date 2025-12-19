package io.github.Nyeksenn.grooveboat.audio;

import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import io.github.Nyeksenn.grooveboat.ui.AlbumTextureLoader;
import io.github.Nyeksenn.grooveboat.ui.GrooveboatApp;
import org.gnome.gio.ActionEntry;
import org.gnome.gio.ListStore;
import org.gnome.glib.GLib;
import org.gnome.glib.Variant;
import org.gnome.gobject.GObject;
import org.gnome.gobject.ParamSpec;
import org.gnome.gtk.SingleSelection;
import org.javagi.glib.types.VariantTypes;
import org.javagi.gobject.annotations.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;

public class QueueStore extends GObject {
    private final GrooveboatApp app;
    private static final Logger logger = LoggerFactory.getLogger(QueueStore.class);
    private static final AlbumTextureLoader loader = AlbumTextureLoader.getInstance();
    private boolean initialized;
    private final ListStore<SongListItem> model = new ListStore<>();
    private final SingleSelection<ListStore<SongListItem>> selection = new SingleSelection<>(model);

    @Signal
    public interface InitializeSong {
        void run(SongListItem item);
    }

    public SingleSelection<ListStore<SongListItem>> getSelection() {
        return this.selection;
    }

    public void itemSelectionChanged(ParamSpec pspec) {
        var selectedItem = model.getItem(selection.getSelected());
        emit("initialize-song", selectedItem);
        if (!initialized) {
            initialized = true;
        } else {
            var action = app.lookupAction("toggle-play");
            action.activate(null);
        }
    }

    public void loadState() {
        var initSong = new QSongModel().findList().getFirst();
        var action = app.lookupAction("queue-song");
        action.activate(Variant.string(initSong.getSongId()));
    }

    private void setupActions() {
        var exec = app.executor;

        var entries = new ActionEntry[7];

        // These are queue manipulation action
        entries[0] = new ActionEntry(
                "play-song",
                (_, parameter) -> {
                    if (parameter != null) {
                        exec.submit(() -> {
                            logger.info("Playing this song:");
                            var songId = parameter.getString(null);
                            var song = new QSongModel().songId.equalTo(songId).findOne();
                            if (song != null) {
                                var texture = loader.load(song.getAlbum().getAlbumId());
                                var item = new SongListItem(
                                        song.getTitle(),
                                        song.getSongId(),
                                        song.getDisplayArtist(),
                                        song.getAlbum().getTitle(),
                                        song.getDuration(),
                                        song.getContentType(),
                                        texture
                                );
                                GLib.idleAddOnce(() -> {
                                    model.removeAll();
                                    model.append(item);
                                });
                            }
                        });
                    }
                },
                VariantTypes.STRING.dupString(),
                null,
                null,
                Arena.global()
        );
        entries[1] = new ActionEntry(
                "queue-song",
                (_, parameter) -> {
                    if (parameter != null) {
                        exec.submit(() -> {
                            logger.info("Queueing this song:");
                            var songId = parameter.getString(null);
                            var song = new QSongModel().songId.equalTo(songId).findOne();
                            if (song != null) {
                                var texture = loader.load(song.getAlbum().getAlbumId());
                                var item = new SongListItem(
                                        song.getTitle(),
                                        song.getSongId(),
                                        song.getDisplayArtist(),
                                        song.getAlbum().getTitle(),
                                        song.getDuration(),
                                        song.getContentType(),
                                        texture
                                );
                                GLib.idleAddOnce(() -> model.append(item));
                            }
                        });
                    }
                },
                VariantTypes.STRING.dupString(),
                null,
                null,
                Arena.global()
        );
        entries[2] = new ActionEntry(
                "play-album",
                (_, parameter) -> {
                    if (parameter != null) {
                        exec.submit(() -> {
                            var albumId = parameter.getString(null);
                            var album = new QAlbumModel().albumId.equalTo(albumId).findOne();
                            if (album != null) {
                                GLib.idleAddOnce(model::removeAll);
                                var songs = album.getSongs();
                                var texture = loader.load(album.getAlbumId());
                                for (var song : songs) {
                                    var item = new SongListItem(
                                            song.getTitle(),
                                            song.getSongId(),
                                            song.getDisplayArtist(),
                                            song.getAlbum().getTitle(),
                                            song.getDuration(),
                                            song.getContentType(),
                                            texture
                                    );
                                    GLib.idleAddOnce(() -> model.append(item));
                                }
                            }
                        });
                    }
                },
                VariantTypes.STRING.dupString(),
                null,
                null,
                Arena.global()
        );
        entries[3] = new ActionEntry(
                "queue-album",
                (_, parameter) -> {
                    if (parameter != null) {
                        exec.submit(() -> {
                            var albumId = parameter.getString(null);
                            var album = new QAlbumModel().albumId.equalTo(albumId).findOne();
                            if (album != null) {
                                var texture = loader.load(album.getAlbumId());
                                var songs = album.getSongs();
                                for (var song : songs) {
                                    var item = new SongListItem(
                                            song.getTitle(),
                                            song.getSongId(),
                                            song.getDisplayArtist(),
                                            song.getAlbum().getTitle(),
                                            song.getDuration(),
                                            song.getContentType(),
                                            texture
                                    );
                                    GLib.idleAddOnce(() -> model.append(item));
                                }
                            }
                        });
                    }
                },
                VariantTypes.STRING.dupString(),
                null,
                null,
                Arena.global()
        );
        entries[4] = new ActionEntry(
                "remove-song",
                (_, parameter) -> {
                    if (parameter != null) {
                        GLib.idleAddOnce(() -> model.removeAt(parameter.getInt32()));
                    }
                },
                VariantTypes.INT32.dupString(),
                null,
                null,
                Arena.global()
        );

        // These are play actions
        entries[5] = new ActionEntry(
                "play-next",
                (_, parameter) -> {
                    var isLooping = parameter.getBoolean();
                    var selectedIdx = selection.getSelected();
                    if (isLooping) {
                        selection.emitNotify("selected", null);
                    } else {
                        if (selectedIdx < (model.size() - 1)) {
                            selection.selectItem(selectedIdx + 1, true);
                        }
                    }
                },
                VariantTypes.BOOLEAN.dupString(),
                null,
                null,
                Arena.global()
        );
        entries[6] = new ActionEntry(
                "play-previous",
                (_, _) -> {
                    var selectedIdx = selection.getSelected();
                    if (selectedIdx > 0) {
                        selection.selectItem(selectedIdx - 1, true);
                    }
                },
                null,
                null,
                null,
                Arena.global()
        );
        app.addActionEntries(entries, null);
    }

    public QueueStore(GrooveboatApp app) {
        this.app = app;
        selection.onNotify("selected", this::itemSelectionChanged);
        setupActions();
    }
}

package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import org.gnome.adw.Bin;
import org.gnome.gio.ActionEntry;
import org.gnome.gio.ListStore;
import org.gnome.glib.GLib;
import org.gnome.glib.Variant;
import org.gnome.gobject.GObject;
import org.gnome.gobject.ParamSpec;
import org.gnome.gtk.*;
import org.javagi.glib.types.VariantTypes;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.Signal;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Set;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/song-queue.ui", name = "SongQueue")
public final class SongQueue extends Bin {
    private static final Logger logger = LoggerFactory.getLogger(SongQueue.class);
    private static final AlbumTextureLoader loader = AlbumTextureLoader.getInstance();
    private boolean initialized;
    private final ListStore<SongListItem> model = new ListStore<>();
    private final SingleSelection<ListStore<SongListItem>> selection = new SingleSelection<>(model);

    @Signal
    public interface InitializeSong {
        void run(SongListItem item);
    }


    @GtkChild(name = "queue-scroll-window")
    public ScrolledWindow queueScrollWindow;

    @GtkChild(name = "queue-list-view")
    public ListView queueListView;

    @GtkCallback(name = "queue-list-setup")
    public void queueListSetup(GObject object) {
        var listItem = (ListItem) object;
        var queueCard = new QueueCard();
        listItem.setChild(queueCard);
    }

    @GtkCallback(name = "queue-list-bind")
    public void queueListBind(GObject object) {
        var listItem = (ListItem) object;
        var queueCard = (QueueCard) listItem.getChild();
        var songItem = (SongListItem) listItem.getItem();
        queueCard.albumCover.setTexture(songItem.getCoverArt());
        queueCard.albumArtistLabel.setLabel(songItem.getArtistName());
        queueCard.songTitleLabel.setLabel(songItem.getSongName());
        listItem.bindProperty("position", queueCard, "index").syncCreate().build();
    }

    public void itemSelectionChanged(ParamSpec pspec) {
        var selectedItem = model.getItem(selection.getSelected());
        queueListView.scrollTo(selection.getSelected(), Set.of(ListScrollFlags.FOCUS), null);
        emit("initialize-song", selectedItem);
        if (!initialized) {
            initialized = true;
        } else {
            activateActionIfExists("win.toggle-play", null);
        }
    }

    private void loadState(GrooveboatWindow win) {
        var initSong = new QSongModel().findList().getFirst();
        var action = win.lookupAction("queue-song");
        action.activate(Variant.string(initSong.getSongId()));
    }

    private void setupActions(GrooveboatWindow win) {
        var exec = win.executor;

        var entries = new ActionEntry[7];

        // These are queue manipulation action
        entries[0] = new ActionEntry(
                "play-song",
                (action, parameter) -> {
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
                (action, parameter) -> {
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
                (action, parameter) -> {
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
                (action, parameter) -> {
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
                (action, parameter) -> {
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
                (action, parameter) -> {
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
                (action, parameter) -> {
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
        win.addActionEntries(entries, null);
    }

    @InstanceInit
    public void init() {
        onRealize(() -> {
            var win = (GrooveboatWindow) getRoot();
            assert win != null;
            selection.onNotify("selected", this::itemSelectionChanged);
            queueListView.setModel(selection);
            setupActions(win);
            loadState(win);
        });
    }

    public SongQueue() {
    }

    public SongQueue(MemorySegment address) {
        super(address);
    }
}

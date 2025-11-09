package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.audio.SongQueue;
import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import io.github.Nyeksenn.grooveboat.gobjects.MediaSubsonic;
import io.github.Nyeksenn.grooveboat.gobjects.QueueListItem;
import io.github.Nyeksenn.grooveboat.gobjects.RoundedImage;
import org.gnome.gio.ListStore;
import org.gnome.glib.GLib;
import org.gnome.gobject.GObject;
import org.gnome.gtk.*;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.ExecutorService;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/music-player.ui", name = "MusicPlayer")
@RegisteredType(name = "MusicPlayer")
public class MusicPlayer extends Box {
    private static final Logger logger = LoggerFactory.getLogger(MusicPlayer.class);

    @GtkChild(name = "player-album-picture")
    public RoundedImage playerAlbumPicture;

    @GtkChild(name = "player-song-name")
    public Label playerSongName;

    @GtkChild(name = "player-album-artist")
    public Label playerAlbumArtist;

    @GtkChild(name = "media-controls")
    public MediaControls mediaControls;

    @GtkChild(name = "queue-list-view")
    public ListView queueListView;

    private Subsonic subsonic;

    private ExecutorService executor;

    private final AlbumTextureLoader loader = AlbumTextureLoader.getInstance();

    private SongQueue songQueue;

    private ListStore<QueueListItem> store;

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
        var queueItem = (QueueListItem) listItem.getItem();

        queueCard.songName.setLabel(queueItem.getSongName());
        queueCard.songArtist.setLabel(queueItem.getArtistName());
        queueCard.songDuration.setLabel(queueItem.getDurationString());
        queueCard.songPicture.setTexture(queueItem.getCoverArt());
        queueCard.setSongId(queueItem.getSongId());
    }

    public void initPlayer() {
        if (this.subsonic == null && executor == null) {
            var window = (GrooveboatWindow) getRoot();
            var app = (GrooveboatApp) window.getApplication();

            this.songQueue = app.songQueue;
            this.executor = app.executor;
            this.subsonic = app.subsonic;

            this.store = new ListStore<>();
            NoSelection<ListStore<QueueListItem>> model = new NoSelection<>(this.store);

            songQueue.connect("queue-changed", (SongQueue.QueueChanged) this::queueChanged);
            songQueue.connect("queue-replaced", (SongQueue.QueueReplaced) this::queueReplaced);
            songQueue.connect("queue-added", (SongQueue.QueueAdded) this::queueAdded);

            if (songQueue.isEmpty()) {
                var initSong = new QSongModel().findList().getFirst();
                songQueue.addSong(initSong);
            }
        }
    }

    public void queueAdded(String songId) {
        this.executor.submit(() -> {
            var song = new QSongModel().songId.equalTo(songId).findOne();
            if (song != null) {
                var texture = loader.load(song.getAlbum().getAlbumId());
                var queueItem = new QueueListItem(
                        song.getTitle(),
                        song.getSongId(),
                        song.getDisplayArtist(),
                        song.getAlbum().getTitle(),
                        song.getDuration(),
                        texture
                );
                GLib.idleAddOnce(() -> this.store.add(queueItem));
            }
        });
    }

    public void queueReplaced() {
        executor.submit(() -> {
            GLib.idleAddOnce(() -> this.store.removeAll());
            for (var it = songQueue.iterate(); it.hasNext(); ) {
                var song = it.next();
                var texture = loader.load(song.getAlbum().getAlbumId());
                var queueItem = new QueueListItem(
                        song.getTitle(),
                        song.getSongId(),
                        song.getDisplayArtist(),
                        song.getAlbum().getTitle(),
                        song.getDuration(),
                        texture
                );
                GLib.idleAddOnce(() -> this.store.append(queueItem));
            }
        });
    }

    // Make sure that no two threads try to change pipeline in parallel
    public void queueChanged() {
        var oldStream = (MediaSubsonic) this.mediaControls.getMediaStream();
        var nextSong = this.songQueue.peekSong();
        var textureLoader = AlbumTextureLoader.getInstance();

        executor.submit(() -> {
            if (oldStream != null) {
                oldStream.close();
            }

            var stream = new MediaSubsonic(
                    nextSong.getSongId(),
                    nextSong.getDuration().getSeconds(),
                    subsonic, executor,
                    nextSong.getContentType()
            );
            GLib.idleAddOnce(() -> this.mediaControls.setMediaStream(stream));
            stream.initializeStream();
        });
        this.playerAlbumArtist.setLabel(nextSong.getDisplayArtist());
        this.playerSongName.setLabel(nextSong.getTitle());
        this.playerAlbumPicture.setTexture(textureLoader.load(nextSong.getAlbum().getAlbumId()));
    }

    @InstanceInit
    public void init() {
        this.onRealize(this::initPlayer);
        this.onUnrealize(this::destroy);
    }

    public MusicPlayer() {
    }

    public MusicPlayer(MemorySegment address) {
        super(address);
    }

    public void destroy() {
        logger.info("Closing Music Player");
        var stream = (MediaSubsonic) this.mediaControls.getMediaStream();
        if (stream != null) {
            stream.close();
        }
    }
}

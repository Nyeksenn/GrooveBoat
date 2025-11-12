package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.audio.SongQueue;
import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import io.github.Nyeksenn.grooveboat.gobjects.MediaSubsonic;
import io.github.Nyeksenn.grooveboat.gobjects.RoundedImage;
import org.gnome.gdk.Texture;
import org.gnome.glib.GLib;
import org.gnome.glib.Variant;
import org.gnome.gtk.Box;
import org.gnome.gtk.Label;
import org.gnome.gtk.ListBox;
import org.gnome.gtk.MediaControls;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.RegisteredType;
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

    @GtkChild(name = "queue-list-box")
    public ListBox queueListBox;

    private Subsonic subsonic;

    private ExecutorService executor;

    private final AlbumTextureLoader loader = AlbumTextureLoader.getInstance();

    private SongQueue songQueue;

    public void initPlayer() {
        if (this.subsonic == null && executor == null) {
            var window = (GrooveboatWindow) getRoot();

            var app = (GrooveboatApp) window.getApplication();

            this.songQueue = app.songQueue;
            this.executor = app.executor;
            this.subsonic = app.subsonic;

            songQueue.connect("queue-replaced", (SongQueue.QueueReplaced) this::replaceSongsListBox);
            songQueue.connect("queue-added", (SongQueue.QueueAdded) this::addSongListBox);

            if (songQueue.isEmpty()) {
                var initSong = new QSongModel().findList().getFirst();
                var action = window.lookupAction("play-song");
                action.activate(Variant.string(initSong.getSongId()));
            }
        }
    }

    public void addSongListBox(String idString) {
        this.executor.submit(() -> {
            var songIds = idString.split(";");

            for (var songId : songIds) {
                var song = new QSongModel().songId.equalTo(songId).findOne();
                if (song != null) {
                    var texture = loader.load(song.getAlbum().getAlbumId());
                    var card = new QueueCard();
                    var coverArt = new RoundedImage();
                    coverArt.setTexture(texture);
                    coverArt.setPixelSize(40);
                    card.setTitle(song.getTitle());
                    card.setSubtitle(song.getDisplayArtist());
                    card.addPrefix(coverArt);
                    card.setSongId(song.getSongId());
                    GLib.idleAddOnce(() -> {
                        this.queueListBox.append(card);
                    });
                }
            }
        });
    }

    public void replaceSongsListBox() {
        executor.submit(() -> {
            GLib.idleAddOnce(() -> queueListBox.removeAll());
            var texture = loader.load(songQueue.peekSong().getAlbum().getAlbumId());

            for (var it = songQueue.iterate(); it.hasNext(); ) {
                var song = it.next();
                var card = new QueueCard();
                var coverArt = new RoundedImage();
                coverArt.setPixelSize(40);
                coverArt.setTexture(texture);
                card.setTitle(song.getTitle());
                card.setSubtitle(song.getDisplayArtist());
                card.addPrefix(coverArt);
                card.setSongId(song.getSongId());
                GLib.idleAddOnce(() -> queueListBox.append(card));
            }

            reinitStream(texture);
        });
    }

    public void reinitStream(Texture albumCover) {
        var oldStream = (MediaSubsonic) this.mediaControls.getMediaStream();
        var nextSong = this.songQueue.peekSong();

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
        this.playerAlbumPicture.setTexture(albumCover);
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

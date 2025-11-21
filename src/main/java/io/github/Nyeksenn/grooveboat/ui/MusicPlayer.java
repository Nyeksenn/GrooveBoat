package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.audio.AppPipeline;
import io.github.Nyeksenn.grooveboat.audio.SongQueue;
import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.Nyeksenn.grooveboat.entities.SongModel;
import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import io.github.Nyeksenn.grooveboat.gobjects.MediaSubsonic;
import io.github.Nyeksenn.grooveboat.gobjects.RoundedImage;
import org.freedesktop.gstreamer.gst.Bus;
import org.freedesktop.gstreamer.gst.Message;
import org.freedesktop.gstreamer.gst.MessageType;
import org.freedesktop.gstreamer.gst.State;
import org.gnome.gdk.Texture;
import org.gnome.gio.ActionEntry;
import org.gnome.glib.GError;
import org.gnome.glib.GLib;
import org.gnome.glib.Variant;
import org.gnome.gtk.Box;
import org.gnome.gtk.Label;
import org.gnome.gtk.ListBox;
import org.gnome.gtk.MediaControls;
import org.javagi.base.Out;
import org.javagi.glib.types.VariantTypes;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
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

    private AppPipeline appPipeline;

    private ExecutorService exec;

    private final AlbumTextureLoader loader = AlbumTextureLoader.getInstance();

    private final SongQueue songQueue = new SongQueue();

    public MediaSubsonic stream;

    public void initPlayer() {
        if (this.subsonic == null && exec == null) {
            var window = (GrooveboatWindow) getRoot();
            var app = (GrooveboatApp) window.getApplication();

            this.exec = window.executor;
            this.subsonic = window.subsonic;
            this.appPipeline = app.pipeline;

            setupActions(window);

            var bus = appPipeline.getBus();
            bus.addWatch(0, this::handleBusMessage);

            if (songQueue.isEmpty()) {
                var initSong = new QSongModel().findList().getFirst();
                var action = window.lookupAction("queue-song");
                action.activate(Variant.string(initSong.getSongId()));
            }
        }
    }

    public boolean handleBusMessage(Bus bus, Message msg) {
        if (msg.readType().contains(MessageType.EOS)) {
            logger.info("End of Stream");
            exec.submit(this::playNext);
        } else if (msg.readType().contains(MessageType.ERROR)) {
            Out<GError> error = new Out<>();
            Out<String> debug = new Out<>();
            msg.parseError(error, debug);

            logger.warn("Error: {}", error.get().readMessage());

            appPipeline.setState(State.NULL);
            appPipeline.setState(State.PAUSED);
        }

        return true;
    }

    private void manipulateQueue(int pos, int length) {
        // All textures which will be used here are in the cache already
        assert Thread.currentThread().getName().equals("main") : "Must be called from main thread";

        if (length == 0) {
            // this is the replacement case
            queueListBox.removeAll();
            var nextSong = songQueue.takeSong();
            var texture = loader.load(nextSong.getAlbum().getAlbumId());

            for (var it = songQueue.iterate(); it.hasNext(); ) {
                var song = it.next();
                var card = getQueueCard(texture, song);
                queueListBox.append(card);
            }

            initializeMediaStream(nextSong, texture, true);
        } else {
            // this is the add to queue case
            // TODO: differentiate on initial queue
            if (songQueue.lengthOne()) {
                var nextSong = songQueue.takeSong();
                var texture = loader.load(nextSong.getAlbum().getAlbumId());
                initializeMediaStream(nextSong, texture, false);
            } else {
                for (var i = pos; i < (pos + length); i++) {
                    var song = songQueue.getAt(i);
                    var texture = loader.load(song.getAlbum().getAlbumId());
                    var card = getQueueCard(texture, song);
                    this.queueListBox.append(card);
                }
            }
        }
    }

    private static QueueCard getQueueCard(Texture texture, SongModel song) {
        var card = new QueueCard();
        var coverArt = new RoundedImage();
        coverArt.setTexture(texture);
        coverArt.setPixelSize(40);
        card.setTitle(song.getTitle());
        card.setSubtitle(song.getDisplayArtist());
        card.addPrefix(coverArt);
        card.setSongId(song.getSongId());
        return card;
    }

    private void playNext() {
        assert !Thread.currentThread().getName().equals("main") : "Must not be called from main thread";
        var newSong = songQueue.takeSong();
        if (newSong != null) {
            var texture = loader.load(newSong.getAlbum().getAlbumId());

            GLib.idleAddOnce(() -> {
                var oldSong = queueListBox.getFirstChild();
                queueListBox.remove(oldSong);
                initializeMediaStream(newSong, texture, true);
            });
        }
    }

    private void initializeMediaStream(SongModel song, Texture albumCover, boolean shouldPlay) {
        assert Thread.currentThread().getName().equals("main") : "Must be called from main thread";
        this.mediaControls.setMediaStream(null);

        if (stream != null) {
            stream.destroyStream();
            stream = null;
        }

        stream = new MediaSubsonic(
                song.getSongId(),
                song.getDuration().getSeconds(),
                subsonic,
                appPipeline,
                exec,
                song.getContentType()
        );

        this.mediaControls.setMediaStream(stream);
        stream.initializeStream();
        stream.setPlaying(shouldPlay);
        this.playerAlbumArtist.setLabel(song.getDisplayArtist());
        this.playerSongName.setLabel(song.getTitle());
        this.playerAlbumPicture.setTexture(albumCover);
    }

    private void setupActions(GrooveboatWindow win) {
        var entries = new ActionEntry[4];
        entries[0] = new ActionEntry(
                "play-song",
                (action, parameter) -> {
                    if (parameter != null) {
                        exec.submit(() -> {
                            logger.info("Playing this song:");
                            var songId = parameter.getString(null);
                            var song = new QSongModel().songId.equalTo(songId).findOne();
                            if (song != null) {
                                songQueue.replace(List.of(song));
                                GLib.idleAddOnce(() -> manipulateQueue(0, 0));
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
                                int pos = songQueue.length();
                                int length = 1;
                                songQueue.addSongList(List.of(song));
                                GLib.idleAddOnce(() -> manipulateQueue(pos, length));
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
                                var songs = album.getSongs();
                                songQueue.replace(songs);
                                GLib.idleAddOnce(() -> manipulateQueue(0, 0));
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
                                var songs = album.getSongs();
                                var pos = songQueue.length();
                                var length = songs.size();
                                songQueue.addSongList(songs);
                                GLib.idleAddOnce(() -> manipulateQueue(pos, length));
                            }
                        });
                    }
                },
                VariantTypes.STRING.dupString(),
                null,
                null,
                Arena.global()
        );
        win.addActionEntries(entries, null);
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
            stream.destroyStream();
        }
    }
}

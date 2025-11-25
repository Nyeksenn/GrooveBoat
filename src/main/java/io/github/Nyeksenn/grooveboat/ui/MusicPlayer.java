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
import org.gnome.graphene.Rect;
import org.gnome.gtk.*;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/music-player.ui", name = "MusicPlayer")
@RegisteredType(name = "MusicPlayer")
public class MusicPlayer extends Box {
    private static final Logger logger = LoggerFactory.getLogger(MusicPlayer.class);
    private int currQueuePos = 0;

    @GtkChild(name = "player-album-picture")
    public RoundedImage playerAlbumPicture;

    @GtkChild(name = "player-song-name")
    public Label playerSongName;

    @GtkChild(name = "player-album-artist")
    public Label playerAlbumArtist;

    @GtkChild(name = "music-controls")
    public MusicControls mediaControls;

    @GtkChild(name = "queue-list-box")
    public ListBox queueListBox;

    @GtkChild(name = "queue-scroll-window")
    public ScrolledWindow queueScrollWindow;

    private Subsonic subsonic;

    private AppPipeline appPipeline;

    private ExecutorService exec;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

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
            activateActionIfExists("win.play-next", null);
        } else if (msg.readType().contains(MessageType.ERROR)) {
            Out<GError> error = new Out<>();
            Out<String> debug = new Out<>();
            msg.parseError(error, debug);

            logger.warn("Error: {}", error.get().readMessage());

            appPipeline.setState(State.NULL);
            appPipeline.setState(State.PAUSED);
        } else if (msg.readType().contains(MessageType.STATE_CHANGED)) {
            Out<State> prev = new Out<>();
            Out<State> curr = new Out<>();
            msg.parseStateChanged(prev, curr, null);
            if (prev.get().equals(State.PAUSED) && curr.get().equals(State.PLAYING)) {
                stream.startTimestampUpdate();
            } else if (curr.get().equals(State.PAUSED) && prev.get().equals(State.PLAYING)) {
                stream.cancelTimestampUpdate();
            }
        }

        return true;
    }

    private void changeListBox(int insertPosition, int length) {
        // All textures which will be used here are in the cache already
        assert Thread.currentThread().getName().equals("main") : "Must be called from main thread";

        if (length == 0) {
            // this is the replacement case
            queueListBox.removeAll();
            var firstSong = songQueue.getAt(0);
            var texture = loader.load(firstSong.getAlbum().getAlbumId());

            for (var it = songQueue.iterate(); it.hasNext(); ) {
                var song = it.next();
                var card = getQueueCard(texture, song);
                queueListBox.append(card);
            }
            repositionPlayHead(0, true);
        } else {
            // this is the add to queue case
            if (songQueue.lengthOne() && stream == null) {
                var song = songQueue.getAt(0);
                var texture = loader.load(song.getAlbum().getAlbumId());
                var card = getQueueCard(texture, song);
                this.queueListBox.append(card);
                repositionPlayHead(0, false);
            } else {
                for (var i = insertPosition; i < (insertPosition + length); i++) {
                    var song = songQueue.getAt(i);
                    var texture = loader.load(song.getAlbum().getAlbumId());
                    var card = getQueueCard(texture, song);
                    this.queueListBox.append(card);
                }
            }
        }
    }

    private void repositionPlayHead(int pos, boolean shouldPlay) {
        var song = songQueue.getAt(pos);
        var queueCard = queueListBox.getRowAtIndex(pos);
        scrollToCard(queueCard);
        var texture = loader.load(song.getAlbum().getAlbumId());
        initializeMediaStream(song, texture, shouldPlay);
    }

    private void scrollToCard(ListBoxRow card) {
        var vadj = queueScrollWindow.getVadjustment();
        var bounds = new Rect();
        if (card.computeBounds(queueListBox, bounds)) {
            vadj.setValue(bounds.getY());
        } else {
            logger.warn("Unable to compute bounds for queue row item");
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

    private void initializeMediaStream(SongModel song, Texture albumCover, boolean shouldPlay) {
        assert Thread.currentThread().getName().equals("main") : "Must be called from main thread";
        this.mediaControls.setMediaStream(null);

        if (stream != null) {
            stream.destroyStream();
            stream = null;
        }

        stream = new MediaSubsonic(
                song.getSongId(),
                song.getDuration().getSeconds() * 1000000L,
                subsonic,
                appPipeline,
                exec,
                song.getContentType(),
                scheduler
        );

        stream.initializeStream();
        this.mediaControls.setMediaStream(stream);
        stream.setPlaying(shouldPlay);
        this.playerAlbumArtist.setLabel(song.getDisplayArtist());
        this.playerSongName.setLabel(song.getTitle());
        this.playerAlbumPicture.setTexture(albumCover);
    }

    private void setupActions(GrooveboatWindow win) {
        var entries = new ActionEntry[6];
        entries[0] = new ActionEntry(
                "play-song",
                (action, parameter) -> {
                    currQueuePos = 0;
                    if (parameter != null) {
                        exec.submit(() -> {
                            logger.info("Playing this song:");
                            var songId = parameter.getString(null);
                            var song = new QSongModel().songId.equalTo(songId).findOne();
                            if (song != null) {
                                songQueue.replace(List.of(song));
                                GLib.idleAddOnce(() -> changeListBox(0, 0));
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
                                GLib.idleAddOnce(() -> changeListBox(pos, length));
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
                    currQueuePos = 0;
                    if (parameter != null) {
                        exec.submit(() -> {
                            var albumId = parameter.getString(null);
                            var album = new QAlbumModel().albumId.equalTo(albumId).findOne();
                            if (album != null) {
                                var songs = album.getSongs();
                                songQueue.replace(songs);
                                GLib.idleAddOnce(() -> changeListBox(0, 0));
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
                                GLib.idleAddOnce(() -> changeListBox(pos, length));
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
                "play-next",
                (action, parameter) -> {
                    currQueuePos += 1;
                    repositionPlayHead(currQueuePos, true);
                },
                null,
                null,
                null,
                Arena.global()
        );
        entries[5] = new ActionEntry(
                "play-previous",
                (action, parameter) -> {
                    currQueuePos -= 1;
                    repositionPlayHead(currQueuePos, true);
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
        if (stream != null) {
            stream.destroyStream();
        }
        scheduler.shutdownNow();
    }
}

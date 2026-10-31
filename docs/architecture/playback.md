# Playback

Playback has four parts: one long-lived **player** (a playbin3 pipeline), a **playback source** that the backend builds for each song, one long-lived **media stream** that GTK widgets bind to, and the **play queue** that decides which song is current. Audio is always the original file. The client never asks for transcoding, so the local GStreamer installation is expected to decode every format in the library.

Songs follow each other **gaplessly**. The next song is handed to the player before the current one ends, and the player switches over without stopping.

## Player (`AppPipeline`)

The player is a single `playbin3`, GStreamer's own high-level player element, created at startup and kept for the whole process. It is restricted to audio, so containers with embedded cover art don't get video branches. playbin3 builds and replaces its internal elements itself (the source, HLS demuxer, demuxers, decoders, audio sink); the app only ever changes its URL.

- **Starting a song** means setting the player to ready, setting the new URL, and prerolling to paused.
- **Stopping** returns it to ready.
- **Gapless continuation:** shortly before the current song runs out of data, playbin3 emits *about-to-finish* on one of its streaming threads. The player then asks a supplier for the next URL. If it gets one, it sets it right away, and playbin3 moves on to that song without a gap. When the new song reaches the audio sink, playbin3 posts a *stream-start* message. If the supplier has nothing, the player ends with end-of-stream.
- **Seeking, position and volume:** time-based seeks, position queries (relative to the current song), and volume and mute through playbin3's own properties. GTK's cubic volume scale is converted to playbin3's linear one.
- **Plugin check:** it can list required GStreamer elements that are not installed, which the window uses at startup.

The player runs its own GLib main loop on a dedicated thread, separate from the GTK main loop.

### Bus messages

The application subscribes to exactly four message types, each through the bus's per-type message signal, and handles them on the GTK main thread:

- **Stream start:** after a gapless switch, the media stream moves to the new song and the queue follows (see below).
- **End of stream:** only happens when no next song was handed over in time. The queue advances manually, or, at the end of the queue, the stream stops and rewinds.
- **Error:** the stream stops without restarting the failed download, a toast names the song, and the queue moves on. Only the error message is logged. GStreamer's debug text can contain the stream URL, and with it the credentials.
- **Buffering:** while playing, the player pauses when its buffer runs low and resumes when the buffer is full again, provided the user still wants to play. During a gapless switch, low-buffer messages from the next song are ignored: the current song is still playing out, and pausing would cause a gap.

Two things are deliberately avoided:

- **No catch-all bus watch.** Decoding a message's type in java-gi 0.14.1 (and still in 1.0.0-RC3) never returns for GStreamer's "extended" message types, such as stream-collection and streams-selected, which playbin3 posts for every song. The flag conversion uses a signed shift, so the loop never ends. With per-type signals, those messages never reach Java code.
- **No state-changed handling.** playbin3 posts state changes for each of its internal elements. Reading the source of each message created a java-gi proxy for elements that playbin3 tears down at any time, which led to crashes in java-gi's reference cleanup. The position ticker follows the stream's own play and pause calls instead.

## Playback sources

The backend turns a song ID and its stored content type into a *playback source*: a URL for the player, whether it is HLS, and a server-side play session ID where there is one. Building the URL needs no request to the server, so building one that is never used costs nothing.

- **Jellyfin:** if the song's codec can be carried in fMP4 segments (FLAC, ALAC, AAC, MP3, Opus, AC-3, E-AC-3), the URL is the song's HLS master playlist. It asks for that same codec in fMP4 segments, so the server only repackages the audio without re-encoding, and includes a new play session ID. Other formats get the original-file stream as a progressive download.
- **OpenSubsonic:** always the stream endpoint asking for the raw original format, as a progressive download. The protocol's HLS endpoint segments by transcoding, which the app doesn't use.

Both kinds of URL carry their credentials in the query string, because GStreamer's HLS demuxer fetches playlists and segments itself and can't be relied on to send custom headers. Jellyfin repeats its API key parameter in every variant and segment URL; the Subsonic auth parameters are part of the URL anyway. A playback source never logs its URL: its text form cuts off the query string.

For Jellyfin, *releasing* a playback source tells the server to stop the segmenting job for that device and play session. Otherwise it would keep running until its idle timeout. A source is released once its song has stopped playing: when another song is loaded, after a gapless switch away from it, or when playback stops.

## Media stream (`RemoteMediaStream`)

`RemoteMediaStream` subclasses GTK's media stream class, so GTK widgets and property bindings can treat it like any other playable media. There is **one instance for the whole process**, created at startup and bound to the player widgets once. Songs are loaded into it rather than creating a stream per song. That keeps the widgets' bindings stable and avoids creating and dropping a GObject for every track.

The stream tracks three songs: the **current** one, the **next** one prepared for gapless playback, and the one the player has accepted for a **switch** that hasn't started yet. Each comes with its playback source and the backend that has to release it.

- **Loading a song** (the user picked something, or a manual skip): play sessions for the current and any pending song are released, a new source is built, the player starts it, and the stream is marked as prepared with the song's duration from the cache.
- **Preparing the next song:** the queue tells the stream which song follows. The stream builds its source in advance, so the player's supplier on the streaming thread only has to take it, and records it as the pending switch.
- **Following a gapless switch:** on stream-start, the pending song becomes current and the previous source is released. The stream re-prepares itself with the new song's duration. GTK's way to change the duration briefly marks the stream as paused; while this happens the stream is *detached*, so those calls don't reach the player, and the playing state is restored afterwards.
- **Play, pause, seek and volume** go straight to the player. Play starts the position ticker and pause stops it.
- **Position updates:** while playing, a scheduled task asks the player for its position at a short fixed interval and posts the update to the GTK main loop, which drives the stream's timestamp property.
- **End of queue, errors and stop** each stop the player in the appropriate way and release play sessions. Stop is used for logout and shutdown, and also clears the prepared next song.

## Play queue (`QueueStore`)

The queue is a GObject that holds a list store of song list items and a single-selection model on top of it. The selected item is the current song.

**Queue actions** (registered on the application, each taking a song ID, album ID or index):

- *Play song* and *play album* replace the queue.
- *Queue song* and *queue album* append to it.
- *Remove song* removes the entry at a given position.

Each action reads its parameter immediately, because callback arguments are only valid during the callback. The lookup itself then runs on the background pool: it reads entities from the cache, gets the album texture, builds list items, and posts the list-store changes back to the UI thread.

**Advancing:** *play next* (the action, and the end-of-stream fallback) restarts the current song in loop mode, or otherwise selects the following song, and reports whether there was one. *Play previous* selects the song before the current one.

**When the selection changes** because of the user, a manual skip or end-of-stream, and a session is active, the queue:

1. loads the selected song into the media stream;
2. emits its *initialize-song* signal with the selected item;
3. starts playback, except for the first song after startup or after the queue was cleared, so a restored song is ready but does not start on its own.

**Next-song bookkeeping:** whenever the selection, the queue contents or loop mode change, the queue tells the stream which song follows: the same song in loop mode, the next entry otherwise, or none at the end of the queue.

**After a gapless switch**, the queue moves its selection to the song the stream is now playing *without* reloading it, emits *initialize-song* so the widgets update, and prepares the song after that.

When the library is first shown, the queue is seeded with a song from the cache, if there is one and the queue is still empty. Logout clears the queue.

## Player widgets

The full player, the compact mini player and the transport controls bind to the single media stream once, when they are realised:

- the play/pause icon follows *playing*;
- the position slider and time labels follow *timestamp* and *duration*, and the slider's range follows the duration of each song;
- the slider is only enabled while the stream is *prepared* and not *seeking*.

They listen for the queue's *initialize-song* signal only to update the cover, title and artist, and disconnect from it when they are unrealised. Volume and seek changes from the controls go straight to the stream. The loop toggle switches the application's loop mode, which also changes the prepared next song. The queue view shows the queue's selection model directly.

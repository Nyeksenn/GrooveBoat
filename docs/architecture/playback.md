# Playback

Playback has three parts: a long-lived **pipeline**, a short-lived **media stream** for each song, and the **play queue** that decides which song is current.

## Pipeline (`AppPipeline`)

One GStreamer pipeline exists for the whole life of the application. It is made of:

- **an app source**, swapped out for every song, which receives raw bytes pushed in from Java;
- **a decode bin**, which detects the format and decodes it;
- **an audio converter and resampler**, which normalise the decoded audio;
- **an automatic audio sink**, which outputs to the system's default device.

The decoder's output pad only appears once the format is known, so it is linked to the converter on the fly when the pad shows up.

The pipeline runs its own GLib main loop on a dedicated thread, separate from the GTK main loop. It offers state changes, time-based seeking, position queries and volume control, using whichever element in the pipeline implements the stream-volume interface. Messages from the pipeline's bus are handled by the application:

- **End of stream** triggers the play-next action, passing along whether loop mode is on.
- **Error** logs the error and resets the pipeline to paused.
- **State change** starts the position ticker when playback goes from paused to playing, and stops it when playback goes from playing to paused.

## Media stream (`MediaSubsonic`)

`MediaSubsonic` subclasses GTK's media stream class, so GTK widgets and property bindings can treat it like any other playable media. A new one is created for every song, and the previous one is destroyed first.

- **Creation.** A new app source is added to the pipeline and linked to the decoder. It is set up as seekable, time-based and non-live, with the song's duration and three callbacks: need data, enough data and seek data.
- **Initialisation.** If the song's MIME type has a known GStreamer equivalent, the source is given explicit caps. Otherwise the decoder works the format out itself. The pipeline is paused and the stream is marked as prepared with the song's duration, at full volume and unmuted.
- **Feeding data.** When the source asks for data, a background task reads fixed-size chunks from the HTTP response and pushes them in. It stops when the source says it has enough, when a flush is in progress, or when a short read signals the end of the file (in which case end-of-stream is sent).
- **Seeking.** A seek from the UI becomes a pipeline seek. The pipeline then calls the seek-data callback, which closes the current HTTP response and opens a new stream request at the matching time offset, so data starts flowing from the new position. A lock keeps the feeder task and the seek handler from running at the same time.
- **Position updates.** While playing, a scheduled task asks the pipeline for its position at a short fixed interval and posts the update to the GTK main loop. This drives the stream's timestamp property.
- **Destruction.** The HTTP response is closed, end-of-stream is sent, the source is taken out of the pipeline, the pipeline goes back to ready, the callback arena is released, the ticker stops, and the stream is marked as unprepared.

## Play queue (`QueueStore`)

The queue is a GObject that holds a list store of song list items and a single-selection model on top of it. The selected item is the current song.

**Queue actions** (registered on the application, each taking a song ID, album ID or index):

- *Play song* and *play album* replace the queue.
- *Queue song* and *queue album* append to it.
- *Remove song* removes the entry at a given position.

Each lookup runs on the background pool. It reads entities from the cache, gets the album texture, builds list items, and posts the list-store changes back to the UI thread.

**Playback actions:**

- *Play next* selects the following item. In loop mode it re-sends the selection change for the current item, so the same song restarts.
- *Play previous* selects the item before the current one.

**When the selection changes**, the queue:

1. destroys the old media stream through the application;
2. creates a new one for the selected song;
3. emits its *initialize-song* signal with the selected item;
4. toggles playback, except the first time, so the song restored at startup is ready but does not start on its own.

## Player widgets

The full player, the compact mini player and the transport controls all listen for the queue's *initialize-song* signal. When a new song starts they:

- update the cover, title and artist labels;
- bind their controls to the new media stream's properties: the play/pause icon follows *playing*; the position slider and time labels follow *timestamp* and *duration*; the slider is only enabled while the stream is *prepared* and not *seeking*.

Volume and seek changes from the controls go straight to the current stream. The loop toggle sets the application's loop flag, which the end-of-stream handler reads. The queue view shows the queue's selection model directly.

package io.github.Nyeksenn.grooveboat.audio;

import org.freedesktop.gstreamer.app.AppSrc;
import org.freedesktop.gstreamer.app.AppSrcCallbacks;
import org.freedesktop.gstreamer.app.AppStreamType;
import org.freedesktop.gstreamer.gst.*;
import org.gnome.glib.GError;
import org.javagi.base.Out;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.stream.Stream;

public final class AppPipeline {
    private static final Logger logger = LoggerFactory.getLogger(AppPipeline.class);
    public Pipeline pipeline;
    private Bus bus;
    public static final int BUFFER_SIZE = 8192;
    private int busWatchId;
    public Element audioconvert;
    public Element decodebin;
    public Element audioresample;
    public Element autoaudiosink;

    private boolean busCall(Bus bus, Message msg) {
        if (msg.readType().contains(MessageType.EOS)) {
            logger.info("End of Stream");
        } else if (msg.readType().contains(MessageType.ERROR)) {
            Out<GError> error = new Out<>();
            Out<String> debug = new Out<>();
            msg.parseError(error, debug);

            logger.warn("Error: {}", error.get().readMessage());

            pipeline.setState(State.NULL);
            pipeline.setState(State.PAUSED);
        }

        return true;
    }

    private void onPadAdded(Pad pad) {
        Pad sinkpad = audioconvert.getStaticPad("sink");

        if (sinkpad != null) {
            pad.link(sinkpad);
            pad.getParentElement().syncStateWithParent();
        } else {
            logger.warn("Sink pad not set");
        }
    }

    public AppSrc initNewAppSrc(long duration, AppSrcCallbacks callbacks, Caps caps) {
        pipeline.setState(State.NULL);
        var appsrc = new AppSrc();
        appsrc.setName("appsrc");
        appsrc.setStreamType(AppStreamType.SEEKABLE);
        appsrc.setFormat(Format.TIME);
        //appsrc.setMaxBuffers(BUFFER_SIZE * 10L);
        appsrc.setMaxTime(new ClockTime(20000000000L));
        appsrc.setLive(false);
        appsrc.setProperty("block", false);
        appsrc.decorateStreamId("grooveboat-appsrc-stream");
        appsrc.setDuration(new ClockTime(duration));
        appsrc.setCallbacks(callbacks, null);
        appsrc.setCapabilities(caps);

        pipeline.add(appsrc);
        var decoder = pipeline.getByName("decoder");
        appsrc.link(decoder);

        pipeline.setState(State.READY);

        return appsrc;
    }

    public AppPipeline() {
        Gst.init(null);
        pipeline = new Pipeline("grooveboat-player");
        decodebin = ElementFactory.make("decodebin", "decoder");
        audioconvert = ElementFactory.make("audioconvert", "converter");
        audioresample = ElementFactory.make("audioresample", "resample");
        autoaudiosink = ElementFactory.make("autoaudiosink", "sink");

        if (Stream.of(decodebin, audioconvert, audioresample, autoaudiosink).anyMatch(Objects::isNull)) {
            logger.warn("Couldn't create a gstreamer elmenet");
            return;
        }

        bus = pipeline.getBus();
        busWatchId = bus.addWatch(0, this::busCall);

        pipeline.addMany(decodebin, audioconvert, audioresample, autoaudiosink, null);
        audioconvert.linkMany(audioresample, autoaudiosink, null);
        decodebin.onPadAdded(this::onPadAdded);

        pipeline.setState(State.READY);
    }
}

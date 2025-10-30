package io.github.Nyeksenn.grooveboat.audio;

import org.freedesktop.gstreamer.app.AppSrc;
import org.freedesktop.gstreamer.app.AppStreamType;
import org.freedesktop.gstreamer.gst.*;
import org.gnome.glib.GError;
import org.javagi.base.Out;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.stream.Stream;

public class AppPipeline {
    private static final Logger logger = LoggerFactory.getLogger(AppPipeline.class);
    private Pipeline pipeline;
    private Bus bus;
    private int busWatchId;
    public AppSrc appsrc;
    public Element audioconvert;
    public Element decodebin;
    public Element audioresample;
    public Element autoaudiosink;

    private boolean busCall(Bus bus, Message msg) {
        if (msg.readType().contains(MessageType.EOS)) {
            logger.info("End of Stream");
            pipeline.setState(State.NULL);
        } else if (msg.readType().contains(MessageType.ERROR)) {
            Out<GError> error = new Out<>();
            Out<String> debug = new Out<>();
            msg.parseError(error, debug);

            logger.warn("Error: {}", error.get().readMessage());

            pipeline.setState(State.NULL);
        }

        return true;
    }

    private void onPadAdded(Pad pad) {
        Pad sinkpad = audioconvert.getStaticPad("sink");

        if (sinkpad != null) {
            pad.link(sinkpad);
        } else {
            logger.warn("Sink pad not set");
        }
    }

    public AppPipeline() {
        Gst.init(null);
        pipeline = new Pipeline("grooveboat-player");
        appsrc = new AppSrc();
        appsrc.setName("appsrc");
        appsrc.setStreamType(AppStreamType.STREAM);
        appsrc.setMaxBuffers(100);
        appsrc.setLive(false);
        appsrc.setProperty("block", true);
        decodebin = ElementFactory.make("decodebin", "decoder");
        audioconvert = ElementFactory.make("audioconvert", "converter");
        audioresample = ElementFactory.make("audioresample", "resample");
        autoaudiosink = ElementFactory.make("autoaudiosink", "sink");

        if (Stream.of(appsrc, decodebin, audioconvert, audioresample, autoaudiosink).anyMatch(Objects::isNull)) {
            logger.warn("Couldn't create a gstreamer elmenet");
            return;
        }

        bus = pipeline.getBus();
        busWatchId = bus.addWatch(0, this::busCall);

        pipeline.addMany(appsrc, decodebin, audioconvert, audioresample, autoaudiosink, null);

        appsrc.link(decodebin);
        audioconvert.linkMany(audioresample, autoaudiosink, null);
        decodebin.onPadAdded(this::onPadAdded);
    }
}

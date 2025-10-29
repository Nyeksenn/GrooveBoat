package io.github.Nyeksenn.grooveboat;

import io.github.jwharm.javagi.base.Out;
import org.freedesktop.gstreamer.gst.*;
import org.gnome.glib.GError;
import org.gnome.glib.MainLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.stream.Stream;

public class AppPipeline {
    private static final Logger logger = LoggerFactory.getLogger(AppPipeline.class);
    private MainLoop loop;
    private Pipeline pipeline;
    private Bus bus;
    private int busWatchId;
    public Element appsrc;
    public Element audioconvert;

    private boolean busCall(Bus bus, Message msg) {
        if (msg.readType().contains(MessageType.EOS)) {
            logger.info("End of Stream");
            loop.quit();
        } else if (msg.readType().contains(MessageType.ERROR)) {
            Out<GError> error = new Out<>();
            Out<String> debug = new Out<>();
            msg.parseError(error, debug);

            logger.warn("Error: {}", error.get().readMessage());

            loop.quit();
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

    public void run() {
        loop.run();
    }

    public AppPipeline() {
        Gst.init(new Out<>());
        loop = new MainLoop(null, false);

        pipeline = new Pipeline("grooveboat-player");
        appsrc = ElementFactory.make("appsrc", "app-source");
        var decodebin = ElementFactory.make("decodebin", "decoder");
        audioconvert = ElementFactory.make("audioconvert", "converter");
        var audioresample = ElementFactory.make("audioresample", "resample");
        var autoaudiosink = ElementFactory.make("autoaudiosink", "sink");

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

package io.github.Nyeksenn.grooveboat.audio;

import org.freedesktop.gstreamer.app.AppSrc;
import org.freedesktop.gstreamer.app.AppSrcCallbacks;
import org.freedesktop.gstreamer.app.AppStreamType;
import org.freedesktop.gstreamer.audio.StreamVolume;
import org.freedesktop.gstreamer.gst.*;
import org.javagi.base.Out;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.stream.Stream;

import static org.freedesktop.gstreamer.gst.Gst.CLOCK_TIME_NONE;

public final class AppPipeline {
    private static final Logger logger = LoggerFactory.getLogger(AppPipeline.class);
    private Pipeline pipeline;
    private Bus bus;
    public static final int BUFFER_SIZE = 8192;
    private Element audioconvert;
    private Element decodebin;
    private Element audioresample;
    private Element autoaudiosink;

    private AppPipeline() {
        Gst.init(null);
        pipeline = new Pipeline("grooveboat-player");
        decodebin = ElementFactory.make("decodebin", "decoder");
        audioconvert = ElementFactory.make("audioconvert", "converter");
        audioresample = ElementFactory.make("audioresample", "resample");
        autoaudiosink = ElementFactory.make("autoaudiosink", "sink");

        if (Stream.of(decodebin, audioconvert, audioresample, autoaudiosink).anyMatch(Objects::isNull)) {
            logger.warn("Couldn't create a gstreamer element");
            return;
        }

        pipeline.addMany(decodebin, audioconvert, audioresample, autoaudiosink, null);
        audioconvert.linkMany(audioresample, autoaudiosink, null);
        decodebin.onPadAdded(this::onPadAdded);

        bus = pipeline.getBus();

        pipeline.setState(State.READY);
    }

    private static class Holder {
        private static final AppPipeline INSTANCE = new AppPipeline();
    }

    public static AppPipeline getInstance() {
        return Holder.INSTANCE;
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

    public StateChangeReturn setState(State state) {
        return pipeline.setState(state);
    }

    public boolean seek(long timestampMs) {
        return pipeline.seek(1.0, Format.TIME, SeekFlags.FLUSH, SeekType.SET, timestampMs * 1000, SeekType.NONE, CLOCK_TIME_NONE.getValue());
    }

    public StreamVolume getStreamVolume() {
        var sv = pipeline.getByInterface(StreamVolume.getType());
        if (sv != null) {
            return (StreamVolume) sv;
        }
        return null;
    }

    public boolean removeAppSrc(AppSrc appSrc) {
        return pipeline.remove(appSrc);
    }

    public long queryPosition() {
        var pos = new Out<Long>();
        var success = pipeline.queryPosition(Format.TIME, pos);
        if (success) {
            return pos.get() / 1000L;
        } else {
            return -1L;
        }
    }

    public Bus getBus() {
        return bus;
    }

    public AppSrc initNewAppSrc(long duration, AppSrcCallbacks callbacks, Caps caps) {
        pipeline.setState(State.NULL);
        var appsrc = new AppSrc();
        appsrc.setName("appsrc");
        appsrc.setStreamType(AppStreamType.SEEKABLE);
        appsrc.setFormat(Format.TIME);
        appsrc.setMaxTime(new ClockTime(40000000000L));
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
}

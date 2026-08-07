package fmfeel;

import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadProbeReturn;
import org.freedesktop.gstreamer.PadProbeType;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.TagList;
import org.freedesktop.gstreamer.Version;

public final class RadioPlayer {

    public interface Listener {
        void onStationName(String name);
        void onTitle(String title);
    }

    private static final Listener NO_OP = new Listener() {
        @Override public void onStationName(String name) { }
        @Override public void onTitle(String title) { }
    };

    private final Listener listener;
    private final Object tagLock = new Object();
    private String stationName = "";
    private String title = "";

    private final Pipeline noisePipeline;
    private final Element noiseVolume;

    private Pipeline stationPipeline;
    private Element stationVolume;
    private volatile boolean stationReady;

    private double stationGain = 1.0;
    private double noiseGain = 1.0;
    private String url;
    private boolean playing;

    public RadioPlayer(String url) {
        this(url, null);
    }

    public RadioPlayer(String url, Listener listener) {
        this.listener = listener == null ? NO_OP : listener;

        Gst.init(Version.BASELINE, "fmfeel");

        noisePipeline = new Pipeline("noise");

        Element src = ElementFactory.make("audiotestsrc", "noisesrc");
        src.set("wave", 5);
        src.set("is-live", true);

        Element convert = ElementFactory.make("audioconvert", "noiseconv");
        Element resample = ElementFactory.make("audioresample", "noiseres");
        noiseVolume = ElementFactory.make("volume", "noisevol");
        noiseVolume.set("volume", noiseGain);
        Element sink = ElementFactory.make("autoaudiosink", "noisesink");

        noisePipeline.addMany(src, convert, resample, noiseVolume, sink);
        Element.linkMany(src, convert, resample, noiseVolume, sink);

        noisePipeline.getBus().connect((Bus.ERROR) (source, code, message) ->
                System.err.println("noise: " + message));

        setUrl(url);
    }

    public synchronized void play() {
        playing = true;
        noisePipeline.setState(State.PLAYING);
        if (stationPipeline != null) {
            stationPipeline.setState(State.PLAYING);
        }
    }

    public synchronized void stop() {
        playing = false;
        destroyStation();
        noisePipeline.setState(State.NULL);
    }

    public synchronized void setUrl(String uri) {
        destroyStation();
        this.url = uri;
        if (uri != null) {
            createStation(uri);
        }
    }

    public synchronized String getUrl() {
        return url;
    }

    public boolean isStationReady() {
        return stationReady;
    }

    public String getStationName() {
        synchronized (tagLock) {
            return stationName;
        }
    }

    public String getTitle() {
        synchronized (tagLock) {
            return title;
        }
    }

    public synchronized void setStationVolume(double value) {
        stationGain = clamp(value);
        if (stationVolume != null && stationReady) {
            stationVolume.set("volume", stationGain);
        }
    }

    public synchronized void setNoiseVolume(double value) {
        noiseGain = clamp(value);
        noiseVolume.set("volume", noiseGain);
    }

    private void createStation(String uri) {
        Pipeline p = new Pipeline("station");

        Element src = ElementFactory.make("souphttpsrc", "httpsrc");
        src.set("location", uri);
        src.set("is-live", true);
        src.set("iradio-mode", true);
        src.set("user-agent", "fmfeel/1.0");
        src.set("timeout", 10);

        Element dec = ElementFactory.make("decodebin", "decodebin");
        Element convert = ElementFactory.make("audioconvert", "stationconv");
        Element resample = ElementFactory.make("audioresample", "stationres");
        Element volume = ElementFactory.make("volume", "stationvol");
        volume.set("volume", 0.0);
        Element sink = ElementFactory.make("autoaudiosink", "stationsink");

        p.addMany(src, dec, convert, resample, volume, sink);
        src.link(dec);
        Element.linkMany(convert, resample, volume, sink);

        dec.connect((Element.PAD_ADDED) (element, pad) -> {
            Caps caps = pad.getCurrentCaps();
            if (caps == null || !caps.toString().startsWith("audio/")) {
                return;
            }
            Pad target = convert.getStaticPad("sink");
            if (target.isLinked()) {
                return;
            }
            pad.link(target);
            pad.addProbe(PadProbeType.BUFFER, (probePad, info) -> {
                stationReady = true;
                volume.set("volume", stationGain);
                return PadProbeReturn.REMOVE;
            });
        });

        Bus bus = p.getBus();
        bus.connect((Bus.TAG) (source, tags) -> {
            String name = tagString(tags, "organization");
            if (name != null) {
                emitStationName(name);
            }
            String song = tagString(tags, "title");
            if (song != null) {
                emitTitle(song);
            }
        });
        bus.connect((Bus.ERROR) (source, code, message) -> {
            System.err.println("station: " + message);
            clearTags();
        });

        stationPipeline = p;
        stationVolume = volume;
        stationReady = false;

        if (playing) {
            p.setState(State.PLAYING);
        }
    }

    private void destroyStation() {
        Pipeline p = stationPipeline;
        stationPipeline = null;
        stationVolume = null;
        stationReady = false;
        clearTags();
        if (p != null) {
            p.setState(State.NULL);
        }
    }

    private void clearTags() {
        emitStationName("");
        emitTitle("");
    }

    private void emitStationName(String value) {
        String next = value == null ? "" : value.trim();
        synchronized (tagLock) {
            if (next.equals(stationName)) {
                return;
            }
            stationName = next;
        }
        listener.onStationName(next);
    }

    private void emitTitle(String value) {
        String next = value == null ? "" : value.trim();
        synchronized (tagLock) {
            if (next.equals(title)) {
                return;
            }
            title = next;
        }
        listener.onTitle(next);
    }

    private static String tagString(TagList tags, String tag) {
        try {
            return tags.getString(tag, 0);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static void main(String[] args) throws InterruptedException {
        String station = "https://cdn.btv.bg/radio/btv-radio.mp3";

        RadioPlayer player = new RadioPlayer(station, new Listener() {
            @Override public void onStationName(String name) {
                System.out.println("name  = [" + name + "]");
            }
            @Override public void onTitle(String title) {
                System.out.println("title = [" + title + "]");
            }
        });

        player.play();

        for (int i = 0; i < 5; i++) {
            while (!player.isStationReady()) {
                Thread.sleep(50);
            }
            player.setNoiseVolume(0.0);
            Thread.sleep(20_000);

            player.setNoiseVolume(1.0);
            player.setUrl(null);
            Thread.sleep(3_000);

            player.setUrl(station);
        }

        player.stop();
    }
}
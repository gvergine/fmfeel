package fmfeel;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class Persistency {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final Path file;
    private final Config config;

    public Persistency() {
        this.file = resolvePath();
        this.config = load();
    }

    private static Path resolvePath() {
        String home = System.getProperty("user.home", ".");
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return Paths.get(home, windows ? "fmfeel.json" : ".fmfeel.json");
    }

    private Config load() {
        try {
            return MAPPER.readValue(file.toFile(), Config.class);
        } catch (Exception e) {
            Config fresh = new Config();
            try {
                MAPPER.writeValue(file.toFile(), fresh);
            } catch (IOException io) {
                System.err.println("Persistency: cannot create " + file + " (" + io.getMessage() + ")");
            }
            return fresh;
        }
    }

    public void save() throws IOException {
        MAPPER.writeValue(file.toFile(), config);
    }

    public Config getConfig() {
        return config;
    }

    public static final class Config {
        public int frequency = Tuner.DEFAULT_FREQ;
        public int volume = Volume.DEFAULT_VOL;
        public Map<Integer, String> stations = new LinkedHashMap<>();
    }
}
package wikidiosesmon.viewer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings and remembered state, stored in config/wikidiosesmonviewer.json.
 * Window position and size are fractions of the screen so they fit any resolution and GUI scale.
 */
public final class ViewerConfig {
    private static final Logger LOG = LoggerFactory.getLogger("WikiDiosesmonViewer");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String HOME_URL = "https://ialbin73.com/wikidiosesmon";

    private static ViewerConfig instance = new ViewerConfig();
    private static Path file;
    private static boolean dirty;
    private static long dirtyAt;

    /** Last page visited, reopened on the next start. */
    public String lastUrl = HOME_URL;
    /** Vertical scroll of that page in CSS pixels. */
    public int scrollY = 0;

    /** Small window, as fractions of the screen (negative x/y = default place, right side). */
    public double miniX = -1;
    public double miniY = -1;
    public double miniW = 0.34;
    public double miniH = 0.46;

    /** The player's own zoom on top of the automatic one (1.0 = automatic). */
    public double zoom = 1.0;

    /**
     * Seconds a closed page is kept (drawing nothing) so reopening right away is instant; after
     * that the browser is freed completely and reopening reloads the remembered page.
     */
    public int freeAfterSeconds = 10;

    /**
     * Chromium draws on the CPU instead of a second GPU context. Measured in the dev client, it cuts
     * the time the page costs Minecraft's render thread by about 65% (full screen 56.6 to 19.6 ms per
     * second) because frames no longer have to be read back from the GPU. Applied at game start.
     */
    public boolean chromiumLigero = true;

    /** Extra Chromium switches for testing, applied at game start. */
    public List<String> chromiumFlags = new ArrayList<>();

    /** Sound when someone moves you while you are AFK. */
    public boolean afkSound = true;

    /** Sites that open inside the viewer; any other link opens in the normal browser. */
    public List<String> allowedHosts = new ArrayList<>(List.of("ialbin73.com"));

    public static ViewerConfig get() {
        return instance;
    }

    public static void load(Path configDir) {
        file = configDir.resolve("wikidiosesmonviewer.json");
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                ViewerConfig loaded = GSON.fromJson(r, ViewerConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (IOException | RuntimeException e) {
                LOG.warn("No se pudo leer {}: {}", file, e.toString());
            }
        }
        instance.sanitize();
    }

    private void sanitize() {
        if (lastUrl == null || lastUrl.isBlank()) {
            lastUrl = HOME_URL;
        }
        scrollY = Math.max(0, scrollY);
        miniW = clamp(miniW, 0.12, 1.0);
        miniH = clamp(miniH, 0.12, 1.0);
        zoom = clamp(zoom, 0.5, 2.0);
        freeAfterSeconds = Math.max(0, freeAfterSeconds);
        if (chromiumFlags == null) {
            chromiumFlags = new ArrayList<>();
        }
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            allowedHosts = new ArrayList<>(List.of("ialbin73.com"));
        }
    }

    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Saves a few seconds later, so dragging or scrolling does not write the file every frame. */
    public static void markDirty() {
        if (!dirty) {
            dirty = true;
            dirtyAt = System.currentTimeMillis();
        }
    }

    public static void tick() {
        if (dirty && System.currentTimeMillis() - dirtyAt > 3000) {
            save();
        }
    }

    public static void save() {
        dirty = false;
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, w);
            }
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.warn("No se pudo guardar {}: {}", file, e.toString());
        }
    }
}

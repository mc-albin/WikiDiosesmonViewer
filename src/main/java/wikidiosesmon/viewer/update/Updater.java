package wikidiosesmon.viewer.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Self-update from the mod's GitHub releases. On start it reads
 * {@code https://github.com/<repo>/releases/latest/download/latest.json}
 * ({@code {"version":"1.1.0","sha256":"...","notes":"..."}}). When that version is newer, the
 * viewer header offers an update button; the jar is downloaded from
 * {@code https://github.com/<repo>/releases/download/v<version>/WikiDiosesmonViewer.jar}, its SHA-256
 * is checked against latest.json, and it replaces the installed jar when the game closes.
 * The download address is built here from the repository and the version, never taken from the
 * JSON, so latest.json cannot send players anywhere else.
 */
public final class Updater {
    public enum State { DISABLED, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR }

    private static final String MOD_ID = "wikidiosesmonviewer";
    private static final String JAR_NAME = "WikiDiosesmonViewer.jar";
    private static final long MAX_JAR_BYTES = 32L * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static volatile State state = State.DISABLED;
    private static volatile String latest = "";
    private static volatile String expectedSha = "";
    private static volatile String notes = "";
    private static volatile String error = "";
    private static volatile long availableAt;
    private static String repo = "";
    private static Path staged;
    /** While playing, check again every 30 minutes (only while no update was found). */
    public static final long RECHECK_MS = 30L * 60 * 1000;
    private static volatile long lastCheckAt;
    /** True when the last check could not reach GitHub (offline, GitHub down, no release). */
    private static volatile boolean lastCheckFailed;
    /** When the player's own check finished (for the "Al día" label); -1 while it runs; 0 when automatic. */
    private static volatile long manualAt;

    private Updater() {
    }

    public static State state() {
        return state;
    }

    public static String latestVersion() {
        return latest;
    }

    public static String notes() {
        return notes;
    }

    public static String error() {
        return error;
    }

    /** When the update was found (for the one-time notice). */
    public static long availableAt() {
        return availableAt;
    }

    public static boolean lastCheckFailed() {
        return lastCheckFailed;
    }

    public static long manualAt() {
        return manualAt;
    }

    /** Client tick: a new check every 30 minutes while up to date. */
    public static void tick() {
        if (state == State.UP_TO_DATE && System.currentTimeMillis() - lastCheckAt > RECHECK_MS) {
            manualAt = 0;
            fetchLatest();
        }
    }

    /** The player pressed "Buscar": check now. */
    public static void checkNow() {
        if (state == State.UP_TO_DATE) {
            manualAt = -1;
            fetchLatest();
        }
    }

    public static String currentVersion() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("0");
    }

    /** Reads the repository from wikidiosesmonviewer-update.properties and checks in the background. */
    public static void checkAsync() {
        repo = readRepo();
        if (!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            state = State.DISABLED;
            return;
        }
        resumeStaged();
        if (state == State.READY) {
            return;
        }
        fetchLatest();
    }

    private static void fetchLatest() {
        lastCheckAt = System.currentTimeMillis();
        state = State.CHECKING;
        String url = "https://github.com/" + repo + "/releases/latest/download/latest.json";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", "WikiDiosesmonViewer-Updater").GET().build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
            if (manualAt == -1) {
                manualAt = System.currentTimeMillis();
            }
            if (err != null || res.statusCode() != 200) {
                // No release yet, offline or GitHub down: just stay quiet (tried again later).
                lastCheckFailed = true;
                state = State.UP_TO_DATE;
                return;
            }
            lastCheckFailed = false;
            try {
                JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                String v = o.get("version").getAsString().strip();
                String sha = o.get("sha256").getAsString().strip().toLowerCase(Locale.ROOT);
                if (!v.matches("[0-9A-Za-z.+-]{1,32}") || !sha.matches("[0-9a-f]{64}")) {
                    state = State.UP_TO_DATE;
                    return;
                }
                latest = v;
                expectedSha = sha;
                notes = o.has("notes") ? o.get("notes").getAsString() : "";
                if (newer(v, currentVersion())) {
                    availableAt = System.currentTimeMillis();
                    state = State.AVAILABLE;
                } else {
                    state = State.UP_TO_DATE;
                }
            } catch (RuntimeException e) {
                state = State.UP_TO_DATE;
            }
        });
    }

    /** Player pressed the update button: download and verify; installed when the game closes. */
    public static void downloadAsync() {
        if (state != State.AVAILABLE && state != State.ERROR) {
            return;
        }
        state = State.DOWNLOADING;
        String url = "https://github.com/" + repo + "/releases/download/v" + latest + "/" + JAR_NAME;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("User-Agent", "WikiDiosesmonViewer-Updater").GET().build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).whenComplete((res, err) -> {
            if (err != null || res.statusCode() != 200) {
                fail("No se pudo descargar la actualización.");
                return;
            }
            try (InputStream in = res.body()) {
                byte[] data = in.readNBytes((int) MAX_JAR_BYTES + 1);
                if (data.length == 0 || data.length > MAX_JAR_BYTES) {
                    fail("La descarga no es válida.");
                    return;
                }
                String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
                if (!sha.equals(expectedSha)) {
                    fail("La huella del archivo no coincide; no se instaló.");
                    return;
                }
                Path dir = updateDir();
                Files.createDirectories(dir);
                Path file = dir.resolve("WikiDiosesmonViewer-" + latest + ".jar");
                Files.write(file, data);
                staged = file;
                state = State.READY;
            } catch (IOException | NoSuchAlgorithmException e) {
                fail("No se pudo guardar la actualización.");
            }
        });
    }

    /** Game closing: replace the installed jar with the verified download. */
    public static void applyOnExit() {
        if (state != State.READY || staged == null || !Files.isRegularFile(staged)) {
            return;
        }
        Optional<Path> target = installedJar();
        if (target.isEmpty()) {
            return;
        }
        // Replace the jar only after the game has fully exited (never while classes may still load from it).
        if (!copyAfterExit(staged, target.get())) {
            try {
                Files.copy(staged, target.get(), StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(staged);
            } catch (IOException ignored) {
                // Stays in config/wikidiosesmonviewer/update; offered again on the next start.
            }
        }
    }

    private static boolean copyAfterExit(Path from, Path to) {
        String pid = Long.toString(ProcessHandle.current().pid());
        String script = "Wait-Process -Id " + pid + " -ErrorAction SilentlyContinue; Start-Sleep -Seconds 1; "
                + "Copy-Item -LiteralPath '" + ps(from) + "' -Destination '" + ps(to) + "' -Force; "
                + "if ($?) { Remove-Item -LiteralPath '" + ps(from) + "' -Force }";
        try {
            new ProcessBuilder(List.of("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", script))
                    .start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * A verified download left from last time (the copy did not happen): offer it again if it is
     * still newer than the installed version, otherwise delete it.
     */
    private static void resumeStaged() {
        Path dir = updateDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var files = Files.list(dir)) {
            for (Path f : files.toList()) {
                String name = f.getFileName().toString();
                if (!name.startsWith("WikiDiosesmonViewer-") || !name.endsWith(".jar")) {
                    continue;
                }
                String v = name.substring("WikiDiosesmonViewer-".length(), name.length() - 4);
                if (newer(v, currentVersion())) {
                    latest = v;
                    staged = f;
                    state = State.READY;
                } else {
                    Files.deleteIfExists(f);
                }
            }
        } catch (IOException ignored) {
            // Nothing to resume.
        }
    }

    private static String ps(Path p) {
        return p.toAbsolutePath().toString().replace("'", "''");
    }

    /** The jar this mod was loaded from (absent in the development environment). */
    private static Optional<Path> installedJar() {
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(MOD_ID);
        if (mod.isEmpty()) {
            return Optional.empty();
        }
        for (Path p : mod.get().getOrigin().getPaths()) {
            if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private static Path updateDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("wikidiosesmonviewer").resolve("update");
    }

    private static void fail(String message) {
        error = message;
        state = State.ERROR;
    }

    private static String readRepo() {
        try (InputStream in = Updater.class.getResourceAsStream("/wikidiosesmonviewer-update.properties")) {
            if (in == null) {
                return "";
            }
            Properties p = new Properties();
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            return p.getProperty("repo", "").strip();
        } catch (IOException e) {
            return "";
        }
    }

    /** True when {@code a} is a higher version than {@code b} (numbers compared part by part). */
    static boolean newer(String a, String b) {
        String[] x = a.split("[.+-]");
        String[] y = b.split("[.+-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? num(x[i]) : 0;
            int yi = i < y.length ? num(y[i]) : 0;
            if (xi != yi) {
                return xi > yi;
            }
        }
        return false;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

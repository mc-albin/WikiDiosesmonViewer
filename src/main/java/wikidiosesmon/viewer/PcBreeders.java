package wikidiosesmon.viewer;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import org.cef.browser.CefBrowser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stock de la PC para la crianza de la wiki. Solo funciona si también está OrganizerPC: ese mod escribe
 * {@code organizerpc/breeders.txt} cada vez que abres la PC, y aquí se le pasa a la página de crianza
 * con {@code window.recibirStockPC(texto)}. Al cargar la página y cuando el archivo cambia.
 * Sin OrganizerPC no hace nada.
 */
final class PcBreeders {
    private static final Logger LOG = LoggerFactory.getLogger("WikiDiosesmonViewer");
    private static final String ORGANIZER_ID = "organizerpc";
    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Gson GSON = new Gson();
    /** Prefijo de los mensajes de consola con las parejas que elige la crianza. */
    static final String PAIRS_TAG = "__wdv_parejas:";

    /** Fecha del archivo que recibió la página abierta (-1 = ninguno). */
    private static long sentMtime = -1;
    private static String sentUrl;
    private static int ticks;

    private PcBreeders() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getGameDir().resolve("organizerpc").resolve("breeders.txt");
    }

    private static boolean enabled() {
        return FabricLoader.getInstance().isModLoaded(ORGANIZER_ID);
    }

    private static boolean isCrianza(String url) {
        return url != null && url.contains("/crianza") && WebView.allowed(url) && url.toLowerCase(java.util.Locale.ROOT).startsWith("http");
    }

    /** Terminó de cargar una página: si es la crianza, le pasa el stock. */
    static void onLoad(CefBrowser b) {
        sentMtime = -1;
        sentUrl = null;
        send(b);
    }

    /** Cada 2 segundos: si OrganizerPC reescribió el archivo y la crianza está abierta, se lo pasa otra vez. */
    static void tick(CefBrowser b) {
        if (b == null || ++ticks % 40 != 0) return;
        send(b);
    }

    /**
     * Canal de vuelta: la crianza manda las parejas que eligió con {@code console.log(PAIRS_TAG + json)} y aquí
     * se guardan en {@code organizerpc/parejas.json}, que OrganizerPC lee para marcarlas en la PC.
     */
    static boolean onConsole(CefBrowser b, String message) {
        if (message == null || !message.startsWith(PAIRS_TAG)) return false;
        if (!enabled() || b == null || !isCrianza(b.getURL())) return true;
        String json = message.substring(PAIRS_TAG.length());
        if (json.length() > MAX_BYTES / 4) return true;
        try {
            if (!com.google.gson.JsonParser.parseString(json).isJsonObject()) return true;
            Path f = file().resolveSibling("parejas.json");
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("parejas.json.tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOG.warn("No se pudo guardar parejas.json", e);
        }
        return true;
    }

    private static void send(CefBrowser b) {
        if (b == null || !enabled()) return;
        String url = b.getURL();
        if (!isCrianza(url)) return;
        try {
            Path f = file();
            if (!Files.isRegularFile(f) || Files.size(f) > MAX_BYTES) return;
            long mtime = Files.getLastModifiedTime(f).toMillis();
            if (mtime == sentMtime && url.equals(sentUrl)) return;
            String text = Files.readString(f, StandardCharsets.UTF_8);
            sentMtime = mtime;
            sentUrl = url;
            // La página puede tardar en tener lista la función: se reintenta hasta 10 segundos.
            b.executeJavaScript("(function(t){var n=0;function go(){if(typeof window.recibirStockPC==='function'){window.recibirStockPC(t);}"
                    + "else if(++n<40){setTimeout(go,250);}}go();})(" + GSON.toJson(text) + ");", url, 0);
        } catch (Exception e) {
            LOG.warn("No se pudo pasar breeders.txt a la crianza", e);
        }
    }
}

package wikidiosesmon.viewer.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import wikidiosesmon.viewer.ViewerConfig;
import wikidiosesmon.viewer.WebView;
import wikidiosesmon.viewer.afk.Afk;
import wikidiosesmon.viewer.update.Updater;

import java.util.ArrayList;
import java.util.List;

/** Window frame shared by the full screen and the small window: header, buttons and status overlay. */
public final class Chrome {
    public enum Btn { AFK, UPDATE, BACK, HOME, RELOAD, ZOOM_OUT, ZOOM_IN, MINI, FULL, CLOSE }

    public record Hit(Btn btn, int x, int y, int w, int h) {
        public boolean has(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    public static final int FULL_HEADER = 22;
    public static final int MINI_HEADER = 14;
    private static final String TITLE = "WikiDiosesmon";

    private final List<Hit> hits = new ArrayList<>();

    public Btn at(double mx, double my) {
        for (Hit h : hits) {
            if (h.has(mx, my)) {
                return h.btn();
            }
        }
        return null;
    }

    private static TextRenderer tr() {
        return MinecraftClient.getInstance().textRenderer;
    }

    /** Header of the full-screen window. */
    public void full(DrawContext c, int x, int y, int w, int mx, int my) {
        hits.clear();
        TextRenderer tr = tr();
        int h = FULL_HEADER;
        Theme.roundTop(c, x, y, w, h, Theme.R_PANEL, Theme.HEADER);
        c.fill(x, y + h - 1, x + w, y + h, Theme.BORDER);
        c.drawItem(new ItemStack(Items.KNOWLEDGE_BOOK), x + 5, y + 3);
        c.drawText(tr, TITLE, x + 25, y + 7, Theme.TEXT, false);

        int s = 16;
        int by = y + 3;
        // bx is the right edge of the next button; each call returns the left edge of the one drawn.
        int bx = x + w - 4;
        bx = button(c, Btn.CLOSE, bx, by, s, s, mx, my) - 2;
        bx = button(c, Btn.MINI, bx, by, s, s, mx, my) - 6;
        bx = button(c, Btn.RELOAD, bx, by, s, s, mx, my) - 2;
        bx = button(c, Btn.HOME, bx, by, s, s, mx, my) - 2;
        bx = button(c, Btn.BACK, bx, by, s, s, mx, my) - 8;
        bx = button(c, Btn.ZOOM_IN, bx, by, s, s, mx, my);
        String pct = Math.round(WebView.zoomFactor(false) * 100) + "%";
        int pw = Math.max(30, tr.getWidth(pct) + 6);
        bx -= pw;
        c.drawText(tr, pct, bx + (pw - tr.getWidth(pct)) / 2, by + 4, Theme.MUTED, false);
        bx = button(c, Btn.ZOOM_OUT, bx, by, s, s, mx, my) - 10;

        // Update pill, only while there is something to say about an update.
        String up = updateLabel();
        if (up != null) {
            int uw = tr.getWidth(up) + 10;
            if (bx - uw > x + 25 + tr.getWidth(TITLE) + 8) {
                updateButton(c, bx - uw, by, uw, s, up, mx, my);
                bx -= uw + 6;
            }
        }

        // AFK pill: bed + state + counter, green when active, red while AFK.
        String label = Afk.isAfk() ? "AFK " + Afk.elapsed() : "AFK";
        int aw = 22 + tr.getWidth(label) + 6;
        int ax = bx - aw;
        if (ax > x + 25 + tr.getWidth(TITLE) + 8) {
            afkButton(c, ax, by, aw, s, label, mx, my, true);
        }
        loadingBar(c, x, y + h - 1, w);
    }

    /** Header of the small window. {@code interactive}: the cursor is free, buttons react. */
    public void mini(DrawContext c, int x, int y, int w, int mx, int my, boolean interactive) {
        hits.clear();
        TextRenderer tr = tr();
        int h = MINI_HEADER;
        Theme.roundTop(c, x, y, w, h, Theme.R_CARD, Theme.HEADER);
        c.fill(x, y + h - 1, x + w, y + h, Theme.BORDER);
        int s = 11;
        int by = y + 1;
        int bx = x + w - 2;
        bx = button(c, Btn.CLOSE, bx, by, s, s, interactive ? mx : -1, my) - 1;
        bx = button(c, Btn.FULL, bx, by, s, s, interactive ? mx : -1, my) - 4;
        String label = Afk.isAfk() ? "AFK " + Afk.elapsed() : "AFK";
        int aw = 9 + tr.getWidth(label) + 4;
        int ax = bx - aw;
        int titleRoom = ax - (x + 5) - 4;
        if (titleRoom > 20) {
            c.drawText(tr, tr.trimToWidth(TITLE, titleRoom), x + 5, y + 3, Theme.TEXT, false);
        }
        if (ax > x + 4) {
            afkButton(c, ax, by, aw, s, label, interactive ? mx : -1, my, false);
        }
        loadingBar(c, x, y + h - 1, w);
    }

    private void afkButton(DrawContext c, int x, int y, int w, int h, String label, int mx, int my, boolean big) {
        hits.add(new Hit(Btn.AFK, x, y, w, h));
        boolean hover = mx >= x && my >= y && mx < x + w && my < y + h;
        boolean available = Afk.available();
        int tint = !available ? Theme.FAINT : Afk.recentlyMoved() ? Theme.WARN : Afk.isAfk() ? Theme.DANGER : Theme.SUCCESS;
        Theme.box(c, x, y, w, h, Math.min(Theme.R_CARD, h / 3), Theme.withAlpha(tint, hover ? 0x70 : 0x40), Theme.withAlpha(tint, hover ? 0xFF : 0xCC));
        TextRenderer tr = tr();
        if (big) {
            c.drawItem(new ItemStack(Items.RED_BED), x + 3, y);
            c.drawText(tr, label, x + 22, y + 4, available ? Theme.TEXT : Theme.FAINT, false);
        } else {
            Theme.round(c, x + 3, y + h / 2 - 2, 4, 4, 2, tint | 0xFF000000);
            c.drawText(tr, label, x + 9, y + (h - 8) / 2 + 1, available ? Theme.TEXT : Theme.FAINT, false);
        }
    }

    /** Text of the update pill, or null when there is no update to show. */
    private static String updateLabel() {
        return switch (Updater.state()) {
            case AVAILABLE -> "⬆ Actualizar " + Updater.latestVersion();
            case DOWNLOADING -> "Descargando…";
            case READY -> "✔ Se instala al cerrar";
            case ERROR -> "⚠ Reintentar";
            default -> null;
        };
    }

    private void updateButton(DrawContext c, int x, int y, int w, int h, String label, int mx, int my) {
        hits.add(new Hit(Btn.UPDATE, x, y, w, h));
        boolean hover = mx >= x && my >= y && mx < x + w && my < y + h;
        int tint = switch (Updater.state()) {
            case READY -> Theme.SUCCESS;
            case ERROR -> Theme.DANGER;
            default -> Theme.INFO;
        };
        Theme.box(c, x, y, w, h, Math.min(Theme.R_CARD, h / 3), Theme.withAlpha(tint, hover ? 0x70 : 0x40), Theme.withAlpha(tint, hover ? 0xFF : 0xCC));
        c.drawText(tr(), label, x + 5, y + 4, Theme.TEXT, false);
    }

    /** Draws a button whose right edge is {@code right}; returns its left edge. */
    private int button(DrawContext c, Btn btn, int right, int y, int w, int h, int mx, int my) {
        int x = right - w;
        hits.add(new Hit(btn, x, y, w, h));
        boolean hover = mx >= x && my >= y && mx < x + w && my < y + h;
        boolean enabled = btn != Btn.BACK || WebView.canGoBack();
        int fill = hover && enabled ? (btn == Btn.CLOSE ? Theme.withAlpha(Theme.DANGER, 0x90) : Theme.HOVER) : 0;
        Theme.box(c, x, y, w, h, Math.min(Theme.R_CARD, h / 3), fill, hover && enabled ? Theme.BORDER_STRONG : 0);
        int color = enabled ? (hover ? 0xFFFFFFFF : Theme.MUTED) : Theme.FAINT;
        int cx = x + w / 2;
        int cy = y + h / 2;
        switch (btn) {
            case CLOSE -> {
                for (int i = 0; i < 7; i++) {
                    c.fill(cx - 3 + i, cy - 3 + i, cx - 2 + i, cy - 2 + i, color);
                    c.fill(cx + 3 - i, cy - 3 + i, cx + 4 - i, cy - 2 + i, color);
                }
            }
            case MINI -> c.fill(cx - 4, cy + 2, cx + 4, cy + 4, color);
            case FULL -> {
                c.drawBorder(cx - 4, cy - 3, 9, 7, color);
                c.fill(cx - 4, cy - 3, cx + 5, cy - 2, color);
            }
            case ZOOM_OUT -> c.fill(cx - 3, cy, cx + 4, cy + 1, color);
            case ZOOM_IN -> {
                c.fill(cx - 3, cy, cx + 4, cy + 1, color);
                c.fill(cx, cy - 3, cx + 1, cy + 4, color);
            }
            case BACK -> glyph(c, "←", cx, cy, color);
            case HOME -> glyph(c, "⌂", cx, cy, color);
            case RELOAD -> glyph(c, "↻", cx, cy, color);
            default -> {
            }
        }
        return x;
    }

    private static void glyph(DrawContext c, String s, int cx, int cy, int color) {
        TextRenderer tr = tr();
        c.drawText(tr, s, cx - tr.getWidth(s) / 2, cy - 4, color, false);
    }

    private static void loadingBar(DrawContext c, int x, int y, int w) {
        if (!WebView.loading()) {
            return;
        }
        long t = System.currentTimeMillis();
        int seg = Math.max(20, w / 5);
        int off = (int) ((t / 4) % (w + seg)) - seg;
        int a = Math.max(x, x + off);
        int b = Math.min(x + w, x + off + seg);
        if (b > a) {
            c.fill(a, y, b, y + 1, Theme.INFO);
        }
    }

    public static String tooltip(Btn btn) {
        return switch (btn) {
            case AFK -> !Afk.available() ? "Este servidor no tiene /afk"
                    : Afk.recentlyMoved() ? "¡Te movieron mientras estabas AFK!"
                    : Afk.isAfk() ? "Estás AFK · " + Afk.elapsed() + " (clic para volver)" : "Ponerte AFK";
            case UPDATE -> switch (Updater.state()) {
                case AVAILABLE -> "Versión " + Updater.latestVersion() + " disponible (tienes " + Updater.currentVersion() + ")"
                        + (Updater.notes().isBlank() ? "" : ": " + Updater.notes()) + ". Clic para descargarla.";
                case DOWNLOADING -> "Descargando la versión " + Updater.latestVersion() + "…";
                case READY -> "Versión " + Updater.latestVersion() + " descargada. Se instala al cerrar el juego.";
                case ERROR -> Updater.error() + " Clic para reintentar.";
                default -> "";
            };
            case BACK -> "Atrás";
            case HOME -> "Inicio de la Wiki";
            case RELOAD -> "Recargar (F5)";
            case ZOOM_OUT -> "Alejar (Ctrl + rueda)";
            case ZOOM_IN -> "Acercar (Ctrl + rueda)";
            case MINI -> "Achicar (Fin)";
            case FULL -> "Pantalla completa (Inicio)";
            case CLOSE -> "Cerrar (Esc)";
        };
    }

    public static void drawTooltip(DrawContext c, Btn btn, int mx, int my) {
        if (btn != null) {
            c.drawTooltip(tr(), Text.literal(tooltip(btn)), mx, my);
        }
    }

    /** Message over the page area while Chromium starts, the page loads, or after an error. */
    public static void status(DrawContext c, int x, int y, int w, int h) {
        String err = WebView.error();
        boolean ready = WebView.runtimeReady();
        if (err == null && WebView.painted()) {
            return;
        }
        c.fill(x, y, x + w, y + h, 0xFF0B0D10);
        TextRenderer tr = tr();
        String line1;
        String line2;
        int color = Theme.TEXT;
        if (err != null) {
            line1 = err;
            line2 = "Pulsa F5 o el botón ↻ para reintentar";
            color = Theme.DANGER;
        } else if (!ready) {
            line1 = "Preparando el navegador…";
            line2 = "La primera vez se descarga una sola vez (~150 MB)";
        } else {
            int dots = (int) (System.currentTimeMillis() / 350 % 4);
            line1 = "Cargando WikiDiosesmon" + ".".repeat(dots);
            line2 = "";
        }
        int cy = y + h / 2 - 8;
        c.drawText(tr, tr.trimToWidth(line1, w - 8), x + (w - Math.min(w - 8, tr.getWidth(line1))) / 2, cy, color, false);
        if (!line2.isEmpty()) {
            c.drawText(tr, tr.trimToWidth(line2, w - 8), x + (w - Math.min(w - 8, tr.getWidth(line2))) / 2, cy + 12, Theme.DIM, false);
        }
    }

    /** Changes the player's zoom by {@code steps} of 10% (0 resets to automatic). */
    public static void zoom(int steps) {
        ViewerConfig cfg = ViewerConfig.get();
        cfg.zoom = steps == 0 ? 1.0 : ViewerConfig.clamp(Math.round((cfg.zoom + steps * 0.1) * 10) / 10.0, 0.5, 2.0);
        ViewerConfig.markDirty();
    }
}

package wikidiosesmon.viewer.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import wikidiosesmon.viewer.ViewerConfig;
import wikidiosesmon.viewer.WebView;

/**
 * The small window. Drawn on the HUD while playing (cursor captured, WASD works) and by
 * {@link MiniScreen} when the cursor is free to move or resize it.
 * Position and size are stored as fractions of the screen, so they fit every resolution.
 */
public final class MiniHud {
    public static final int MIN_W = 140;
    public static final int MIN_H = 90;
    static final Chrome CHROME = new Chrome();

    private MiniHud() {
    }

    /** Current rectangle {x, y, w, h} in virtual units for a virtual screen of vw x vh. */
    public static int[] rect(int vw, int vh) {
        ViewerConfig cfg = ViewerConfig.get();
        int w = (int) Math.round(ViewerConfig.clamp(cfg.miniW * vw, Math.min(MIN_W, vw), vw));
        int h = (int) Math.round(ViewerConfig.clamp(cfg.miniH * vh, Math.min(MIN_H, vh), vh));
        int x = cfg.miniX < 0 ? vw - w - 6 : (int) Math.round(cfg.miniX * vw);
        int y = cfg.miniY < 0 ? Math.round(vh * 0.10f) : (int) Math.round(cfg.miniY * vh);
        x = Math.max(0, Math.min(vw - w, x));
        y = Math.max(0, Math.min(vh - h, y));
        return new int[]{x, y, w, h};
    }

    /** Called from the HUD callback (real GUI coordinates). */
    public static void renderHud(DrawContext c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float ui = VScreen.uiScale();
        int vw = Math.round(mc.getWindow().getScaledWidth() / ui);
        int vh = Math.round(mc.getWindow().getScaledHeight() / ui);
        var m = c.getMatrices();
        m.push();
        m.scale(ui, ui, 1f);
        try {
            draw(c, rect(vw, vh), -1, -1, false);
        } finally {
            m.pop();
        }
    }

    /** Draws the window at r = {x, y, w, h}, in virtual units. */
    static void draw(DrawContext c, int[] r, int mx, int my, boolean interactive) {
        int x = r[0];
        int y = r[1];
        int w = r[2];
        int h = r[3];
        var m = c.getMatrices();
        m.push();
        // Above the hotbar and other HUD elements.
        m.translate(0, 0, 200);
        Theme.round(c, x + 1, y + 2, w, h, Theme.R_CARD, 0x50000000);
        Theme.box(c, x, y, w, h, Theme.R_CARD, Theme.PANEL, 0);
        CHROME.mini(c, x, y, w, mx, my, interactive);
        int px = x + 1;
        int py = y + Chrome.MINI_HEADER;
        int pw = w - 2;
        int ph = h - Chrome.MINI_HEADER - 1;
        c.fill(px, py, px + pw, py + ph, 0xFF0B0D10);
        WebView.frame(c, px, py, pw, ph, VScreen.unitPx(), true);
        Chrome.status(c, px, py, pw, ph);
        Theme.outline(c, x, y, w, h, Theme.R_CARD, interactive ? Theme.BORDER_STRONG : Theme.BORDER);
        if (interactive) {
            // Resize grip, bottom-right corner.
            int gx = x + w - 2;
            int gy = y + h - 2;
            for (int i = 0; i < 3; i++) {
                int d = 3 + i * 3;
                c.fill(gx - d, gy - 1, gx - d + 1, gy, Theme.ACCENT);
                c.fill(gx - 1, gy - d, gx, gy - d + 1, Theme.ACCENT);
            }
        }
        m.pop();
    }
}

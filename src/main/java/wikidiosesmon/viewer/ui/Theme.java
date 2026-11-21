package wikidiosesmon.viewer.ui;

import net.minecraft.client.gui.DrawContext;

/**
 * Colour palette and rounded-shape helpers, the same look as ChatUtilities DiosesMon
 * ("Noche" palette: neutral dark panel, light text). ARGB colours.
 */
public final class Theme {
    public static final int ACCENT = 0xFFD8DEE9;
    public static final int TEXT = 0xFFEDEFF3;
    public static final int MUTED = 0xFFB4BAC4;
    public static final int DIM = 0xFF8A919C;
    public static final int FAINT = 0xFF5C626B;
    public static final int BASE = 0x111418;
    public static final int PANEL = 0xF2111418;
    public static final int HEADER = 0xFF171B20;
    public static final int BORDER = 0x66D8DEE9;
    public static final int BORDER_STRONG = 0xAAD8DEE9;
    public static final int HOVER = 0x26FFFFFF;
    public static final int PRESSED = 0x40FFFFFF;
    public static final int FIELD = 0xB30E1114;
    public static final int SUCCESS = 0xFF4ADE80;
    public static final int DANGER = 0xFFFF3D63;
    public static final int WARN = 0xFFFBBF24;
    public static final int INFO = 0xFF7DD3FC;

    public static final int R_PANEL = 6;
    public static final int R_CARD = 4;

    private Theme() {
    }

    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    /** Rounded filled box with a 1px rounded border. */
    public static void box(DrawContext c, int x, int y, int w, int h, int r, int fill, int border) {
        if ((fill >>> 24) != 0) {
            round(c, x, y, w, h, r, fill);
        }
        if ((border >>> 24) != 0) {
            outline(c, x, y, w, h, r, border);
        }
    }

    private static int inset(int r, int row) {
        double d = r - row - 0.5;
        return r - (int) Math.round(Math.sqrt(Math.max(0, (double) r * r - d * d)));
    }

    /** Filled rounded rectangle. */
    public static void round(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            c.fill(x + in, y + i, x + w - in, y + i + 1, color);
            c.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, color);
        }
        c.fill(x, y + r, x + w, y + h - r, color);
    }

    /** Rounded rectangle whose bottom corners are square (window headers). */
    public static void roundTop(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            c.fill(x + in, y + i, x + w - in, y + i + 1, color);
        }
        c.fill(x, y + r, x + w, y + h, color);
    }

    /** 1px rounded outline. */
    public static void outline(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            c.drawBorder(x, y, w, h, color);
            return;
        }
        int top = inset(r, 0);
        c.fill(x + top, y, x + w - top, y + 1, color);
        c.fill(x + top, y + h - 1, x + w - top, y + h, color);
        for (int i = 1; i < r; i++) {
            int in = inset(r, i);
            int end = Math.max(in + 1, inset(r, i - 1));
            c.fill(x + in, y + i, x + end, y + i + 1, color);
            c.fill(x + w - end, y + i, x + w - in, y + i + 1, color);
            c.fill(x + in, y + h - 1 - i, x + end, y + h - i, color);
            c.fill(x + w - end, y + h - 1 - i, x + w - in, y + h - i, color);
        }
        c.fill(x, y + r, x + 1, y + h - r, color);
        c.fill(x + w - 1, y + r, x + w, y + h - r, color);
    }
}

package wikidiosesmon.viewer.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import wikidiosesmon.viewer.Viewer;
import wikidiosesmon.viewer.ViewerConfig;
import wikidiosesmon.viewer.WebView;

/**
 * Cursor free over the small window: drag the bar to move it, drag an edge or the corner to
 * resize it, click and scroll the page. Fin / End goes back to playing, Inicio / Home to full
 * screen, Esc closes.
 */
public final class MiniScreen extends VScreen {
    private enum Drag { NONE, MOVE, RESIZE_R, RESIZE_B, RESIZE_RB, RESIZE_L, RESIZE_LB }

    private static final int EDGE = 4;
    private Drag drag = Drag.NONE;
    private double grabX;
    private double grabY;
    private int[] start;

    public MiniScreen() {
        super(Text.literal("WikiDiosesmon"));
    }

    private int[] rect() {
        return MiniHud.rect(width, height);
    }

    @Override
    protected void renderScaled(DrawContext c, int mx, int my, float delta) {
        int[] r = rect();
        MiniHud.draw(c, r, mx, my, true);
        String hint = "Arrastra la barra para mover · bordes para cambiar tamaño · Fin: volver a jugar · Inicio: completa · Esc: cerrar";
        int tw = textRenderer.getWidth(hint);
        int hx = (width - tw) / 2;
        int hy = height - 34;
        if (tw + 12 < width) {
            Theme.box(c, hx - 6, hy - 4, tw + 12, 16, Theme.R_CARD, Theme.withAlpha(Theme.BASE, 0xD0), Theme.BORDER);
            c.drawText(textRenderer, hint, hx, hy, Theme.MUTED, false);
        }
        Drag hover = drag != Drag.NONE ? drag : zone(r, mx, my);
        Chrome.drawTooltip(c, hover == Drag.NONE ? MiniHud.CHROME.at(mx, my) : null, mx, my);
    }

    /** Which part of the window is under the cursor. */
    private Drag zone(int[] r, double mx, double my) {
        int x = r[0];
        int y = r[1];
        int w = r[2];
        int h = r[3];
        boolean inX = mx >= x - EDGE && mx < x + w + EDGE;
        boolean inY = my >= y - EDGE && my < y + h + EDGE;
        if (!inX || !inY) {
            return Drag.NONE;
        }
        boolean right = mx >= x + w - EDGE;
        boolean left = mx < x + EDGE;
        boolean bottom = my >= y + h - EDGE;
        if (right && bottom) {
            return Drag.RESIZE_RB;
        }
        if (left && bottom) {
            return Drag.RESIZE_LB;
        }
        if (right) {
            return Drag.RESIZE_R;
        }
        if (left) {
            return Drag.RESIZE_L;
        }
        if (bottom) {
            return Drag.RESIZE_B;
        }
        if (my < y + Chrome.MINI_HEADER && MiniHud.CHROME.at(mx, my) == null) {
            return Drag.MOVE;
        }
        return Drag.NONE;
    }

    @Override
    protected boolean clicked(double mx, double my, int button) {
        int[] r = rect();
        if (button == 0) {
            Chrome.Btn btn = MiniHud.CHROME.at(mx, my);
            if (btn != null) {
                Viewer.press(btn);
                return true;
            }
            Drag z = zone(r, mx, my);
            if (z != Drag.NONE) {
                drag = z;
                grabX = mx;
                grabY = my;
                start = r;
                return true;
            }
        }
        if (WebView.contains(mx, my)) {
            WebView.mouseButton(mx, my, button, true);
            return true;
        }
        return false;
    }

    @Override
    protected boolean dragged(double mx, double my, int button) {
        if (drag == Drag.NONE || start == null) {
            return false;
        }
        int dx = (int) Math.round(mx - grabX);
        int dy = (int) Math.round(my - grabY);
        int x = start[0];
        int y = start[1];
        int w = start[2];
        int h = start[3];
        switch (drag) {
            case MOVE -> {
                x += dx;
                y += dy;
            }
            case RESIZE_R -> w += dx;
            case RESIZE_B -> h += dy;
            case RESIZE_RB -> {
                w += dx;
                h += dy;
            }
            case RESIZE_L -> {
                x += dx;
                w -= dx;
            }
            case RESIZE_LB -> {
                x += dx;
                w -= dx;
                h += dy;
            }
            default -> {
            }
        }
        int minW = Math.min(MiniHud.MIN_W, width);
        int minH = Math.min(MiniHud.MIN_H, height);
        if (w < minW) {
            if (drag == Drag.RESIZE_L || drag == Drag.RESIZE_LB) {
                x -= minW - w;
            }
            w = minW;
        }
        h = Math.max(minH, h);
        w = Math.min(w, width);
        h = Math.min(h, height);
        x = Math.max(0, Math.min(width - w, x));
        y = Math.max(0, Math.min(height - h, y));
        ViewerConfig cfg = ViewerConfig.get();
        cfg.miniX = (double) x / width;
        cfg.miniY = (double) y / height;
        cfg.miniW = (double) w / width;
        cfg.miniH = (double) h / height;
        return true;
    }

    @Override
    protected boolean released(double mx, double my, int button) {
        if (drag != Drag.NONE) {
            drag = Drag.NONE;
            start = null;
            ViewerConfig.markDirty();
            return true;
        }
        WebView.mouseButton(mx, my, button, false);
        return true;
    }

    @Override
    protected boolean scrolled(double mx, double my, double amount) {
        if (ctrl()) {
            Chrome.zoom(amount > 0 ? 1 : -1);
            return true;
        }
        if (WebView.contains(mx, my)) {
            WebView.wheel(mx, my, amount, 0);
        }
        return true;
    }

    @Override
    protected void moved(double mx, double my) {
        if (drag == Drag.NONE && WebView.contains(mx, my)) {
            WebView.mouseMove(mx, my);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            Viewer.close();
            return true;
        }
        if (Viewer.isMiniKey(keyCode, scanCode)) {
            Viewer.minimize();
            return true;
        }
        if (Viewer.isOpenKey(keyCode, scanCode)) {
            Viewer.openFull();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F5) {
            WebView.reload();
            return true;
        }
        WebView.key(keyCode, scanCode, modifiers, true);
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        WebView.key(keyCode, scanCode, modifiers, false);
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        WebView.typed(chr, modifiers);
        return true;
    }

    @Override
    public void removed() {
        Viewer.onScreenRemoved(this);
    }
}

package wikidiosesmon.viewer.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import wikidiosesmon.viewer.Viewer;
import wikidiosesmon.viewer.WebView;

/** Full-screen wiki (Inicio / Home). Esc closes, Fin / End shrinks it to the small window. */
public final class ViewerScreen extends VScreen {
    private static final int MARGIN = 4;
    private final Chrome chrome = new Chrome();
    private int px;
    private int py;
    private int pw;
    private int ph;

    public ViewerScreen() {
        super(Text.literal("WikiDiosesmon"));
    }

    @Override
    protected void initScaled() {
        int x = MARGIN;
        int y = MARGIN;
        int w = width - MARGIN * 2;
        int h = height - MARGIN * 2;
        px = x + 1;
        py = y + Chrome.FULL_HEADER;
        pw = w - 2;
        ph = h - Chrome.FULL_HEADER - 1;
    }

    @Override
    protected void renderScaled(DrawContext c, int mx, int my, float delta) {
        int x = MARGIN;
        int y = MARGIN;
        int w = width - MARGIN * 2;
        int h = height - MARGIN * 2;
        Theme.round(c, x + 2, y + 3, w, h, Theme.R_PANEL, 0x50000000);
        Theme.box(c, x, y, w, h, Theme.R_PANEL, Theme.PANEL, 0);
        chrome.full(c, x, y, w, mx, my);
        c.fill(px, py, px + pw, py + ph, 0xFF0B0D10);
        WebView.frame(c, px, py, pw, ph, unitPx(), false);
        Chrome.status(c, px, py, pw, ph);
        Theme.outline(c, x, y, w, h, Theme.R_PANEL, Theme.BORDER);
        Chrome.drawTooltip(c, chrome.at(mx, my), mx, my);
    }

    @Override
    public void tick() {
        if (!WebView.exists()) {
            WebView.ensure();
        }
    }

    @Override
    protected boolean clicked(double mx, double my, int button) {
        Chrome.Btn btn = chrome.at(mx, my);
        if (btn != null) {
            if (button == 0) {
                Viewer.press(btn);
            }
            return true;
        }
        if (WebView.contains(mx, my)) {
            WebView.mouseButton(mx, my, button, true);
            return true;
        }
        return false;
    }

    @Override
    protected boolean released(double mx, double my, int button) {
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
        if (WebView.contains(mx, my)) {
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
        if (keyCode == GLFW.GLFW_KEY_F5) {
            WebView.reload();
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
                Chrome.zoom(1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
                Chrome.zoom(-1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_0 || keyCode == GLFW.GLFW_KEY_KP_0) {
                Chrome.zoom(0);
                return true;
            }
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

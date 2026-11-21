package wikidiosesmon.viewer.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Screen drawn in "virtual" units so the frame looks the same with GUI scale 2, 3 or 4
 * (like ChatUtilities' panels), while the page itself always renders at the real pixel size.
 * Does not pause the game and draws no blur or dark background (both cost frames).
 */
public abstract class VScreen extends Screen {
    /** Virtual to GUI units. */
    protected float ui = 1f;

    protected VScreen(Text title) {
        super(title);
    }

    /** Virtual to GUI factor for the current window: 1 at GUI scale 1-2, smaller above. */
    public static float uiScale() {
        double gs = MinecraftClient.getInstance().getWindow().getScaleFactor();
        return (float) Math.min(1.0, 2.0 / Math.max(1.0, gs));
    }

    /** Virtual units to real window pixels. */
    public static double unitPx() {
        return uiScale() * MinecraftClient.getInstance().getWindow().getScaleFactor();
    }

    @Override
    protected final void init() {
        ui = uiScale();
        width = Math.round(client.getWindow().getScaledWidth() / ui);
        height = Math.round(client.getWindow().getScaledHeight() / ui);
        initScaled();
    }

    protected void initScaled() {
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
    }

    @Override
    public final void render(DrawContext c, int mouseX, int mouseY, float delta) {
        var m = c.getMatrices();
        m.push();
        m.scale(ui, ui, 1f);
        try {
            renderScaled(c, Math.round(mouseX / ui), Math.round(mouseY / ui), delta);
        } finally {
            m.pop();
        }
        Toast.render(c);
    }

    protected abstract void renderScaled(DrawContext c, int mouseX, int mouseY, float delta);

    @Override
    public final boolean mouseClicked(double mx, double my, int button) {
        return clicked(mx / ui, my / ui, button);
    }

    @Override
    public final boolean mouseReleased(double mx, double my, int button) {
        return released(mx / ui, my / ui, button);
    }

    @Override
    public final boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return dragged(mx / ui, my / ui, button);
    }

    @Override
    public final boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        return scrolled(mx / ui, my / ui, vertical);
    }

    @Override
    public final void mouseMoved(double mx, double my) {
        moved(mx / ui, my / ui);
    }

    protected abstract boolean clicked(double mx, double my, int button);

    protected abstract boolean released(double mx, double my, int button);

    protected boolean dragged(double mx, double my, int button) {
        return false;
    }

    protected abstract boolean scrolled(double mx, double my, double amount);

    protected abstract void moved(double mx, double my);

    protected static boolean ctrl() {
        return Screen.hasControlDown();
    }
}

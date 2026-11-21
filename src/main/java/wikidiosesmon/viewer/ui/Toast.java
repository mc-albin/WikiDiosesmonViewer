package wikidiosesmon.viewer.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Small pill at the top of the screen that floats in and fades out ("Te pusiste AFK", "¡Te movieron!"). */
public final class Toast {
    public enum Kind { AFK_ON, AFK_OFF, MOVED, INFO }

    private record Notice(String text, Kind kind, long at, long life) {
    }

    private static volatile Notice current;

    private Toast() {
    }

    public static void show(String text, Kind kind) {
        current = new Notice(text, kind, System.currentTimeMillis(), kind == Kind.MOVED ? 6000L : 3000L);
    }

    /** Draws in real GUI coordinates; call last so it stays on top. */
    public static void render(DrawContext c) {
        Notice q = current;
        if (q == null) {
            return;
        }
        long age = System.currentTimeMillis() - q.at();
        if (age < 0 || age > q.life()) {
            return;
        }
        float in = Math.min(1f, age / 220f);
        float out = Math.min(1f, (q.life() - age) / 500f);
        float alpha = Math.max(0f, Math.min(in, out));
        if (alpha < 0.03f) {
            return;
        }
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int a = (int) (alpha * 255);
        int w = tr.getWidth(q.text()) + 36;
        int h = 22;
        int x = (c.getScaledWindowWidth() - w) / 2;
        int y = 26 - Math.round((1f - in) * 10f) - Math.round((1f - out) * 6f);
        int accent = switch (q.kind()) {
            case AFK_ON -> Theme.WARN;
            case AFK_OFF -> Theme.SUCCESS;
            case MOVED -> Theme.DANGER;
            case INFO -> Theme.INFO;
        };
        var m = c.getMatrices();
        m.push();
        m.translate(0, 0, 400);
        Theme.round(c, x + 1, y + 2, w, h, h / 2, Theme.withAlpha(0, (int) (a * 0.3f)));
        Theme.box(c, x, y, w, h, h / 2, Theme.withAlpha(Theme.BASE, (int) (a * 0.92f)), Theme.withAlpha(accent, (int) (a * 0.75f)));
        if (alpha > 0.4f) {
            ItemStack icon = q.kind() == Kind.INFO ? new ItemStack(Items.WRITABLE_BOOK) : new ItemStack(Items.RED_BED);
            c.drawItem(icon, x + 7, y + 3);
        }
        if (a >= 30) {
            c.drawText(tr, q.text(), x + 27, y + 7, Theme.withAlpha(Theme.TEXT, a), false);
        }
        m.pop();
    }
}

package wikidiosesmon.viewer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import wikidiosesmon.viewer.afk.Afk;
import wikidiosesmon.viewer.ui.Chrome;
import wikidiosesmon.viewer.ui.MiniScreen;
import wikidiosesmon.viewer.ui.ViewerScreen;

/**
 * Which way the wiki is shown.
 * <ul>
 *   <li>FULL: whole screen, cursor free, the player does not move (Inicio / Home).</li>
 *   <li>MINI: small window on the HUD while playing normally (Fin / End). Pressing Fin again frees
 *       the cursor to move, resize or use the small window.</li>
 *   <li>CLOSED: nothing drawn (Esc).</li>
 * </ul>
 */
public final class Viewer {
    public enum Mode { CLOSED, FULL, MINI }

    private static Mode mode = Mode.CLOSED;
    /** Set while we replace our own screens, so removed() does not count as closing. */
    private static boolean switching;
    static KeyBinding openKey;
    static KeyBinding miniKey;

    private Viewer() {
    }

    public static Mode mode() {
        return mode;
    }

    public static boolean isOpenKey(int keyCode, int scanCode) {
        return openKey != null && openKey.matchesKey(keyCode, scanCode);
    }

    public static boolean isMiniKey(int keyCode, int scanCode) {
        return miniKey != null && miniKey.matchesKey(keyCode, scanCode);
    }

    private static void setScreen(Screen screen) {
        switching = true;
        try {
            MinecraftClient.getInstance().setScreen(screen);
        } finally {
            switching = false;
        }
    }

    public static void openFull() {
        mode = Mode.FULL;
        WebView.expectSize(false);
        WebView.ensure();
        WebView.show();
        WebView.focus(true);
        setScreen(new ViewerScreen());
    }

    /** Small window while playing. */
    public static void minimize() {
        mode = Mode.MINI;
        WebView.expectSize(true);
        WebView.ensure();
        WebView.show();
        WebView.focus(false);
        WebView.resetCursor();
        if (isOurs(MinecraftClient.getInstance().currentScreen)) {
            setScreen(null);
        }
    }

    /** Frees the cursor over the small window (move, resize, click). */
    public static void interactMini() {
        mode = Mode.MINI;
        WebView.expectSize(true);
        WebView.ensure();
        WebView.show();
        setScreen(new MiniScreen());
    }

    public static void close() {
        mode = Mode.CLOSED;
        WebView.hide();
        if (isOurs(MinecraftClient.getInstance().currentScreen)) {
            setScreen(null);
        }
    }

    private static boolean isOurs(Screen s) {
        return s instanceof ViewerScreen || s instanceof MiniScreen;
    }

    /** One of our screens was replaced by something else (a server menu, death screen...). */
    public static void onScreenRemoved(Screen s) {
        if (switching) {
            return;
        }
        WebView.focus(false);
        WebView.resetCursor();
        if (s instanceof ViewerScreen && mode == Mode.FULL) {
            mode = Mode.CLOSED;
            WebView.hide();
        }
    }

    /** Left the world: free everything. */
    public static void reset() {
        mode = Mode.CLOSED;
        WebView.destroy();
        Afk.reset();
    }

    /** A header button was clicked. */
    public static void press(Chrome.Btn btn) {
        switch (btn) {
            case AFK -> {
                if (Afk.available()) {
                    Afk.toggle();
                }
            }
            case UPDATE -> {
                if (wikidiosesmon.viewer.update.Updater.state() == wikidiosesmon.viewer.update.Updater.State.UP_TO_DATE) {
                    wikidiosesmon.viewer.update.Updater.checkNow();
                } else {
                    wikidiosesmon.viewer.update.Updater.downloadAsync();
                }
            }
            case BACK -> WebView.goBack();
            case HOME -> WebView.goHome();
            case RELOAD -> WebView.reload();
            case ZOOM_OUT -> Chrome.zoom(-1);
            case ZOOM_IN -> Chrome.zoom(1);
            case MINI -> minimize();
            case FULL -> openFull();
            case CLOSE -> close();
        }
    }

    public static void tick(MinecraftClient client) {
        while (openKey.wasPressed()) {
            if (client.player != null && client.currentScreen == null) {
                openFull();
            }
        }
        while (miniKey.wasPressed()) {
            if (client.player != null && client.currentScreen == null) {
                if (mode == Mode.MINI) {
                    interactMini();
                } else {
                    minimize();
                }
            }
        }
        WebView.tickBreeders();
        // Chromium may finish starting after the viewer was opened.
        if (mode != Mode.CLOSED && !WebView.exists() && WebView.runtimeReady() && WebView.error() == null) {
            WebView.ensure();
        }
    }
}

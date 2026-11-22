package wikidiosesmon.viewer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development only (never runs in a normal game): with -Dwdv.autotest=true the dev client opens
 * the viewer by itself, takes screenshots at several GUI scales and closes the game.
 */
final class AutoTest {
    private static final Logger LOG = LoggerFactory.getLogger("WikiDiosesmonViewer/AutoTest");
    private static int step;
    private static int wait;
    private static int total;

    private AutoTest() {
    }

    static void registerIfEnabled() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("wdv.autotest")) {
            return;
        }
        LOG.info("Autotest activado");
        ClientTickEvents.END_CLIENT_TICK.register(AutoTest::tick);
    }

    private static void shot(MinecraftClient mc, String name) {
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name + ".png", mc.getFramebuffer(),
                text -> LOG.info("Captura {}: {}", name, text.getString()));
    }

    private static void scale(MinecraftClient mc, int gs) {
        mc.options.getGuiScale().setValue(gs);
        mc.onResolutionChanged();
    }

    private static boolean pageReady() {
        return WebView.painted() && !WebView.loading();
    }

    private static long fpsSum;
    private static int fpsN;
    private static int fpsTicks;

    /** Samples Minecraft's FPS counter once per second for {@code seconds}; true when done. */
    private static boolean measure(MinecraftClient mc, String label, int seconds) {
        fpsTicks++;
        if (fpsTicks == 20) {
            WikiBrowser.paintNanos = 0;
            WikiBrowser.paintCount = 0;
        }
        if (fpsTicks % 20 == 0 && fpsTicks > 20) {
            fpsSum += mc.getCurrentFps();
            fpsN++;
        }
        if (fpsTicks >= 20 * (seconds + 1)) {
            LOG.info("FPS {}: media {} | Chromium subió {} frames/s, {} ms/s en el hilo del juego | procesos Chromium con prioridad baja: {}", label,
                    fpsN == 0 ? 0 : fpsSum / fpsN, WikiBrowser.paintCount / Math.max(1, seconds),
                    String.format("%.1f", WikiBrowser.paintNanos / 1e6 / Math.max(1, seconds)), ChromiumPriority.count());
            fpsSum = 0;
            fpsN = 0;
            fpsTicks = 0;
            return true;
        }
        return false;
    }

    private static int dragTicks;
    private static double dragStartX;
    private static double dragStartY;

    /**
     * -Dwdv.dragtest=true: opens the small window at its default place (against the right edge)
     * and drags its bar with the same calls Minecraft's Mouse makes: right and down past the edge,
     * then back left. The window must stop at the edge and follow the cursor again as soon as it
     * turns back. Logs the window rectangle meanwhile.
     */
    private static boolean dragTest(MinecraftClient mc) {
        if (!Boolean.getBoolean("wdv.dragtest")) {
            return false;
        }
        if (dragTicks == 0) {
            if (!(WebView.runtimeReady() && mc.currentScreen instanceof TitleScreen)) {
                return true;
            }
            mc.getWindow().setWindowedSize(1600, 900);
            scale(mc, 2);
            ViewerConfig.get().miniX = -1;
            ViewerConfig.get().miniY = 0.2;
            Viewer.interactMini();
        }
        dragTicks++;
        var s = mc.currentScreen;
        if (dragTicks >= 40 && dragTicks <= 100 && s != null) {
            int[] r0 = wikidiosesmon.viewer.ui.MiniHud.rect(s.width, s.height);
            float ui = wikidiosesmon.viewer.ui.VScreen.uiScale();
            if (dragTicks == 40) {
                LOG.info("dragtest inicio rect={}", java.util.Arrays.toString(r0));
                dragStartX = (r0[0] + 60) * ui;
                dragStartY = (r0[1] + 6) * ui;
                s.mouseClicked(dragStartX, dragStartY, 0);
            } else if (dragTicks < 100) {
                int i = dragTicks - 40;
                double x = dragStartX + (i <= 30 ? i * 4 : 240 - i * 4);
                double y = dragStartY + i * 2;
                s.mouseMoved(x, y);
                s.mouseDragged(x, y, 0, 4, 2);
            } else {
                s.mouseReleased(dragStartX, dragStartY + 120, 0);
            }
        }
        if (dragTicks > 40 && dragTicks <= 104 && dragTicks % 2 == 0 && s != null) {
            LOG.info("dragtest t={} rect={}", dragTicks, java.util.Arrays.toString(wikidiosesmon.viewer.ui.MiniHud.rect(s.width, s.height)));
        }
        if (dragTicks == 120) {
            mc.scheduleStop();
        }
        return true;
    }

    private static void tick(MinecraftClient mc) {
        if (dragTest(mc)) {
            return;
        }
        total++;
        if (total > 20 * 240 && step < 99) {
            LOG.warn("Autotest: tiempo agotado en el paso {}", step);
            step = 99;
            wait = 0;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (step) {
            case 0 -> {
                if (WebView.runtimeReady() && mc.currentScreen instanceof TitleScreen) {
                    mc.getWindow().setWindowedSize(1600, 900);
                    mc.options.getMaxFps().setValue(260);
                    mc.options.getEnableVsync().setValue(false);
                    mc.getWindow().setVsync(false);
                    scale(mc, 2);
                    step = 1;
                    wait = 40;
                }
            }
            case 1 -> {
                if (measure(mc, "sin abrir", 6)) {
                    step = 2;
                }
            }
            case 2 -> {
                long t = System.currentTimeMillis();
                Viewer.openFull();
                LOG.info("Autotest: openFull tardó {} ms", System.currentTimeMillis() - t);
                step = 3;
            }
            case 3 -> {
                if (pageReady()) {
                    step = 4;
                    wait = 60;
                }
            }
            case 4 -> {
                if (measure(mc, "pantalla completa", 8)) {
                    shot(mc, "wdv_full_gs2");
                    Viewer.interactMini();
                    step = 5;
                    wait = 40;
                }
            }
            case 5 -> {
                if (measure(mc, "ventana pequeña", 6)) {
                    shot(mc, "wdv_mini_gs2");
                    Viewer.close();
                    step = 6;
                    wait = 20;
                }
            }
            case 6 -> {
                if (measure(mc, "cerrada (aún sin liberar)", 5)) {
                    step = 7;
                    wait = 20 * 8;
                }
            }
            case 7 -> {
                if (measure(mc, "cerrada y liberada (exists=" + WebView.exists() + ")", 5)) {
                    step = 99;
                }
            }
            case 99 -> {
                LOG.info("Autotest terminado. url={} scroll={}", ViewerConfig.get().lastUrl, ViewerConfig.get().scrollY);
                step = 100;
                mc.scheduleStop();
            }
            default -> {
            }
        }
    }
}

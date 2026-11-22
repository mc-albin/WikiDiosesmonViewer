package wikidiosesmon.viewer;

import com.cinemamod.mcef.MCEF;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Util;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.network.CefCookieManager;
import org.cef.network.CefRequest;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wikidiosesmon.viewer.ui.Toast;

import java.net.URI;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The one Chromium page of the mod. Light by design:
 * <ul>
 *   <li>Nothing is created until the player opens the viewer for the first time.</li>
 *   <li>Only one browser exists, never tabs.</li>
 *   <li>It renders at the real pixel size of the window it is drawn in, never a fixed 1920x1080,
 *       and a resize is applied only once the size stops changing (dragging stays smooth).</li>
 *   <li>When hidden it uploads nothing to the GPU and, after {@link ViewerConfig#freeAfterSeconds},
 *       it is closed completely so it uses no CPU or memory; the page and scroll are remembered.</li>
 * </ul>
 * All methods run on the render thread (MCEF pumps Chromium's messages there too).
 */
public final class WebView {
    private static final Logger LOG = LoggerFactory.getLogger("WikiDiosesmonViewer");
    private static final String SCROLL_TAG = "__wdv_scroll:";
    private static final long RESIZE_SETTLE_MS = 150;
    private static final int MAX_PX = 4096;

    private static WikiBrowser browser;
    private static boolean handlersInstalled;
    private static boolean loading;
    private static boolean canGoBack;
    private static String error;
    /** When the page was hidden (ms), -1 while visible or not created. */
    private static long hiddenSince = -1;
    /** Hidden only because the game window is minimized. */
    private static boolean pausedMinimized;
    /** Scroll to restore after the first load of a remembered page, -1 = none. */
    private static int restoreScroll = -1;

    private static int appliedW;
    private static int appliedH;
    private static int pendingW;
    private static int pendingH;
    private static long pendingSince;
    private static double appliedZoom = Double.NaN;

    /** Rectangle of the last frame, in the caller's drawing units, and units-to-pixels factor. */
    private static double rx;
    private static double ry;
    private static double rw;
    private static double rh;
    private static double unitPx = 1;

    private WebView() {
    }

    /** True once Chromium is ready to create pages (MCEF downloads and starts it at the title screen). */
    public static boolean runtimeReady() {
        return MCEF.isInitialized();
    }

    /** Stock de la PC para la crianza: lo vuelve a pasar si OrganizerPC reescribió el archivo. */
    public static void tickBreeders() {
        if (browser != null && !loading) {
            PcBreeders.tick(browser);
        }
    }

    public static boolean exists() {
        return browser != null;
    }

    /** Page drawn at least once: the texture can be shown. */
    public static boolean painted() {
        return browser != null && browser.painted;
    }

    public static boolean loading() {
        return browser != null && loading;
    }

    public static String error() {
        return error;
    }

    public static boolean canGoBack() {
        return browser != null && canGoBack;
    }

    /**
     * Size the page will be drawn at, set before the browser is created. Without it the browser
     * started at 1280x720 and was resized a moment later, so opening laid out and drew the whole
     * page twice.
     */
    public static void expectSize(boolean mini) {
        if (browser != null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        float ui = wikidiosesmon.viewer.ui.VScreen.uiScale();
        int vw = Math.round(mc.getWindow().getScaledWidth() / ui);
        int vh = Math.round(mc.getWindow().getScaledHeight() / ui);
        int[] size = mini ? wikidiosesmon.viewer.ui.MiniHud.pageSize(vw, vh) : wikidiosesmon.viewer.ui.ViewerScreen.pageSize(vw, vh);
        double k = wikidiosesmon.viewer.ui.VScreen.unitPx();
        pendingW = (int) Math.max(64, Math.min(MAX_PX, Math.round(size[0] * k)));
        pendingH = (int) Math.max(64, Math.min(MAX_PX, Math.round(size[1] * k)));
    }

    /** Creates the browser if needed. Returns false while Chromium is not ready yet. */
    public static boolean ensure() {
        if (browser != null) {
            return true;
        }
        if (!runtimeReady()) {
            return false;
        }
        installHandlers();
        ViewerConfig cfg = ViewerConfig.get();
        String url = allowed(cfg.lastUrl) ? cfg.lastUrl : ViewerConfig.HOME_URL;
        restoreScroll = url.equals(cfg.lastUrl) && cfg.scrollY > 0 ? cfg.scrollY : -1;
        int w = pendingW > 0 ? pendingW : 1280;
        int h = pendingH > 0 ? pendingH : 720;
        try {
            WikiBrowser b = new WikiBrowser(MCEF.getClient(), url);
            b.setCloseAllowed();
            b.setCursorChangeListener(WebView::setCursor);
            b.createImmediately();
            b.resize(w, h);
            browser = b;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            LOG.error("No se pudo crear el navegador", e);
            error = "No se pudo iniciar Chromium";
            return false;
        }
        appliedW = w;
        appliedH = h;
        appliedZoom = Double.NaN;
        loading = true;
        canGoBack = false;
        error = null;
        hiddenSince = -1;
        LOG.info("Navegador creado {}x{}: {}", w, h, url);
        return true;
    }

    public static void show() {
        hiddenSince = -1;
        pausedMinimized = false;
        if (browser != null) {
            browser.setHidden(false);
            browser.setWindowVisibility(true);
        }
    }

    /** Stops input and starts the keep-alive countdown. */
    public static void hide() {
        if (browser == null) {
            return;
        }
        if (hiddenSince < 0) {
            hiddenSince = System.currentTimeMillis();
        }
        pausedMinimized = false;
        browser.setHidden(true);
        browser.setFocus(false);
        browser.setWindowVisibility(false);
        resetCursor();
        ViewerConfig.markDirty();
        flushCookies();
    }

    /**
     * Writes the cookies (the wiki login) to disk now. Chromium on its own saves them only every
     * ~30 seconds, and MCEF kills it when the game closes, so a recent login could be lost.
     */
    public static void flushCookies() {
        if (!runtimeReady()) {
            return;
        }
        try {
            CefCookieManager m = CefCookieManager.getGlobalManager();
            if (m != null) {
                m.flushStore(null);
            }
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("No se pudieron guardar las cookies: {}", e.toString());
        }
    }

    /** Game closing: flushes the cookies and waits (pumping Chromium) until they are on disk. */
    public static void flushCookiesAndWait(long maxMs) {
        if (!runtimeReady()) {
            return;
        }
        try {
            CefCookieManager m = CefCookieManager.getGlobalManager();
            CountDownLatch done = new CountDownLatch(1);
            if (m == null || !m.flushStore(done::countDown)) {
                return;
            }
            long end = System.currentTimeMillis() + maxMs;
            while (done.getCount() > 0 && System.currentTimeMillis() < end) {
                MCEF.getApp().getHandle().N_DoMessageLoopWork();
                done.await(10, TimeUnit.MILLISECONDS);
            }
            LOG.info(done.getCount() == 0 ? "Cookies guardadas" : "Las cookies tardaron demasiado en guardarse");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("No se pudieron guardar las cookies: {}", e.toString());
        }
    }

    public static void focus(boolean focused) {
        if (browser != null) {
            browser.setFocus(focused);
        }
    }

    /** Frees the browser entirely. The page and scroll stay saved for next time. */
    public static void destroy() {
        WikiBrowser b = browser;
        browser = null;
        loading = false;
        canGoBack = false;
        hiddenSince = -1;
        pausedMinimized = false;
        appliedW = appliedH = 0;
        appliedZoom = Double.NaN;
        if (b != null) {
            try {
                b.close();
            } catch (RuntimeException e) {
                LOG.warn("Error al cerrar el navegador: {}", e.toString());
            }
            resetCursor();
            ViewerConfig.markDirty();
            LOG.info("Navegador liberado");
        }
    }

    public static void tick() {
        pauseWhileMinimized();
        if (browser != null && hiddenSince > 0) {
            long keep = ViewerConfig.get().freeAfterSeconds * 1000L;
            if (System.currentTimeMillis() - hiddenSince > keep) {
                destroy();
            }
        }
    }

    /**
     * Game minimized (or alt-tabbed out of full screen): nobody can see the page, so Chromium
     * stops drawing it and nothing is uploaded. It continues where it was when the game comes back.
     */
    private static void pauseWhileMinimized() {
        if (browser == null || hiddenSince > 0) {
            return;
        }
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        boolean minimized = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
        if (minimized && !pausedMinimized) {
            pausedMinimized = true;
            browser.setHidden(true);
            browser.setWindowVisibility(false);
        } else if (!minimized && pausedMinimized) {
            pausedMinimized = false;
            browser.setHidden(false);
            browser.setWindowVisibility(true);
        }
    }

    public static void reload() {
        if (browser == null) {
            error = null;
            ensure();
            return;
        }
        error = null;
        if (browser.getURL() == null || browser.getURL().isBlank() || browser.getURL().startsWith("data:")) {
            browser.loadURL(ViewerConfig.get().lastUrl);
        } else {
            browser.reload();
        }
    }

    public static void goBack() {
        if (browser != null && browser.canGoBack()) {
            browser.goBack();
        }
    }

    public static void goHome() {
        if (browser != null) {
            browser.loadURL(ViewerConfig.HOME_URL);
        }
    }

    /**
     * Zoom factor the page should use: follows Minecraft's GUI scale (x2 = 100%, in the full screen
     * and in the small window alike) times the player's own zoom.
     */
    public static double zoomFactor(boolean mini) {
        double gs = MinecraftClient.getInstance().getWindow().getScaleFactor();
        double auto = ViewerConfig.clamp(gs / 2.0, 0.75, 2.0);
        return auto * ViewerConfig.get().zoom;
    }

    /**
     * Lays out and draws the page in the rectangle (x, y, w, h), in the current drawing units.
     * {@code unitPx} converts those units to real window pixels.
     */
    public static void frame(DrawContext c, double x, double y, double w, double h, double unitPx, boolean mini) {
        rx = x;
        ry = y;
        rw = w;
        rh = h;
        WebView.unitPx = unitPx;
        int pw = (int) Math.max(64, Math.min(MAX_PX, Math.round(w * unitPx)));
        int ph = (int) Math.max(64, Math.min(MAX_PX, Math.round(h * unitPx)));
        if (browser == null) {
            pendingW = pw;
            pendingH = ph;
            return;
        }
        long now = System.currentTimeMillis();
        if (pw != appliedW || ph != appliedH) {
            if (pw != pendingW || ph != pendingH) {
                pendingW = pw;
                pendingH = ph;
                pendingSince = now;
            } else if (now - pendingSince >= RESIZE_SETTLE_MS) {
                browser.resize(pw, ph);
                appliedW = pw;
                appliedH = ph;
            }
        }
        double zoom = zoomFactor(mini);
        if (Double.isNaN(appliedZoom) || Math.abs(zoom - appliedZoom) > 0.001) {
            browser.setZoomLevel(Math.log(zoom) / Math.log(1.2));
            appliedZoom = zoom;
        }
        if (browser.painted) {
            drawTexture(c, browser.getRenderer().getTextureID(), (float) x, (float) y, (float) (x + w), (float) (y + h));
        }
    }

    private static void drawTexture(DrawContext c, int texture, float x1, float y1, float x2, float y2) {
        if (texture == 0) {
            return;
        }
        c.draw();
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, texture);
        Matrix4f m = c.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        b.vertex(m, x1, y2, 0).texture(0f, 1f).color(255, 255, 255, 255);
        b.vertex(m, x2, y2, 0).texture(1f, 1f).color(255, 255, 255, 255);
        b.vertex(m, x2, y1, 0).texture(1f, 0f).color(255, 255, 255, 255);
        b.vertex(m, x1, y1, 0).texture(0f, 0f).color(255, 255, 255, 255);
        BufferRenderer.drawWithGlobalProgram(b.end());
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
    }

    // ---- input, in the same units as the last frame() call ----

    public static boolean contains(double mx, double my) {
        return mx >= rx && my >= ry && mx < rx + rw && my < ry + rh;
    }

    private static int px(double mx) {
        return (int) Math.round((mx - rx) * unitPx * appliedW / Math.max(1.0, rw * unitPx));
    }

    private static int py(double my) {
        return (int) Math.round((my - ry) * unitPx * appliedH / Math.max(1.0, rh * unitPx));
    }

    public static void mouseMove(double mx, double my) {
        if (browser != null) {
            browser.sendMouseMove(px(mx), py(my));
        }
    }

    public static void mouseButton(double mx, double my, int button, boolean press) {
        if (browser == null) {
            return;
        }
        if (press) {
            browser.setFocus(true);
            browser.sendMousePress(px(mx), py(my), button);
        } else {
            browser.sendMouseRelease(px(mx), py(my), button);
        }
    }

    public static void wheel(double mx, double my, double amount, int modifiers) {
        if (browser != null) {
            // Ctrl+wheel is our zoom; MCEF would change the zoom on its own otherwise.
            browser.sendMouseWheel(px(mx), py(my), amount, modifiers & ~GLFW.GLFW_MOD_CONTROL);
        }
    }

    /** Scancodes of the top-row keys 0..9 (physical position, same on every layout). */
    private static final int[] DIGIT_SCANCODES = {0x0B, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A};

    private interface User32Keys extends com.sun.jna.Library {
        short GetKeyState(int virtKey);
    }

    private static User32Keys user32;
    private static boolean user32Failed;

    /** NumLock state. Assumed on when it cannot be read (not Windows, or JNA missing). */
    private static boolean numLockOn() {
        if (Util.getOperatingSystem() != Util.OperatingSystem.WINDOWS || user32Failed) {
            return true;
        }
        try {
            if (user32 == null) {
                user32 = com.sun.jna.Native.load("user32", User32Keys.class);
            }
            return (user32.GetKeyState(0x90 /* VK_NUMLOCK */) & 1) != 0;
        } catch (Throwable t) {
            user32Failed = true;
            LOG.warn("Could not read the NumLock state", t);
            return true;
        }
    }

    public static void key(int keyCode, int scanCode, int modifiers, boolean press) {
        if (browser == null) {
            return;
        }
        // MCEF's native side builds the Windows key from the scancode and ignores NumLock, so the
        // numpad digits arrive as End, Down, PgDn, Left, Right, Home, Up, PgUp and Insert. Chromium
        // runs that navigation command and drops the digit typed after it. Only 5 ("Clear") got
        // through. With NumLock on, send them as the top-row digits instead.
        if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9 && numLockOn()) {
            int digit = keyCode - GLFW.GLFW_KEY_KP_0;
            keyCode = GLFW.GLFW_KEY_0 + digit;
            scanCode = DIGIT_SCANCODES[digit];
        } else if (keyCode == GLFW.GLFW_KEY_KP_DECIMAL && numLockOn()) {
            // Its scancode becomes Delete. The '.' or ',' still arrives through typed().
            return;
        }
        if (press) {
            browser.sendKeyPress(keyCode, scanCode, modifiers);
        } else {
            browser.sendKeyRelease(keyCode, scanCode, modifiers);
        }
    }

    public static void typed(char chr, int modifiers) {
        if (browser != null) {
            browser.sendKeyTyped(chr, modifiers);
        }
    }

    private static final java.util.Map<Integer, Long> CURSORS = new java.util.HashMap<>();

    /**
     * Page cursor (hand over links, text beam over text). Replaces MCEF's own mapping, which asks
     * GLFW for an invalid cursor on some shapes and logs a GL error.
     */
    private static void setCursor(int cefType) {
        if (browser == null || hiddenSince > 0) {
            return;
        }
        int shape = switch (cefType) {
            case 1 -> GLFW.GLFW_CROSSHAIR_CURSOR;
            case 2 -> GLFW.GLFW_HAND_CURSOR;
            case 3 -> GLFW.GLFW_IBEAM_CURSOR;
            case 6, 13, 15 -> GLFW.GLFW_HRESIZE_CURSOR;
            case 7, 10, 14 -> GLFW.GLFW_VRESIZE_CURSOR;
            default -> 0;
        };
        long cursor = shape == 0 ? 0L : CURSORS.computeIfAbsent(shape, GLFW::glfwCreateStandardCursor);
        GLFW.glfwSetCursor(MinecraftClient.getInstance().getWindow().getHandle(), cursor);
    }

    public static void resetCursor() {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        GLFW.glfwSetCursor(handle, 0L);
    }

    // ---- links ----

    /** Only the wiki's own site opens inside the viewer. */
    public static boolean allowed(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("about:") || lower.startsWith("data:") || lower.startsWith("blob:") || lower.startsWith("javascript:")) {
            return true;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null || scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            for (String h : ViewerConfig.get().allowedHosts) {
                String a = h.toLowerCase(Locale.ROOT).strip();
                if (!a.isEmpty() && (host.equals(a) || host.endsWith("." + a))) {
                    return true;
                }
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
        return false;
    }

    private static void openExternal(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return;
        }
        MinecraftClient.getInstance().execute(() -> {
            Util.getOperatingSystem().open(url);
            Toast.show("Enlace abierto en tu navegador", Toast.Kind.INFO);
        });
    }

    private static boolean isMain(CefFrame frame) {
        return frame == null || frame.isMain();
    }

    private static void installHandlers() {
        if (handlersInstalled) {
            return;
        }
        handlersInstalled = true;
        MCEF.getClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadingStateChange(CefBrowser b, boolean isLoading, boolean back, boolean forward) {
                if (b == browser) {
                    loading = isLoading;
                    canGoBack = back;
                }
            }

            @Override
            public void onLoadEnd(CefBrowser b, CefFrame frame, int status) {
                if (b != browser || !isMain(frame)) {
                    return;
                }
                // Reports the scroll position (throttled) through the console so it can be remembered.
                b.executeJavaScript("(function(){if(window.__wdv)return;window.__wdv=1;var t=0;"
                        + "function r(){t=0;var y=Math.round(window.scrollY||(document.scrollingElement||document.documentElement).scrollTop||0);"
                        + "console.log('" + SCROLL_TAG + "'+y);}"
                        + "addEventListener('scroll',function(){if(!t)t=setTimeout(r,500);},{passive:true});})();", "", 0);
                if (restoreScroll > 0) {
                    int y = restoreScroll;
                    restoreScroll = -1;
                    b.executeJavaScript("(function(){var y=" + y + ";function s(){window.scrollTo(0,y);}"
                            + "setTimeout(s,150);setTimeout(s,700);setTimeout(s,1600);})();", "", 0);
                }
                appliedZoom = Double.NaN;
                // A login or logout ends with a page load: keep its cookies even if the game crashes.
                flushCookies();
                // Crianza: el stock de la PC (solo con OrganizerPC).
                PcBreeders.onLoad(b);
            }

            @Override
            public void onLoadError(CefBrowser b, CefFrame frame, CefLoadHandler.ErrorCode code, String text, String url) {
                if (b == browser && isMain(frame) && code != CefLoadHandler.ErrorCode.ERR_ABORTED) {
                    error = "No se pudo cargar la página (" + text + ")";
                }
            }
        });
        MCEF.getClient().addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onAddressChange(CefBrowser b, CefFrame frame, String url) {
                if (b != browser || !isMain(frame) || url == null) {
                    return;
                }
                if (url.startsWith("http") && allowed(url)) {
                    ViewerConfig cfg = ViewerConfig.get();
                    if (!url.equals(cfg.lastUrl)) {
                        cfg.lastUrl = url;
                        cfg.scrollY = 0;
                        ViewerConfig.markDirty();
                    }
                }
            }

            @Override
            public boolean onConsoleMessage(CefBrowser b, CefSettings.LogSeverity level, String message, String source, int line) {
                if (b == browser && PcBreeders.onConsole(b, message)) {
                    return true;
                }
                if (b == browser && message != null && message.startsWith(SCROLL_TAG)) {
                    try {
                        int y = Integer.parseInt(message.substring(SCROLL_TAG.length()).strip());
                        ViewerConfig cfg = ViewerConfig.get();
                        if (y != cfg.scrollY) {
                            cfg.scrollY = Math.max(0, y);
                            ViewerConfig.markDirty();
                        }
                    } catch (NumberFormatException ignored) {
                        // Not ours after all.
                    }
                    return true;
                }
                return false;
            }
        });
        MCEF.getClient().getHandle().addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public boolean onBeforeBrowse(CefBrowser b, CefFrame frame, CefRequest request, boolean userGesture, boolean isRedirect) {
                if (b != browser || !isMain(frame) || request == null) {
                    return false;
                }
                String url = request.getURL();
                if (allowed(url)) {
                    return false;
                }
                openExternal(url);
                return true;
            }
        });
        MCEF.getClient().getHandle().addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser b, CefFrame frame, String url, String name) {
                if (b != browser) {
                    return false;
                }
                // No pop-up windows: wiki links open in the same page, anything else outside the game.
                if (allowed(url)) {
                    MinecraftClient.getInstance().execute(() -> {
                        if (browser != null) {
                            browser.loadURL(url);
                        }
                    });
                } else {
                    openExternal(url);
                }
                return true;
            }

            @Override
            public boolean doClose(CefBrowser b) {
                // A registered handler replaces the browser's own close logic, so keep calling it.
                return b.doClose();
            }
        });
    }
}

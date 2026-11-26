package wikidiosesmon.viewer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wikidiosesmon.viewer.afk.Afk;
import wikidiosesmon.viewer.ui.MiniHud;
import wikidiosesmon.viewer.ui.MiniScreen;
import wikidiosesmon.viewer.ui.Toast;
import wikidiosesmon.viewer.ui.ViewerScreen;
import wikidiosesmon.viewer.update.Updater;

/** Fabric entry point: keys, events and HUD. */
public class WikiViewerClient implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("WikiDiosesmonViewer");
    private static final String CATEGORY = "category.wikidiosesmonviewer";
    private static Updater.State notifiedUpdate = Updater.State.DISABLED;

    @Override
    public void onInitializeClient() {
        ViewerConfig.load(FabricLoader.getInstance().getConfigDir());
        java.util.List<String> flags = new java.util.ArrayList<>();
        if (ViewerConfig.get().chromiumLigero) {
            flags.add("disable-gpu");
            flags.add("disable-gpu-compositing");
        }
        flags.addAll(ViewerConfig.get().chromiumFlags);
        ChromiumFlags.install(flags);

        Viewer.openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.wikidiosesmonviewer.open",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_HOME, CATEGORY));
        Viewer.miniKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.wikidiosesmonviewer.mini",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_END, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> safe(() -> {
            Viewer.tick(client);
            WebView.tick();
            ChromiumPriority.tick();
            Afk.tick(client);
            ViewerConfig.tick();
            updateNotice(client);
            safe(Updater::tick);
        }));

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                safe(() -> Afk.onGameMessage(message));
            }
        });
        ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> {
            if (!overlay) {
                safe(() -> Afk.onGameMessage(message));
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> safe(Viewer::reset)));
        // The proxy moving you to the lobby or another server is not a disconnect, only a new join:
        // the AFK period of the previous server ends there.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> safe(Afk::reset));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            safe(ViewerConfig::save);
            safe(WebView::destroy);
            // MCEF kills Chromium right after this; write the login cookies to disk first.
            safe(() -> WebView.flushCookiesAndWait(1500));
            safe(Updater::applyOnExit);
        });
        Updater.checkAsync();

        HudRenderCallback.EVENT.register((context, tickCounter) -> safe(() -> {
            var screen = MinecraftClient.getInstance().currentScreen;
            boolean ours = screen instanceof ViewerScreen || screen instanceof MiniScreen;
            if (Viewer.mode() == Viewer.Mode.MINI && !ours) {
                MiniHud.renderHud(context);
            }
            if (!ours) {
                Toast.render(context);
            }
        }));
        AutoTest.registerIfEnabled();
        LOG.info("WikiDiosesmon Viewer cargado (Inicio: abrir, Fin: achicar, Esc: cerrar).");
    }

    /** One notice in the world when an update is found, and one when it is ready to install. */
    private static void updateNotice(MinecraftClient client) {
        Updater.State st = Updater.state();
        if (client.player == null || st == notifiedUpdate) {
            return;
        }
        if (st == Updater.State.AVAILABLE) {
            String key = Viewer.openKey == null ? "Inicio" : Viewer.openKey.getBoundKeyLocalizedText().getString();
            Toast.show("WikiDiosesmonViewer " + Updater.latestVersion() + " disponible · Pulsa " + key + " y luego \"Actualizar\"",
                    Toast.Kind.INFO, 10_000L);
            notifiedUpdate = st;
        } else if (st == Updater.State.READY) {
            Toast.show("Actualización descargada · Se instala al cerrar el juego", Toast.Kind.INFO, 6_000L);
            notifiedUpdate = st;
        }
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            LOG.warn("Error en WikiDiosesmon Viewer: {}", e.toString());
        }
    }
}

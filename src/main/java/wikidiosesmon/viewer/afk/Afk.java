package wikidiosesmon.viewer.afk;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.command.CommandSource;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import wikidiosesmon.viewer.ViewerConfig;
import wikidiosesmon.viewer.ui.Toast;

import java.util.regex.Pattern;

/**
 * AFK button: sends /afk, follows the real state from the server's replies
 * ("{@code <Name> ahora está AFK.}" / "{@code <Name> ya no está AFK.}", as in ChatUtilities),
 * counts the time and warns with a sound when someone moves you while you are AFK.
 */
public final class Afk {
    private static final Pattern FORMAT = Pattern.compile("(?i)§x(§[0-9a-f]){6}|§[0-9a-fk-orx]");
    private static final long ASK_WINDOW_MS = 10_000L;
    /** Without any reply from the server after this long, the button toggles on its own. */
    private static final long FALLBACK_MS = 4_000L;
    /** Distance (blocks) that counts as "moved". */
    private static final double MOVE_DIST = 0.6;

    private static boolean afk;
    private static long afkSince;
    private static long askedAt;
    private static boolean askedTarget;
    private static double startX;
    private static double startY;
    private static double startZ;
    private static boolean moved;
    /** The current AFK period was started with this mod's button (only then it shows notices). */
    private static boolean fromButton;
    private static long movedAt;
    private static long lastOwnInput;

    private Afk() {
    }

    public static boolean isAfk() {
        return afk;
    }

    /** True for a few seconds after someone moved you while AFK. */
    public static boolean recentlyMoved() {
        return moved && System.currentTimeMillis() - movedAt < 15_000L;
    }

    /** Whether the server offers /afk (unknown command tree counts as yes). */
    public static boolean available() {
        ClientPlayNetworkHandler h = MinecraftClient.getInstance().getNetworkHandler();
        if (h == null) {
            return false;
        }
        CommandDispatcher<CommandSource> d = h.getCommandDispatcher();
        return d == null || d.getRoot().getChildren().isEmpty() || d.getRoot().getChild("afk") != null;
    }

    public static void toggle() {
        ClientPlayNetworkHandler h = MinecraftClient.getInstance().getNetworkHandler();
        if (h == null) {
            return;
        }
        askedAt = System.currentTimeMillis();
        askedTarget = !afk;
        h.sendChatCommand("afk");
    }

    /** "mm:ss" or "h:mm:ss" since the AFK period started, "" when not AFK. */
    public static String elapsed() {
        if (!afk || afkSince <= 0) {
            return "";
        }
        long s = Math.max(0, (System.currentTimeMillis() - afkSince) / 1000);
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format("%02d:%02d", s / 60, s % 60);
    }

    private static String self() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.getSession() == null ? "" : mc.getSession().getUsername();
    }

    private static String clean(String raw) {
        String s = FORMAT.matcher(raw == null ? "" : raw).replaceAll("");
        s = s.replaceAll("[\\u200B-\\u200D\\uFEFF]", "").replaceAll("\\p{Co}+", " ");
        return s.replaceAll("[\\p{Zs}\\s]+", " ").strip();
    }

    /** Every system/plugin chat line. */
    public static void onGameMessage(Text message) {
        if (message == null) {
            return;
        }
        String text = clean(message.getString()).replaceFirst("^\\(!\\)\\s*", "").strip();
        String me = self();
        if (me.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean asked = now - askedAt < ASK_WINDOW_MS;
        // Notices only for what the player did with this mod's AFK button. AFK set or cleared any
        // other way (typing /afk, another mod, the server after you move) only updates the state.
        if (text.equalsIgnoreCase(me + " ahora está AFK.") || text.equalsIgnoreCase(me + " ahora está AFK")) {
            if (!afk) {
                fromButton = asked && askedTarget;
            }
            setAfk(true);
            if (asked && askedTarget) {
                askedAt = 0;
                Toast.show("Te pusiste AFK", Toast.Kind.AFK_ON);
            }
        } else if (text.equalsIgnoreCase(me + " ya no está AFK.") || text.equalsIgnoreCase(me + " ya no está AFK")) {
            setAfk(false);
            fromButton = false;
            if (asked && !askedTarget) {
                askedAt = 0;
                Toast.show("Ya no estás AFK", Toast.Kind.AFK_OFF);
            }
        }
    }

    private static void setAfk(boolean value) {
        if (value && !afk) {
            afkSince = System.currentTimeMillis();
            moved = false;
            markStart();
        }
        if (!value) {
            afkSince = 0;
        }
        afk = value;
    }

    private static void markStart() {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p != null) {
            startX = p.getX();
            startY = p.getY();
            startZ = p.getZ();
        }
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        if (p == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (p.input != null && (p.input.movementForward != 0 || p.input.movementSideways != 0
                || p.input.jumping || p.input.sneaking)) {
            lastOwnInput = now;
        }
        // The server never answered: assume the command worked.
        if (askedAt > 0 && now - askedAt > FALLBACK_MS) {
            askedAt = 0;
            if (afk != askedTarget) {
                fromButton = askedTarget;
                setAfk(askedTarget);
                Toast.show(askedTarget ? "Te pusiste AFK" : "Ya no estás AFK", askedTarget ? Toast.Kind.AFK_ON : Toast.Kind.AFK_OFF);
            }
        }
        if (!afk) {
            return;
        }
        double dx = p.getX() - startX;
        double dy = p.getY() - startY;
        double dz = p.getZ() - startZ;
        if (dx * dx + dy * dy + dz * dz > MOVE_DIST * MOVE_DIST) {
            boolean byMe = now - lastOwnInput < 1500;
            if (!byMe && fromButton && now - movedAt > 8000) {
                moved = true;
                movedAt = now;
                Toast.show("AFK: ¡Te movieron!", Toast.Kind.MOVED);
                if (ViewerConfig.get().afkSound) {
                    beep(client);
                }
            }
            markStart();
        }
    }

    private static void beep(MinecraftClient client) {
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.5f, 1.0f));
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.2f, 0.8f));
    }

    public static void reset() {
        afk = false;
        afkSince = 0;
        askedAt = 0;
        moved = false;
        movedAt = 0;
        fromButton = false;
    }
}

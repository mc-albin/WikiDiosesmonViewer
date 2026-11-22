package wikidiosesmon.viewer.mixin;

import org.cef.CefSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Keeps the wiki login across game restarts (and updates, which restart the game).
 * MCEF stores cookies on disk but leaves {@code persist_session_cookies} off, so a login cookie
 * without an expiry date was dropped every time the game closed. Optional: if MCEF changes,
 * the game still starts and only this fix is lost.
 */
@Mixin(targets = "com.cinemamod.mcef.CefUtil", remap = false)
public abstract class CefUtilMixin {
    @ModifyArg(method = "init", remap = false, require = 0,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lorg/cef/CefApp;getInstance([Ljava/lang/String;Lorg/cef/CefSettings;)Lorg/cef/CefApp;"),
            index = 1)
    private static CefSettings wikidiosesmon$keepSession(CefSettings settings) {
        if (settings != null) {
            settings.persist_session_cookies = true;
        }
        return settings;
    }
}

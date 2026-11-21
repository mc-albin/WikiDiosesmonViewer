package wikidiosesmon.viewer.mixin;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wikidiosesmon.viewer.Viewer;

/** Esc while playing with the small window open closes the window instead of opening the pause menu. */
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void wikidiosesmon$escClosesMini(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS && window == mc.getWindow().getHandle()
                && mc.currentScreen == null && Viewer.mode() == Viewer.Mode.MINI) {
            Viewer.close();
            ci.cancel();
        }
    }
}

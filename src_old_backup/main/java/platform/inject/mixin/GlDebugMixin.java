package platform.inject.mixin;

import net.minecraft.client.gl.GlDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlDebug.class)
public class GlDebugMixin {
    @Inject(method = "onDebugMessage", at = @At("HEAD"), cancellable = true, require = 0)
    private void onDebug(int source, int type, int id, int severity, int length, long message, long userParam, CallbackInfo ci) {
        if (id == 1281 || id == 1282) {
            String msg = org.lwjgl.system.MemoryUtil.memUTF8(message, length);
            System.err.println("[GL ERROR " + id + "] " + msg);
            ci.cancel();
        }
    }
}

package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.combat.HitColor;
import egg.client.module.render.AspectRatio;
import egg.client.module.render.NoRender;
import egg.client.module.render.WorldTweaks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    private float getFov(Camera camera, float tickProgress, boolean changingFov) {
        throw new AssertionError();
    }

    @Shadow
    public abstract Matrix4f getBasicProjectionMatrix(float fov);

    @ModifyArg(method = "getBasicProjectionMatrix", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;"), index = 1)
    private float modifyAspectRatioInBasicProjectionMatrix(float ratio) {
        AspectRatio ar = ModuleManager.getInstance().getModule(AspectRatio.class);
        if (ar != null && ar.isEnabled()) {
            return ar.getRatio();
        }
        return ratio;
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void onTiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        NoRender noRender = ModuleManager.getInstance().getModule(NoRender.class);
        if (noRender != null && noRender.isEnabled() && noRender.getHurtCam().isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        HitColor hitColor = ModuleManager.getInstance().getModule(HitColor.class);
        GameRenderer self = (GameRenderer) (Object) this;
        if (hitColor != null) {
            hitColor.updateOverlay(self.getOverlayTexture());
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderTail(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.currentScreen instanceof egg.client.ui.ClickGuiScreen gui) {
            gui.renderLate();
        }
    }
}

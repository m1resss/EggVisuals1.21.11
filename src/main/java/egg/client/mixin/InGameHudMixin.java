package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.render.Crosshair;
import egg.client.module.render.NoRender;
import egg.client.render.animation.HudAnimations;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderOverlay(DrawContext context, Identifier texture, float opacity, CallbackInfo ci) {
        NoRender noRender = ModuleManager.getInstance().getModule(NoRender.class);
        if (noRender != null && noRender.isEnabled()) {
            if (texture != null && texture.getPath().contains("pumpkin") && noRender.getPumpkin().isEnabled()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderVignetteOverlay(DrawContext context, Entity entity, CallbackInfo ci) {
        NoRender noRender = ModuleManager.getInstance().getModule(NoRender.class);
        if (noRender != null && noRender.isEnabled() && noRender.getVignette().isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Crosshair crosshair = ModuleManager.getInstance().getModule(Crosshair.class);
        if (crosshair != null && crosshair.isEnabled()) {
            crosshair.render(context, tickCounter);
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void egg$renderClosingTabList(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        HudAnimations.INSTANCE.renderClosingTabList(context);
    }
}

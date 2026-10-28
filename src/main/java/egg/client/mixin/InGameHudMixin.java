package egg.client.mixin;

import net.minecraft.client.MinecraftClient;

import egg.client.module.ModuleManager;
import egg.client.module.render.*;
import egg.client.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
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

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Crosshair crosshair = ModuleManager.getInstance().getModule(Crosshair.class);
        if (crosshair != null && crosshair.isEnabled()) {
            crosshair.render(context, tickCounter);
            ci.cancel();
        }
    }



    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderHudTail(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Interface iface = ModuleManager.getInstance().getModule(Interface.class);
        if (iface != null && iface.isEnabled()) {
            iface.render(context, tickCounter);
        }

        PotionHUD potionHUD = ModuleManager.getInstance().getModule(PotionHUD.class);
        if (potionHUD != null && potionHUD.isEnabled()) {
            potionHUD.render(context, tickCounter);
        }

        Hitmarker hitmarker = ModuleManager.getInstance().getModule(Hitmarker.class);
        if (hitmarker != null && hitmarker.isEnabled()) {
            hitmarker.render(context, tickCounter);
        }

        ViewModel vm = ModuleManager.getInstance().getModule(ViewModel.class);
        if (vm != null && vm.isEnabled()) {
            vm.render(context, tickCounter);
        }

        BreakBlock bb = ModuleManager.getInstance().getModule(BreakBlock.class);
        if (bb != null && bb.isEnabled()) {
            bb.render(context, tickCounter);
        }

        TargetESP targetESP = ModuleManager.getInstance().getModule(TargetESP.class);
        if (targetESP != null && targetESP.isEnabled()) {
            targetESP.render2D(context, tickCounter);
        }

        TargetHUD targetHUD = ModuleManager.getInstance().getModule(TargetHUD.class);
        if (targetHUD != null && targetHUD.isEnabled()) {
            targetHUD.render(context, tickCounter);
        }

        egg.client.command.waypoint.WaypointManager.getInstance().render(context);

        // renderKillToast(context);

        egg.client.render.msdf.pipeline.DrawBatcher.flushPending();
    }

    /*
    @org.spongepowered.asm.mixin.Unique
    private static final Identifier CASE_COIN = Identifier.of("egg", "textures/cases/coin.png");

    @org.spongepowered.asm.mixin.Unique
    private void renderKillToast(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return;
        int screenW = mc.getWindow().getScaledWidth();

        float tw = 175.0f;
        float th = 32.0f;
        float tx = (screenW - tw) / 2.0f;
        float ty = 28.0f;

        float alpha = 1.0f; // Placeholder as CaseManager is gone
        float scale = 0.85f + 0.15f * Math.min(1.0f, (1.0f - alpha) * 4.0f);
        if (alpha > 0.8f) {
            scale = 0.85f + 0.15f * ((1.0f - alpha) / 0.2f);
        } else {
            scale = 1.0f;
        }

        context.getMatrices().push();
        context.getMatrices().translate(tx + tw / 2.0f, ty + th / 2.0f, 0);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.getMatrices().translate(-(tx + tw / 2.0f), -(ty + th / 2.0f), 0);

        egg.client.render.Render2D.drawShadow(context, tx, ty, tw, th, 8.0f, 6.0f,
                egg.client.util.ColorUtil.rgba(0, 0, 0, (int) (120 * alpha)));
        egg.client.render.Render2D.drawRoundedRect(context, tx, ty, tw, th, 8.0f,
                egg.client.util.ColorUtil.rgba(18, 20, 29, (int) (240 * alpha)));
        egg.client.render.Render2D.drawRoundedOutline(context, tx, ty, tw, th, 8.0f, 1.0f,
                egg.client.util.ColorUtil.rgba(255, 215, 0, (int) (160 * alpha)));

        float iconSize = 20.0f;
        float iconX = tx + 8.0f;
        float iconY = ty + (th - iconSize) / 2.0f;
        egg.client.render.Render2D.drawTexture(context, CASE_COIN, iconX, iconY, iconSize, iconSize,
                egg.client.util.ColorUtil.rgba(255, 255, 255, (int) (255 * alpha)));

        String title = "НАГРАДА ЗА КИЛЛ";
        egg.client.render.font.ClientFont.draw(context, title, iconX + iconSize + 8.0f, ty + 7.0f,
                egg.client.render.font.ClientFont.SF_BOLD,
                egg.client.util.ColorUtil.rgba(255, 215, 0, (int) (255 * alpha)));

        String reward = "+50 COINS";
        egg.client.render.font.ClientFont.draw(context, reward, iconX + iconSize + 8.0f, ty + 18.0f,
                egg.client.render.font.ClientFont.SF_BOLD,
                egg.client.util.ColorUtil.rgba(255, 255, 255, (int) (230 * alpha)));

        context.getMatrices().pop();
    }
    */
}

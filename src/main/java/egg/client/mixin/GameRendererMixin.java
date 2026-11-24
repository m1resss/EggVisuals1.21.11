package egg.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;

import egg.client.command.waypoint.WaypointManager;

import egg.client.module.ModuleManager;

import egg.client.module.combat.HitColor;

import egg.client.module.render.*;

import egg.client.render.GuiOverlays;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.ScissorUtil;
import egg.client.render.msdf.pipeline.DrawBatcher;

import net.minecraft.client.MinecraftClient;

import net.minecraft.client.gui.DrawContext;

import net.minecraft.client.render.Camera;

import net.minecraft.client.render.GameRenderer;

import net.minecraft.client.render.RenderTickCounter;

import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix4f;

import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.Shadow;

import org.spongepowered.asm.mixin.Unique;

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
        ScissorUtil.reset();
        HitColor hitColor = ModuleManager.getInstance().getModule(HitColor.class);
        GameRenderer self = (GameRenderer) (Object) this;
        if (hitColor != null) {
            hitColor.updateOverlay(self.getOverlayTexture());
        }
        BlockOverlay blockOverlay = ModuleManager.getInstance().getModule(BlockOverlay.class);
        if (blockOverlay != null && blockOverlay.isEnabled()) {
            self.setBlockOutlineEnabled(false);
        } else {
            self.setBlockOutlineEnabled(true);
        }
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/MinecraftClient;getOverlay()Lnet/minecraft/client/gui/screen/Overlay;",
                    ordinal = 0
            )
    )
    private void onRenderHudBeforeOverlays(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci, @Local DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.options.hudHidden || mc.getOverlay() != null || (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen))) {
            return;
        }
        renderCustomHud(context, tickCounter);
    }

    @Unique
    private void renderCustomHud(DrawContext context, RenderTickCounter tickCounter) {
        ScissorUtil.reset();
        if (Interface.isHudBlurEnabled()) {
            Draw2DProcessor.INSTANCE.captureBlur(Interface.getHudBlurStrength());
        }

        Interface iface = ModuleManager.getInstance().getModule(Interface.class);

        if (iface != null && iface.isEnabled()) {

            iface.render(context, tickCounter);

        }

        PotionHUD potionHUD = ModuleManager.getInstance().getModule(PotionHUD.class);

        if (potionHUD != null && potionHUD.isEnabled()) {

            potionHUD.render(context, tickCounter);

        }

        Cooldowns cooldowns = ModuleManager.getInstance().getModule(Cooldowns.class);

        if (cooldowns != null && cooldowns.isEnabled()) {

            cooldowns.render(context, tickCounter);

        }

        ArmorHUD armorHUD = ModuleManager.getInstance().getModule(ArmorHUD.class);
        if (armorHUD != null && armorHUD.isEnabled()) {
            armorHUD.render(context, tickCounter);
        }

        Keybinds keybinds = ModuleManager.getInstance().getModule(Keybinds.class);
        if (keybinds != null && keybinds.isEnabled()) {
            keybinds.render(context, tickCounter);
        }

        EventTracker tracker = ModuleManager.getInstance().getModule(EventTracker.class);
        if (tracker != null && tracker.isEnabled()) {
            tracker.render(context, tickCounter);
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

        NameTags nameTags = ModuleManager.getInstance().getModule(NameTags.class);
        if (nameTags != null && nameTags.isEnabled()) {
            nameTags.render2D(context, tickCounter);
        }

        PearlPredict pearlPredict = ModuleManager.getInstance().getModule(PearlPredict.class);
        if (pearlPredict != null && pearlPredict.isEnabled() && MinecraftClient.getInstance().currentScreen == null) {
            pearlPredict.render(context, false, 0, 0);
        }

        WaypointManager.getInstance().render(context);

        LethalZone lethalZone = ModuleManager.getInstance().getModule(LethalZone.class);
        if (lethalZone != null && lethalZone.isEnabled()) {
            lethalZone.render2D(context);
        }

        DamageNumbers damageNumbers = ModuleManager.getInstance().getModule(DamageNumbers.class);
        if (damageNumbers != null && damageNumbers.isEnabled()) {
            damageNumbers.render2D(context);
        }
    }

    @ModifyArg(
        method = "renderWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/RawProjectionMatrix;set(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
        )
    )
    private Matrix4f egg$motionBlurProjection(Matrix4f matrix4f) {
        egg.client.render.MotionBlurRenderer.INSTANCE.setProjection(matrix4f);
        return matrix4f;
    }

    @Inject(
        method = "renderWorld",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/systems/ProjectionType;)V",
            ordinal = 1
        )
    )
    private void egg$motionBlurPass(RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
        egg.client.render.MotionBlurRenderer.INSTANCE.render((GameRenderer)(Object)this);
    }

    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void egg$naturalMotionBlurAfterRenderWorld(RenderTickCounter renderTickCounter, CallbackInfo ci) {
        egg.client.render.NaturalMotionBlurRenderer.INSTANCE.clearFrameAllocator();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderTail(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.currentScreen instanceof egg.client.ui.ClickGuiScreen gui) {
            gui.renderLate();
        }
        GuiOverlays.renderPending();
        DrawBatcher.flushPending();
        ScissorUtil.reset();
        com.mojang.blaze3d.opengl.GlStateManager._disableScissorTest();
        com.mojang.blaze3d.systems.RenderSystem.disableScissorForRenderTypeDraws();
        com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride = null;
        com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride = null;
    }

}

package egg.client.mixin;



import com.llamalad7.mixinextras.sugar.Local;

import egg.client.command.waypoint.WaypointManager;

import egg.client.module.ModuleManager;

import egg.client.module.combat.HitColor;

import egg.client.module.render.*;

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

        HitColor hitColor = ModuleManager.getInstance().getModule(HitColor.class);

        GameRenderer self = (GameRenderer) (Object) this;

        if (hitColor != null) {

            hitColor.updateOverlay(self.getOverlayTexture());

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

        if (mc == null || mc.world == null || mc.options.hudHidden || mc.getOverlay() != null) {

            return;

        }



        renderCustomHud(context, tickCounter);

    }



    @Unique

    private void renderCustomHud(DrawContext context, RenderTickCounter tickCounter) {

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



        WaypointManager.getInstance().render(context);

        Lyrics lyrics = ModuleManager.getInstance().getModule(Lyrics.class);
        if (lyrics != null && lyrics.isEnabled()) {
            lyrics.render2D(context, tickCounter);
        }

        DrawBatcher.flushPending();

    }



    @ModifyArg(
        method = "renderWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/RawProjectionMatrix;set(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
        )
    )
    private Matrix4f egg$ambienceCaptureProjection(Matrix4f matrix4f) {
        egg.client.render.shader.AmbienceRenderer.setSkyParams(matrix4f.m00(), matrix4f.m11());
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
    private void egg$ambiencePostFx(RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
        egg.client.render.shader.AmbienceRenderer.INSTANCE.renderPostEffects((GameRenderer)(Object)this);
    }

    @Inject(method = "render", at = @At("TAIL"))

    private void onRenderTail(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc != null && mc.currentScreen instanceof egg.client.ui.ClickGuiScreen gui) {

            gui.renderLate();

        }

    }

}


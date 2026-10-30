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



        DrawBatcher.flushPending();

    }



    @Inject(method = "render", at = @At("TAIL"))

    private void onRenderTail(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc != null && mc.currentScreen instanceof egg.client.ui.ClickGuiScreen gui) {

            gui.renderLate();

        }

    }

}


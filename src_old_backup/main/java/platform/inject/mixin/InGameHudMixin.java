package platform.inject.mixin;
import net.minecraft.client.util.math.MatrixStack;





import static delta.core.Interface.aM_;



import delta.module.render.Interface_2;

import delta.render.Animations;

import delta.render.ColorUtil;

import delta.event.CrosshairEvent;

import delta.core.Delta;

import delta.event.DrawEvent;


import delta.module.render.EntityESP;

import delta.core.EventManager;

import delta.core.IEvent;

import delta.core.Interface;

import delta.core.InterfaceC0020Opcode;

import delta.event.RemovalsEvent;

import delta.event.ScoreboardEvent;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.entity.player.PlayerEntity;

import net.minecraft.entity.player.ItemCooldownManager;

import net.minecraft.item.ItemStack;

import net.minecraft.item.Items;

import net.minecraft.text.Text;

import net.minecraft.scoreboard.ScoreboardObjective;

import net.minecraft.client.font.TextRenderer;

import net.minecraft.client.gui.hud.InGameHud;

import net.minecraft.client.gui.DrawContext;

import net.minecraft.client.gui.hud.PlayerListHud;

import net.minecraft.text.StringVisitable;

import net.minecraft.scoreboard.ScoreboardDisplaySlot;

import net.minecraft.component.type.ChargedProjectilesComponent;

import net.minecraft.component.DataComponentTypes;

import net.minecraft.client.render.RenderTickCounter;

import org.spongepowered.asm.mixin.Final;

import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.Shadow;

import org.spongepowered.asm.mixin.Unique;

import org.spongepowered.asm.mixin.injection.At;

import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.ModifyArg;

import org.spongepowered.asm.mixin.injection.ModifyArgs;

import org.spongepowered.asm.mixin.injection.Redirect;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import delta.config.ThemeInfo;

import delta.util.MathUtil;

import net.minecraft.util.Arm;

import platform.inject.accessors.ItemCooldownManagerAccessor;



@Mixin({InGameHud.class})

public class InGameHudMixin {



    @Shadow

    @Final

    private PlayerListHud playerListHud;



    @Unique

    private static float animatedSlotX = -1.0f;



    @ModifyArg(method = {"renderHotbar"}, index = 4, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"))

    private int hideVanillaHotbarTextures(int width) {

        if (Interface_2.getInstance() != null && Interface_2.getInstance().isCustomHotbar()) {

            return 0;

        }

        return width;

    }



    @Inject(method = {"renderHotbar"}, at = {@At("HEAD")})

    private void renderCustomHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {

        Interface_2 hud = Interface_2.getInstance();

        if (hud == null || !hud.isCustomHotbar()) {

            return;

        }

        PlayerEntity player = Interface.aM_.player;

        if (player == null) {

            return;

        }



        int screenW = context.getScaledWindowWidth();

        int screenH = context.getScaledWindowHeight();

        float midX = screenW / 2.0f;

        float midY = screenH - 22.0f;



        float barW = 186.0f;

        float barH = 24.0f;

        float barX = midX - 93.0f;

        float barY = midY - 1.0f;

        float radius = 7.0f;



        delta.render.Draw2DProcessor draw = Delta.h().d().i();
        if (draw == null) return;
        MatrixStack matrices = new MatrixStack();



        // 1. Frosted glass main container with multi-pass blur

        int bgCol = ColorUtil.a(14, 16, 22, 215);

        if (draw.e() != null && !draw.e().e().isEmpty()) {

            draw.b(matrices, barX, barY, barW, barH, radius, bgCol);

        } else {

            draw.a(matrices, barX, barY, barW, barH, radius, bgCol);

        }

        // Subtle crisp white outer contour border

        draw.a(matrices, barX, barY, barW, barH, radius, 0.75f, ColorUtil.a(255, 255, 255, 28));

        // Top specular reflection gloss line

        draw.a(matrices, barX + 6.0f, barY + 0.6f, barW - 12.0f, 0.6f, 0.3f, ColorUtil.a(255, 255, 255, 38));



        // 2. Nine recessed dark slot wells

        int slotBg = ColorUtil.a(0, 0, 0, 75);

        int slotBorder = ColorUtil.a(255, 255, 255, 14);

        for (int s = 0; s < 9; s++) {

            float sx = midX - 90.0f + s * 20.0f;

            float sy = midY + 1.0f;

            draw.a(matrices, sx, sy, 18.0f, 18.0f, 3.5f, slotBg);

            draw.a(matrices, sx, sy, 18.0f, 18.0f, 3.5f, 0.5f, slotBorder);

        }



        // 3. Smooth animated gliding active slot indicator

        int selectedSlot = player.getInventory().getSelectedSlot();

        float targetSlotX = midX - 90.0f + selectedSlot * 20.0f;

        if (animatedSlotX < 0.0f || Math.abs(animatedSlotX - targetSlotX) > 200.0f) {

            animatedSlotX = targetSlotX;

        } else {

            animatedSlotX = MathUtil.c(animatedSlotX, targetSlotX, 0.70f);

        }



        int primaryColor = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();

        float selY = midY + 1.0f;

        // Selection fill aura

        draw.a(matrices, animatedSlotX, selY, 18.0f, 18.0f, 3.5f, ColorUtil.a(primaryColor, 45));

        // Glowing border around active slot

        draw.a(matrices, animatedSlotX, selY, 18.0f, 18.0f, 3.5f, 0.85f, ColorUtil.a(primaryColor, 220));

        // Glowing bottom accent pill

        draw.a(matrices, animatedSlotX + 3.0f, selY + 18.0f - 1.5f, 12.0f, 1.5f, 0.75f, ColorUtil.a(primaryColor, 255));



        // 4. Offhand slot support (frosted blur card if offhand item exists)

        ItemStack offhandStack = player.getOffHandStack();

        if (!offhandStack.isEmpty()) {

            boolean isLeft = (player.getMainArm().getOpposite() == Arm.LEFT);

            float offX = isLeft ? (midX - 93.0f - 27.0f) : (midX + 93.0f + 3.0f);

            float offY = midY - 1.0f;

            float offW = 24.0f;

            float offH = 24.0f;



            if (draw.e() != null && !draw.e().e().isEmpty()) {

                draw.b(matrices, offX, offY, offW, offH, radius, bgCol);

            } else {

                draw.a(matrices, offX, offY, offW, offH, radius, bgCol);

            }

            draw.a(matrices, offX, offY, offW, offH, radius, 0.75f, ColorUtil.a(255, 255, 255, 28));

            draw.a(matrices, offX + 3.0f, offY + 3.0f, 18.0f, 18.0f, 3.5f, slotBg);

            draw.a(matrices, offX + 3.0f, offY + 3.0f, 18.0f, 18.0f, 3.5f, 0.5f, slotBorder);

        }

    }



    @Inject(method = {"renderHotbarItem"}, at = {@At("RETURN")})

    private void onRenderHotbarItem(DrawContext context, int x, int y, RenderTickCounter counter, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {

        ChargedProjectilesComponent charged = (ChargedProjectilesComponent) stack.get(DataComponentTypes.CHARGED_PROJECTILES);

        if (stack.getItem() == Items.CROSSBOW && charged != null && !charged.isEmpty()) {

            context.getMatrices().pushMatrix();

            context.getMatrices().translate(x + 2.0f, y + 2.0f);

            context.getMatrices().scale(0.75f, 0.75f);

            context.drawItem((ItemStack) charged.getProjectiles().getFirst(), 0, 0);

            context.getMatrices().popMatrix();

        }

        if (player == Interface.aM_.player && !stack.isEmpty()) {

            ItemCooldownManagerAccessor cooldowns = (ItemCooldownManagerAccessor) player.getItemCooldownManager();

            Object entry = cooldowns.getEntries().get(player.getItemCooldownManager().getGroup(stack));

            if (entry != null && ((platform.inject.accessors.ItemCooldownEntryAccessor) entry).getEndTick() - cooldowns.getTick() > 0) {

                int remaining = ((platform.inject.accessors.ItemCooldownEntryAccessor) entry).getEndTick() - cooldowns.getTick();

                int seconds = (int) Math.ceil(remaining / 20.0f);

                
                context.getMatrices().pushMatrix();

                context.getMatrices().translate(x, y);

                context.drawText(Interface.aM_.textRenderer, seconds > 99 ? "99+" : String.valueOf(seconds), 0, 0, cooldownColor(remaining), true);

                context.getMatrices().popMatrix();

            }

            delta.module.misc.ServerAssistant sa = Delta.h().d().t().aj();
            if (sa != null && sa.m()) {
                String bind = sa.getBindNameForItem(stack);
                if (bind != null && !bind.equals("N/A")) {
                                        context.getMatrices().pushMatrix();
                    context.getMatrices().translate(x + 10.0f, y + 1.0f);
                    context.getMatrices().scale(0.65f, 0.65f);
                    context.drawText(Interface.aM_.textRenderer, bind, 0, 0, 0xFFFFFFFF, true);
                    context.getMatrices().popMatrix();
                }
            }

        }

    }



    @Unique

    private int cooldownColor(int remaining) {

        int seconds = (int) Math.ceil(remaining / 20.0f);

        switch (seconds) {

            case 0:

            case 1:

            case 2:

            case 3:

                return ColorUtil.a(80, 220, 100, 255);

            case 4:

            case 5:

            case 6:

                return ColorUtil.a(255, InterfaceC0020Opcode.aN, 60, 255);

            default:

                return ColorUtil.a(255, 70, 70, 255);

        }

    }



    @Inject(method = {"render"}, at = {@At("HEAD")})

    public void headRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {

        EventManager.a((IEvent) new DrawEvent(context, tickCounter.getTickProgress(false), DrawEvent.a.D2D));

    }



    @Inject(method = {"render"}, at = {@At("TAIL")})

    private void render(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {

        DrawEvent overlay = new DrawEvent(context, tickCounter.getTickProgress(false), DrawEvent.a.D2D);

        EntityESP entityESP = Delta.h().d().t().aa();


        if (entityESP.m()) {
            delta.render.RenderStateHelper.disableDepthTest();
            try {
                entityESP.s(overlay);
            } finally {
                delta.render.RenderStateHelper.enableDepthTest();
            }
        }

        Animations animations = Delta.h().d().t().Q();

        if (animations.m() && animations.q().a("TAB").c().booleanValue() && animations.r().c() > 0.0f && !Interface.aM_.options.playerListKey.isPressed()) {

            this.playerListHud.render(context, Interface.aM_.getWindow().getScaledWidth(), Interface.aM_.world.getScoreboard(), Interface.aM_.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.LIST));

        }

    }



    @Inject(method = {"renderMainHud"}, at = {@At("HEAD")})

    private void headRenderMainHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {

        Animations animations = Delta.h().d().t().Q();

        if (animations.m() && animations.q().a("Поднятие хотбара").c().booleanValue()) {

            context.getMatrices().pushMatrix();

            context.getMatrices().translate(0.0f, (-16.0f) * animations.s().c());

        }

    }



    @Inject(method = {"renderMainHud"}, at = {@At("RETURN")})

    private void renderMainHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {

        Animations animations = Delta.h().d().t().Q();

        if (animations.m() && animations.q().a("Поднятие хотбара").c().booleanValue()) {

            context.getMatrices().popMatrix();

        }

    }



    @ModifyArg(method = {"renderHotbar"}, index = 2, at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"))

    private int hotbarSelectionSlot(int x) {

        Animations animations = Delta.h().d().t().Q();

        if (animations.m() && animations.q().a("Слот хотбара").c().booleanValue() && Interface.aM_.player != null) {

            return Math.round((x - (Interface.aM_.player.getInventory().getSelectedSlot() * 20)) + (animations.v() * 20.0f));

        }

        return x;

    }



    @Inject(method = {"renderExperienceLevel"}, at = {@At("HEAD")})

    private void headRenderExperienceLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {

        Animations animations = Delta.h().d().t().Q();

        if (animations.m() && animations.q().a("Поднятие хотбара").c().booleanValue()) {

            context.getMatrices().pushMatrix();

            context.getMatrices().translate(0.0f, (-16.0f) * animations.s().c());

        }

    }



    @Inject(method = {"renderExperienceLevel"}, at = {@At("RETURN")})

    private void renderExperienceLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {

        Animations animations = Delta.h().d().t().Q();

        if (animations.m() && animations.q().a("Поднятие хотбара").c().booleanValue()) {

            context.getMatrices().popMatrix();

        }

    }



    @Inject(method = {"renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V"}, at = {@At("HEAD")}, cancellable = true)

    private void renderScoreboardSidebar(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {

        RemovalsEvent event = new RemovalsEvent(RemovalsEvent.a.SCOREBOARD);

        EventManager.a((IEvent) event);

        if (event.a()) {

            ci.cancel();

        }

    }



    @Redirect(method = {"renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/ScoreboardObjective;getDisplayName()Lnet/minecraft/text/Text;"))

    private Text scoreboardTitle(ScoreboardObjective objective) {

        ScoreboardEvent event = new ScoreboardEvent(objective.getDisplayName());

        EventManager.a((IEvent) event);

        return event.b();

    }



    @Redirect(method = {"renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V"}, at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V"))
    private void scoreboardLine(DrawContext context, TextRenderer textRenderer, Text text, int x, int y, int color, boolean shadow) {
        ScoreboardEvent event = new ScoreboardEvent(text);
        EventManager.a((IEvent) event);
        context.drawText(textRenderer, event.b(), x, y, color, shadow);
    }



    @Redirect(method = {"renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V"}, at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/client/font/TextRenderer;getWidth(Lnet/minecraft/text/StringVisitable;)I"))

    private int scoreboardWidth(TextRenderer textRenderer, StringVisitable line) {

        ScoreboardEvent event = new ScoreboardEvent((Text) line);

        EventManager.a((IEvent) event);

        return textRenderer.getWidth(event.b());

    }



    @Inject(method = {"renderPortalOverlay"}, at = {@At("HEAD")}, cancellable = true)

    private void renderPortalOverlay(DrawContext context, float nauseaStrength, CallbackInfo ci) {

        RemovalsEvent event = new RemovalsEvent(RemovalsEvent.a.PORTAL);

        EventManager.a((IEvent) event);

        if (event.a()) {

            ci.cancel();

        }

    }



    @ModifyArgs(method = {"renderMiscOverlays"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V", ordinal = 0))

    private void onRenderPumpkinOverlay(Args args) {

        RemovalsEvent event = new RemovalsEvent(RemovalsEvent.a.PUMPKIN);

        EventManager.a((IEvent) event);

        if (event.a()) {

            args.set(2, Float.valueOf(0.0f));

        }

    }



    @Inject(method = {"renderCrosshair"}, at = {@At("HEAD")}, cancellable = true)

    private void renderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {

        CrosshairEvent event = new CrosshairEvent(context, tickCounter.getTickProgress(false));

        EventManager.a((IEvent) event);

        if (event.a()) {

            ci.cancel();

        }

    }



    @Inject(method = {"renderStatusEffectOverlay"}, at = {@At("HEAD")}, cancellable = true)

    private void renderStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {

        ci.cancel();

    }

}


package platform.inject.mixin;

import static delta.core.Interface.aM_;

import delta.module.render.Globals;
import delta.render.Animations;
import delta.render.ColorUtil;
import delta.core.Delta;
import delta.core.Interface;
import delta.core.InterfaceC0020Opcode;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin({PlayerListHud.class})
public abstract class PlayerListHudMixin {
    @ModifyArgs(method = {"render"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", ordinal = 2), require = 0)
    private void render(Args args, @Local(name = {"list"}) List<PlayerListEntry> list, @Local(name = {"w"}) int w) {
        if (Interface.aM_.player != null && w < list.size() && Interface.aM_.player.getUuid().equals(list.get(w).getProfile().id())) {
            args.set(4, Integer.valueOf(ColorUtil.a(60, InterfaceC0020Opcode.al, 255, 128)));
        }
    }

    @Inject(method = {"setVisible"}, at = {@At("HEAD")})
    private void setVisible(boolean visible, CallbackInfo ci) {
        Animations animations = Delta.h().d().t().Q();
        if (animations.m() && animations.q().a("TAB").c().booleanValue()) {
            animations.r().a(visible);
        }
    }

    @Inject(method = {"render"}, at = {@At("HEAD")})
    private void headRender(DrawContext context, int scaledWindowWidth, Scoreboard scoreboard, @Nullable ScoreboardObjective objective, CallbackInfo ci) {
        Animations animations = Delta.h().d().t().Q();
        if (animations.m() && animations.q().a("TAB").c().booleanValue()) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0.0f, (-200.0f) * (1.0f - animations.r().c()));
        }
    }

    @Inject(method = {"render"}, at = {@At("RETURN")})
    private void render(DrawContext context, int scaledWindowWidth, Scoreboard scoreboard, @Nullable ScoreboardObjective objective, CallbackInfo ci) {
        Animations animations = Delta.h().d().t().Q();
        if (animations.m() && animations.q().a("TAB").c().booleanValue()) {
            context.getMatrices().popMatrix();
        }
    }

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void onGetPlayerName(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        Globals globals = Globals.getInstance();
        if (globals != null && globals.m() && globals.isRenderInTab() && globals.isGlobalUser(entry.getProfile().name(), entry.getProfile().id())) {
            Text original = cir.getReturnValue();
            if (original != null) {
                cir.setReturnValue(globals.formatTabName(original));
            }
        }
    }
}

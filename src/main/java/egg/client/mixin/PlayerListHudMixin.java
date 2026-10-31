package egg.client.mixin;

import egg.client.render.animation.HudAnimations;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Shadow
    private boolean visible;

    @Unique
    private boolean egg$transformed;

    @Inject(method = "setVisible", at = @At("HEAD"))
    private void egg$trackVisibility(boolean flag, CallbackInfo callbackInfo) {
        if (this.visible != flag) {
            HudAnimations.INSTANCE.setTabListVisible(flag);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void egg$pushTabAnimation(DrawContext drawContext, int scaledWidth, Scoreboard scoreboard, ScoreboardObjective scoreboardObjective, CallbackInfo callbackInfo) {
        this.egg$transformed = HudAnimations.INSTANCE.beginTabListTransform(drawContext, scaledWidth);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void egg$popTabAnimation(DrawContext drawContext, int scaledWidth, Scoreboard scoreboard, ScoreboardObjective scoreboardObjective, CallbackInfo callbackInfo) {
        if (this.egg$transformed) {
            this.egg$transformed = false;
            HudAnimations.INSTANCE.endTabListTransform(drawContext);
        }
    }
}

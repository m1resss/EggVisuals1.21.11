package com.holdmylua.source.mixin.render;

import com.holdmylua.source.global.GlobalsStorage;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={InGameHud.class})
public abstract class InGameHudMixin {
    @Shadow
    public abstract TextRenderer getTextRenderer();

    @Inject(method={"render"}, at={@At(value="HEAD")})
    private void debugText(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!GlobalsStorage.debugTextRenderer.get().isEmpty()) {
            int y = 10;
            for (String s : GlobalsStorage.debugTextRenderer.get()) {
                context.drawCenteredTextWithShadow(this.getTextRenderer(), s, 10, y, 255);
                y += 10;
            }
            GlobalsStorage.debugTextRenderer.clear();
        }
    }
}

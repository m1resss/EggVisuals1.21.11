package egg.client.mixin;

import egg.client.render.animation.HudAnimations;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Unique
    private boolean egg$transformed;

    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void egg$beginContainerAnimation(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        this.egg$transformed = HudAnimations.INSTANCE.beginContainerTransform(context, (Screen) (Object) this);
    }

    @Inject(method = "renderWithTooltip", at = @At("RETURN"))
    private void egg$endContainerAnimation(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.egg$transformed) {
            this.egg$transformed = false;
            HudAnimations.INSTANCE.endContainerTransform(context);
        }
    }
}

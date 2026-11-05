package egg.client.mixin;

import egg.client.module.misc.ServerAssistant;
import egg.client.render.animation.HudAnimations;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

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

    @Inject(method = "getTooltipFromItem", at = @At("RETURN"), cancellable = true)
    private static void egg$modifyItemTooltip(MinecraftClient client, ItemStack stack, CallbackInfoReturnable<List<Text>> cir) {
        List<Text> original = cir.getReturnValue();
        if (original != null && !original.isEmpty()) {
            ServerAssistant sa = ServerAssistant.getInstance();
            if (sa != null && sa.isEnabled()) {
                List<Text> mutable = new ArrayList<>(original);
                sa.modifyTooltip(stack, mutable);
                cir.setReturnValue(mutable);
            }
        }
    }
}

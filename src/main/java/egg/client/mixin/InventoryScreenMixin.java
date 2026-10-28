package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.combat.AutoSwap;
import egg.client.module.player.InventorySorter;
import egg.client.render.font.ClientFont;
import egg.client.ui.widget.InventoryScreenOverlay;
import egg.client.ui.widget.SwapWheelWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        InventorySorter sorter = ModuleManager.getInstance().getModule(InventorySorter.class);
        if (sorter != null && sorter.isEnabled()) {
            sorter.onInventoryScreenOpened();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        InventoryScreen self = (InventoryScreen) (Object) this;

        // AutoSwap mini wheel on the left side
        AutoSwap as = ModuleManager.getInstance().getModule(AutoSwap.class);
        if (as != null && as.isEnabled()) {
            int guiX = ((HandledScreenAccessor) self).getX();
            int guiY = ((HandledScreenAccessor) self).getY();
            float miniRadius = 36.0f;
            float miniCx = Math.max(miniRadius + 8.0f, guiX - miniRadius - 16.0f);
            float miniCy = guiY + 83.0f;

            int hoveredSector = SwapWheelWidget.getSectorAt(mouseX, mouseY, miniCx, miniCy, miniRadius);
            SwapWheelWidget.renderWheel(context, miniCx, miniCy, miniRadius, hoveredSector, as.getWheelSlots());

            // Header above the mini wheel
            ClientFont.drawCentered(context, "AutoSwap", miniCx, miniCy - miniRadius - 12.0f, ClientFont.SF_BOLD, 0xFFFFFFFF);
            // Hint below the mini wheel
            ClientFont.drawCentered(context, "ПКМ - сброс", miniCx, miniCy + miniRadius + 6.0f, ClientFont.SF_REGULAR, 0x80FFFFFF);
        }

        // InventorySorter panel on the right side
        InventoryScreenOverlay.render(context, self, mouseX, mouseY);
    }
}

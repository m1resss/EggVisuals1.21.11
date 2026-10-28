package egg.client.ui.screen;

import egg.client.module.combat.AutoSwap;
import egg.client.ui.widget.SwapWheelWidget;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class SwapScreen extends Screen {
    private static final float WHEEL_RADIUS = 56.0f;
    private int hoveredSector = -1;

    public SwapScreen(Text title) {
        super(title);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // НЕ ЗАТЕМНЯТЬ ФОН: мир игры остается полностью прозрачным
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float cx = this.width / 2.0f;
        float cy = this.height / 2.0f;

        this.hoveredSector = SwapWheelWidget.getSectorAt(mouseX, mouseY, cx, cy, WHEEL_RADIUS);

        AutoSwap as = AutoSwap.getInstance();
        ItemStack[] slots = (as != null) ? as.getWheelSlots() : null;

        SwapWheelWidget.renderWheel(context, cx, cy, WHEEL_RADIUS, this.hoveredSector, slots);
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        if (click.button() == 0 && this.hoveredSector >= 0 && this.hoveredSector < 3) {
            AutoSwap as = AutoSwap.getInstance();
            if (as != null) {
                ItemStack stack = as.getWheelSlot(this.hoveredSector);
                if (stack != null && !stack.isEmpty()) {
                    if (AutoSwap.performLegitSwap(stack)) {
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(click, bl);
    }

    public void confirmAndClose() {
        if (this.hoveredSector >= 0 && this.hoveredSector < 3) {
            AutoSwap as = AutoSwap.getInstance();
            if (as != null) {
                ItemStack stack = as.getWheelSlot(this.hoveredSector);
                if (stack != null && !stack.isEmpty()) {
                    if (AutoSwap.performLegitSwap(stack)) {
                        return; // DO NOT call this.close()! startSwap already opened InventoryScreen
                    }
                }
            }
        }
        this.close();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(input);
    }
}

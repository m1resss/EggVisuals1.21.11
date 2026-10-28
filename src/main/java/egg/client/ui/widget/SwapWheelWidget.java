package egg.client.ui.widget;

import egg.client.render.Render2D;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class SwapWheelWidget {

    public static int getSectorAt(double mouseX, double mouseY, float cx, float cy, float radius) {
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.hypot(dx, dy);
        if (dist > radius || dist < 4.0) return -1;

        double angleDeg = Math.toDegrees(Math.atan2(dy, dx));

        if (angleDeg >= -150.0 && angleDeg < -30.0) {
            return 0;
        }

        if (angleDeg >= 90.0 || angleDeg < -150.0) {
            return 1;
        }

        if (angleDeg >= -30.0 && angleDeg < 90.0) {
            return 2;
        }
        return -1;
    }

    public static ItemStack getDefaultSphereStack() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null) {

            for (int i = 0; i < mc.player.getInventory().size(); i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack != null && !stack.isEmpty() && stack.isOf(Items.PLAYER_HEAD)) {
                    return stack;
                }
            }
        }
        return new ItemStack(Items.ENDER_EYE);
    }

    public static void renderWheel(DrawContext context, float cx, float cy, float radius, int hoveredSector, ItemStack[] slots) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int themeColor = Theme.getAccent();

        Render2D.drawWheelBase(context, cx, cy, radius, themeColor);

        if (hoveredSector >= 0 && hoveredSector < 3) {
            Render2D.drawWheelSector(context, cx, cy, radius, hoveredSector, ColorUtil.withAlpha(themeColor, 130));
        }

        Render2D.drawWheelLines(context, cx, cy, radius);

        float[][] sectorPos = new float[][]{
                {cx, cy - radius * 0.52f},
                {cx - radius * 0.45f, cy + radius * 0.26f},
                {cx + radius * 0.45f, cy + radius * 0.26f}
        };

        if (slots != null) {
            for (int i = 0; i < 3; i++) {
                if (i >= slots.length) break;
                ItemStack stack = slots[i];
                if (stack != null && !stack.isEmpty()) {
                    float ix = sectorPos[i][0];
                    float iy = sectorPos[i][1];
                    context.drawItem(stack, Math.round(ix - 8.0f), Math.round(iy - 8.0f));
                    if (mc.textRenderer != null) {
                        context.drawStackOverlay(mc.textRenderer, stack, Math.round(ix - 8.0f), Math.round(iy - 8.0f));
                    }
                }
            }
        }
    }
}

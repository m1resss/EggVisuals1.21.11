package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.hit.EntityHitResult;

/**
 * 100% mathematically symmetric esports Crosshair.
 */
public class Crosshair extends Module {
    private final SliderSetting gap = new SliderSetting("Расстояние от центра", 3.0, 0.0, 10.0, 0.5, "Зазор между центром и сегментами");
    private final SliderSetting length = new SliderSetting("Длина сегментов", 3.5, 1.0, 10.0, 0.5, "Длина каждого сегмента прицела");
    private final SliderSetting thickness = new SliderSetting("Толщина", 1.0, 1.0, 3.0, 1.0, "Толщина линий прицела");
    private final BooleanSetting adaptive = new BooleanSetting("Адаптивность", false, "Расширение прицела при перезарядке удара");
    private final BooleanSetting outline = new BooleanSetting("Контур", true, "Чёрный контур вокруг сегментов");
    private final BooleanSetting dot = new BooleanSetting("Центральная метка", false, "Точка в самом центре прицела");
    private final ColorSetting crosshairColor = new ColorSetting("Цвет прицела", 0xFFFFFFFF, "Цвет прицела");

    public Crosshair() {
        super("Crosshair", "Отображает настраиваемый прицел на экране", Category.ESP);
        addSettings(gap, length, thickness, adaptive, outline, dot, crosshairColor);
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.options.hudHidden || !mc.options.getPerspective().isFirstPerson()) return;

        float tickProgress = tickCounter != null ? tickCounter.getTickProgress(true) : 0.0f;
        float cooldown = 1.0f - mc.player.getAttackCooldownProgress(tickProgress);

        int cx = mc.getWindow().getScaledWidth() / 2;
        int cy = mc.getWindow().getScaledHeight() / 2;

        int gapVal = Math.round((float) this.gap.get());
        int lenVal = Math.max(1, Math.round((float) this.length.get()));
        int th = Math.max(1, Math.round((float) this.thickness.get()));

        int actualGap = this.adaptive.get() ? gapVal + Math.round(8.0f * cooldown) : gapVal;

        int color = (mc.crosshairTarget instanceof EntityHitResult) ? ColorUtil.rgb(255, 60, 60) : crosshairColor.get();
        int outlineCol = 0xFF000000;
        boolean hasOutline = this.outline.get();

        int hSpanY = (th == 1) ? cy : cy - (th / 2);
        int vSpanX = (th == 1) ? cx : cx - (th / 2);

        // Right segment
        int rX1 = cx + actualGap;
        int rX2 = rX1 + lenVal;
        int rY1 = hSpanY;
        int rY2 = rY1 + th;

        // Left segment (symmetric distance from center)
        int lX1 = cx - actualGap - lenVal + 1;
        int lX2 = cx - actualGap + 1;
        int lY1 = hSpanY;
        int lY2 = lY1 + th;

        // Bottom segment
        int bX1 = vSpanX;
        int bX2 = bX1 + th;
        int bY1 = cy + actualGap;
        int bY2 = bY1 + lenVal;

        // Top segment (symmetric distance from center)
        int tX1 = vSpanX;
        int tX2 = tX1 + th;
        int tY1 = cy - actualGap - lenVal + 1;
        int tY2 = cy - actualGap + 1;

        if (hasOutline) {
            context.fill(rX1 - 1, rY1 - 1, rX2 + 1, rY2 + 1, outlineCol);
            context.fill(lX1 - 1, lY1 - 1, lX2 + 1, lY2 + 1, outlineCol);
            context.fill(bX1 - 1, bY1 - 1, bX2 + 1, bY2 + 1, outlineCol);
            context.fill(tX1 - 1, tY1 - 1, tX2 + 1, tY2 + 1, outlineCol);
        }

        context.fill(rX1, rY1, rX2, rY2, color);
        context.fill(lX1, lY1, lX2, lY2, color);
        context.fill(bX1, bY1, bX2, bY2, color);
        context.fill(tX1, tY1, tX2, tY2, color);

        // Center dot
        if (this.dot.get() && actualGap > 0) {
            int dotX1 = vSpanX;
            int dotX2 = vSpanX + th;
            int dotY1 = hSpanY;
            int dotY2 = hSpanY + th;
            if (hasOutline) {
                context.fill(dotX1 - 1, dotY1 - 1, dotX2 + 1, dotY2 + 1, outlineCol);
            }
            context.fill(dotX1, dotY1, dotX2, dotY2, color);
        }
    }
}

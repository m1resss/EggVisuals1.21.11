package egg.client.module.render;

import egg.client.mixin.ClientPlayerInteractionManagerAccessor;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.Render2D;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.ProjectUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

public class BreakBlock extends Module {
    private final ModeSetting colorMode = new ModeSetting("Режим цвета", "Кастом", "Цветовая схема", "Кастом", "Тема", "Градиент");
    private final ColorSetting customColor = new ColorSetting("Свой цвет", 0xFF8B5CF6, "Цвет индикатора");
    private final BooleanSetting showPercent = new BooleanSetting("Проценты", true, "Отображать процент слома блока в мире");

    private float smoothProgress = 0.0f;
    private BlockPos currentBreakingPos = null;

    public BreakBlock() {
        super("Break Block", "Подсветка процесса и процентов при сломе блока", Category.ESP);
        addSettings(colorMode, customColor, showPercent);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.smoothProgress = 0.0f;
        this.currentBreakingPos = null;
    }

    public void onTick() {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null) {
            this.smoothProgress = 0.0f;
            this.currentBreakingPos = null;
            return;
        }

        ClientPlayerInteractionManagerAccessor accessor = (ClientPlayerInteractionManagerAccessor) mc.interactionManager;
        float rawProgress = accessor.getCurrentBreakingProgress();
        BlockPos pos = accessor.getCurrentBreakingPos();
        boolean isBreaking = accessor.isBreakingBlock() && rawProgress > 0.0f && pos != null;

        if (isBreaking) {
            this.currentBreakingPos = pos;
            this.smoothProgress = MathHelper.lerp(0.45f, this.smoothProgress, rawProgress);
        } else {
            this.smoothProgress = MathHelper.lerp(0.5f, this.smoothProgress, 0.0f);
            if (this.smoothProgress < 0.02f) {
                this.smoothProgress = 0.0f;
                this.currentBreakingPos = null;
            }
        }
    }

    private int getActiveColor(float progress, int alpha) {
        if (this.colorMode.is("Кастом")) {
            int rgb = this.customColor.get();
            return ColorUtil.rgba((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
        } else if (this.colorMode.is("Градиент")) {
            float r, g, b;
            if (progress < 0.5f) {
                float factor = progress / 0.5f;
                r = 1.0f;
                g = factor;
                b = 0.2f;
            } else {
                float factor = (progress - 0.5f) / 0.5f;
                r = 1.0f - factor * 0.85f;
                g = 1.0f;
                b = 0.2f + factor * 0.7f;
            }
            return ColorUtil.rgba((int) (r * 255), (int) (g * 255), (int) (b * 255), alpha);
        } else {
            int themeCol = Theme.getAccent();
            return ColorUtil.rgba((themeCol >> 16) & 0xFF, (themeCol >> 8) & 0xFF, themeCol & 0xFF, alpha);
        }
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        if (this.currentBreakingPos == null || this.smoothProgress < 0.02f) {
            return;
        }

        if (!this.showPercent.get()) {
            return;
        }

        Vec3d center = new Vec3d(this.currentBreakingPos.getX() + 0.5, this.currentBreakingPos.getY() + 0.5, this.currentBreakingPos.getZ() + 0.5);
        if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(this.currentBreakingPos)) {
            center = center.add(Vec3d.of(hit.getSide().getVector()).multiply(0.505));
        }

        Vector2f screen = ProjectUtil.toScreen(center.x, center.y, center.z);
        if (!ProjectUtil.isOnScreen(screen)) {
            return;
        }

        float sx = screen.x;
        float sy = screen.y;
        int activeColor = getActiveColor(this.smoothProgress, 255);

        String text = (int) (this.smoothProgress * 100.0f) + "%";
        Render2D.drawCenteredText(context, text, sx, sy - 4.0f, activeColor);
    }
}

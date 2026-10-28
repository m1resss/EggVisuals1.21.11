package delta.module.render;

import static delta.core.Interface.aM_;
import delta.config.ThemeInfo;
import delta.core.Category;
import delta.core.Delta;
import delta.core.EventTarget;
import delta.core.Interface;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.DrawEvent;
import delta.event.TickEvent;
import delta.render.ColorUtil;
import delta.render.Fonts;
import delta.setting.BooleanSetting;
import delta.setting.ColorSetting;
import delta.setting.ModeSetting;
import delta.setting.SliderSetting;
import delta.util.ProjectUtil;
import platform.inject.accessors.ClientPlayerInteractionManagerAccessor;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

@ModuleRegister(a = "Break Block", b = "Подсветка заливки блока и вспышка при сломе", c = Category.Render)
public class BreakBlock extends Module implements Interface {

    private final ModeSetting colorMode = new ModeSetting("Режим цвета", "Кастом", "Кастом", "Тема", "Градиент");
    private final ColorSetting customColor = (ColorSetting) new ColorSetting("Свой цвет", 0xFF8B5CF6).a(() -> Boolean.valueOf(this.colorMode.l("Кастом")));
    private final BooleanSetting flashOnBreak = new BooleanSetting("Вспышка при сломе", true);
    private final SliderSetting flashExpansion = (SliderSetting) new SliderSetting("Расширение вспышки", 0.45f, 0.05f, 3.0f, 0.05f)
            .a(() -> Boolean.valueOf(this.flashOnBreak.c().booleanValue()));
    private final BooleanSetting showPercent = new BooleanSetting("Проценты", true);

    private float smoothProgress = 0.0f;
    private BlockPos currentBreakingPos = null;

    // Flash effect state
    private long lastBreakCompleteTime = 0L;
    private Vec3d lastBreakPosVec = null;

    public BreakBlock() {
        a(this.colorMode, this.customColor, this.flashOnBreak, this.flashExpansion, this.showPercent);
    }

    @Override
    public void c() {
        super.c();
        this.smoothProgress = 0.0f;
        this.currentBreakingPos = null;
        this.lastBreakPosVec = null;
    }

    public void notifyBlockBroken(BlockPos pos) {
        if (!m() || !this.flashOnBreak.c().booleanValue() || pos == null) {
            return;
        }
        this.lastBreakCompleteTime = System.currentTimeMillis();
        this.lastBreakPosVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (aM_.player == null || aM_.world == null || aM_.interactionManager == null) {
            this.smoothProgress = 0.0f;
            this.currentBreakingPos = null;
            return;
        }

        ClientPlayerInteractionManagerAccessor accessor = (ClientPlayerInteractionManagerAccessor) aM_.interactionManager;
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
        if (this.colorMode.l("Кастом")) {
            return ColorUtil.a(this.customColor.c().intValue(), alpha);
        } else if (this.colorMode.l("Градиент")) {
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
            return ColorUtil.a((int) (r * 255), (int) (g * 255), (int) (b * 255), alpha);
        } else {
            int themeCol = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
            return ColorUtil.a(themeCol, alpha);
        }
    }

    @EventTarget
    public void onRender(DrawEvent event) {
        if (aM_.player == null || aM_.world == null) {
            return;
        }

        if (event.c()) {
            render3D(event);
        } else if (event.b()) {
            render2D(event);
        }
    }

    private void render3D(DrawEvent event) {
        long now = System.currentTimeMillis();

        // 1. 3D Box Fill on breaking block (Только режим заливки)
        if (this.currentBreakingPos != null && this.smoothProgress > 0.01f) {
            double minX = this.currentBreakingPos.getX();
            double minY = this.currentBreakingPos.getY();
            double minZ = this.currentBreakingPos.getZ();
            double height = (double) this.smoothProgress;
            Box box = new Box(minX, minY, minZ, minX + 1.0, minY + height, minZ + 1.0);

            int boxColor = getActiveColor(this.smoothProgress, 120);
            event.e().a(event.h(), box, boxColor, 1.5f);
        }

        // 2. 3D Flash effect on break completion (Без дуги вокруг, с ползунком расширения)
        if (this.flashOnBreak.c().booleanValue() && this.lastBreakPosVec != null) {
            long elapsed = now - this.lastBreakCompleteTime;
            if (elapsed < 450L) {
                float factor = (float) elapsed / 450.0f;
                float ease = 1.0f - factor * factor;
                float expansion = factor * this.flashExpansion.c().floatValue();

                double x = this.lastBreakPosVec.x;
                double y = this.lastBreakPosVec.y;
                double z = this.lastBreakPosVec.z;

                Box flashBox = new Box(x - 0.5 - expansion, y - 0.5 - expansion, z - 0.5 - expansion,
                                       x + 0.5 + expansion, y + 0.5 + expansion, z + 0.5 + expansion);

                int flashColor = getActiveColor(1.0f, (int) (ease * 240.0f));
                event.e().a(event.h(), flashBox, flashColor, 2.0f);
            }
        }
    }

    private void render2D(DrawEvent event) {
        if (this.currentBreakingPos == null || this.smoothProgress < 0.02f) {
            return;
        }

        if (!this.showPercent.c().booleanValue()) {
            return;
        }

        Vec3d center = new Vec3d(this.currentBreakingPos.getX() + 0.5, this.currentBreakingPos.getY() + 0.5, this.currentBreakingPos.getZ() + 0.5);
        if (aM_.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(this.currentBreakingPos)) {
            center = center.add(Vec3d.of(hit.getSide().getVector()).multiply(0.505));
        }

        Vector2f screen = ProjectUtil.a(center.x, center.y, center.z);
        if (!ProjectUtil.a(screen)) {
            return;
        }

        float sx = screen.x;
        float sy = screen.y;
        int activeColor = getActiveColor(this.smoothProgress, 255);
        MatrixStack matrices = event.h();

        // Draw Percent Text in Center
        String text = (int) (this.smoothProgress * 100.0f) + "%";
        float w = Fonts.b.a(text, 6.5f);
        float h = Fonts.b.d().lineHeight() * 6.5f;
        Fonts.b.a(matrices, text, sx - (w / 2.0f), sy - (h / 2.0f) + 0.5f, 6.5f, activeColor);
    }
}

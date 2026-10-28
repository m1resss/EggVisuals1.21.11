package delta.module.render;
import delta.render.RenderStateHelper;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import static delta.core.Interface.aM_;
import delta.core.Category;
import delta.core.EventTarget;
import delta.core.Interface;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.DrawEvent;
import delta.render.ColorUtil;
import delta.setting.BooleanSetting;
import delta.setting.ColorSetting;
import delta.setting.SliderSetting;
import lombok.Generated;

import net.minecraft.client.render.BufferBuilder;
import delta.render.ImmediateBufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

@ModuleRegister(a = "Hitbox Color", b = "Кастомизация цвета, заливки и подсветки хитбокса при ударе", c = Category.Render)
public class HitboxColor extends Module implements Interface {
    private static HitboxColor instance;

    private final ColorSetting color = new ColorSetting("Цвет хитбокса", ColorUtil.a(88, 180, 252, 255));
    private final BooleanSetting fill = new BooleanSetting("Заливка", true);
    private final SliderSetting fillAlpha = (SliderSetting) new SliderSetting("Прозрачность заливки", 0.25f, 0.05f, 1.0f, 0.05f).a(() -> Boolean.valueOf(this.fill.c().booleanValue()));
    private final BooleanSetting hurtFade = new BooleanSetting("Краснеть при ударе", true);
    private final SliderSetting lineWidth = new SliderSetting("Толщина линий", 1.5f, 0.5f, 4.0f, 0.5f);
    private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", false);
    private final SliderSetting maxDistance = new SliderSetting("Дистанция", 32.0f, 4.0f, 64.0f, 2.0f);

    public HitboxColor() {
        instance = this;
        a(this.color, this.fill, this.fillAlpha, this.hurtFade, this.lineWidth, this.onlyPlayers, this.maxDistance);
    }

    public static HitboxColor getInstance() {
        return instance;
    }

    @EventTarget
    public void onRender3D(DrawEvent event) {
        if (!event.c()) {
            return;
        }

        if (aM_.player == null || aM_.world == null || aM_.gameRenderer == null) {
            return;
        }

        Camera camera = aM_.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        float tickDelta = event.g();
        double maxDistSq = Math.pow(this.maxDistance.c().doubleValue(), 2.0);

        List<RenderableBox> boxes = new ArrayList<>();

        for (Entity entity : aM_.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isInvisible() || living.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.INVISIBILITY)) {
                continue;
            }
            if (living == aM_.player && camera.isThirdPerson() == false) {
                continue;
            }
            if (this.onlyPlayers.c().booleanValue() && !(living instanceof PlayerEntity)) {
                continue;
            }
            if (aM_.player.squaredDistanceTo(living) > maxDistSq) {
                continue;
            }

            // Interpolate bounding box position
            Box rawBox = living.getBoundingBox();
            double dx = living.getLerpedPos(tickDelta).x - living.getX() - camPos.x;
            double dy = living.getLerpedPos(tickDelta).y - living.getY() - camPos.y;
            double dz = living.getLerpedPos(tickDelta).z - living.getZ() - camPos.z;
            Box box = rawBox.offset(dx, dy, dz);

            // Compute hurt reaction color interpolation
            float hurtFactor = 0.0f;
            if (this.hurtFade.c().booleanValue() && living.hurtTime > 0) {
                hurtFactor = MathHelper.clamp((living.hurtTime - tickDelta) / 10.0f, 0.0f, 1.0f);
            }

            int baseCol = this.color.c().intValue();
            int r = (baseCol >> 16) & 0xFF;
            int g = (baseCol >> 8) & 0xFF;
            int b = baseCol & 0xFF;
            int a = (baseCol >> 24) & 0xFF;

            if (hurtFactor > 0.0f) {
                r = (int) MathHelper.lerp(hurtFactor, (float) r, 255.0f);
                g = (int) MathHelper.lerp(hurtFactor, (float) g, 35.0f);
                b = (int) MathHelper.lerp(hurtFactor, (float) b, 35.0f);
            }

            int outlineCol = ColorUtil.a(r, g, b, a);
            int fillCol = 0;
            if (this.fill.c().booleanValue()) {
                int fillA = (int) (this.fillAlpha.c().floatValue() * 255.0f);
                fillCol = ColorUtil.a(r, g, b, fillA);
            }

            boxes.add(new RenderableBox(box, outlineCol, fillCol));
        }

        if (boxes.isEmpty()) {
            return;
        }

        MatrixStack matrices = event.h();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        RenderStateHelper.enableBlend();
        RenderStateHelper.defaultBlendFunc();
        RenderStateHelper.disableCull();

        RenderStateHelper.enableDepthTest();
        RenderStateHelper.depthFunc(GL11.GL_LEQUAL);
        RenderStateHelper.depthMask(true);

        // 1. Fill Pass
        if (this.fill.c().booleanValue()) {
            // RenderSystem.setShader(...);
            BufferBuilder fb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (RenderableBox rb : boxes) {
                if (rb.fillColor != 0) {
                    drawBoxQuads(fb, matrix, rb.box, rb.fillColor);
                }
            }
            ImmediateBufferRenderer.draw(fb.end());
        }

        // 2. Outline Pass
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        // RenderSystem.setShader(...);
        RenderStateHelper.lineWidth(this.lineWidth.c().floatValue());
        BufferBuilder lb = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL);
        MatrixStack.Entry entry = matrices.peek();
        for (RenderableBox rb : boxes) {
            drawBoxLines(lb, matrix, entry, rb.box, rb.outlineColor);
        }
        ImmediateBufferRenderer.draw(lb.end());
        GL11.glDisable(GL11.GL_LINE_SMOOTH);

        // Restore state
        RenderStateHelper.enableDepthTest();
        RenderStateHelper.enableCull();
        RenderStateHelper.disableBlend();
    }

    private void drawBoxQuads(BufferBuilder buffer, Matrix4f matrix, Box b, int color) {
        float minX = (float) b.minX;
        float minY = (float) b.minY;
        float minZ = (float) b.minZ;
        float maxX = (float) b.maxX;
        float maxY = (float) b.maxY;
        float maxZ = (float) b.maxZ;

        // Bottom
        buffer.vertex(matrix, minX, minY, minZ).color(color);
        buffer.vertex(matrix, maxX, minY, minZ).color(color);
        buffer.vertex(matrix, maxX, minY, maxZ).color(color);
        buffer.vertex(matrix, minX, minY, maxZ).color(color);

        // Top
        buffer.vertex(matrix, minX, maxY, minZ).color(color);
        buffer.vertex(matrix, minX, maxY, maxZ).color(color);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(color);
        buffer.vertex(matrix, maxX, maxY, minZ).color(color);

        // North (-Z)
        buffer.vertex(matrix, minX, minY, minZ).color(color);
        buffer.vertex(matrix, minX, maxY, minZ).color(color);
        buffer.vertex(matrix, maxX, maxY, minZ).color(color);
        buffer.vertex(matrix, maxX, minY, minZ).color(color);

        // South (+Z)
        buffer.vertex(matrix, minX, minY, maxZ).color(color);
        buffer.vertex(matrix, maxX, minY, maxZ).color(color);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(color);
        buffer.vertex(matrix, minX, maxY, maxZ).color(color);

        // West (-X)
        buffer.vertex(matrix, minX, minY, minZ).color(color);
        buffer.vertex(matrix, minX, minY, maxZ).color(color);
        buffer.vertex(matrix, minX, maxY, maxZ).color(color);
        buffer.vertex(matrix, minX, maxY, minZ).color(color);

        // East (+X)
        buffer.vertex(matrix, maxX, minY, minZ).color(color);
        buffer.vertex(matrix, maxX, maxY, minZ).color(color);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(color);
        buffer.vertex(matrix, maxX, minY, maxZ).color(color);
    }

    private void drawBoxLines(BufferBuilder buffer, Matrix4f matrix, MatrixStack.Entry entry, Box b, int color) {
        float minX = (float) b.minX;
        float minY = (float) b.minY;
        float minZ = (float) b.minZ;
        float maxX = (float) b.maxX;
        float maxY = (float) b.maxY;
        float maxZ = (float) b.maxZ;

        // Bottom 4 edges
        addLine(buffer, matrix, entry, minX, minY, minZ, maxX, minY, minZ, color);
        addLine(buffer, matrix, entry, maxX, minY, minZ, maxX, minY, maxZ, color);
        addLine(buffer, matrix, entry, maxX, minY, maxZ, minX, minY, maxZ, color);
        addLine(buffer, matrix, entry, minX, minY, maxZ, minX, minY, minZ, color);

        // Top 4 edges
        addLine(buffer, matrix, entry, minX, maxY, minZ, maxX, maxY, minZ, color);
        addLine(buffer, matrix, entry, maxX, maxY, minZ, maxX, maxY, maxZ, color);
        addLine(buffer, matrix, entry, maxX, maxY, maxZ, minX, maxY, maxZ, color);
        addLine(buffer, matrix, entry, minX, maxY, maxZ, minX, maxY, minZ, color);

        // 4 Vertical Pillars
        addLine(buffer, matrix, entry, minX, minY, minZ, minX, maxY, minZ, color);
        addLine(buffer, matrix, entry, maxX, minY, minZ, maxX, maxY, minZ, color);
        addLine(buffer, matrix, entry, maxX, minY, maxZ, maxX, maxY, maxZ, color);
        addLine(buffer, matrix, entry, minX, minY, maxZ, minX, maxY, maxZ, color);
    }

    private void addLine(BufferBuilder buffer, Matrix4f matrix, MatrixStack.Entry entry, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = len > 1.0E-5f ? dx / len : 0.0f;
        float ny = len > 1.0E-5f ? dy / len : 0.0f;
        float nz = len > 1.0E-5f ? dz / len : 0.0f;

        buffer.vertex(matrix, x1, y1, z1).color(color).normal(entry, nx, ny, nz);
        buffer.vertex(matrix, x2, y2, z2).color(color).normal(entry, nx, ny, nz);
    }

    private static class RenderableBox {
        final Box box;
        final int outlineColor;
        final int fillColor;

        RenderableBox(Box box, int outlineColor, int fillColor) {
            this.box = box;
            this.outlineColor = outlineColor;
            this.fillColor = fillColor;
        }
    }

    @Generated
    public ColorSetting getColor() {
        return this.color;
    }

    @Generated
    public BooleanSetting getFill() {
        return this.fill;
    }

    @Generated
    public SliderSetting getFillAlpha() {
        return this.fillAlpha;
    }

    @Generated
    public BooleanSetting getHurtFade() {
        return this.hurtFade;
    }

    @Generated
    public SliderSetting getLineWidth() {
        return this.lineWidth;
    }

    @Generated
    public BooleanSetting getOnlyPlayers() {
        return this.onlyPlayers;
    }

    @Generated
    public SliderSetting getMaxDistance() {
        return this.maxDistance;
    }
}

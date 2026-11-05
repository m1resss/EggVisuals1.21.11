package egg.client.module.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.ParticleBlurRenderer;
import egg.client.render.shader.Pipelines;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

public class BlockOverlay extends Module {
    private static BlockOverlay instance;

    public static BlockOverlay getInstance() {
        return instance;
    }

    private final ModeSetting mode = new ModeSetting("Режим", "Неон", "Стиль подсветки блока", "Неон", "Только грани", "Заливка");
    private final ModeSetting colorMode = new ModeSetting("Режим цвета", "Кастом", "Цветовая схема", "Кастом", "Тема", "Градиент");
    private final ColorSetting color = new ColorSetting("Цвет", 0xFF00E5FF, "Основной цвет подсветки");
    private final ColorSetting color2 = new ColorSetting("Второй цвет", 0xFF3B82F6, "Второй цвет для градиента");
    private final SliderSetting alpha = new SliderSetting("Прозрачность", 1.0, 0.1, 1.0, 0.05, "Общая прозрачность эффекта");
    private final SliderSetting lineWidth = new SliderSetting("Толщина линий", 2.2, 0.5, 6.0, 0.1, "Толщина светящихся линий");
    private final SliderSetting smoothSpeed = new SliderSetting("Скорость перехода", 18.0, 5.0, 40.0, 1.0, "Плавность перехода между блоками");
    private final BooleanSetting throughWalls = new BooleanSetting("Через стены", false, "Отображение блока сквозь препятствия");

    private double curMinX, curMinY, curMinZ;
    private double curMaxX, curMaxY, curMaxZ;
    private float curAlpha = 0.0f;
    private boolean hasBox = false;
    private long lastTimeNanos = 0L;

    public BlockOverlay() {
        super("Block Overlay", "Плавная шейдерная подсветка выбранного блока", Category.ESP);
        instance = this;
        addSettings(mode, colorMode, color, color2, alpha, lineWidth, smoothSpeed, throughWalls);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.curAlpha = 0.0f;
        this.hasBox = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.gameRenderer != null) {
            mc.gameRenderer.setBlockOutlineEnabled(true);
        }
    }

    public void renderWorld3D(MatrixStack matrices, Camera camera, float tickDelta, VertexConsumerProvider.Immediate immediate, Vec3d camPos) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        long now = System.nanoTime();
        if (lastTimeNanos == 0L) {
            lastTimeNanos = now;
        }
        float dt = (now - lastTimeNanos) / 1_000_000_000.0f;
        lastTimeNanos = now;
        dt = MathHelper.clamp(dt, 0.001f, 0.08f);

        BlockHitResult bhr = null;
        if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            if (!mc.world.isAir(pos)) {
                BlockState state = mc.world.getBlockState(pos);
                VoxelShape shape = state.getOutlineShape(mc.world, pos);
                if (!shape.isEmpty()) {
                    bhr = hit;
                }
            }
        }

        float speed = (float) smoothSpeed.get();
        float factor = 1.0f - (float) Math.exp(-speed * dt);

        if (bhr != null) {
            BlockPos pos = bhr.getBlockPos();
            BlockState state = mc.world.getBlockState(pos);
            VoxelShape shape = state.getOutlineShape(mc.world, pos);
            Box targetBox = shape.getBoundingBox().offset(pos).expand(0.002);

            if (!hasBox) {
                curMinX = targetBox.minX;
                curMinY = targetBox.minY;
                curMinZ = targetBox.minZ;
                curMaxX = targetBox.maxX;
                curMaxY = targetBox.maxY;
                curMaxZ = targetBox.maxZ;
                hasBox = true;
            } else {
                curMinX += (targetBox.minX - curMinX) * factor;
                curMinY += (targetBox.minY - curMinY) * factor;
                curMinZ += (targetBox.minZ - curMinZ) * factor;
                curMaxX += (targetBox.maxX - curMaxX) * factor;
                curMaxY += (targetBox.maxY - curMaxY) * factor;
                curMaxZ += (targetBox.maxZ - curMaxZ) * factor;
            }
            curAlpha += (1.0f - curAlpha) * factor;
        } else {
            curAlpha += (0.0f - curAlpha) * factor;
            if (curAlpha < 0.001f) {
                curAlpha = 0.0f;
                hasBox = false;
                return;
            }
        }

        float totalAlpha = curAlpha * (float) alpha.get();
        if (totalAlpha <= 0.002f) return;

        int baseColor = resolveColor(0.0f);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        float x1 = (float) (curMinX - camPos.x);
        float y1 = (float) (curMinY - camPos.y);
        float z1 = (float) (curMinZ - camPos.z);
        float x2 = (float) (curMaxX - camPos.x);
        float y2 = (float) (curMaxY - camPos.y);
        float z2 = (float) (curMaxZ - camPos.z);

        if (x1 > x2) { float tmp = x1; x1 = x2; x2 = tmp; }
        if (y1 > y2) { float tmp = y1; y1 = y2; y2 = tmp; }
        if (z1 > z2) { float tmp = z1; z1 = z2; z2 = tmp; }

        String curMode = mode.get();
        boolean doFill = curMode.equals("Неон") || curMode.equals("Заливка");
        boolean doNeon = curMode.equals("Неон") || curMode.equals("Только грани");

        if (doFill) {
            VertexConsumer quadBuf = immediate.getBuffer(RenderLayers.debugQuads());
            renderFaceGlow(quadBuf, x1, y1, z1, x2, y2, z2, r, g, b, totalAlpha, curMode.equals("Неон"));
            immediate.draw();
        }

        if (doNeon) {
            renderNeonOutline(matrices, camPos, x1, y1, z1, x2, y2, z2, r, g, b, totalAlpha);
        }
    }

    private void renderFaceGlow(VertexConsumer buf, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b, float alphaMult, boolean vignette) {
        if (!vignette) {
            int fillA = MathHelper.clamp((int) (40.0f * alphaMult), 0, 255);
            drawBoxFaces(buf, x1, y1, z1, x2, y2, z2, r, g, b, fillA);
            return;
        }

        int centerA = MathHelper.clamp((int) (22.0f * alphaMult), 0, 255);
        int edgeA = MathHelper.clamp((int) (95.0f * alphaMult), 0, 255);

        renderVignetteFace(buf, x1, y1, z1, x2, y1, z2, 0, 1, 0, r, g, b, edgeA, centerA);
        renderVignetteFace(buf, x1, y2, z1, x2, y2, z2, 0, -1, 0, r, g, b, edgeA, centerA);
        renderVignetteFace(buf, x1, y1, z1, x2, y2, z1, 0, 0, 1, r, g, b, edgeA, centerA);
        renderVignetteFace(buf, x1, y1, z2, x2, y2, z2, 0, 0, -1, r, g, b, edgeA, centerA);
        renderVignetteFace(buf, x1, y1, z1, x1, y2, z2, 1, 0, 0, r, g, b, edgeA, centerA);
        renderVignetteFace(buf, x2, y1, z1, x2, y2, z2, -1, 0, 0, r, g, b, edgeA, centerA);
    }

    private void renderVignetteFace(VertexConsumer buf, float minA, float minB, float minC, float maxA, float maxB, float maxC,
                                    int nx, int ny, int nz, int r, int g, int b, int edgeA, int centerA) {
        if (ny != 0) {
            float y = ny > 0 ? minB : maxB;
            float insetX = (maxA - minA) * 0.22f;
            float insetZ = (maxC - minC) * 0.22f;
            float ix1 = minA + insetX, ix2 = maxA - insetX;
            float iz1 = minC + insetZ, iz2 = maxC - insetZ;

            drawQuadGradient(buf, minA, y, minC, edgeA, maxA, y, minC, edgeA, ix2, y, iz1, centerA, ix1, y, iz1, centerA, r, g, b);
            drawQuadGradient(buf, minA, y, maxC, edgeA, ix1, y, iz2, centerA, ix2, y, iz2, centerA, maxA, y, maxC, edgeA, r, g, b);
            drawQuadGradient(buf, minA, y, minC, edgeA, ix1, y, iz1, centerA, ix1, y, iz2, centerA, minA, y, maxC, edgeA, r, g, b);
            drawQuadGradient(buf, maxA, y, minC, edgeA, maxA, y, maxC, edgeA, ix2, y, iz2, centerA, ix2, y, iz1, centerA, r, g, b);

            drawQuadFlat(buf, ix1, y, iz1, ix2, y, iz1, ix2, y, iz2, ix1, y, iz2, r, g, b, centerA);
        } else if (nz != 0) {
            float z = nz > 0 ? minC : maxC;
            float insetX = (maxA - minA) * 0.22f;
            float insetY = (maxB - minB) * 0.22f;
            float ix1 = minA + insetX, ix2 = maxA - insetX;
            float iy1 = minB + insetY, iy2 = maxB - insetY;

            drawQuadGradient(buf, minA, minB, z, edgeA, maxA, minB, z, edgeA, ix2, iy1, z, centerA, ix1, iy1, z, centerA, r, g, b);
            drawQuadGradient(buf, minA, maxB, z, edgeA, ix1, iy2, z, centerA, ix2, iy2, z, centerA, maxA, maxB, z, edgeA, r, g, b);
            drawQuadGradient(buf, minA, minB, z, edgeA, ix1, iy1, z, centerA, ix1, iy2, z, centerA, minA, maxB, z, edgeA, r, g, b);
            drawQuadGradient(buf, maxA, minB, z, edgeA, maxA, maxB, z, edgeA, ix2, iy2, z, centerA, ix2, iy1, z, centerA, r, g, b);

            drawQuadFlat(buf, ix1, iy1, z, ix2, iy1, z, ix2, iy2, z, ix1, iy2, z, r, g, b, centerA);
        } else {
            float x = nx > 0 ? minA : maxA;
            float insetY = (maxB - minB) * 0.22f;
            float insetZ = (maxC - minC) * 0.22f;
            float iy1 = minB + insetY, iy2 = maxB - insetY;
            float iz1 = minC + insetZ, iz2 = maxC - insetZ;

            drawQuadGradient(buf, x, minB, minC, edgeA, x, maxB, minC, edgeA, x, iy2, iz1, centerA, x, iy1, iz1, centerA, r, g, b);
            drawQuadGradient(buf, x, minB, maxC, edgeA, x, iy1, iz2, centerA, x, iy2, iz2, centerA, x, maxB, maxC, edgeA, r, g, b);
            drawQuadGradient(buf, x, minB, minC, edgeA, x, iy1, iz1, centerA, x, iy1, iz2, centerA, x, minB, maxC, edgeA, r, g, b);
            drawQuadGradient(buf, x, maxB, minC, edgeA, x, maxB, maxC, edgeA, x, iy2, iz2, centerA, x, iy2, iz1, centerA, r, g, b);

            drawQuadFlat(buf, x, iy1, iz1, x, iy2, iz1, x, iy2, iz2, x, iy1, iz2, r, g, b, centerA);
        }
    }

    private void drawQuadGradient(VertexConsumer buf, float x1, float y1, float z1, int a1,
                                  float x2, float y2, float z2, int a2,
                                  float x3, float y3, float z3, int a3,
                                  float x4, float y4, float z4, int a4,
                                  int r, int g, int b) {
        buf.vertex(x1, y1, z1).color(r, g, b, a1);
        buf.vertex(x2, y2, z2).color(r, g, b, a2);
        buf.vertex(x3, y3, z3).color(r, g, b, a3);
        buf.vertex(x4, y4, z4).color(r, g, b, a4);

        buf.vertex(x4, y4, z4).color(r, g, b, a4);
        buf.vertex(x3, y3, z3).color(r, g, b, a3);
        buf.vertex(x2, y2, z2).color(r, g, b, a2);
        buf.vertex(x1, y1, z1).color(r, g, b, a1);
    }

    private void drawQuadFlat(VertexConsumer buf, float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float x3, float y3, float z3,
                              float x4, float y4, float z4,
                              int r, int g, int b, int a) {
        drawQuadGradient(buf, x1, y1, z1, a, x2, y2, z2, a, x3, y3, z3, a, x4, y4, z4, a, r, g, b);
    }

    private void drawBoxFaces(VertexConsumer buf, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b, int a) {
        drawQuadFlat(buf, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        drawQuadFlat(buf, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
        drawQuadFlat(buf, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);
        drawQuadFlat(buf, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
        drawQuadFlat(buf, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
        drawQuadFlat(buf, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
    }

    private void renderNeonOutline(MatrixStack matrices, Vec3d camPos, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b, float alphaMult) {
        MatrixStack.Entry entry = matrices.peek();
        RenderPipeline pipeline = throughWalls.get() ? Pipelines.LIGHTNING : Pipelines.LIGHTNING_DEPTH;

        float baseWidth = (float) lineWidth.get();
        float bloomWidth = baseWidth * 4.2f;
        float haloWidth = baseWidth * 2.1f;
        float coreWidth = Math.max(1.2f, baseWidth * 0.85f);

        int coreR = MathHelper.clamp((int) MathHelper.lerp(0.80f, r, 255.0f), 0, 255);
        int coreG = MathHelper.clamp((int) MathHelper.lerp(0.80f, g, 255.0f), 0, 255);
        int coreB = MathHelper.clamp((int) MathHelper.lerp(0.80f, b, 255.0f), 0, 255);

        try (BufferAllocator allocBloom = new BufferAllocator(32768)) {
            BufferBuilder bufBloom = new BufferBuilder(allocBloom, DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);
            buildBoxLines(bufBloom, entry, x1, y1, z1, x2, y2, z2, r, g, b, (int) (140 * alphaMult), bloomWidth);
            BuiltBuffer builtBloom = bufBloom.endNullable();
            if (builtBloom != null) {
                float blurSigma = MathHelper.clamp(2.8f * alphaMult, 1.2f, 5.0f);
                float bloomA = MathHelper.clamp(0.90f * alphaMult, 0.1f, 1.0f);
                ParticleBlurRenderer.INSTANCE.render(blurSigma, bloomA, () -> MeshDrawUtil.drawLabeled(pipeline, builtBloom, "egg block overlay bloom"));
            }
        }

        try (
                BufferAllocator allocHalo = new BufferAllocator(32768);
                BufferAllocator allocCore = new BufferAllocator(32768)
        ) {
            BufferBuilder bufHalo = new BufferBuilder(allocHalo, DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);
            BufferBuilder bufCore = new BufferBuilder(allocCore, DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);

            buildBoxLines(bufHalo, entry, x1, y1, z1, x2, y2, z2, r, g, b, (int) (190 * alphaMult), haloWidth);
            buildBoxLines(bufCore, entry, x1, y1, z1, x2, y2, z2, coreR, coreG, coreB, (int) (255 * alphaMult), coreWidth);

            BuiltBuffer builtHalo = bufHalo.endNullable();
            if (builtHalo != null) {
                MeshDrawUtil.drawLabeled(pipeline, builtHalo, "egg block overlay halo");
            }
            BuiltBuffer builtCore = bufCore.endNullable();
            if (builtCore != null) {
                MeshDrawUtil.drawLabeled(pipeline, builtCore, "egg block overlay core");
            }
        }
    }

    private void buildBoxLines(BufferBuilder builder, MatrixStack.Entry entry, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b, int a, float width) {
        if (a <= 0) return;
        int col = (a << 24) | (r << 16) | (g << 8) | b;

        emitLine(builder, entry, x1, y1, z1, x2, y1, z1, col, width);
        emitLine(builder, entry, x2, y1, z1, x2, y1, z2, col, width);
        emitLine(builder, entry, x2, y1, z2, x1, y1, z2, col, width);
        emitLine(builder, entry, x1, y1, z2, x1, y1, z1, col, width);

        emitLine(builder, entry, x1, y2, z1, x2, y2, z1, col, width);
        emitLine(builder, entry, x2, y2, z1, x2, y2, z2, col, width);
        emitLine(builder, entry, x2, y2, z2, x1, y2, z2, col, width);
        emitLine(builder, entry, x1, y2, z2, x1, y2, z1, col, width);

        emitLine(builder, entry, x1, y1, z1, x1, y2, z1, col, width);
        emitLine(builder, entry, x2, y1, z1, x2, y2, z1, col, width);
        emitLine(builder, entry, x2, y1, z2, x2, y2, z2, col, width);
        emitLine(builder, entry, x1, y1, z2, x1, y2, z2, col, width);
    }

    private void emitLine(BufferBuilder builder, MatrixStack.Entry entry, float x1, float y1, float z1, float x2, float y2, float z2, int col, float width) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-5F) return;
        float inv = 1.0F / len;
        float nx = dx * inv;
        float ny = dy * inv;
        float nz = dz * inv;
        builder.vertex(entry, x1, y1, z1).color(col).normal(entry, nx, ny, nz).lineWidth(width);
        builder.vertex(entry, x2, y2, z2).color(col).normal(entry, nx, ny, nz).lineWidth(width);
    }

    private int resolveColor(float offset) {
        String cm = colorMode.get();
        if (cm.equals("Тема")) {
            return Theme.getAccent();
        } else if (cm.equals("Градиент")) {
            float phase = ((System.currentTimeMillis() % 4000L) / 4000.0f + offset) % 1.0f;
            float ratio = (float) (Math.sin(phase * Math.PI * 2.0) + 1.0) * 0.5f;
            return ColorUtil.interpolate(color.get(), color2.get(), ratio);
        }
        return color.get();
    }
}

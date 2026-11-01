package egg.client.module.render;

import egg.client.mixin.ItemRenderStateAccessor;
import egg.client.mixin.LayerRenderStateAccessor;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.ui.theme.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3fc;

import java.util.List;

public class ItemESP extends Module {

    private final ModeSetting colorMode = new ModeSetting("Режим цвета", "Тема", "Источник цвета голограммы", "Тема", "Кастомный");
    private final ColorSetting color = new ColorSetting("Цвет голограммы", 0xFF8B5CF6, "Пользовательский цвет подсветки предмета");
    private final BooleanSetting shell = new BooleanSetting("Оболочка", true, "Объемная внешняя оболочка вокруг модельки предмета");
    private final BooleanSetting scanlines = new BooleanSetting("Сканлинии", true, "Горизонтальные линии цифровой проекции");
    private final BooleanSetting glitch = new BooleanSetting("Глитч эффект", true, "Периодический глитч со смещением слоев каждые 2 секунды");
    private final BooleanSetting box = new BooleanSetting("3D Бокс", true, "3D рамка хитбокса предмета");
    private final BooleanSetting tracer = new BooleanSetting("Трейсер", true, "Световой луч вверх от центра предмета");
    private final SliderSetting glowIntensity = new SliderSetting("Яркость", 1.0, 0.2, 2.0, 0.1, "Интенсивность свечения голограммы");

    public ItemESP() {
        super("ItemESP", "Голографический ESP для выпавших предметов с глитчем и оболочкой", Category.ESP);
        color.setVisible(() -> colorMode.is("Кастомный"));
        addSettings(colorMode, color, shell, scanlines, glitch, box, tracer, glowIntensity);
    }

    public boolean isOccluded(ItemEntityRenderState state, CameraRenderState camera) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return true;

        Vec3d eyePos = mc.player.getEyePos();
        double itemY = state.y + Math.max(0.08, state.height * 0.5);
        Vec3d itemPos = new Vec3d(state.x, itemY, state.z);

        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                eyePos,
                itemPos,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            double distHitSq = hit.getPos().squaredDistanceTo(eyePos);
            double distItemSq = itemPos.squaredDistanceTo(eyePos);
            if (distHitSq < distItemSq - 0.25) {
                return true; // Блокировано твердым блоком — сквозь стену не рендерить!
            }
        }
        return false;
    }

    public boolean renderItem(ItemEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, int light) {
        if (!isEnabled()) return false;

        // Строгая проверка видимости: если предмет за стенкой — НЕ РЕНДЕРИТЬ
        if (isOccluded(state, camera)) {
            return false;
        }

        ItemRenderState itemRenderState = state.itemRenderState;
        if (itemRenderState == null || itemRenderState.isEmpty()) return false;

        ItemRenderStateAccessor stateAcc = (ItemRenderStateAccessor) itemRenderState;
        ItemRenderState.LayerRenderState[] layers = stateAcc.getLayers();
        int layerCount = stateAcc.getLayerCount();
        if (layers == null || layerCount <= 0) return false;

        int baseCol = colorMode.is("Тема") ? Theme.getAccent() : color.get();
        int r = (baseCol >> 16) & 0xFF;
        int g = (baseCol >> 8) & 0xFF;
        int b = baseCol & 0xFF;
        float intensity = (float) glowIntensity.get();

        long now = System.currentTimeMillis();
        long cycle = now % 2000L;
        boolean isGlitching = glitch.get() && (cycle < 180L);
        float glitchProgress = isGlitching ? (cycle / 180.0f) : 0.0f;
        float glitchStrength = isGlitching ? (float) Math.sin(glitchProgress * Math.PI) : 0.0f;
        int glitchSubTick = (int) (cycle / 30L);

        Box modelBox = itemRenderState.getModelBoundingBox();
        float minY = modelBox != null ? (float) modelBox.minY : -0.5f;
        float maxY = modelBox != null ? (float) modelBox.maxY : 0.5f;
        float heightSpan = Math.max(0.1f, maxY - minY);
        float cx = modelBox != null ? (float) ((modelBox.minX + modelBox.maxX) * 0.5) : 0.5f;
        float cy = modelBox != null ? (float) ((modelBox.minY + modelBox.maxY) * 0.5) : 0.5f;
        float cz = modelBox != null ? (float) ((modelBox.minZ + modelBox.maxZ) * 0.5) : 0.0f;

        // Рендер каждого слоя модели
        for (int i = 0; i < layerCount; i++) {
            ItemRenderState.LayerRenderState layer = layers[i];
            if (layer == null) continue;
            List<BakedQuad> quads = layer.getQuads();
            if (quads == null || quads.isEmpty()) continue;

            matrices.push();
            LayerRenderStateAccessor layerAcc = (LayerRenderStateAccessor) (Object) layer;
            Transformation transform = layerAcc.getTransform();
            if (transform != null) {
                transform.apply(false, matrices.peek());
            }

            queue.submitCustom(matrices, RenderLayers.debugQuads(), (entry, consumer) -> {
                renderHoloLayer(entry, consumer, quads, r, g, b, intensity,
                        minY, heightSpan, cx, cy, cz,
                        isGlitching, glitchStrength, glitchSubTick);
            });

            matrices.pop();
        }

        // Рендер 3D бокса и трейсера
        if (box.get() || tracer.get()) {
            queue.submitCustom(matrices, RenderLayers.debugQuads(), (entry, consumer) -> {
                Box effectiveBox = modelBox != null ? modelBox : new Box(-0.25, 0.0, -0.25, 0.25, 0.5, 0.25);
                if (box.get()) {
                    render3DBox(entry, consumer, effectiveBox, r, g, b, intensity);
                }
                if (tracer.get()) {
                    renderTracer(entry, consumer, effectiveBox, r, g, b, intensity);
                }
            });
        }

        return true;
    }

    private void renderHoloLayer(MatrixStack.Entry entry, VertexConsumer consumer,
                                 List<BakedQuad> quads, int r, int g, int b, float intensity,
                                 float minY, float heightSpan, float cx, float cy, float cz,
                                 boolean isGlitching, float glitchStrength, int glitchSubTick) {

        int bodyA = (int) MathHelper.clamp(195.0f * intensity, 20.0f, 255.0f);

        int shellR = Math.min(255, (int) (r * 0.65f + 255 * 0.35f));
        int shellG = Math.min(255, (int) (g * 0.65f + 235 * 0.35f));
        int shellB = 255;
        int shellA = (int) MathHelper.clamp(85.0f * intensity, 15.0f, 210.0f);
        int shellEdgeA = (int) MathHelper.clamp(135.0f * intensity, 20.0f, 255.0f);

        int scanR = (int) (r * 0.32f);
        int scanG = (int) (g * 0.32f);
        int scanB = (int) (b * 0.32f);
        int scanA = (int) MathHelper.clamp(145.0f * intensity, 25.0f, 235.0f);

        boolean doShell = shell.get();
        boolean doScanlines = scanlines.get();

        for (BakedQuad quad : quads) {
            Vector3fc p0 = quad.getPosition(0);
            Vector3fc p1 = quad.getPosition(1);
            Vector3fc p2 = quad.getPosition(2);
            Vector3fc p3 = quad.getPosition(3);

            float quadY = (p0.y() + p1.y() + p2.y() + p3.y()) * 0.25f;
            float normY = MathHelper.clamp((quadY - minY) / heightSpan, 0.0f, 1.0f);
            int slice = (int) (normY * 5.0f);

            float sliceDx = 0.0f;
            float sliceDz = 0.0f;
            if (isGlitching) {
                float sign = ((slice ^ glitchSubTick) % 2 == 0) ? 1.0f : -1.0f;
                float mag = 0.055f + (((slice * 37 + glitchSubTick * 17) % 7) * 0.012f);
                sliceDx = sign * mag * glitchStrength;
                sliceDz = (sign * 0.35f) * mag * glitchStrength;
            }

            float x0 = p0.x() + sliceDx, y0 = p0.y(), z0 = p0.z() + sliceDz;
            float x1 = p1.x() + sliceDx, y1 = p1.y(), z1 = p1.z() + sliceDz;
            float x2 = p2.x() + sliceDx, y2 = p2.y(), z2 = p2.z() + sliceDz;
            float x3 = p3.x() + sliceDx, y3 = p3.y(), z3 = p3.z() + sliceDz;

            // 1. Внутренний фиолетовый силуэт
            drawQuadDoubleSided(consumer, entry,
                    x0, y0, z0,
                    x1, y1, z1,
                    x2, y2, z2,
                    x3, y3, z3,
                    r, g, b, bodyA);

            // 2. Внешняя полупрозрачная оболочка (надутый воксельный слой)
            if (doShell) {
                float nx = 0.0f, ny = 0.0f, nz = 0.0f;
                Direction face = quad.face();
                if (face != null) {
                    nx = face.getOffsetX();
                    ny = face.getOffsetY();
                    nz = face.getOffsetZ();
                }

                float inflate = 0.018f;
                float sx0 = x0 + nx * inflate + (x0 - cx) * 0.045f;
                float sy0 = y0 + ny * inflate + (y0 - cy) * 0.045f;
                float sz0 = z0 + nz * inflate + (z0 - cz) * 0.045f;

                float sx1 = x1 + nx * inflate + (x1 - cx) * 0.045f;
                float sy1 = y1 + ny * inflate + (y1 - cy) * 0.045f;
                float sz1 = z1 + nz * inflate + (z1 - cz) * 0.045f;

                float sx2 = x2 + nx * inflate + (x2 - cx) * 0.045f;
                float sy2 = y2 + ny * inflate + (y2 - cy) * 0.045f;
                float sz2 = z2 + nz * inflate + (z2 - cz) * 0.045f;

                float sx3 = x3 + nx * inflate + (x3 - cx) * 0.045f;
                float sy3 = y3 + ny * inflate + (y3 - cy) * 0.045f;
                float sz3 = z3 + nz * inflate + (z3 - cz) * 0.045f;

                // Заливка грани оболочки
                drawQuadDoubleSided(consumer, entry,
                        sx0, sy0, sz0,
                        sx1, sy1, sz1,
                        sx2, sy2, sz2,
                        sx3, sy3, sz3,
                        shellR, shellG, shellB, shellA);

                // Сетка ребер вокселей оболочки
                float lineThick = 0.0025f;
                drawLineQuad(consumer, entry, sx0, sy0, sz0, sx1, sy1, sz1, lineThick, shellR, shellG, shellB, shellEdgeA);
                drawLineQuad(consumer, entry, sx1, sy1, sz1, sx2, sy2, sz2, lineThick, shellR, shellG, shellB, shellEdgeA);
                drawLineQuad(consumer, entry, sx2, sy2, sz2, sx3, sy3, sz3, lineThick, shellR, shellG, shellB, shellEdgeA);
                drawLineQuad(consumer, entry, sx3, sy3, sz3, sx0, sy0, sz0, lineThick, shellR, shellG, shellB, shellEdgeA);
            }

            // 3. Горизонтальные сканлинии поперек модели
            if (doScanlines) {
                float qMinY = Math.min(Math.min(y0, y1), Math.min(y2, y3));
                float qMaxY = Math.max(Math.max(y0, y1), Math.max(y2, y3));
                float dy = 0.026f;

                if (qMaxY - qMinY > 0.008f) {
                    float startY = (float) (Math.ceil(qMinY / dy) * dy);
                    for (float curY = startY; curY <= qMaxY; curY += dy) {
                        float leftX = MathHelper.lerp((curY - y0) / Math.max(0.001f, y3 - y0), x0, x3);
                        float rightX = MathHelper.lerp((curY - y1) / Math.max(0.001f, y2 - y1), x1, x2);
                        float midZ = (z0 + z1 + z2 + z3) * 0.25f + 0.001f;

                        float lineH = 0.0035f;
                        drawQuadDoubleSided(consumer, entry,
                                leftX, curY - lineH, midZ,
                                rightX, curY - lineH, midZ,
                                rightX, curY + lineH, midZ,
                                leftX, curY + lineH, midZ,
                                scanR, scanG, scanB, scanA);
                    }
                }
            }

            // 4. Хроматическая аберрация во время глитча
            if (isGlitching) {
                float chromOff = (sliceDx + 0.016f) * glitchStrength;
                int cr = (slice % 2 == 0) ? 255 : 30;
                int cg = (slice % 2 == 0) ? 40 : 220;
                int cb = (slice % 2 == 0) ? 140 : 255;
                int ca = (int) MathHelper.clamp(115.0f * glitchStrength, 0.0f, 210.0f);

                drawQuadDoubleSided(consumer, entry,
                        x0 + chromOff, y0, z0,
                        x1 + chromOff, y1, z1,
                        x2 + chromOff, y2, z2,
                        x3 + chromOff, y3, z3,
                        cr, cg, cb, ca);
            }
        }
    }

    private void render3DBox(MatrixStack.Entry entry, VertexConsumer consumer, Box b, int r, int g, int bCol, float intensity) {
        float pad = 0.035f;
        float x1 = (float) b.minX - pad;
        float y1 = (float) b.minY - pad;
        float z1 = (float) b.minZ - pad;
        float x2 = (float) b.maxX + pad;
        float y2 = (float) b.maxY + pad;
        float z2 = (float) b.maxZ + pad;

        int fillA = (int) MathHelper.clamp(20.0f * intensity, 5.0f, 60.0f);
        int outA = (int) MathHelper.clamp(140.0f * intensity, 30.0f, 240.0f);

        // Грани
        drawQuadDoubleSided(consumer, entry, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, bCol, fillA);
        drawQuadDoubleSided(consumer, entry, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, bCol, fillA);
        drawQuadDoubleSided(consumer, entry, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, bCol, fillA);
        drawQuadDoubleSided(consumer, entry, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, bCol, fillA);
        drawQuadDoubleSided(consumer, entry, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, bCol, fillA);
        drawQuadDoubleSided(consumer, entry, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, bCol, fillA);

        // Ребра
        float t = 0.0028f;
        drawSolidBox(consumer, entry, x1 - t, y1 - t, z1 - t, x2 + t, y1 + t, z1 + t, r, g, bCol, outA);
        drawSolidBox(consumer, entry, x1 - t, y1 - t, z2 - t, x2 + t, y1 + t, z2 + t, r, g, bCol, outA);
        drawSolidBox(consumer, entry, x1 - t, y2 - t, z1 - t, x2 + t, y2 + t, z1 + t, r, g, bCol, outA);
        drawSolidBox(consumer, entry, x1 - t, y2 - t, z2 - t, x2 + t, y2 + t, z2 + t, r, g, bCol, outA);

        drawSolidBox(consumer, entry, x1 - t, y1, z1 - t, x1 + t, y2, z1 + t, r, g, bCol, outA);
        drawSolidBox(consumer, entry, x2 - t, y1, z1 - t, x2 + t, y2, z1 + t, r, g, bCol, outA);
        drawSolidBox(consumer, entry, x1 - t, y1, z2 - t, x1 + t, y2, z2 + t, r, g, bCol, outA);
        drawSolidBox(consumer, entry, x2 - t, y1, z2 - t, x2 + t, y2, z2 + t, r, g, bCol, outA);

        drawSolidBox(consumer, entry, x1 - t, y1 - t, z1, x1 + t, y1 + t, z2, r, g, bCol, outA);
        drawSolidBox(consumer, entry, x2 - t, y1 - t, z1, x2 + t, y1 + t, z2, r, g, bCol, outA);
        drawSolidBox(consumer, entry, x1 - t, y2 - t, z1, x1 + t, y2 + t, z2, r, g, bCol, outA);
        drawSolidBox(consumer, entry, x2 - t, y2 - t, z1, x2 + t, y2 + t, z2, r, g, bCol, outA);
    }

    private void renderTracer(MatrixStack.Entry entry, VertexConsumer consumer, Box b, int r, int g, int bCol, float intensity) {
        float cx = (float) ((b.minX + b.maxX) * 0.5);
        float cz = (float) ((b.minZ + b.maxZ) * 0.5);
        float topY = (float) b.maxY + 0.035f;
        float beamHeight = 2.8f;
        float halfW = 0.003f;

        int baseA = (int) MathHelper.clamp(180.0f * intensity, 30.0f, 255.0f);

        // Вертикальный световой луч, затухающий кверху
        drawFadingQuad(consumer, entry,
                cx - halfW, topY, cz,
                cx + halfW, topY, cz,
                cx + halfW, topY + beamHeight, cz,
                cx - halfW, topY + beamHeight, cz,
                r, g, bCol, baseA, 0);

        drawFadingQuad(consumer, entry,
                cx, topY, cz - halfW,
                cx, topY, cz + halfW,
                cx, topY + beamHeight, cz + halfW,
                cx, topY + beamHeight, cz - halfW,
                r, g, bCol, baseA, 0);
    }

    private static void drawLineQuad(VertexConsumer buf, MatrixStack.Entry entry,
                                     float x1, float y1, float z1,
                                     float x2, float y2, float z2,
                                     float thickness, int r, int g, int b, int a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.0001f) return;

        float nx = -dy / len * thickness;
        float ny = dx / len * thickness;

        drawQuadDoubleSided(buf, entry,
                x1 - nx, y1 - ny, z1,
                x1 + nx, y1 + ny, z1,
                x2 + nx, y2 + ny, z2,
                x2 - nx, y2 - ny, z2,
                r, g, b, a);
    }

    private static void drawFadingQuad(VertexConsumer buf, MatrixStack.Entry entry,
                                       float x1, float y1, float z1,
                                       float x2, float y2, float z2,
                                       float x3, float y3, float z3,
                                       float x4, float y4, float z4,
                                       int r, int g, int b, int aBottom, int aTop) {
        buf.vertex(entry, x1, y1, z1).color(r, g, b, aBottom);
        buf.vertex(entry, x2, y2, z2).color(r, g, b, aBottom);
        buf.vertex(entry, x3, y3, z3).color(r, g, b, aTop);
        buf.vertex(entry, x4, y4, z4).color(r, g, b, aTop);

        buf.vertex(entry, x4, y4, z4).color(r, g, b, aTop);
        buf.vertex(entry, x3, y3, z3).color(r, g, b, aTop);
        buf.vertex(entry, x2, y2, z2).color(r, g, b, aBottom);
        buf.vertex(entry, x1, y1, z1).color(r, g, b, aBottom);
    }

    private static void drawSolidBox(VertexConsumer buf, MatrixStack.Entry entry,
                                     float x1, float y1, float z1,
                                     float x2, float y2, float z2,
                                     int r, int g, int b, int a) {
        drawQuadDoubleSided(buf, entry, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        drawQuadDoubleSided(buf, entry, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
        drawQuadDoubleSided(buf, entry, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);
        drawQuadDoubleSided(buf, entry, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
        drawQuadDoubleSided(buf, entry, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
        drawQuadDoubleSided(buf, entry, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
    }

    private static void drawQuadDoubleSided(VertexConsumer buf, MatrixStack.Entry entry,
                                            float x1, float y1, float z1,
                                            float x2, float y2, float z2,
                                            float x3, float y3, float z3,
                                            float x4, float y4, float z4,
                                            int r, int g, int b, int a) {
        buf.vertex(entry, x1, y1, z1).color(r, g, b, a);
        buf.vertex(entry, x2, y2, z2).color(r, g, b, a);
        buf.vertex(entry, x3, y3, z3).color(r, g, b, a);
        buf.vertex(entry, x4, y4, z4).color(r, g, b, a);

        buf.vertex(entry, x4, y4, z4).color(r, g, b, a);
        buf.vertex(entry, x3, y3, z3).color(r, g, b, a);
        buf.vertex(entry, x2, y2, z2).color(r, g, b, a);
        buf.vertex(entry, x1, y1, z1).color(r, g, b, a);
    }
}

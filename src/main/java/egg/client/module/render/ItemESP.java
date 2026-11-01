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
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
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
    private final BooleanSetting shell = new BooleanSetting("Оболочка", true, "Объемная внешняя оболочка, обтягивающая форму предмета");
    private final BooleanSetting scanlines = new BooleanSetting("Сканлинии", true, "Горизонтальные линии цифровой проекции");
    private final BooleanSetting glitch = new BooleanSetting("Глитч эффект", true, "Периодический глитч со смещением слоев каждые 2 секунды");
    private final BooleanSetting box = new BooleanSetting("3D Бокс", false, "3D рамка вокруг контура предмета");
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
                return true;
            }
        }
        return false;
    }

    public boolean renderItem(ItemEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, int light) {
        if (!isEnabled()) return false;

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

        for (int i = 0; i < layerCount; i++) {
            ItemRenderState.LayerRenderState layer = layers[i];
            if (layer == null) continue;
            List<BakedQuad> quads = layer.getQuads();
            if (quads == null || quads.isEmpty()) continue;

            LayerRenderStateAccessor layerAcc = (LayerRenderStateAccessor) (Object) layer;
            RenderLayer renderLayer = layerAcc.getRenderLayer();
            Transformation transform = layerAcc.getTransform();

            float sMinX = 1.0f, sMaxX = 0.0f;
            float sMinY = 1.0f, sMaxY = 0.0f;
            float sMinZ = 0.5f, sMaxZ = -0.5f;
            boolean hasPerimeter = false;

            for (BakedQuad quad : quads) {
                Direction face = quad.face();
                if (face != Direction.NORTH && face != Direction.SOUTH) {
                    hasPerimeter = true;
                    for (int v = 0; v < 4; v++) {
                        Vector3fc pos = quad.getPosition(v);
                        if (pos.x() < sMinX) sMinX = pos.x();
                        if (pos.x() > sMaxX) sMaxX = pos.x();
                        if (pos.y() < sMinY) sMinY = pos.y();
                        if (pos.y() > sMaxY) sMaxY = pos.y();
                        if (pos.z() < sMinZ) sMinZ = pos.z();
                        if (pos.z() > sMaxZ) sMaxZ = pos.z();
                    }
                }
            }

            if (!hasPerimeter || sMinX >= sMaxX) {
                sMinX = 0.2f; sMaxX = 0.8f;
                sMinY = 0.2f; sMaxY = 0.8f;
                sMinZ = -0.05f; sMaxZ = 0.05f;
            }

            float cx = (sMinX + sMaxX) * 0.5f;
            float cy = (sMinY + sMaxY) * 0.5f;
            float cz = (sMinZ + sMaxZ) * 0.5f;
            float heightSpan = Math.max(0.1f, sMaxY - sMinY);

            matrices.push();
            if (transform != null) {
                transform.apply(false, matrices.peek());
            }

            if (shell.get() && renderLayer != null) {
                matrices.push();
                matrices.translate(cx, cy, cz);
                matrices.scale(1.08f, 1.08f, 1.08f);
                matrices.translate(-cx, -cy, -cz);

                queue.submitCustom(matrices, renderLayer, (entry, consumer) -> {
                    float shR = Math.min(1.0f, r / 255.0f * 0.6f + 0.4f);
                    float shG = Math.min(1.0f, g / 255.0f * 0.6f + 0.4f);
                    float shB = 1.0f;
                    float shA = 0.42f * intensity;

                    for (BakedQuad quad : quads) {
                        consumer.quad(entry, quad, shR, shG, shB, shA, 15728880, OverlayTexture.DEFAULT_UV);
                    }
                });

                matrices.pop();
            }

            if (renderLayer != null) {
                queue.submitCustom(matrices, renderLayer, (entry, consumer) -> {
                    float bodyR = r / 255.0f;
                    float bodyG = g / 255.0f;
                    float bodyB = b / 255.0f;
                    float bodyA = 0.92f * intensity;

                    for (BakedQuad quad : quads) {
                        consumer.quad(entry, quad, bodyR, bodyG, bodyB, bodyA, 15728880, OverlayTexture.DEFAULT_UV);
                    }
                });
            }

            final float fMinY = sMinY;
            final float fHeightSpan = heightSpan;
            final float fCx = cx;
            final float fCy = cy;
            final float fCz = cz;

            queue.submitCustom(matrices, RenderLayers.debugQuads(), (entry, consumer) -> {
                renderPerimeterVoxelPass(entry, consumer, quads, r, g, b, intensity,
                        fMinY, fHeightSpan, fCx, fCy, fCz,
                        isGlitching, glitchStrength, glitchSubTick);
            });

            if (isGlitching && renderLayer != null) {
                float glitchDx = (float) Math.sin(glitchProgress * Math.PI) * 0.08f;

                matrices.push();
                matrices.translate(glitchDx, 0.0f, 0.0f);
                queue.submitCustom(matrices, renderLayer, (entry, consumer) -> {
                    for (BakedQuad quad : quads) {
                        consumer.quad(entry, quad, 0.2f, 0.9f, 1.0f, 0.45f * glitchStrength, 15728880, OverlayTexture.DEFAULT_UV);
                    }
                });
                matrices.pop();

                matrices.push();
                matrices.translate(-glitchDx * 0.7f, 0.0f, 0.0f);
                queue.submitCustom(matrices, renderLayer, (entry, consumer) -> {
                    for (BakedQuad quad : quads) {
                        consumer.quad(entry, quad, 1.0f, 0.2f, 0.6f, 0.40f * glitchStrength, 15728880, OverlayTexture.DEFAULT_UV);
                    }
                });
                matrices.pop();
            }

            if (box.get() || tracer.get()) {
                final Box tightBox = new Box(sMinX, sMinY, sMinZ, sMaxX, sMaxY, sMaxZ);
                queue.submitCustom(matrices, RenderLayers.debugQuads(), (entry, consumer) -> {
                    if (box.get()) {
                        renderTightBox(entry, consumer, tightBox, r, g, b, intensity);
                    }
                    if (tracer.get()) {
                        renderTracer(entry, consumer, tightBox, r, g, b, intensity);
                    }
                });
            }

            matrices.pop();
        }

        return true;
    }

    private void renderPerimeterVoxelPass(MatrixStack.Entry entry, VertexConsumer consumer,
                                          List<BakedQuad> quads, int r, int g, int b, float intensity,
                                          float minY, float heightSpan, float cx, float cy, float cz,
                                          boolean isGlitching, float glitchStrength, int glitchSubTick) {

        int bodyA = (int) MathHelper.clamp(200.0f * intensity, 20.0f, 255.0f);
        int shellR = Math.min(255, (int) (r * 0.65f + 255 * 0.35f));
        int shellG = Math.min(255, (int) (g * 0.65f + 235 * 0.35f));
        int shellB = 255;
        int shellEdgeA = (int) MathHelper.clamp(140.0f * intensity, 20.0f, 255.0f);

        int scanR = (int) (r * 0.30f);
        int scanG = (int) (g * 0.30f);
        int scanB = (int) (b * 0.30f);
        int scanA = (int) MathHelper.clamp(150.0f * intensity, 25.0f, 240.0f);

        boolean doShell = shell.get();
        boolean doScanlines = scanlines.get();

        for (BakedQuad quad : quads) {
            Direction face = quad.face();
            if (face == Direction.NORTH || face == Direction.SOUTH) {
                continue;
            }

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
                float mag = 0.06f + (((slice * 37 + glitchSubTick * 17) % 7) * 0.012f);
                sliceDx = sign * mag * glitchStrength;
                sliceDz = (sign * 0.35f) * mag * glitchStrength;
            }

            float x0 = p0.x() + sliceDx, y0 = p0.y(), z0 = p0.z() + sliceDz;
            float x1 = p1.x() + sliceDx, y1 = p1.y(), z1 = p1.z() + sliceDz;
            float x2 = p2.x() + sliceDx, y2 = p2.y(), z2 = p2.z() + sliceDz;
            float x3 = p3.x() + sliceDx, y3 = p3.y(), z3 = p3.z() + sliceDz;

            drawQuadDoubleSided(consumer, entry,
                    x0, y0, z0,
                    x1, y1, z1,
                    x2, y2, z2,
                    x3, y3, z3,
                    r, g, b, bodyA);

            if (doShell) {
                float nx = 0.0f, ny = 0.0f, nz = 0.0f;
                if (face != null) {
                    nx = face.getOffsetX();
                    ny = face.getOffsetY();
                    nz = face.getOffsetZ();
                }

                float inflate = 0.014f;
                float sx0 = x0 + nx * inflate;
                float sy0 = y0 + ny * inflate;
                float sz0 = z0 + nz * inflate;

                float sx1 = x1 + nx * inflate;
                float sy1 = y1 + ny * inflate;
                float sz1 = z1 + nz * inflate;

                float sx2 = x2 + nx * inflate;
                float sy2 = y2 + ny * inflate;
                float sz2 = z2 + nz * inflate;

                float sx3 = x3 + nx * inflate;
                float sy3 = y3 + ny * inflate;
                float sz3 = z3 + nz * inflate;

                float lineThick = 0.0022f;
                drawLineQuad(consumer, entry, sx0, sy0, sz0, sx1, sy1, sz1, lineThick, shellR, shellG, shellB, shellEdgeA);
                drawLineQuad(consumer, entry, sx1, sy1, sz1, sx2, sy2, sz2, lineThick, shellR, shellG, shellB, shellEdgeA);
                drawLineQuad(consumer, entry, sx2, sy2, sz2, sx3, sy3, sz3, lineThick, shellR, shellG, shellB, shellEdgeA);
                drawLineQuad(consumer, entry, sx3, sy3, sz3, sx0, sy0, sz0, lineThick, shellR, shellG, shellB, shellEdgeA);
            }

            if (doScanlines) {
                float qMinY = Math.min(Math.min(y0, y1), Math.min(y2, y3));
                float qMaxY = Math.max(Math.max(y0, y1), Math.max(y2, y3));
                float dy = 0.024f;

                if (qMaxY - qMinY > 0.006f) {
                    float startY = (float) (Math.ceil(qMinY / dy) * dy);
                    for (float curY = startY; curY <= qMaxY; curY += dy) {
                        float leftX = MathHelper.lerp((curY - y0) / Math.max(0.001f, y3 - y0), x0, x3);
                        float rightX = MathHelper.lerp((curY - y1) / Math.max(0.001f, y2 - y1), x1, x2);
                        float midZ = (z0 + z1 + z2 + z3) * 0.25f;

                        float lineH = 0.003f;
                        drawQuadDoubleSided(consumer, entry,
                                leftX, curY - lineH, midZ,
                                rightX, curY - lineH, midZ,
                                rightX, curY + lineH, midZ,
                                leftX, curY + lineH, midZ,
                                scanR, scanG, scanB, scanA);
                    }
                }
            }
        }
    }

    private void renderTightBox(MatrixStack.Entry entry, VertexConsumer consumer, Box b, int r, int g, int bCol, float intensity) {
        float pad = 0.02f;
        float x1 = (float) b.minX - pad;
        float y1 = (float) b.minY - pad;
        float z1 = (float) b.minZ - pad;
        float x2 = (float) b.maxX + pad;
        float y2 = (float) b.maxY + pad;
        float z2 = (float) b.maxZ + pad;

        int outA = (int) MathHelper.clamp(120.0f * intensity, 25.0f, 220.0f);
        float t = 0.0022f;

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
        float topY = (float) b.maxY + 0.02f;
        float beamHeight = 2.4f;
        float halfW = 0.0025f;

        int baseA = (int) MathHelper.clamp(160.0f * intensity, 30.0f, 240.0f);

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

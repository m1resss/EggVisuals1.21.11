package egg.client.cosmetic.watch;

import egg.client.module.cosmetic.CosmeticsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.util.Arm;

public final class WatchRenderer {
    private static final float[][] CIRCLE_CONTOUR = createCircleContour(28);
    private static final float[][] OCTAGON_CONTOUR = createOctagonContour();
    private static final float[][] TONNEAU_CONTOUR = createTonneauContour(32);
    private static final float[][] ULTRA_CONTOUR = createSquircleContour(0.42f);
    private static final float[][] GSHOCK_CONTOUR = createSquircleContour(0.28f);

    private WatchRenderer() {
    }

    private static float[][] createCircleContour(int segments) {
        float[] nr = new float[segments];
        float[] nu = new float[segments];
        for (int i = 0; i < segments; i++) {
            double a = (2.0 * Math.PI * i) / segments;
            nr[i] = (float) Math.sin(a);
            nu[i] = (float) Math.cos(a);
        }
        return new float[][]{nr, nu};
    }

    private static float[][] createOctagonContour() {
        int segments = 8;
        float[] nr = new float[segments];
        float[] nu = new float[segments];
        float invCos = (float) (1.0 / Math.cos(Math.PI / 8.0));
        for (int i = 0; i < segments; i++) {
            double a = (Math.PI / 8.0) + (2.0 * Math.PI * i) / segments;
            nr[i] = (float) (Math.sin(a) * invCos);
            nu[i] = (float) (Math.cos(a) * invCos);
        }
        return new float[][]{nr, nu};
    }

    private static float[][] createTonneauContour(int segments) {
        float[] nr = new float[segments];
        float[] nu = new float[segments];
        for (int i = 0; i < segments; i++) {
            double a = (2.0 * Math.PI * i) / segments;
            float su = (float) Math.cos(a);
            float sr = (float) Math.sin(a);
            nu[i] = su;
            nr[i] = sr * (23.5f + 3.5f * (1.0f - su * su)) / 30.0f;
        }
        return new float[][]{nr, nu};
    }

    private static float[][] createSquircleContour(float cornerRadius) {
        int perCorner = 8;
        int total = perCorner * 4;
        float[] nr = new float[total];
        float[] nu = new float[total];
        float inner = 1.0f - cornerRadius;
        float[] crCorners = new float[]{inner, inner, -inner, -inner};
        float[] cuCorners = new float[]{inner, -inner, -inner, inner};
        for (int c = 0; c < 4; c++) {
            double start = c * (Math.PI * 0.5);
            for (int s = 0; s < perCorner; s++) {
                double theta = start + (s / (double) (perCorner - 1)) * (Math.PI * 0.5);
                int idx = c * perCorner + s;
                nr[idx] = crCorners[c] + (float) Math.sin(theta) * cornerRadius;
                nu[idx] = cuCorners[c] + (float) Math.cos(theta) * cornerRadius;
            }
        }
        return new float[][]{nr, nu};
    }

    private static float[][] getContour(WatchStyle.CaseShape shape) {
        return switch (shape) {
            case OCTAGON -> OCTAGON_CONTOUR;
            case TONNEAU -> TONNEAU_CONTOUR;
            case SQUIRCLE_ULTRA -> ULTRA_CONTOUR;
            case SQUIRCLE_GSHOCK -> GSHOCK_CONTOUR;
            default -> CIRCLE_CONTOUR;
        };
    }

    private static float getUvRadius(WatchStyle.CaseShape shape) {
        return switch (shape) {
            case OCTAGON -> 27.0f / 128.0f;
            case TONNEAU -> 29.4f / 128.0f;
            case SQUIRCLE_ULTRA -> 27.4f / 128.0f;
            case SQUIRCLE_GSHOCK -> 29.4f / 128.0f;
            default -> 29.6f / 128.0f;
        };
    }

    public static void renderArmWatch(
            PlayerEntityRenderer renderer,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            ModelPart arm,
            boolean sleeve
    ) {
        CosmeticsModule mod = CosmeticsModule.getInstance();
        if (mod == null || !mod.isEnabled()) return;
        WatchStyle style = mod.getSelectedWatchStyle();
        if (style == null || style == WatchStyle.NONE || style.getTexture() == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        boolean slim = mc.player.getSkin().model() == PlayerSkinType.SLIM;

        if (renderer.getModel() instanceof PlayerEntityModel playerModel) {
            boolean isRightArm = (arm == playerModel.rightArm);
            boolean isLeftArm = (arm == playerModel.leftArm);
            if (!isRightArm && !isLeftArm) return;
            boolean isMainArm = (mc.player.getMainArm() == Arm.RIGHT) ? isRightArm : isLeftArm;
            if (!isMainArm) return;

            matrices.push();
            arm.applyTransform(matrices);
            matrices.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
            renderWatch(matrices, queue, light, slim, isRightArm, sleeve, false, style);
            matrices.pop();
        }
    }

    public static void renderThirdPersonWatch(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            PlayerEntityModel model,
            int light,
            boolean slim,
            boolean hasChestplate
    ) {
        CosmeticsModule mod = CosmeticsModule.getInstance();
        if (mod == null || !mod.isEnabled()) return;
        WatchStyle style = mod.getSelectedWatchStyle();
        if (style == null || style == WatchStyle.NONE || style.getTexture() == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean rightArm = mc.player == null || mc.player.getMainArm() == Arm.RIGHT;
        ModelPart armPart = rightArm ? model.rightArm : model.leftArm;

        matrices.push();
        armPart.applyTransform(matrices);
        matrices.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
        renderWatch(matrices, queue, light, slim, rightArm, true, hasChestplate, style);
        matrices.pop();
    }

    public static void renderWatch(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            boolean slim,
            boolean rightArm,
            boolean sleeve,
            boolean hasChestplate,
            WatchStyle style
    ) {
        if (style == null || style == WatchStyle.NONE || style.getTexture() == null) return;

        float outerX = rightArm ? (slim ? -2.0f : -3.0f) : (slim ? 2.0f : 3.0f);
        float innerX = rightArm ? 1.0f : -1.0f;
        float facingSign = rightArm ? -1.0f : 1.0f;
        float ySign = rightArm ? -1.0f : 1.0f;

        float frontZ = -2.0f;
        float backZ = 2.0f;

        float baseClearance = hasChestplate ? 1.04f : (sleeve ? 0.28f : 0.04f);
        float strapThick = 0.26f;
        float outerClearance = baseClearance + strapThick;

        float wristY = 7.75f;
        float wristZ = 0.0f;

        float strapHalfY = (style == WatchStyle.APPLE_ULTRA || style == WatchStyle.G_SHOCK) ? 1.08f : 0.96f;
        float strapY1 = wristY - strapHalfY;
        float strapY2 = wristY + strapHalfY;

        float armOuterX = outerX + facingSign * baseClearance;
        float armInnerX = innerX - facingSign * baseClearance;
        float armFrontZ = frontZ - baseClearance;
        float armBackZ = backZ + baseClearance;

        float strapOuterX = outerX + facingSign * outerClearance;
        float strapInnerX = innerX - facingSign * outerClearance;
        float strapFrontZ = frontZ - outerClearance;
        float strapBackZ = backZ + outerClearance;

        WatchStyle.CaseShape shape = style.getCaseShape();
        float[][] contour = getContour(shape);
        float uvRadius = getUvRadius(shape);

        boolean isDeepDome = style == WatchStyle.JACOB_ASTRONOMIA || style == WatchStyle.JACOB_GODFATHER;
        boolean isChunky = style == WatchStyle.APPLE_ULTRA || style == WatchStyle.G_SHOCK || style == WatchStyle.RICHARD_MILLE || style == WatchStyle.AP_OFFSHORE;

        float caseR = (style == WatchStyle.APPLE_ULTRA || style == WatchStyle.G_SHOCK) ? 1.42f : 1.38f;
        float caseBaseH = 0.0f;
        float caseMidLowH = 0.14f;
        float caseMidHighH = isDeepDome ? 0.56f : (isChunky ? 0.54f : 0.44f);
        float caseRimH = caseMidHighH + (isDeepDome ? 0.14f : 0.13f);
        float dialH = isDeepDome ? 0.34f : (caseMidHighH - 0.04f);
        float baseX = strapOuterX - facingSign * 0.05f;

        float hourAngle = WatchClockDriver.getHourAngle();
        float minAngle = WatchClockDriver.getMinuteAngle();
        float secAngle = WatchClockDriver.getSecondAngle();
        float animSec = (System.nanoTime() % 3_600_000_000_000L) / 1.0e9f;

        queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(style.getTexture()), (entry, vc) -> {
            emitSolidStrap(
                    vc, entry, light, rightArm,
                    armOuterX, armInnerX, armFrontZ, armBackZ,
                    strapOuterX, strapInnerX, strapFrontZ, strapBackZ,
                    strapY1, strapY2
            );
            if (shape == WatchStyle.CaseShape.CIRCLE || shape == WatchStyle.CaseShape.OCTAGON) {
                emitLugs(vc, entry, light, facingSign, ySign, baseX, wristY, wristZ, caseBaseH, caseMidHighH, caseR, strapHalfY);
            }
            emitContourCase(
                    vc, entry, light, facingSign, ySign,
                    contour, uvRadius,
                    baseX, wristY, wristZ,
                    caseBaseH, caseMidLowH, caseMidHighH, caseRimH, dialH,
                    caseR
            );
            emitBezelDetails(vc, entry, light, facingSign, ySign, style, baseX, wristY, wristZ, caseMidHighH, caseRimH, caseR);
            emit3DDialInterior(vc, entry, light, facingSign, ySign, style, baseX, wristY, wristZ, dialH, caseRimH, caseR, animSec, hourAngle, minAngle);
            emitCrownAndButtons(vc, entry, light, facingSign, ySign, style, baseX, wristY, wristZ, caseMidLowH, caseMidHighH, caseR);
            if (style != WatchStyle.JACOB_ASTRONOMIA) {
                float handsBaseH = (style == WatchStyle.JACOB_GODFATHER || style == WatchStyle.RICHARD_MILLE) ? (dialH + 0.16f) : (dialH + 0.05f);
                emit3DHands(vc, entry, light, facingSign, ySign, baseX, wristY, wristZ, handsBaseH, hourAngle, minAngle, secAngle);
            }
        });

        queue.submitCustom(matrices, RenderLayers.entityTranslucent(style.getTexture()), (entry, vc) -> {
            emitSapphireCrystal(vc, entry, light, facingSign, ySign, contour, baseX, wristY, wristZ, caseRimH, caseR, isDeepDome);
        });
    }

    private static float wx(float baseX, float facingSign, float h) {
        return baseX + h * facingSign;
    }

    private static float wy(float cy, float ySign, float r) {
        return cy + r * ySign;
    }

    private static float wz(float cz, float u) {
        return cz + u;
    }

    private static void emitSolidStrap(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            boolean rightArm,
            float aox, float aix, float afz, float abz,
            float sox, float six, float sfz, float sbz,
            float y1, float y2
    ) {
        float u0 = 0.0f;
        float u1 = 0.25f;
        float u2 = 0.5f;
        float u3 = 0.75f;
        float u4 = 1.0f;
        float v0 = 0.5f;
        float v1 = 0.75f;

        float sMinX = Math.min(sox, six);
        float sMaxX = Math.max(sox, six);
        float aMinX = Math.min(aox, aix);
        float aMaxX = Math.max(aox, aix);

        renderQuad(vc, entry, light,
                sMinX, y1, sfz, u0, v0,
                sMaxX, y1, sfz, u1, v0,
                sMaxX, y2, sfz, u1, v1,
                sMinX, y2, sfz, u0, v1,
                0.0f, 0.0f, -1.0f);

        renderQuad(vc, entry, light,
                six, y1, sfz, u1, v0,
                six, y1, sbz, u2, v0,
                six, y2, sbz, u2, v1,
                six, y2, sfz, u1, v1,
                rightArm ? 1.0f : -1.0f, 0.0f, 0.0f);

        renderQuad(vc, entry, light,
                sMaxX, y1, sbz, u2, v0,
                sMinX, y1, sbz, u3, v0,
                sMinX, y2, sbz, u3, v1,
                sMaxX, y2, sbz, u2, v1,
                0.0f, 0.0f, 1.0f);

        renderQuad(vc, entry, light,
                sox, y1, sbz, u3, v0,
                sox, y1, sfz, u4, v0,
                sox, y2, sfz, u4, v1,
                sox, y2, sbz, u3, v1,
                rightArm ? -1.0f : 1.0f, 0.0f, 0.0f);

        float ev0 = 0.54f;
        float ev1 = 0.71f;

        renderQuad(vc, entry, light,
                sMinX, y1, sfz, u0, ev0,
                sMaxX, y1, sfz, u1, ev0,
                aMaxX, y1, afz, u1, ev1,
                aMinX, y1, afz, u0, ev1,
                0.0f, -1.0f, 0.0f);

        renderQuad(vc, entry, light,
                six, y1, sfz, u1, ev0,
                six, y1, sbz, u2, ev0,
                aix, y1, abz, u2, ev1,
                aix, y1, afz, u1, ev1,
                0.0f, -1.0f, 0.0f);

        renderQuad(vc, entry, light,
                sMaxX, y1, sbz, u2, ev0,
                sMinX, y1, sbz, u3, ev0,
                aMinX, y1, abz, u3, ev1,
                aMaxX, y1, abz, u2, ev1,
                0.0f, -1.0f, 0.0f);

        renderQuad(vc, entry, light,
                sox, y1, sbz, u3, ev0,
                sox, y1, sfz, u4, ev0,
                aox, y1, afz, u4, ev1,
                aox, y1, abz, u3, ev1,
                0.0f, -1.0f, 0.0f);

        renderQuad(vc, entry, light,
                aMinX, y2, afz, u0, ev0,
                aMaxX, y2, afz, u1, ev0,
                sMaxX, y2, sfz, u1, ev1,
                sMinX, y2, sfz, u0, ev1,
                0.0f, 1.0f, 0.0f);

        renderQuad(vc, entry, light,
                aix, y2, afz, u1, ev0,
                aix, y2, abz, u2, ev0,
                six, y2, sbz, u2, ev1,
                six, y2, sfz, u1, ev1,
                0.0f, 1.0f, 0.0f);

        renderQuad(vc, entry, light,
                aMaxX, y2, abz, u2, ev0,
                aMinX, y2, abz, u3, ev0,
                sMinX, y2, sbz, u3, ev1,
                sMaxX, y2, sbz, u2, ev1,
                0.0f, 1.0f, 0.0f);

        renderQuad(vc, entry, light,
                aox, y2, abz, u3, ev0,
                aox, y2, afz, u4, ev0,
                sox, y2, sfz, u4, ev1,
                sox, y2, sbz, u3, ev1,
                0.0f, 1.0f, 0.0f);
    }

    private static void emitLugs(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            float baseX,
            float cy,
            float cz,
            float baseH,
            float highH,
            float caseR,
            float strapHalfY
    ) {
        float mu0 = 0.64f;
        float mu1 = 0.84f;
        float mv0 = 0.08f;
        float mv1 = 0.40f;

        float rootH = baseH + (highH - baseH) * 0.78f;
        float tipH = baseH + (highH - baseH) * 0.22f;

        float rIn = Math.max(0.58f, strapHalfY - 0.34f);
        float rOut = strapHalfY + 0.08f;
        float uRoot = caseR * 0.62f;
        float uTip = caseR * 1.24f;

        float[] rPairs = new float[]{-rOut, -rIn, rIn, rOut};
        for (int p = 0; p < 4; p += 2) {
            float rA = rPairs[p];
            float rB = rPairs[p + 1];
            emitTaperedLug(vc, entry, light, facingSign, ySign, baseX, cy, cz, baseH, rootH, tipH, rA, rB, uRoot, uTip, 1.0f, mu0, mu1, mv0, mv1);
            emitTaperedLug(vc, entry, light, facingSign, ySign, baseX, cy, cz, baseH, rootH, tipH, rA, rB, -uRoot, -uTip, -1.0f, mu0, mu1, mv0, mv1);
        }
    }

    private static void emitTaperedLug(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            float baseX,
            float cy,
            float cz,
            float baseH,
            float rootH,
            float tipH,
            float rA,
            float rB,
            float uIn,
            float uOut,
            float uDir,
            float u0, float u1, float v0, float v1
    ) {
        float xBase = wx(baseX, facingSign, baseH);
        float xRoot = wx(baseX, facingSign, rootH);
        float xTip = wx(baseX, facingSign, tipH);
        float yA = wy(cy, ySign, rA);
        float yB = wy(cy, ySign, rB);
        float zIn = wz(cz, uIn);
        float zOut = wz(cz, uOut);

        renderQuad(vc, entry, light,
                xRoot, yA, zIn, u0, v0,
                xRoot, yB, zIn, u1, v0,
                xTip, yB, zOut, u1, v1,
                xTip, yA, zOut, u0, v1,
                facingSign * 0.85f, 0.0f, uDir * 0.52f);

        renderQuad(vc, entry, light,
                xBase, yA, zOut, u0, v0,
                xTip, yA, zOut, u1, v0,
                xTip, yB, zOut, u1, v1,
                xBase, yB, zOut, u0, v1,
                0.0f, 0.0f, uDir);

        renderQuad(vc, entry, light,
                xBase, yA, zIn, u0, v0,
                xRoot, yA, zIn, u1, v0,
                xTip, yA, zOut, u1, v1,
                xBase, yA, zOut, u0, v1,
                0.0f, -ySign, 0.0f);

        renderQuad(vc, entry, light,
                xBase, yB, zOut, u0, v0,
                xTip, yB, zOut, u1, v0,
                xRoot, yB, zIn, u1, v1,
                xBase, yB, zIn, u0, v1,
                0.0f, ySign, 0.0f);
    }

    private static void emitContourCase(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            float[][] contour,
            float uvRadius,
            float baseX,
            float cy,
            float cz,
            float baseH,
            float midLowH,
            float midHighH,
            float rimH,
            float dialH,
            float caseR
    ) {
        float[] nr = contour[0];
        float[] nu = contour[1];
        int n = nr.length;

        float mu0 = 0.64f;
        float mu1 = 0.86f;
        float mv0 = 0.06f;
        float mv1 = 0.44f;

        float xBase = wx(baseX, facingSign, baseH);
        float xMidLow = wx(baseX, facingSign, midLowH);
        float xMidHigh = wx(baseX, facingSign, midHighH);
        float xRim = wx(baseX, facingSign, rimH);
        float xDial = wx(baseX, facingSign, dialH);

        float sBase = 0.96f;
        float sBody = 1.04f;
        float sRim = 0.97f;
        float sInner = 0.83f;

        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;

            float r0 = nr[i] * caseR;
            float u0c = nu[i] * caseR;
            float r1 = nr[j] * caseR;
            float u1c = nu[j] * caseR;

            float radR = nr[i] + nr[j];
            float radU = nu[i] + nu[j];
            float radLen = (float) Math.hypot(radR, radU);
            if (radLen > 1e-4f) {
                radR /= radLen;
                radU /= radLen;
            }
            float nY = radR * ySign;
            float nZ = radU;

            float segU0 = mu0 + (mu1 - mu0) * ((float) i / (float) n);
            float segU1 = mu0 + (mu1 - mu0) * ((float) (i + 1) / (float) n);

            renderQuad(vc, entry, light,
                    xBase, wy(cy, ySign, r0 * sBase), wz(cz, u0c * sBase), segU0, mv1,
                    xBase, wy(cy, ySign, r1 * sBase), wz(cz, u1c * sBase), segU1, mv1,
                    xMidLow, wy(cy, ySign, r1 * sBody), wz(cz, u1c * sBody), segU1, mv0 + 0.24f,
                    xMidLow, wy(cy, ySign, r0 * sBody), wz(cz, u0c * sBody), segU0, mv0 + 0.24f,
                    -0.45f * facingSign, 0.89f * nY, 0.89f * nZ);

            renderQuad(vc, entry, light,
                    xMidLow, wy(cy, ySign, r0 * sBody), wz(cz, u0c * sBody), segU0, mv0 + 0.24f,
                    xMidLow, wy(cy, ySign, r1 * sBody), wz(cz, u1c * sBody), segU1, mv0 + 0.24f,
                    xMidHigh, wy(cy, ySign, r1 * sBody), wz(cz, u1c * sBody), segU1, mv0 + 0.10f,
                    xMidHigh, wy(cy, ySign, r0 * sBody), wz(cz, u0c * sBody), segU0, mv0 + 0.10f,
                    0.0f, nY, nZ);

            renderQuad(vc, entry, light,
                    xMidHigh, wy(cy, ySign, r0 * sBody), wz(cz, u0c * sBody), segU0, mv0 + 0.10f,
                    xMidHigh, wy(cy, ySign, r1 * sBody), wz(cz, u1c * sBody), segU1, mv0 + 0.10f,
                    xRim, wy(cy, ySign, r1 * sRim), wz(cz, u1c * sRim), segU1, mv0,
                    xRim, wy(cy, ySign, r0 * sRim), wz(cz, u0c * sRim), segU0, mv0,
                    0.55f * facingSign, 0.83f * nY, 0.83f * nZ);

            float outU0 = 0.25f + nr[i] * uvRadius;
            float outV0 = 0.25f - nu[i] * uvRadius;
            float outU1 = 0.25f + nr[j] * uvRadius;
            float outV1 = 0.25f - nu[j] * uvRadius;

            float inU0 = 0.25f + (nr[i] * sInner) * uvRadius;
            float inV0 = 0.25f - (nu[i] * sInner) * uvRadius;
            float inU1 = 0.25f + (nr[j] * sInner) * uvRadius;
            float inV1 = 0.25f - (nu[j] * sInner) * uvRadius;

            float bezelMidH = rimH - 0.03f;
            float xBezelMid = wx(baseX, facingSign, bezelMidH);

            renderQuad(vc, entry, light,
                    xRim, wy(cy, ySign, r0 * sRim), wz(cz, u0c * sRim), outU0, outV0,
                    xRim, wy(cy, ySign, r1 * sRim), wz(cz, u1c * sRim), outU1, outV1,
                    xBezelMid, wy(cy, ySign, r1 * sInner), wz(cz, u1c * sInner), inU1, inV1,
                    xBezelMid, wy(cy, ySign, r0 * sInner), wz(cz, u0c * sInner), inU0, inV0,
                    0.96f * facingSign, -0.28f * nY, -0.28f * nZ);

            renderQuad(vc, entry, light,
                    xBezelMid, wy(cy, ySign, r0 * sInner), wz(cz, u0c * sInner), segU0, mv0 + 0.08f,
                    xBezelMid, wy(cy, ySign, r1 * sInner), wz(cz, u1c * sInner), segU1, mv0 + 0.08f,
                    xDial, wy(cy, ySign, r1 * sInner), wz(cz, u1c * sInner), segU1, mv1,
                    xDial, wy(cy, ySign, r0 * sInner), wz(cz, u0c * sInner), segU0, mv1,
                    0.25f * facingSign, -0.96f * nY, -0.96f * nZ);
        }

        for (int i = 0; i < n; i += 2) {
            int i1 = (i + 1) % n;
            int i2 = (i + 2) % n;

            float du0 = 0.25f + (nr[i] * sInner) * uvRadius;
            float dv0 = 0.25f - (nu[i] * sInner) * uvRadius;
            float du1 = 0.25f + (nr[i1] * sInner) * uvRadius;
            float dv1 = 0.25f - (nu[i1] * sInner) * uvRadius;
            float du2 = 0.25f + (nr[i2] * sInner) * uvRadius;
            float dv2 = 0.25f - (nu[i2] * sInner) * uvRadius;

            renderQuad(vc, entry, light,
                    xDial, cy, cz, 0.25f, 0.25f,
                    xDial, wy(cy, ySign, nr[i] * caseR * sInner), wz(cz, nu[i] * caseR * sInner), du0, dv0,
                    xDial, wy(cy, ySign, nr[i1] * caseR * sInner), wz(cz, nu[i1] * caseR * sInner), du1, dv1,
                    xDial, wy(cy, ySign, nr[i2] * caseR * sInner), wz(cz, nu[i2] * caseR * sInner), du2, dv2,
                    facingSign, 0.0f, 0.0f);

            renderQuad(vc, entry, light,
                    xBase, cy, cz, 0.75f, 0.25f,
                    xBase, wy(cy, ySign, nr[i2] * caseR * sBase), wz(cz, nu[i2] * caseR * sBase), 0.75f, 0.25f,
                    xBase, wy(cy, ySign, nr[i1] * caseR * sBase), wz(cz, nu[i1] * caseR * sBase), 0.75f, 0.25f,
                    xBase, wy(cy, ySign, nr[i] * caseR * sBase), wz(cz, nu[i] * caseR * sBase), 0.75f, 0.25f,
                    -facingSign, 0.0f, 0.0f);
        }
    }

    private static void emitBezelDetails(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            WatchStyle style,
            float baseX,
            float cy,
            float cz,
            float midHighH,
            float rimH,
            float caseR
    ) {
        if (style == WatchStyle.ROYAL_OAK || style == WatchStyle.AP_OFFSHORE) {
            float screwR = caseR * 0.90f;
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(i * 45.0 + 22.5);
                float sr = (float) Math.sin(a) * screwR;
                float su = (float) Math.cos(a) * screwR;
                emitTintedBoxLocal(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        sr - 0.06f, sr + 0.06f, su - 0.06f, su + 0.06f, rimH - 0.02f, rimH + 0.03f,
                        230, 235, 244, 255);
            }
        } else if (style == WatchStyle.CARTIER_SANTOS) {
            float off1 = caseR * 0.42f;
            float off2 = caseR * 0.89f;
            float[][] pts = new float[][]{
                    {-off1, off2}, {off1, off2}, {-off1, -off2}, {off1, -off2},
                    {-off2, off1}, {-off2, -off1}, {off2, off1}, {off2, -off1}
            };
            for (float[] pt : pts) {
                emitTintedBoxLocal(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        pt[0] - 0.055f, pt[0] + 0.055f, pt[1] - 0.055f, pt[1] + 0.055f, rimH - 0.02f, rimH + 0.03f,
                        235, 240, 248, 255);
            }
        } else if (style == WatchStyle.HUBLOT_BIG_BANG) {
            float screwR = caseR * 0.90f;
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(i * 60.0 + 30.0);
                float sr = (float) Math.sin(a) * screwR;
                float su = (float) Math.cos(a) * screwR;
                emitTintedBoxLocal(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        sr - 0.065f, sr + 0.065f, su - 0.065f, su + 0.065f, rimH - 0.02f, rimH + 0.035f,
                        215, 220, 228, 255);
            }
        } else if (style == WatchStyle.G_SHOCK) {
            float b = caseR * 0.78f;
            float[][] corners = new float[][]{{-b, b}, {b, b}, {-b, -b}, {b, -b}};
            for (float[] c : corners) {
                emitTintedBoxLocal(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        c[0] - 0.22f, c[0] + 0.22f, c[1] - 0.22f, c[1] + 0.22f, midHighH, rimH + 0.08f,
                        32, 34, 40, 255);
            }
        } else if (style == WatchStyle.SUBMARINER || style == WatchStyle.HULK || style == WatchStyle.PEPSI
                || style == WatchStyle.DAY_DATE || style == WatchStyle.DATEJUST || style == WatchStyle.BREITLING_NAVITIMER) {
            boolean gold = style == WatchStyle.DAY_DATE;
            int cr = gold ? 235 : 205;
            int cg = gold ? 195 : 210;
            int cb = gold ? 92 : 220;
            float notchR = caseR * 1.03f;
            for (int i = 0; i < 24; i++) {
                double a = (2.0 * Math.PI * i) / 24.0;
                float nr = (float) Math.sin(a) * notchR;
                float nu = (float) Math.cos(a) * notchR;
                emitTintedBoxLocal(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        nr - 0.055f, nr + 0.055f, nu - 0.055f, nu + 0.055f, midHighH - 0.02f, rimH - 0.02f,
                        cr, cg, cb, 255);
            }
        }
    }

    private static void emit3DDialInterior(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            WatchStyle style,
            float baseX,
            float cy,
            float cz,
            float dialH,
            float rimH,
            float caseR,
            float animSec,
            float hourAngle,
            float minAngle
    ) {
        if (style == WatchStyle.JACOB_ASTRONOMIA) {
            emitAstronomia3D(vc, entry, light, facingSign, ySign, baseX, cy, cz, dialH, rimH, caseR, animSec, hourAngle, minAngle);
            return;
        }
        if (style == WatchStyle.JACOB_GODFATHER) {
            emitGodfather3D(vc, entry, light, facingSign, ySign, baseX, cy, cz, dialH, caseR, animSec);
            return;
        }
        if (style == WatchStyle.RICHARD_MILLE) {
            emitRichardMille3D(vc, entry, light, facingSign, ySign, baseX, cy, cz, dialH, caseR, animSec);
            return;
        }
        if (style == WatchStyle.APPLE_ULTRA) {
            return;
        }

        boolean goldAcc = style == WatchStyle.DAY_DATE || style == WatchStyle.CALATRAVA || style == WatchStyle.HUBLOT_BIG_BANG;
        int mr = goldAcc ? 235 : 225;
        int mg = goldAcc ? 196 : 235;
        int mb = goldAcc ? 105 : 245;

        float markerR = caseR * 0.64f;
        for (int h = 0; h < 12; h++) {
            double a = Math.toRadians(h * 30.0);
            float pr = (float) Math.sin(a) * markerR;
            float pu = (float) Math.cos(a) * markerR;
            float sz = (h == 0 || h == 3 || h == 6 || h == 9) ? 0.065f : 0.045f;
            float mh = (h == 0) ? 0.05f : 0.038f;
            emitTintedBoxLocal(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                    pr - sz, pr + sz, pu - sz, pu + sz, dialH, dialH + mh,
                    mr, mg, mb, 255);
        }

        boolean hasChronoSubs = style == WatchStyle.DAYTONA || style == WatchStyle.OMEGA_SPEEDMASTER
                || style == WatchStyle.BREITLING_NAVITIMER || style == WatchStyle.HUBLOT_BIG_BANG || style == WatchStyle.AP_OFFSHORE;
        if (hasChronoSubs) {
            float subOff = caseR * 0.34f;
            float subR = caseR * 0.17f;
            int sr = (style == WatchStyle.HUBLOT_BIG_BANG) ? 218 : (style == WatchStyle.AP_OFFSHORE ? 220 : 195);
            int sg = (style == WatchStyle.HUBLOT_BIG_BANG) ? 162 : (style == WatchStyle.AP_OFFSHORE ? 42 : 200);
            int sb = (style == WatchStyle.HUBLOT_BIG_BANG) ? 115 : (style == WatchStyle.AP_OFFSHORE ? 45 : 210);
            float[][] subs = new float[][]{{-subOff, 0.0f}, {subOff, 0.0f}, {0.0f, -subOff}};
            for (int s = 0; s < subs.length; s++) {
                emitTintedRing(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        subs[s][0], subs[s][1], subR * 0.78f, subR, dialH, dialH + 0.035f, 14, sr, sg, sb, 255);
                double subAng = animSec * (s == 0 ? 1.8 : (s == 1 ? 0.6 : 0.2));
                emitMiniHand(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        subs[s][0], subs[s][1], dialH + 0.04f, (float) subAng, subR * 0.72f, 0.022f, 235, 65, 65, 255);
            }
        }

        if (style == WatchStyle.SUBMARINER || style == WatchStyle.HULK || style == WatchStyle.PEPSI
                || style == WatchStyle.DAY_DATE || style == WatchStyle.DATEJUST || style == WatchStyle.AQUANAUT) {
            float dr = caseR * 0.56f;
            emitTintedBoxLocal(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                    dr - 0.11f, dr + 0.11f, -0.09f, 0.09f, dialH + 0.01f, dialH + 0.055f,
                    225, 238, 250, 255);
        }
    }

    private static void emitAstronomia3D(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            float baseX,
            float cy,
            float cz,
            float dialH,
            float rimH,
            float caseR,
            float animSec,
            float hourAngle,
            float minAngle
    ) {
        float carouselAngle = animSec * 0.28f;
        float spinFast = animSec * 1.6f;
        float armLen = caseR * 0.50f;
        float hubH = dialH + 0.10f;
        float topH = dialH + 0.26f;

        emitTintedRing(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                0.0f, 0.0f, 0.05f, 0.16f, dialH, hubH + 0.06f, 12, 232, 188, 98, 255);

        for (int a = 0; a < 4; a++) {
            float ang = carouselAngle + (float) (a * Math.PI * 0.5);
            float dx = (float) Math.sin(ang);
            float dy = (float) Math.cos(ang);
            float endR = dx * armLen;
            float endU = dy * armLen;

            emit3DStick(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                    0.0f, 0.0f, endR, endU, hubH, hubH + 0.055f, 0.042f, 232, 186, 94, 255);

            if (a == 0) {
                emitTintedSphere(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        endR, endU, topH, 0.22f, spinFast, 38, 138, 235, 255, 58, 196, 122);
            } else if (a == 1) {
                emitTintedDiamond(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        endR, endU, topH, 0.21f, spinFast * 1.4f, 225, 242, 255, 255);
            } else if (a == 2) {
                emitTintedRing(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        endR, endU, 0.14f, 0.21f, topH - 0.04f, topH + 0.04f, 14, 238, 196, 96, 255);
                emitTintedGear(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        endR, endU, 0.13f, topH - 0.02f, topH + 0.02f, 8, spinFast * 2.0f, 245, 212, 120, 255);
            } else {
                emitTintedRing(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        endR, endU, 0.0f, 0.23f, topH - 0.04f, topH + 0.02f, 14, 242, 244, 248, 255);
                emitTintedRing(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        endR, endU, 0.19f, 0.24f, topH - 0.03f, topH + 0.04f, 14, 232, 188, 98, 255);
                emitMiniHand(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        endR, endU, topH + 0.045f, (float) Math.toRadians(hourAngle), 0.12f, 0.022f, 22, 28, 42, 255);
                emitMiniHand(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        endR, endU, topH + 0.055f, (float) Math.toRadians(minAngle), 0.17f, 0.018f, 22, 28, 42, 255);
            }
        }
    }

    private static void emitGodfather3D(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            float baseX,
            float cy,
            float cz,
            float dialH,
            float caseR,
            float animSec
    ) {
        float gOff = caseR * 0.30f;
        emitTintedGear(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                -gOff, gOff * 0.55f, 0.26f, dialH + 0.02f, dialH + 0.11f, 10, animSec * 0.9f, 230, 182, 86, 255);
        emitTintedGear(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                gOff, gOff * 0.55f, 0.26f, dialH + 0.02f, dialH + 0.11f, 10, -animSec * 0.9f, 230, 182, 86, 255);
        emitTintedSphere(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                0.0f, -caseR * 0.36f, dialH + 0.10f, 0.20f, 0.0f, 195, 22, 38, 255, 130, 14, 24);
    }

    private static void emitRichardMille3D(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            float baseX,
            float cy,
            float cz,
            float dialH,
            float caseR,
            float animSec
    ) {
        emit3DStick(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                -caseR * 0.48f, -caseR * 0.52f, caseR * 0.48f, caseR * 0.52f, dialH + 0.04f, dialH + 0.09f, 0.055f, 95, 102, 114, 255);
        emit3DStick(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                -caseR * 0.48f, caseR * 0.52f, caseR * 0.48f, -caseR * 0.52f, dialH + 0.04f, dialH + 0.09f, 0.055f, 95, 102, 114, 255);
        emitTintedGear(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                -0.14f, 0.24f, 0.22f, dialH + 0.05f, dialH + 0.12f, 9, animSec * 1.2f, 232, 188, 76, 255);
        emitTintedGear(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                0.14f, -0.26f, 0.20f, dialH + 0.06f, dialH + 0.13f, 8, -animSec * 1.8f, 225, 42, 46, 255);
    }

    private static void emitCrownAndButtons(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            WatchStyle style,
            float baseX,
            float cy,
            float cz,
            float midLowH,
            float midHighH,
            float caseR
    ) {
        float sideScale = (style == WatchStyle.RICHARD_MILLE) ? 0.90f : 1.03f;
        float bodyEdgeR = caseR * sideScale;

        float crH1 = midLowH + 0.02f;
        float crH2 = midHighH - 0.02f;

        if (style == WatchStyle.APPLE_ULTRA) {
            emitBoxLocalUv(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                    bodyEdgeR - 0.08f, bodyEdgeR + 0.18f, -0.58f, 0.58f, midLowH, midHighH + 0.05f,
                    0.66f, 0.84f, 0.10f, 0.36f);

            emitBoxLocalUv(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                    bodyEdgeR + 0.12f, bodyEdgeR + 0.34f, 0.06f, 0.46f, crH1, crH2,
                    0.94f, 1.00f, 0.18f, 0.32f);

            emitBoxLocalUv(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                    bodyEdgeR + 0.12f, bodyEdgeR + 0.26f, -0.46f, -0.08f, crH1 + 0.02f, crH2 - 0.02f,
                    0.68f, 0.82f, 0.12f, 0.34f);

            emitBoxLocalUv(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                    -bodyEdgeR - 0.16f, -bodyEdgeR + 0.04f, -0.34f, 0.34f, crH1 + 0.02f, crH2 - 0.02f,
                    0.535f, 0.575f, 0.180f, 0.320f);
        } else {
            emitBoxLocalUv(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                    bodyEdgeR - 0.06f, bodyEdgeR + 0.28f, -0.22f, 0.22f, crH1, crH2,
                    0.94f, 1.00f, 0.18f, 0.32f);

            boolean isChrono = style == WatchStyle.DAYTONA
                    || style == WatchStyle.AP_OFFSHORE
                    || style == WatchStyle.HUBLOT_BIG_BANG
                    || style == WatchStyle.OMEGA_SPEEDMASTER
                    || style == WatchStyle.TAG_MONACO
                    || style == WatchStyle.BREITLING_NAVITIMER
                    || style == WatchStyle.G_SHOCK;
            if (isChrono) {
                emitBoxLocalUv(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        bodyEdgeR - 0.14f, bodyEdgeR + 0.18f, 0.40f, 0.66f, crH1 + 0.03f, crH2 - 0.03f,
                        0.68f, 0.84f, 0.10f, 0.36f);
                emitBoxLocalUv(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        bodyEdgeR - 0.14f, bodyEdgeR + 0.18f, -0.66f, -0.40f, crH1 + 0.03f, crH2 - 0.03f,
                        0.68f, 0.84f, 0.10f, 0.36f);
            }
            if (style == WatchStyle.G_SHOCK) {
                emitBoxLocalUv(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        -bodyEdgeR - 0.16f, -bodyEdgeR + 0.06f, 0.40f, 0.66f, crH1 + 0.03f, crH2 - 0.03f,
                        0.68f, 0.84f, 0.10f, 0.36f);
                emitBoxLocalUv(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                        -bodyEdgeR - 0.16f, -bodyEdgeR + 0.06f, -0.66f, -0.40f, crH1 + 0.03f, crH2 - 0.03f,
                        0.68f, 0.84f, 0.10f, 0.36f);
            }
        }
    }

    private static void emit3DHands(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            float baseX,
            float cy,
            float cz,
            float hBase,
            float hourAngle,
            float minAngle,
            float secAngle
    ) {
        emit3DHand(vc, entry, light, facingSign, ySign, baseX, cy, cz, hBase, 0.022f, hourAngle, 0.58f, -0.10f, 0.075f,
                0.086f, 0.102f, 0.781f, 0.906f);
        emit3DHand(vc, entry, light, facingSign, ySign, baseX, cy, cz, hBase + 0.026f, 0.020f, minAngle, 0.82f, -0.12f, 0.055f,
                0.195f, 0.211f, 0.765f, 0.968f);
        emit3DHand(vc, entry, light, facingSign, ySign, baseX, cy, cz, hBase + 0.050f, 0.015f, secAngle, 0.90f, -0.22f, 0.030f,
                0.305f, 0.320f, 0.750f, 0.984f);

        emitTintedRing(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                0.0f, 0.0f, 0.0f, 0.11f, hBase - 0.01f, hBase + 0.072f, 10, 232, 236, 244, 255);
    }

    private static void emit3DHand(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            float baseX,
            float cy,
            float cz,
            float hBottom,
            float thick,
            float angleDeg,
            float lengthTip,
            float lengthTail,
            float halfWidth,
            float u0, float u1, float v0, float v1
    ) {
        double rad = Math.toRadians(angleDeg);
        float dirR = (float) Math.sin(rad);
        float dirU = (float) Math.cos(rad);
        float perpR = dirU;
        float perpU = -dirR;

        float rTipA = lengthTip * dirR - halfWidth * perpR;
        float uTipA = lengthTip * dirU - halfWidth * perpU;
        float rTipB = lengthTip * dirR + halfWidth * perpR;
        float uTipB = lengthTip * dirU + halfWidth * perpU;
        float rTailB = lengthTail * dirR + halfWidth * perpR;
        float uTailB = lengthTail * dirU + halfWidth * perpU;
        float rTailA = lengthTail * dirR - halfWidth * perpR;
        float uTailA = lengthTail * dirU - halfWidth * perpU;

        float xTop = wx(baseX, facingSign, hBottom + thick);
        float xBot = wx(baseX, facingSign, hBottom);

        float yTA = wy(cy, ySign, rTipA), zTA = wz(cz, uTipA);
        float yTB = wy(cy, ySign, rTipB), zTB = wz(cz, uTipB);
        float yLB = wy(cy, ySign, rTailB), zLB = wz(cz, uTailB);
        float yLA = wy(cy, ySign, rTailA), zLA = wz(cz, uTailA);

        renderQuad(vc, entry, light,
                xTop, yLA, zLA, u0, v1,
                xTop, yLB, zLB, u1, v1,
                xTop, yTB, zTB, u1, v0,
                xTop, yTA, zTA, u0, v0,
                facingSign, 0.0f, 0.0f);

        renderQuad(vc, entry, light,
                xBot, yLA, zLA, u0, v1,
                xTop, yLA, zLA, u1, v1,
                xTop, yTA, zTA, u1, v0,
                xBot, yTA, zTA, u0, v0,
                0.0f, -perpR * ySign, -perpU);

        renderQuad(vc, entry, light,
                xBot, yTB, zTB, u0, v0,
                xTop, yTB, zTB, u1, v0,
                xTop, yLB, zLB, u1, v1,
                xBot, yLB, zLB, u0, v1,
                0.0f, perpR * ySign, perpU);
    }

    private static void emitSapphireCrystal(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            float[][] contour,
            float baseX,
            float cy,
            float cz,
            float rimH,
            float caseR,
            boolean isDome
    ) {
        float[] nr = contour[0];
        float[] nu = contour[1];
        int n = nr.length;
        float sEdge = 0.85f;

        if (isDome) {
            float h0 = rimH - 0.02f;
            float domeRise = 0.24f;
            int rings = 3;
            for (int ring = 0; ring < rings; ring++) {
                float t0 = (float) ring / rings;
                float t1 = (float) (ring + 1) / rings;
                float sc0 = sEdge * (float) Math.cos(t0 * Math.PI * 0.5);
                float sc1 = sEdge * (float) Math.cos(t1 * Math.PI * 0.5);
                float hr0 = h0 + domeRise * (float) Math.sin(t0 * Math.PI * 0.5);
                float hr1 = h0 + domeRise * (float) Math.sin(t1 * Math.PI * 0.5);
                float x0 = wx(baseX, facingSign, hr0);
                float x1 = wx(baseX, facingSign, hr1);
                int alpha = ring == 2 ? 52 : 34;

                for (int i = 0; i < n; i++) {
                    int j = (i + 1) % n;
                    renderTintedQuad(vc, entry, light,
                            x0, wy(cy, ySign, nr[i] * caseR * sc0), wz(cz, nu[i] * caseR * sc0), 0.25f, 0.25f,
                            x0, wy(cy, ySign, nr[j] * caseR * sc0), wz(cz, nu[j] * caseR * sc0), 0.25f, 0.25f,
                            x1, wy(cy, ySign, nr[j] * caseR * sc1), wz(cz, nu[j] * caseR * sc1), 0.25f, 0.25f,
                            x1, wy(cy, ySign, nr[i] * caseR * sc1), wz(cz, nu[i] * caseR * sc1), 0.25f, 0.25f,
                            facingSign * 0.85f, (nr[i] + nr[j]) * 0.25f * ySign, (nu[i] + nu[j]) * 0.25f,
                            215, 236, 255, alpha);
                }
            }
        } else {
            float xGlass = wx(baseX, facingSign, rimH - 0.015f);
            for (int i = 0; i < n; i += 2) {
                int i1 = (i + 1) % n;
                int i2 = (i + 2) % n;
                renderTintedQuad(vc, entry, light,
                        xGlass, cy, cz, 0.25f, 0.25f,
                        xGlass, wy(cy, ySign, nr[i] * caseR * sEdge), wz(cz, nu[i] * caseR * sEdge), 0.25f, 0.25f,
                        xGlass, wy(cy, ySign, nr[i1] * caseR * sEdge), wz(cz, nu[i1] * caseR * sEdge), 0.25f, 0.25f,
                        xGlass, wy(cy, ySign, nr[i2] * caseR * sEdge), wz(cz, nu[i2] * caseR * sEdge), 0.25f, 0.25f,
                        facingSign, 0.0f, 0.0f,
                        220, 238, 255, 28);
            }
        }
    }

    private static void emitTintedSphere(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            float baseX,
            float cy,
            float cz,
            float cR,
            float cU,
            float cH,
            float radius,
            float spinAngle,
            int r1, int g1, int b1, int a1,
            int r2, int g2, int b2
    ) {
        int latSteps = 6;
        int lonSteps = 10;
        for (int lat = 0; lat < latSteps; lat++) {
            double phi0 = -Math.PI * 0.5 + (Math.PI * lat) / latSteps;
            double phi1 = -Math.PI * 0.5 + (Math.PI * (lat + 1)) / latSteps;
            float cPhi0 = (float) Math.cos(phi0), sPhi0 = (float) Math.sin(phi0);
            float cPhi1 = (float) Math.cos(phi1), sPhi1 = (float) Math.sin(phi1);

            for (int lon = 0; lon < lonSteps; lon++) {
                double th0 = spinAngle + (2.0 * Math.PI * lon) / lonSteps;
                double th1 = spinAngle + (2.0 * Math.PI * (lon + 1)) / lonSteps;
                float cTh0 = (float) Math.cos(th0), sTh0 = (float) Math.sin(th0);
                float cTh1 = (float) Math.cos(th1), sTh1 = (float) Math.sin(th1);

                boolean alt = ((lat + lon) % 3 == 0);
                int cr = alt ? r2 : r1;
                int cg = alt ? g2 : g1;
                int cb = alt ? b2 : b1;

                float pr00 = cR + radius * cPhi0 * cTh0, pu00 = cU + radius * sPhi0, ph00 = cH + radius * cPhi0 * sTh0;
                float pr10 = cR + radius * cPhi0 * cTh1, pu10 = cU + radius * sPhi0, ph10 = cH + radius * cPhi0 * sTh1;
                float pr11 = cR + radius * cPhi1 * cTh1, pu11 = cU + radius * sPhi1, ph11 = cH + radius * cPhi1 * sTh1;
                float pr01 = cR + radius * cPhi1 * cTh0, pu01 = cU + radius * sPhi1, ph01 = cH + radius * cPhi1 * sTh0;

                float cPhiMid = (cPhi0 + cPhi1) * 0.5f;
                float cThMid = (cTh0 + cTh1) * 0.5f;
                float sThMid = (sTh0 + sTh1) * 0.5f;
                float nr = cPhiMid * cThMid;
                float nu = (sPhi0 + sPhi1) * 0.5f;
                float nh = cPhiMid * sThMid;
                float nLen = (float) Math.sqrt(nr * nr + nu * nu + nh * nh);
                if (nLen > 1e-4f) {
                    nr /= nLen;
                    nu /= nLen;
                    nh /= nLen;
                }

                renderTintedQuad(vc, entry, light,
                        wx(baseX, facingSign, ph00), wy(cy, ySign, pr00), wz(cz, pu00), 0.25f, 0.25f,
                        wx(baseX, facingSign, ph10), wy(cy, ySign, pr10), wz(cz, pu10), 0.25f, 0.25f,
                        wx(baseX, facingSign, ph11), wy(cy, ySign, pr11), wz(cz, pu11), 0.25f, 0.25f,
                        wx(baseX, facingSign, ph01), wy(cy, ySign, pr01), wz(cz, pu01), 0.25f, 0.25f,
                        facingSign * nh, ySign * nr, nu,
                        cr, cg, cb, a1);
            }
        }
    }

    private static void emitTintedDiamond(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            float baseX,
            float cy,
            float cz,
            float cR,
            float cU,
            float cH,
            float radius,
            float spinAngle,
            int cr, int cg, int cb, int ca
    ) {
        int sides = 8;
        float xTop = wx(baseX, facingSign, cH + radius);
        float xBot = wx(baseX, facingSign, cH - radius);
        float xMid = wx(baseX, facingSign, cH);
        float yCenter = wy(cy, ySign, cR);
        float zCenter = wz(cz, cU);

        for (int i = 0; i < sides; i++) {
            double a0 = spinAngle + (2.0 * Math.PI * i) / sides;
            double a1 = spinAngle + (2.0 * Math.PI * (i + 1)) / sides;
            float r0 = cR + (float) Math.sin(a0) * radius;
            float u0 = cU + (float) Math.cos(a0) * radius;
            float r1 = cR + (float) Math.sin(a1) * radius;
            float u1 = cU + (float) Math.cos(a1) * radius;
            int shade = (i % 2 == 0) ? 0 : -28;

            renderTintedQuad(vc, entry, light,
                    xTop, yCenter, zCenter, 0.25f, 0.25f,
                    xMid, wy(cy, ySign, r0), wz(cz, u0), 0.25f, 0.25f,
                    xMid, wy(cy, ySign, r1), wz(cz, u1), 0.25f, 0.25f,
                    xTop, yCenter, zCenter, 0.25f, 0.25f,
                    facingSign * 0.7f, (float) Math.sin(a0) * 0.7f * ySign, (float) Math.cos(a0) * 0.7f,
                    Math.max(0, cr + shade), Math.max(0, cg + shade), Math.max(0, cb + shade), ca);

            renderTintedQuad(vc, entry, light,
                    xBot, yCenter, zCenter, 0.25f, 0.25f,
                    xMid, wy(cy, ySign, r1), wz(cz, u1), 0.25f, 0.25f,
                    xMid, wy(cy, ySign, r0), wz(cz, u0), 0.25f, 0.25f,
                    xBot, yCenter, zCenter, 0.25f, 0.25f,
                    -facingSign * 0.7f, (float) Math.sin(a0) * 0.7f * ySign, (float) Math.cos(a0) * 0.7f,
                    Math.max(0, cr - 35), Math.max(0, cg - 35), Math.max(0, cb - 30), ca);
        }
    }

    private static void emitTintedRing(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            float baseX,
            float cy,
            float cz,
            float cR,
            float cU,
            float innerR,
            float outerR,
            float h0,
            float h1,
            int segments,
            int cr, int cg, int cb, int ca
    ) {
        float x0 = wx(baseX, facingSign, h0);
        float x1 = wx(baseX, facingSign, h1);
        for (int i = 0; i < segments; i++) {
            double a0 = (2.0 * Math.PI * i) / segments;
            double a1 = (2.0 * Math.PI * (i + 1)) / segments;
            float sr0 = (float) Math.sin(a0), su0 = (float) Math.cos(a0);
            float sr1 = (float) Math.sin(a1), su1 = (float) Math.cos(a1);

            float or0 = cR + sr0 * outerR, ou0 = cU + su0 * outerR;
            float or1 = cR + sr1 * outerR, ou1 = cU + su1 * outerR;
            float ir0 = cR + sr0 * innerR, iu0 = cU + su0 * innerR;
            float ir1 = cR + sr1 * innerR, iu1 = cU + su1 * innerR;

            renderTintedQuad(vc, entry, light,
                    x1, wy(cy, ySign, or0), wz(cz, ou0), 0.25f, 0.25f,
                    x1, wy(cy, ySign, or1), wz(cz, ou1), 0.25f, 0.25f,
                    x1, wy(cy, ySign, ir1), wz(cz, iu1), 0.25f, 0.25f,
                    x1, wy(cy, ySign, ir0), wz(cz, iu0), 0.25f, 0.25f,
                    facingSign, 0.0f, 0.0f, cr, cg, cb, ca);

            renderTintedQuad(vc, entry, light,
                    x0, wy(cy, ySign, or0), wz(cz, ou0), 0.25f, 0.25f,
                    x0, wy(cy, ySign, or1), wz(cz, ou1), 0.25f, 0.25f,
                    x1, wy(cy, ySign, or1), wz(cz, ou1), 0.25f, 0.25f,
                    x1, wy(cy, ySign, or0), wz(cz, ou0), 0.25f, 0.25f,
                    0.0f, sr0 * ySign, su0, Math.max(0, cr - 22), Math.max(0, cg - 22), Math.max(0, cb - 22), ca);

            if (innerR > 0.001f) {
                renderTintedQuad(vc, entry, light,
                        x0, wy(cy, ySign, ir1), wz(cz, iu1), 0.25f, 0.25f,
                        x0, wy(cy, ySign, ir0), wz(cz, iu0), 0.25f, 0.25f,
                        x1, wy(cy, ySign, ir0), wz(cz, iu0), 0.25f, 0.25f,
                        x1, wy(cy, ySign, ir1), wz(cz, iu1), 0.25f, 0.25f,
                        0.0f, -sr0 * ySign, -su0, Math.max(0, cr - 32), Math.max(0, cg - 32), Math.max(0, cb - 32), ca);
            }
        }
    }

    private static void emitTintedGear(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            float baseX,
            float cy,
            float cz,
            float cR,
            float cU,
            float radius,
            float h0,
            float h1,
            int teeth,
            float spinAngle,
            int cr, int cg, int cb, int ca
    ) {
        emitTintedRing(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                cR, cU, radius * 0.45f, radius * 0.84f, h0, h1, teeth * 2, cr, cg, cb, ca);
        emitTintedRing(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                cR, cU, 0.0f, radius * 0.28f, h0, h1 + 0.015f, 8, cr, cg, cb, ca);

        for (int t = 0; t < teeth; t++) {
            float ang = spinAngle + (float) (2.0 * Math.PI * t / teeth);
            float tr = cR + (float) Math.sin(ang) * radius * 0.94f;
            float tu = cU + (float) Math.cos(ang) * radius * 0.94f;
            float sz = radius * 0.14f;
            emitTintedBoxLocal(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                    tr - sz, tr + sz, tu - sz, tu + sz, h0, h1, cr, cg, cb, ca);
        }
        for (int s = 0; s < 3; s++) {
            float ang = spinAngle + (float) (Math.PI * s / 3.0);
            float dx = (float) Math.sin(ang) * radius * 0.65f;
            float dy = (float) Math.cos(ang) * radius * 0.65f;
            emit3DStick(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                    cR - dx, cU - dy, cR + dx, cU + dy, h0, h1, radius * 0.09f, cr, cg, cb, ca);
        }
    }

    private static void emitMiniHand(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            float baseX,
            float cy,
            float cz,
            float cR,
            float cU,
            float h,
            float angleRad,
            float len,
            float halfW,
            int cr, int cg, int cb, int ca
    ) {
        float endR = cR + (float) Math.sin(angleRad) * len;
        float endU = cU + (float) Math.cos(angleRad) * len;
        emit3DStick(vc, entry, light, facingSign, ySign, baseX, cy, cz,
                cR, cU, endR, endU, h, h + 0.015f, halfW, cr, cg, cb, ca);
    }

    private static void emit3DStick(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            float baseX,
            float cy,
            float cz,
            float r0, float u0,
            float r1, float u1,
            float h0, float h1,
            float halfW,
            int cr, int cg, int cb, int ca
    ) {
        float dr = r1 - r0;
        float du = u1 - u0;
        float len = (float) Math.hypot(dr, du);
        if (len < 1e-4f) return;
        float pr = -du / len * halfW;
        float pu = dr / len * halfW;

        float x0 = wx(baseX, facingSign, h0);
        float x1 = wx(baseX, facingSign, h1);

        float yA0 = wy(cy, ySign, r0 - pr), zA0 = wz(cz, u0 - pu);
        float yB0 = wy(cy, ySign, r0 + pr), zB0 = wz(cz, u0 + pu);
        float yB1 = wy(cy, ySign, r1 + pr), zB1 = wz(cz, u1 + pu);
        float yA1 = wy(cy, ySign, r1 - pr), zA1 = wz(cz, u1 - pu);

        renderTintedQuad(vc, entry, light,
                x1, yA0, zA0, 0.25f, 0.25f,
                x1, yB0, zB0, 0.25f, 0.25f,
                x1, yB1, zB1, 0.25f, 0.25f,
                x1, yA1, zA1, 0.25f, 0.25f,
                facingSign, 0.0f, 0.0f, cr, cg, cb, ca);

        int sr = Math.max(0, cr - 28);
        int sg = Math.max(0, cg - 28);
        int sb = Math.max(0, cb - 28);
        renderTintedQuad(vc, entry, light,
                x0, yA0, zA0, 0.25f, 0.25f,
                x1, yA0, zA0, 0.25f, 0.25f,
                x1, yA1, zA1, 0.25f, 0.25f,
                x0, yA1, zA1, 0.25f, 0.25f,
                0.0f, -pr / halfW * ySign, -pu / halfW, sr, sg, sb, ca);

        renderTintedQuad(vc, entry, light,
                x0, yB1, zB1, 0.25f, 0.25f,
                x1, yB1, zB1, 0.25f, 0.25f,
                x1, yB0, zB0, 0.25f, 0.25f,
                x0, yB0, zB0, 0.25f, 0.25f,
                0.0f, pr / halfW * ySign, pu / halfW, sr, sg, sb, ca);
    }

    private static void emitBoxLocalUv(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            float baseX,
            float cy,
            float cz,
            float rA, float rB,
            float uA, float uB,
            float hA, float hB,
            float tu0, float tu1, float tv0, float tv1
    ) {
        float x0 = Math.min(wx(baseX, facingSign, hA), wx(baseX, facingSign, hB));
        float x1 = Math.max(wx(baseX, facingSign, hA), wx(baseX, facingSign, hB));
        float y0 = Math.min(wy(cy, ySign, rA), wy(cy, ySign, rB));
        float y1 = Math.max(wy(cy, ySign, rA), wy(cy, ySign, rB));
        float z0 = Math.min(wz(cz, uA), wz(cz, uB));
        float z1 = Math.max(wz(cz, uA), wz(cz, uB));
        emitSolidBox(vc, entry, light, x0, x1, y0, y1, z0, z1, tu0, tu1, tv0, tv1, 255, 255, 255, 255);
    }

    private static void emitTintedBoxLocal(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float facingSign,
            float ySign,
            float baseX,
            float cy,
            float cz,
            float rA, float rB,
            float uA, float uB,
            float hA, float hB,
            int cr, int cg, int cb, int ca
    ) {
        float x0 = Math.min(wx(baseX, facingSign, hA), wx(baseX, facingSign, hB));
        float x1 = Math.max(wx(baseX, facingSign, hA), wx(baseX, facingSign, hB));
        float y0 = Math.min(wy(cy, ySign, rA), wy(cy, ySign, rB));
        float y1 = Math.max(wy(cy, ySign, rA), wy(cy, ySign, rB));
        float z0 = Math.min(wz(cz, uA), wz(cz, uB));
        float z1 = Math.max(wz(cz, uA), wz(cz, uB));
        emitSolidBox(vc, entry, light, x0, x1, y0, y1, z0, z1, 0.25f, 0.25f, 0.25f, 0.25f, cr, cg, cb, ca);
    }

    private static void emitSolidBox(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float x0, float x1,
            float y0, float y1,
            float z0, float z1,
            float u0, float u1, float v0, float v1,
            int cr, int cg, int cb, int ca
    ) {
        renderTintedQuad(vc, entry, light,
                x0, y0, z0, u0, v0,
                x0, y0, z1, u1, v0,
                x0, y1, z1, u1, v1,
                x0, y1, z0, u0, v1,
                -1.0f, 0.0f, 0.0f, cr, cg, cb, ca);

        renderTintedQuad(vc, entry, light,
                x1, y0, z1, u0, v0,
                x1, y0, z0, u1, v0,
                x1, y1, z0, u1, v1,
                x1, y1, z1, u0, v1,
                1.0f, 0.0f, 0.0f, cr, cg, cb, ca);

        renderTintedQuad(vc, entry, light,
                x0, y0, z0, u0, v0,
                x1, y0, z0, u1, v0,
                x1, y0, z1, u1, v1,
                x0, y0, z1, u0, v1,
                0.0f, -1.0f, 0.0f, cr, cg, cb, ca);

        renderTintedQuad(vc, entry, light,
                x0, y1, z1, u0, v0,
                x1, y1, z1, u1, v0,
                x1, y1, z0, u1, v1,
                x0, y1, z0, u0, v1,
                0.0f, 1.0f, 0.0f, cr, cg, cb, ca);

        renderTintedQuad(vc, entry, light,
                x1, y0, z0, u0, v0,
                x0, y0, z0, u1, v0,
                x0, y1, z0, u1, v1,
                x1, y1, z0, u0, v1,
                0.0f, 0.0f, -1.0f, cr, cg, cb, ca);

        renderTintedQuad(vc, entry, light,
                x0, y0, z1, u0, v0,
                x1, y0, z1, u1, v0,
                x1, y1, z1, u1, v1,
                x0, y1, z1, u0, v1,
                0.0f, 0.0f, 1.0f, cr, cg, cb, ca);
    }

    private static void renderQuad(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3,
            float nx, float ny, float nz
    ) {
        renderTintedQuad(vc, entry, light,
                x0, y0, z0, u0, v0,
                x1, y1, z1, u1, v1,
                x2, y2, z2, u2, v2,
                x3, y3, z3, u3, v3,
                nx, ny, nz, 255, 255, 255, 255);
    }

    private static void renderTintedQuad(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3,
            float nx, float ny, float nz,
            int cr, int cg, int cb, int ca
    ) {
        vc.vertex(entry, x0, y0, z0).color(cr, cg, cb, ca).texture(u0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
        vc.vertex(entry, x1, y1, z1).color(cr, cg, cb, ca).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
        vc.vertex(entry, x2, y2, z2).color(cr, cg, cb, ca).texture(u2, v2).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
        vc.vertex(entry, x3, y3, z3).color(cr, cg, cb, ca).texture(u3, v3).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
    }
}

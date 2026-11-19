package egg.client.cosmetic.sword;

import egg.client.module.cosmetic.CosmeticsModule;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public final class SwordRenderer {
    private static final Identifier BLADE_TEXTURE = Identifier.of("egg", "textures/swords/blade_base.png");
    private static final int FULLBRIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    private SwordRenderer() {
    }

    public static void renderPreviewInHand(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            PlayerEntityModel model,
            int light
    ) {
        CosmeticsModule mod = CosmeticsModule.getInstance();
        if (mod == null || !mod.isEnabled()) return;
        SwordStyle style = mod.getSelectedSwordStyle();
        if (style == null || style == SwordStyle.NONE) return;

        matrices.push();
        model.rightArm.applyTransform(matrices);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
        matrices.translate(-0.0625f, 0.125f, -0.625f);
        render(style, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, matrices, queue, light);
        matrices.pop();
    }

    public static void render(
            SwordStyle style,
            ItemDisplayContext mode,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light
    ) {
        if (style == null || style == SwordStyle.NONE) return;
        ItemDisplayContext ctx = (mode == null || mode == ItemDisplayContext.NONE)
                ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                : mode;

        matrices.push();
        applyDisplayTransform(matrices, ctx);

        float animSec = (System.nanoTime() % 3_600_000_000_000L) / 1.0e9f;
        int bladeCol = style.getBladeColor();
        int edgeCol = style.getEdgeColor();
        int guardCol = style.getGuardColor();
        int gripCol = style.getGripColor();
        int glowCol = style.getGlowColor();

        if (style == SwordStyle.PRISM_SWORD) {
            bladeCol = hsvToRgb((animSec * 0.22f) % 1.0f, 0.55f, 1.0f);
            edgeCol = hsvToRgb(((animSec * 0.22f) + 0.18f) % 1.0f, 0.72f, 1.0f);
            glowCol = hsvToRgb(((animSec * 0.22f) + 0.35f) % 1.0f, 0.65f, 1.0f);
        }

        final int fBlade = bladeCol;
        final int fEdge = edgeCol;
        final int fGuard = guardCol;
        final int fGrip = gripCol;
        final int fGlow = glowCol;

        queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(BLADE_TEXTURE), (entry, vc) -> {
            emitSolidSword(vc, entry, light, style, fBlade, fEdge, fGuard, fGrip, animSec);
        });

        if (fGlow != 0 || style.isTranslucentBlade()) {
            queue.submitCustom(matrices, RenderLayers.entityTranslucent(BLADE_TEXTURE), (entry, vc) -> {
                emitEmissiveAndCrystal(vc, entry, FULLBRIGHT, style, fBlade, fEdge, fGlow, animSec);
            });
        }

        matrices.pop();
    }

    private static void applyDisplayTransform(MatrixStack matrices, ItemDisplayContext ctx) {
        switch (ctx) {
            case FIRST_PERSON_RIGHT_HAND -> {
                matrices.translate(0.06f, 0.14f, 0.04f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-14.0f));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(4.0f));
                matrices.scale(0.82f, 0.82f, 0.82f);
            }
            case FIRST_PERSON_LEFT_HAND -> {
                matrices.translate(-0.06f, 0.14f, 0.04f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-14.0f));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-4.0f));
                matrices.scale(0.82f, 0.82f, 0.82f);
            }
            case THIRD_PERSON_RIGHT_HAND -> {
                matrices.translate(0.0f, 0.04f, 0.06f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-6.0f));
                matrices.scale(0.95f, 0.95f, 0.95f);
            }
            case THIRD_PERSON_LEFT_HAND -> {
                matrices.translate(0.0f, 0.04f, 0.06f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-6.0f));
                matrices.scale(0.95f, 0.95f, 0.95f);
            }
            case GROUND -> {
                matrices.translate(0.0f, 0.25f, 0.0f);
                matrices.scale(0.68f, 0.68f, 0.68f);
            }
            case FIXED -> {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-45.0f));
                matrices.translate(0.0f, -0.32f, 0.0f);
                matrices.scale(0.72f, 0.72f, 0.72f);
            }
            default -> {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-42.0f));
                matrices.translate(0.0f, -0.35f, 0.0f);
                matrices.scale(0.72f, 0.72f, 0.72f);
            }
        }
    }

    private static void emitSolidSword(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            SwordStyle style,
            int bladeCol,
            int edgeCol,
            int guardCol,
            int gripCol,
            float animSec
    ) {
        SwordStyle.Archetype arch = style.getArchetype();
        float lScale = style.getLengthScale();
        float wScale = style.getWidthScale();

        switch (arch) {
            case KATANA -> emitKatana(vc, entry, light, style, bladeCol, edgeCol, guardCol, gripCol, lScale, wScale);
            case LONGSWORD -> emitLongsword(vc, entry, light, style, bladeCol, edgeCol, guardCol, gripCol, lScale, wScale);
            case GREATSWORD -> emitGreatsword(vc, entry, light, style, bladeCol, edgeCol, guardCol, gripCol, lScale, wScale);
            case CURVED -> emitCurvedSword(vc, entry, light, style, bladeCol, edgeCol, guardCol, gripCol, lScale, wScale);
            case WAVY -> emitWavySword(vc, entry, light, style, bladeCol, edgeCol, guardCol, gripCol, lScale, wScale);
            case RAPIER -> emitRapier(vc, entry, light, bladeCol, edgeCol, guardCol, gripCol, lScale);
            case SABER -> emitSaberHilt(vc, entry, light, guardCol, gripCol);
            case BUSTER -> emitBusterSword(vc, entry, light, bladeCol, edgeCol, guardCol, gripCol, lScale, wScale);
            case KHOPESH -> emitKhopesh(vc, entry, light, style, bladeCol, edgeCol, guardCol, gripCol, lScale, wScale);
            case GUNBLADE -> emitGunblade(vc, entry, light, bladeCol, edgeCol, guardCol, gripCol, lScale, wScale);
            case CLOCKWORK -> emitClockworkBlade(vc, entry, light, bladeCol, edgeCol, guardCol, gripCol, lScale, wScale, animSec);
            case CANDY_CANE -> emitCandyCane(vc, entry, light, guardCol, lScale);
            case PIXEL -> emitPixelSword(vc, entry, light, bladeCol, edgeCol, guardCol, gripCol, lScale);
        }
    }

    private static void emitStandardHilt(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            int guardCol,
            int gripCol,
            float gripLen,
            float gripRadius,
            boolean octPommel
    ) {
        int gripDark = darken(gripCol, 0.76f);
        float yTop = 0.08f;
        float yBot = yTop - gripLen;
        emitPrismSegment(vc, entry, light, 0.0f, yBot, 0.0f, gripRadius, gripRadius * 0.85f,
                0.0f, yTop, 0.0f, gripRadius * 1.06f, gripRadius * 0.90f, gripCol, 255);

        int wraps = 6;
        for (int i = 0; i < wraps; i++) {
            float wy = yBot + 0.02f + (gripLen - 0.04f) * ((float) i / wraps);
            emitBox(vc, entry, light, -gripRadius * 1.12f, gripRadius * 1.12f, wy, wy + 0.016f, -gripRadius * 0.96f, gripRadius * 0.96f, gripDark, 255);
        }

        if (octPommel) {
            emitOctahedron(vc, entry, light, 0.0f, yBot - 0.045f, 0.0f, gripRadius * 1.65f, 0.055f, gripRadius * 1.55f, guardCol, 255);
        } else {
            emitBox(vc, entry, light, -gripRadius * 1.35f, gripRadius * 1.35f, yBot - 0.04f, yBot, -gripRadius * 1.25f, gripRadius * 1.25f, guardCol, 255);
        }
    }

    private static void emitKatana(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            SwordStyle style,
            int bladeCol,
            int edgeCol,
            int guardCol,
            int gripCol,
            float lScale,
            float wScale
    ) {
        emitStandardHilt(vc, entry, light, guardCol, gripCol, 0.30f, 0.028f, false);

        emitCylinderY(vc, entry, light, 0.0f, 0.075f, 0.105f, 0.088f, 0.068f, 12, guardCol, 255);
        emitBox(vc, entry, light, -0.040f, 0.046f, 0.105f, 0.145f, -0.024f, 0.024f, lighten(guardCol, 1.12f), 255);

        int segs = 12;
        float bladeLen = 0.96f * lScale;
        float halfW = 0.040f * wScale;
        float halfZ = 0.018f;
        float curveAmt = (style == SwordStyle.CYBER_BLADE) ? 0.055f : 0.115f;

        for (int i = 0; i < segs; i++) {
            float t0 = (float) i / segs;
            float t1 = (float) (i + 1) / segs;
            float y0 = 0.145f + t0 * bladeLen;
            float y1 = 0.145f + t1 * bladeLen;
            float cx0 = -t0 * t0 * curveAmt;
            float cx1 = -t1 * t1 * curveAmt;
            float w0 = halfW * (1.0f - 0.18f * t0);
            float w1 = (i == segs - 1) ? 0.004f : halfW * (1.0f - 0.18f * t1);
            float z0 = halfZ * (1.0f - 0.35f * t0);
            float z1 = (i == segs - 1) ? 0.002f : halfZ * (1.0f - 0.35f * t1);

            emitDiamondBladeSegment(vc, entry, light,
                    cx0, y0, w0, z0,
                    cx1, y1, w1, z1,
                    bladeCol, edgeCol, 255);
        }

        if (style == SwordStyle.CYBER_BLADE) {
            emitBox(vc, entry, light, -0.048f, 0.052f, 0.08f, 0.13f, -0.042f, 0.042f, guardCol, 255);
            emitBox(vc, entry, light, -0.065f, 0.032f, 0.18f, 0.24f, -0.028f, 0.028f, guardCol, 255);
        }
    }

    private static void emitLongsword(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            SwordStyle style,
            int bladeCol,
            int edgeCol,
            int guardCol,
            int gripCol,
            float lScale,
            float wScale
    ) {
        emitStandardHilt(vc, entry, light, guardCol, gripCol, 0.28f, 0.030f, true);

        if (style == SwordStyle.HIGHLAND_CLAYMORE) {
            emitTiltedBar(vc, entry, light, 0.0f, 0.10f, 0.20f, 0.22f, 0.022f, 0.024f, guardCol, 255);
            emitTiltedBar(vc, entry, light, 0.0f, 0.10f, -0.20f, 0.22f, 0.022f, 0.024f, guardCol, 255);
            emitOctahedron(vc, entry, light, 0.21f, 0.23f, 0.0f, 0.032f, 0.032f, 0.032f, lighten(guardCol, 1.15f), 255);
            emitOctahedron(vc, entry, light, -0.21f, 0.23f, 0.0f, 0.032f, 0.032f, 0.032f, lighten(guardCol, 1.15f), 255);
        } else if (style == SwordStyle.HOLY_AVENGER) {
            emitBox(vc, entry, light, -0.08f, 0.08f, 0.08f, 0.15f, -0.036f, 0.036f, guardCol, 255);
            emitTiltedBar(vc, entry, light, 0.06f, 0.11f, 0.22f, 0.23f, 0.026f, 0.022f, guardCol, 255);
            emitTiltedBar(vc, entry, light, -0.06f, 0.11f, -0.22f, 0.23f, 0.026f, 0.022f, guardCol, 255);
            emitTiltedBar(vc, entry, light, 0.08f, 0.10f, 0.19f, 0.15f, 0.018f, 0.018f, lighten(guardCol, 1.15f), 255);
            emitTiltedBar(vc, entry, light, -0.08f, 0.10f, -0.19f, 0.15f, 0.018f, 0.018f, lighten(guardCol, 1.15f), 255);
        } else {
            float gW = (style == SwordStyle.ZWEIHANDER) ? 0.24f : (style == SwordStyle.GLADIUS ? 0.13f : 0.18f);
            emitBox(vc, entry, light, -gW, gW, 0.08f, 0.13f, -0.034f, 0.034f, guardCol, 255);
            emitOctahedron(vc, entry, light, gW + 0.015f, 0.105f, 0.0f, 0.030f, 0.032f, 0.034f, lighten(guardCol, 1.12f), 255);
            emitOctahedron(vc, entry, light, -gW - 0.015f, 0.105f, 0.0f, 0.030f, 0.032f, 0.034f, lighten(guardCol, 1.12f), 255);
        }

        float bladeLen = 0.92f * lScale;
        float halfW = 0.056f * wScale;
        float halfZ = 0.024f;

        if (!style.isTranslucentBlade()) {
            int segs = 8;
            for (int i = 0; i < segs; i++) {
                float t0 = (float) i / segs;
                float t1 = (float) (i + 1) / segs;
                float y0 = 0.13f + t0 * bladeLen;
                float y1 = 0.13f + t1 * bladeLen;
                float w0 = halfW * (1.0f - 0.25f * t0);
                float w1 = (i == segs - 1) ? 0.003f : halfW * (1.0f - 0.25f * t1);
                float z0 = halfZ * (1.0f - 0.45f * t0);
                float z1 = (i == segs - 1) ? 0.002f : halfZ * (1.0f - 0.45f * t1);
                emitDiamondBladeSegment(vc, entry, light, 0.0f, y0, w0, z0, 0.0f, y1, w1, z1, bladeCol, edgeCol, 255);
            }
        }

        if (style == SwordStyle.ZWEIHANDER) {
            emitTiltedBar(vc, entry, light, 0.04f, 0.34f, 0.12f, 0.41f, 0.014f, 0.014f, guardCol, 255);
            emitTiltedBar(vc, entry, light, -0.04f, 0.34f, -0.12f, 0.41f, 0.014f, 0.014f, guardCol, 255);
        }
    }

    private static void emitGreatsword(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            SwordStyle style,
            int bladeCol,
            int edgeCol,
            int guardCol,
            int gripCol,
            float lScale,
            float wScale
    ) {
        emitStandardHilt(vc, entry, light, guardCol, gripCol, 0.32f, 0.034f, true);

        emitBox(vc, entry, light, -0.11f, 0.11f, 0.08f, 0.17f, -0.048f, 0.048f, guardCol, 255);
        emitTiltedBar(vc, entry, light, 0.08f, 0.13f, 0.25f, 0.24f, 0.032f, 0.034f, guardCol, 255);
        emitTiltedBar(vc, entry, light, -0.08f, 0.13f, -0.25f, 0.24f, 0.032f, 0.034f, guardCol, 255);
        emitTiltedBar(vc, entry, light, 0.12f, 0.10f, 0.21f, 0.05f, 0.020f, 0.022f, darken(guardCol, 0.85f), 255);
        emitTiltedBar(vc, entry, light, -0.12f, 0.10f, -0.21f, 0.05f, 0.020f, 0.022f, darken(guardCol, 0.85f), 255);

        float bladeLen = 1.00f * lScale;
        float halfW = 0.082f * wScale;
        float halfZ = 0.032f;

        if (!style.isTranslucentBlade()) {
            int segs = 10;
            for (int i = 0; i < segs; i++) {
                float t0 = (float) i / segs;
                float t1 = (float) (i + 1) / segs;
                float y0 = 0.16f + t0 * bladeLen;
                float y1 = 0.16f + t1 * bladeLen;
                float flare0 = 1.0f + 0.12f * (float) Math.sin(t0 * Math.PI);
                float flare1 = 1.0f + 0.12f * (float) Math.sin(t1 * Math.PI);
                float w0 = halfW * flare0 * (1.0f - 0.18f * t0);
                float w1 = (i == segs - 1) ? 0.004f : halfW * flare1 * (1.0f - 0.18f * t1);
                float z0 = halfZ * (1.0f - 0.45f * t0);
                float z1 = (i == segs - 1) ? 0.002f : halfZ * (1.0f - 0.45f * t1);
                emitDiamondBladeSegment(vc, entry, light, 0.0f, y0, w0, z0, 0.0f, y1, w1, z1, bladeCol, edgeCol, 255);
            }
        }

        if (style == SwordStyle.BLOODTHIRSTER || style == SwordStyle.OBSIDIAN_GREATSWORD) {
            for (int k = 0; k < 3; k++) {
                float sy = 0.28f + k * 0.22f;
                emitTiltedBar(vc, entry, light, halfW * 0.8f, sy, halfW * 1.35f, sy + 0.07f, 0.016f, 0.014f, edgeCol, 255);
                emitTiltedBar(vc, entry, light, -halfW * 0.8f, sy, -halfW * 1.35f, sy + 0.07f, 0.016f, 0.014f, edgeCol, 255);
            }
        }
    }

    private static void emitCurvedSword(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            SwordStyle style,
            int bladeCol,
            int edgeCol,
            int guardCol,
            int gripCol,
            float lScale,
            float wScale
    ) {
        emitStandardHilt(vc, entry, light, guardCol, gripCol, 0.27f, 0.029f, true);

        emitBox(vc, entry, light, -0.14f, 0.16f, 0.08f, 0.13f, -0.032f, 0.032f, guardCol, 255);
        if (style == SwordStyle.PIRATE_CUTLASS) {
            emitTiltedBar(vc, entry, light, 0.14f, 0.10f, 0.16f, -0.06f, 0.016f, 0.022f, guardCol, 255);
            emitTiltedBar(vc, entry, light, 0.16f, -0.06f, 0.03f, -0.20f, 0.016f, 0.022f, guardCol, 255);
        } else if (style == SwordStyle.SULTAN_SCIMITAR) {
            emitTiltedBar(vc, entry, light, 0.13f, 0.10f, 0.19f, 0.19f, 0.018f, 0.020f, guardCol, 255);
            emitTiltedBar(vc, entry, light, -0.12f, 0.10f, -0.18f, 0.02f, 0.018f, 0.020f, guardCol, 255);
            emitOctahedron(vc, entry, light, 0.0f, 0.105f, 0.0f, 0.034f, 0.034f, 0.044f, gripCol, 255);
        }

        if (!style.isTranslucentBlade()) {
            int segs = 12;
            float bladeLen = 0.90f * lScale;
            float halfW = 0.056f * wScale;
            float halfZ = 0.022f;
            float curve = (style == SwordStyle.SULTAN_SCIMITAR) ? 0.24f : 0.17f;

            for (int i = 0; i < segs; i++) {
                float t0 = (float) i / segs;
                float t1 = (float) (i + 1) / segs;
                float y0 = 0.13f + t0 * bladeLen;
                float y1 = 0.13f + t1 * bladeLen;
                float cx0 = -t0 * t0 * curve;
                float cx1 = -t1 * t1 * curve;
                float swell0 = 1.0f + 0.35f * (float) Math.sin(t0 * Math.PI * 0.9);
                float swell1 = 1.0f + 0.35f * (float) Math.sin(t1 * Math.PI * 0.9);
                float w0 = halfW * swell0;
                float w1 = (i == segs - 1) ? 0.004f : halfW * swell1;
                float z0 = halfZ * (1.0f - 0.4f * t0);
                float z1 = (i == segs - 1) ? 0.002f : halfZ * (1.0f - 0.4f * t1);
                emitDiamondBladeSegment(vc, entry, light, cx0, y0, w0, z0, cx1, y1, w1, z1, bladeCol, edgeCol, 255);
            }
        }
    }

    private static void emitWavySword(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            SwordStyle style,
            int bladeCol,
            int edgeCol,
            int guardCol,
            int gripCol,
            float lScale,
            float wScale
    ) {
        emitStandardHilt(vc, entry, light, guardCol, gripCol, 0.28f, 0.030f, true);
        emitBox(vc, entry, light, -0.18f, 0.18f, 0.08f, 0.13f, -0.034f, 0.034f, guardCol, 255);

        int segs = 18;
        float bladeLen = 0.96f * lScale;
        float halfW = 0.052f * wScale;
        float halfZ = 0.022f;
        float amp = (style == SwordStyle.SERPENT_KRIS) ? 0.048f : 0.036f;

        for (int i = 0; i < segs; i++) {
            float t0 = (float) i / segs;
            float t1 = (float) (i + 1) / segs;
            float y0 = 0.13f + t0 * bladeLen;
            float y1 = 0.13f + t1 * bladeLen;
            float cx0 = (float) Math.sin(t0 * Math.PI * 5.0) * amp * (1.0f - 0.3f * t0);
            float cx1 = (float) Math.sin(t1 * Math.PI * 5.0) * amp * (1.0f - 0.3f * t1);
            float w0 = halfW * (1.0f - 0.25f * t0);
            float w1 = (i == segs - 1) ? 0.003f : halfW * (1.0f - 0.25f * t1);
            float z0 = halfZ * (1.0f - 0.4f * t0);
            float z1 = (i == segs - 1) ? 0.002f : halfZ * (1.0f - 0.4f * t1);
            emitDiamondBladeSegment(vc, entry, light, cx0, y0, w0, z0, cx1, y1, w1, z1, bladeCol, edgeCol, 255);
        }
    }

    private static void emitRapier(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            int bladeCol,
            int edgeCol,
            int guardCol,
            int gripCol,
            float lScale
    ) {
        emitStandardHilt(vc, entry, light, guardCol, gripCol, 0.26f, 0.024f, true);
        emitCylinderY(vc, entry, light, 0.0f, 0.04f, 0.14f, 0.115f, 0.048f, 12, guardCol, 255);
        emitBox(vc, entry, light, -0.17f, 0.17f, 0.09f, 0.115f, -0.016f, 0.016f, lighten(guardCol, 1.1f), 255);
        emitTiltedBar(vc, entry, light, 0.11f, 0.09f, 0.13f, -0.05f, 0.012f, 0.012f, guardCol, 255);
        emitTiltedBar(vc, entry, light, 0.13f, -0.05f, 0.02f, -0.19f, 0.012f, 0.012f, guardCol, 255);

        float bladeLen = 1.05f * lScale;
        emitDiamondBladeSegment(vc, entry, light, 0.0f, 0.14f, 0.024f, 0.018f, 0.0f, 0.14f + bladeLen, 0.002f, 0.002f, bladeCol, edgeCol, 255);
    }

    private static void emitSaberHilt(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            int guardCol,
            int gripCol
    ) {
        emitCylinderY(vc, entry, light, 0.0f, -0.22f, 0.10f, 0.036f, 0.036f, 10, gripCol, 255);
        for (int i = 0; i < 5; i++) {
            float ry = -0.19f + i * 0.055f;
            emitCylinderY(vc, entry, light, 0.0f, ry, ry + 0.018f, 0.042f, 0.042f, 10, guardCol, 255);
        }
        emitCylinderY(vc, entry, light, 0.0f, 0.10f, 0.16f, 0.048f, 0.044f, 10, lighten(guardCol, 1.15f), 255);
        emitBox(vc, entry, light, -0.10f, 0.10f, 0.095f, 0.145f, -0.036f, 0.036f, guardCol, 255);
    }

    private static void emitBusterSword(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            int bladeCol,
            int edgeCol,
            int guardCol,
            int gripCol,
            float lScale,
            float wScale
    ) {
        emitStandardHilt(vc, entry, light, guardCol, gripCol, 0.34f, 0.034f, false);
        emitBox(vc, entry, light, -0.145f, 0.145f, 0.08f, 0.145f, -0.048f, 0.048f, guardCol, 255);

        float bladeLen = 1.02f * lScale;
        float halfW = 0.086f * wScale;
        float halfZ = 0.028f;
        float yMid = 0.145f + bladeLen * 0.80f;
        float yTop = 0.145f + bladeLen;

        emitBox(vc, entry, light, -halfW, halfW * 0.55f, 0.145f, yMid, -halfZ, halfZ, bladeCol, 255);
        emitDiamondBladeSegment(vc, entry, light, halfW * 0.55f, 0.145f, halfW * 0.45f, halfZ, halfW * 0.55f, yMid, halfW * 0.45f, halfZ, edgeCol, lighten(edgeCol, 1.15f), 255);
        emitDiamondBladeSegment(vc, entry, light, -halfW * 0.15f, yMid, halfW * 0.85f, halfZ, halfW * 0.45f, yTop, 0.005f, 0.003f, bladeCol, edgeCol, 255);

        emitOctahedron(vc, entry, light, -0.02f, 0.25f, 0.0f, 0.034f, 0.034f, 0.038f, 0x3CEB82, 255);
        emitOctahedron(vc, entry, light, -0.02f, 0.36f, 0.0f, 0.034f, 0.034f, 0.038f, 0x41D7FF, 255);
    }

    private static void emitKhopesh(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            SwordStyle style,
            int bladeCol,
            int edgeCol,
            int guardCol,
            int gripCol,
            float lScale,
            float wScale
    ) {
        emitStandardHilt(vc, entry, light, guardCol, gripCol, 0.28f, 0.028f, true);
        emitBox(vc, entry, light, -0.10f, 0.10f, 0.08f, 0.125f, -0.030f, 0.030f, guardCol, 255);

        float bladeLen = 0.90f * lScale;
        float halfW = 0.048f * wScale;
        float halfZ = 0.020f;
        int segs = 14;
        for (int i = 0; i < segs; i++) {
            float t0 = (float) i / segs;
            float t1 = (float) (i + 1) / segs;
            float y0 = 0.125f + t0 * bladeLen;
            float y1 = 0.125f + t1 * bladeLen;
            float cx0 = (t0 < 0.32f) ? 0.0f : (float) Math.sin(((t0 - 0.32f) / 0.68f) * Math.PI) * 0.22f;
            float cx1 = (t1 < 0.32f) ? 0.0f : (float) Math.sin(((t1 - 0.32f) / 0.68f) * Math.PI) * 0.22f;
            float w0 = halfW * (0.9f + 0.35f * (float) Math.sin(t0 * Math.PI));
            float w1 = (i == segs - 1) ? 0.004f : halfW * (0.9f + 0.35f * (float) Math.sin(t1 * Math.PI));
            emitDiamondBladeSegment(vc, entry, light, cx0, y0, w0, halfZ, cx1, y1, w1, halfZ * 0.7f, bladeCol, edgeCol, 255);
        }
    }

    private static void emitGunblade(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            int bladeCol,
            int edgeCol,
            int guardCol,
            int gripCol,
            float lScale,
            float wScale
    ) {
        emitStandardHilt(vc, entry, light, guardCol, gripCol, 0.25f, 0.032f, false);
        emitBox(vc, entry, light, -0.06f, 0.10f, 0.04f, 0.24f, -0.046f, 0.046f, guardCol, 255);
        emitCylinderZ(vc, entry, light, 0.03f, 0.14f, -0.056f, 0.056f, 0.062f, 10, lighten(guardCol, 1.25f), 255);
        emitTiltedBar(vc, entry, light, 0.08f, 0.04f, 0.10f, -0.08f, 0.012f, 0.014f, guardCol, 255);
        emitTiltedBar(vc, entry, light, 0.10f, -0.08f, 0.02f, -0.10f, 0.012f, 0.014f, guardCol, 255);
        emitBox(vc, entry, light, -0.05f, 0.01f, 0.24f, 0.78f, -0.028f, 0.028f, darken(guardCol, 0.85f), 255);

        float bladeLen = 0.88f * lScale;
        float halfW = 0.064f * wScale;
        emitDiamondBladeSegment(vc, entry, light, 0.04f, 0.22f, halfW, 0.024f, 0.04f, 0.22f + bladeLen * 0.82f, halfW * 0.92f, 0.020f, bladeCol, edgeCol, 255);
        emitDiamondBladeSegment(vc, entry, light, 0.04f, 0.22f + bladeLen * 0.82f, halfW * 0.92f, 0.020f, -0.01f, 0.22f + bladeLen, 0.004f, 0.002f, bladeCol, edgeCol, 255);
    }

    private static void emitClockworkBlade(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            int bladeCol,
            int edgeCol,
            int guardCol,
            int gripCol,
            float lScale,
            float wScale,
            float animSec
    ) {
        emitStandardHilt(vc, entry, light, guardCol, gripCol, 0.28f, 0.030f, true);
        emitBox(vc, entry, light, -0.17f, 0.17f, 0.08f, 0.135f, -0.036f, 0.036f, guardCol, 255);

        emitGearZ(vc, entry, light, 0.0f, 0.14f, 0.115f, -0.038f, 0.038f, 10, animSec * 1.4f, edgeCol, 255);
        emitGearZ(vc, entry, light, 0.035f, 0.30f, 0.075f, -0.032f, 0.032f, 8, -animSec * 2.1f, bladeCol, 255);

        float bladeLen = 0.96f * lScale;
        float halfW = 0.058f * wScale;
        emitDiamondBladeSegment(vc, entry, light, 0.0f, 0.14f, halfW, 0.024f, 0.0f, 0.14f + bladeLen * 0.82f, halfW * 0.78f, 0.018f, bladeCol, edgeCol, 255);
        emitDiamondBladeSegment(vc, entry, light, 0.0f, 0.14f + bladeLen * 0.82f, halfW * 0.78f, 0.018f, 0.0f, 0.14f + bladeLen, 0.003f, 0.002f, bladeCol, edgeCol, 255);
    }

    private static void emitCandyCane(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            int guardCol,
            float lScale
    ) {
        int segs = 12;
        float shaftLen = 0.88f * lScale;
        for (int i = 0; i < segs; i++) {
            float y0 = -0.22f + (shaftLen * i / segs);
            float y1 = -0.22f + (shaftLen * (i + 1) / segs);
            int col = (i % 2 == 0) ? 0xF5F5F8 : 0xE42028;
            emitCylinderY(vc, entry, light, 0.0f, y0, y1, 0.042f, 0.042f, 10, col, 255);
        }
        float topY = -0.22f + shaftLen;
        int hookSegs = 8;
        float hookR = 0.14f;
        for (int i = 0; i < hookSegs; i++) {
            double a0 = Math.PI * i / hookSegs;
            double a1 = Math.PI * (i + 1) / hookSegs;
            float x0 = hookR - (float) Math.cos(a0) * hookR;
            float y0 = topY + (float) Math.sin(a0) * hookR;
            float x1 = hookR - (float) Math.cos(a1) * hookR;
            float y1 = topY + (float) Math.sin(a1) * hookR;
            int col = (i % 2 == 0) ? 0xE42028 : 0xF5F5F8;
            emitTiltedBar(vc, entry, light, x0, y0, x1, y1, 0.040f, 0.040f, col, 255);
        }
        emitBox(vc, entry, light, -0.14f, 0.14f, 0.07f, 0.12f, -0.048f, 0.048f, guardCol, 255);
        emitTiltedBar(vc, entry, light, 0.08f, 0.09f, 0.16f, 0.01f, 0.018f, 0.020f, lighten(guardCol, 1.15f), 255);
        emitTiltedBar(vc, entry, light, -0.08f, 0.09f, -0.16f, 0.01f, 0.018f, 0.020f, lighten(guardCol, 1.15f), 255);
    }

    private static void emitPixelSword(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            int bladeCol,
            int edgeCol,
            int guardCol,
            int gripCol,
            float lScale
    ) {
        float step = 0.075f * lScale;
        for (int i = 0; i < 4; i++) {
            float y = -0.22f + i * 0.075f;
            emitBox(vc, entry, light, -0.036f, 0.036f, y, y + 0.075f, -0.036f, 0.036f, (i == 0) ? guardCol : gripCol, 255);
        }
        emitBox(vc, entry, light, -0.19f, 0.19f, 0.08f, 0.155f, -0.044f, 0.044f, guardCol, 255);
        for (int i = 0; i < 11; i++) {
            float y = 0.155f + i * step;
            float w = (i >= 9) ? (0.075f - (i - 8) * 0.022f) : 0.075f;
            emitBox(vc, entry, light, -w, w, y, y + step, -0.028f, 0.028f, bladeCol, 255);
            emitBox(vc, entry, light, -w * 0.45f, w * 0.45f, y, y + step, -0.034f, 0.034f, edgeCol, 255);
        }
    }

    private static void emitEmissiveAndCrystal(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int fullbright,
            SwordStyle style,
            int bladeCol,
            int edgeCol,
            int glowCol,
            float animSec
    ) {
        float pulse = 0.80f + 0.20f * (float) Math.sin(animSec * 3.8f);
        float lScale = style.getLengthScale();
        float wScale = style.getWidthScale();

        if (style == SwordStyle.CROSSGUARD_SABER) {
            float len = 0.96f * lScale;
            emitCylinderY(vc, entry, fullbright, 0.0f, 0.15f, 0.15f + len, 0.017f, 0.014f, 10, 0xFFF2F2, 245);
            emitCylinderY(vc, entry, fullbright, 0.0f, 0.145f, 0.165f + len, 0.036f * pulse, 0.030f * pulse, 10, glowCol, 155);
            emitTiltedBar(vc, entry, fullbright, 0.09f, 0.12f, 0.26f, 0.12f, 0.012f, 0.012f, 0xFFF2F2, 245);
            emitTiltedBar(vc, entry, fullbright, 0.09f, 0.12f, 0.28f, 0.12f, 0.025f * pulse, 0.025f * pulse, glowCol, 155);
            emitTiltedBar(vc, entry, fullbright, -0.09f, 0.12f, -0.26f, 0.12f, 0.012f, 0.012f, 0xFFF2F2, 245);
            emitTiltedBar(vc, entry, fullbright, -0.09f, 0.12f, -0.28f, 0.12f, 0.025f * pulse, 0.025f * pulse, glowCol, 155);
            return;
        }

        if (style.isTranslucentBlade()) {
            float bladeLen = (style.getArchetype() == SwordStyle.Archetype.GREATSWORD ? 1.00f : 0.92f) * lScale;
            float halfW = (style.getArchetype() == SwordStyle.Archetype.GREATSWORD ? 0.082f : 0.058f) * wScale;
            float halfZ = (style.getArchetype() == SwordStyle.Archetype.GREATSWORD ? 0.034f : 0.024f);
            float curve = (style.getArchetype() == SwordStyle.Archetype.CURVED) ? 0.17f : 0.0f;

            int segs = 10;
            for (int i = 0; i < segs; i++) {
                float t0 = (float) i / segs;
                float t1 = (float) (i + 1) / segs;
                float y0 = 0.15f + t0 * bladeLen;
                float y1 = 0.15f + t1 * bladeLen;
                float cx0 = -t0 * t0 * curve;
                float cx1 = -t1 * t1 * curve;
                float w0 = halfW * (1.0f - 0.18f * t0);
                float w1 = (i == segs - 1) ? 0.004f : halfW * (1.0f - 0.18f * t1);
                float z0 = halfZ * (1.0f - 0.45f * t0);
                float z1 = (i == segs - 1) ? 0.002f : halfZ * (1.0f - 0.45f * t1);

                emitDiamondBladeSegment(vc, entry, fullbright, cx0, y0, w0 * 0.45f, z0 * 0.55f, cx1, y1, w1 * 0.45f, z1 * 0.55f, edgeCol, 0xFFFFFF, 225);
                emitDiamondBladeSegment(vc, entry, fullbright, cx0, y0, w0, z0, cx1, y1, w1, z1, bladeCol, glowCol != 0 ? glowCol : edgeCol, 165);
            }
            return;
        }

        if (style == SwordStyle.CYBER_BLADE) {
            int segs = 12;
            float bladeLen = 0.96f * lScale;
            float halfW = 0.040f * wScale;
            for (int i = 0; i < segs; i++) {
                float t0 = (float) i / segs;
                float t1 = (float) (i + 1) / segs;
                float y0 = 0.145f + t0 * bladeLen;
                float y1 = 0.145f + t1 * bladeLen;
                float w0 = halfW * (1.0f - 0.18f * t0);
                float w1 = (i == segs - 1) ? 0.004f : halfW * (1.0f - 0.18f * t1);
                float x0 = -t0 * t0 * 0.055f + w0;
                float x1 = -t1 * t1 * 0.055f + w1;
                emitTiltedBar(vc, entry, fullbright, x0, y0, x1, y1, 0.011f, 0.013f, glowCol, 225);
            }
            return;
        }

        if (style == SwordStyle.THUNDER_NODACHI) {
            float bladeLen = 0.96f * lScale;
            float prevX = 0.0f;
            float prevY = 0.18f;
            for (int i = 1; i <= 8; i++) {
                float t = i / 8.0f;
                float ny = 0.18f + t * (bladeLen - 0.08f);
                float nx = -t * t * 0.115f + (float) Math.sin(animSec * 14.0f + i * 2.1f) * 0.055f;
                emitTiltedBar(vc, entry, fullbright, prevX, prevY, nx, ny, 0.011f, 0.014f, glowCol, 220);
                prevX = nx;
                prevY = ny;
            }
            return;
        }

        if (style == SwordStyle.VOID_RUNEBLADE || style == SwordStyle.OBSIDIAN_GREATSWORD || style == SwordStyle.BLOODTHIRSTER) {
            emitOctahedron(vc, entry, fullbright, 0.0f, 0.125f, 0.0f, 0.046f * pulse, 0.046f * pulse, 0.056f * pulse, glowCol, 235);
            for (int k = 0; k < 4; k++) {
                float ry = 0.30f + k * 0.21f;
                emitOctahedron(vc, entry, fullbright, 0.0f, ry, 0.0f, 0.026f * pulse, 0.045f, 0.036f, glowCol, 220);
            }
            return;
        }

        if (style == SwordStyle.HOLY_AVENGER) {
            emitOctahedron(vc, entry, fullbright, 0.0f, 0.12f, 0.0f, 0.044f * pulse, 0.044f * pulse, 0.052f * pulse, glowCol, 235);
            float bladeLen = 0.92f * lScale;
            float halfW = 0.056f * wScale;
            float halfZ = 0.024f;
            int segs = 8;
            for (int i = 0; i < segs; i++) {
                float t0 = (float) i / segs;
                float t1 = (float) (i + 1) / segs;
                float y0 = 0.13f + t0 * bladeLen;
                float y1 = 0.13f + t1 * bladeLen;
                float w0 = halfW * (1.0f - 0.25f * t0) * 1.18f;
                float w1 = (i == segs - 1) ? 0.005f : halfW * (1.0f - 0.25f * t1) * 1.18f;
                float z0 = halfZ * (1.0f - 0.45f * t0) * 1.38f;
                float z1 = (i == segs - 1) ? 0.003f : halfZ * (1.0f - 0.45f * t1) * 1.38f;
                emitDiamondBladeSegment(vc, entry, fullbright, 0.0f, y0, w0, z0, 0.0f, y1, w1, z1, glowCol, glowCol, 90);
            }
            return;
        }

        if (glowCol != 0) {
            SwordStyle.Archetype arch = style.getArchetype();
            if (arch == SwordStyle.Archetype.KATANA) {
                int segs = 12;
                float bladeLen = 0.96f * lScale;
                float halfW = 0.040f * wScale;
                float halfZ = 0.018f;
                for (int i = 0; i < segs; i++) {
                    float t0 = (float) i / segs;
                    float t1 = (float) (i + 1) / segs;
                    float y0 = 0.145f + t0 * bladeLen;
                    float y1 = 0.145f + t1 * bladeLen;
                    float cx0 = -t0 * t0 * 0.115f;
                    float cx1 = -t1 * t1 * 0.115f;
                    float w0 = halfW * (1.0f - 0.18f * t0) * 1.20f;
                    float w1 = (i == segs - 1) ? 0.005f : halfW * (1.0f - 0.18f * t1) * 1.20f;
                    float z0 = halfZ * (1.0f - 0.35f * t0) * 1.40f;
                    float z1 = (i == segs - 1) ? 0.003f : halfZ * (1.0f - 0.35f * t1) * 1.40f;
                    emitDiamondBladeSegment(vc, entry, fullbright, cx0, y0, w0, z0, cx1, y1, w1, z1, glowCol, glowCol, 92);
                }
            } else if (arch == SwordStyle.Archetype.CURVED) {
                int segs = 12;
                float bladeLen = 0.90f * lScale;
                float halfW = 0.056f * wScale;
                float halfZ = 0.022f;
                float curve = 0.17f;
                for (int i = 0; i < segs; i++) {
                    float t0 = (float) i / segs;
                    float t1 = (float) (i + 1) / segs;
                    float y0 = 0.13f + t0 * bladeLen;
                    float y1 = 0.13f + t1 * bladeLen;
                    float cx0 = -t0 * t0 * curve;
                    float cx1 = -t1 * t1 * curve;
                    float swell0 = 1.0f + 0.35f * (float) Math.sin(t0 * Math.PI * 0.9);
                    float swell1 = 1.0f + 0.35f * (float) Math.sin(t1 * Math.PI * 0.9);
                    float w0 = halfW * swell0 * 1.18f;
                    float w1 = (i == segs - 1) ? 0.005f : halfW * swell1 * 1.18f;
                    float z0 = halfZ * (1.0f - 0.4f * t0) * 1.38f;
                    float z1 = (i == segs - 1) ? 0.003f : halfZ * (1.0f - 0.4f * t1) * 1.38f;
                    emitDiamondBladeSegment(vc, entry, fullbright, cx0, y0, w0, z0, cx1, y1, w1, z1, glowCol, glowCol, 92);
                }
            } else if (arch == SwordStyle.Archetype.WAVY) {
                int segs = 18;
                float bladeLen = 0.96f * lScale;
                float halfW = 0.052f * wScale;
                float halfZ = 0.022f;
                float amp = 0.036f;
                for (int i = 0; i < segs; i++) {
                    float t0 = (float) i / segs;
                    float t1 = (float) (i + 1) / segs;
                    float y0 = 0.13f + t0 * bladeLen;
                    float y1 = 0.13f + t1 * bladeLen;
                    float cx0 = (float) Math.sin(t0 * Math.PI * 5.0) * amp * (1.0f - 0.3f * t0);
                    float cx1 = (float) Math.sin(t1 * Math.PI * 5.0) * amp * (1.0f - 0.3f * t1);
                    float w0 = halfW * (1.0f - 0.25f * t0) * 1.20f;
                    float w1 = (i == segs - 1) ? 0.005f : halfW * (1.0f - 0.25f * t1) * 1.20f;
                    float z0 = halfZ * (1.0f - 0.4f * t0) * 1.40f;
                    float z1 = (i == segs - 1) ? 0.003f : halfZ * (1.0f - 0.4f * t1) * 1.40f;
                    emitDiamondBladeSegment(vc, entry, fullbright, cx0, y0, w0, z0, cx1, y1, w1, z1, glowCol, glowCol, 102);
                }
            } else if (arch == SwordStyle.Archetype.PIXEL) {
                float step = 0.075f * lScale;
                for (int i = 0; i < 11; i++) {
                    float y = 0.155f + i * step;
                    float w = ((i >= 9) ? (0.075f - (i - 8) * 0.022f) : 0.075f) * 1.15f;
                    emitBox(vc, entry, fullbright, -w, w, y, y + step, -0.042f, 0.042f, glowCol, 95);
                }
            }
        }
    }

    private static void emitDiamondBladeSegment(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float cx0, float y0, float w0, float z0,
            float cx1, float y1, float w1, float z1,
            int bodyCol,
            int edgeCol,
            int alpha
    ) {
        int rB = (bodyCol >> 16) & 0xFF, gB = (bodyCol >> 8) & 0xFF, bB = bodyCol & 0xFF;
        int rE = (edgeCol >> 16) & 0xFF, gE = (edgeCol >> 8) & 0xFF, bE = edgeCol & 0xFF;

        quad(vc, entry, light,
                cx0, y0, z0, cx0 + w0, y0, 0.0f, cx1 + w1, y1, 0.0f, cx1, y1, z1,
                0.55f, 0.0f, 0.83f, rE, gE, bE, alpha);

        quad(vc, entry, light,
                cx0 - w0, y0, 0.0f, cx0, y0, z0, cx1, y1, z1, cx1 - w1, y1, 0.0f,
                -0.55f, 0.0f, 0.83f, rB, gB, bB, alpha);

        quad(vc, entry, light,
                cx0 + w0, y0, 0.0f, cx0, y0, -z0, cx1, y1, -z1, cx1 + w1, y1, 0.0f,
                0.55f, 0.0f, -0.83f, rE, gE, bE, alpha);

        quad(vc, entry, light,
                cx0, y0, -z0, cx0 - w0, y0, 0.0f, cx1 - w1, y1, 0.0f, cx1, y1, -z1,
                -0.55f, 0.0f, -0.83f, rB, gB, bB, alpha);
    }

    private static void emitPrismSegment(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float x0, float y0, float z0, float rx0, float rz0,
            float x1, float y1, float z1, float rx1, float rz1,
            int rgb,
            int alpha
    ) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        quad(vc, entry, light, x0 - rx0, y0, z0 + rz0, x0 + rx0, y0, z0 + rz0, x1 + rx1, y1, z1 + rz1, x1 - rx1, y1, z1 + rz1, 0, 0, 1, r, g, b, alpha);
        quad(vc, entry, light, x0 + rx0, y0, z0 - rz0, x0 - rx0, y0, z0 - rz0, x1 - rx1, y1, z1 - rz1, x1 + rx1, y1, z1 - rz1, 0, 0, -1, r, g, b, alpha);
        quad(vc, entry, light, x0 + rx0, y0, z0 + rz0, x0 + rx0, y0, z0 - rz0, x1 + rx1, y1, z1 - rz1, x1 + rx1, y1, z1 + rz1, 1, 0, 0, r, g, b, alpha);
        quad(vc, entry, light, x0 - rx0, y0, z0 - rz0, x0 - rx0, y0, z0 + rz0, x1 - rx1, y1, z1 + rz1, x1 - rx1, y1, z1 - rz1, -1, 0, 0, r, g, b, alpha);
    }

    private static void emitTiltedBar(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float x0, float y0,
            float x1, float y1,
            float halfW,
            float halfZ,
            int rgb,
            int alpha
    ) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) return;
        float px = -dy / len * halfW;
        float py = dx / len * halfW;
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;

        quad(vc, entry, light, x0 - px, y0 - py, halfZ, x0 + px, y0 + py, halfZ, x1 + px, y1 + py, halfZ, x1 - px, y1 - py, halfZ, 0, 0, 1, r, g, b, alpha);
        quad(vc, entry, light, x0 + px, y0 + py, -halfZ, x0 - px, y0 - py, -halfZ, x1 - px, y1 - py, -halfZ, x1 + px, y1 + py, -halfZ, 0, 0, -1, r, g, b, alpha);
        quad(vc, entry, light, x0 + px, y0 + py, halfZ, x0 + px, y0 + py, -halfZ, x1 + px, y1 + py, -halfZ, x1 + px, y1 + py, halfZ, px / halfW, py / halfW, 0, r, g, b, alpha);
        quad(vc, entry, light, x0 - px, y0 - py, -halfZ, x0 - px, y0 - py, halfZ, x1 - px, y1 - py, halfZ, x1 - px, y1 - py, -halfZ, -px / halfW, -py / halfW, 0, r, g, b, alpha);
    }

    private static void emitCylinderY(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float cx,
            float y0,
            float y1,
            float rBot,
            float rTop,
            int segs,
            int rgb,
            int alpha
    ) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        for (int i = 0; i < segs; i++) {
            double a0 = 2.0 * Math.PI * i / segs;
            double a1 = 2.0 * Math.PI * (i + 1) / segs;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(vc, entry, light,
                    cx + c0 * rBot, y0, s0 * rBot,
                    cx + c1 * rBot, y0, s1 * rBot,
                    cx + c1 * rTop, y1, s1 * rTop,
                    cx + c0 * rTop, y1, s0 * rTop,
                    (c0 + c1) * 0.5f, 0.0f, (s0 + s1) * 0.5f, r, g, b, alpha);
            quad(vc, entry, light,
                    cx, y1, 0.0f,
                    cx + c0 * rTop, y1, s0 * rTop,
                    cx + c1 * rTop, y1, s1 * rTop,
                    cx, y1, 0.0f,
                    0.0f, 1.0f, 0.0f, r, g, b, alpha);
            quad(vc, entry, light,
                    cx, y0, 0.0f,
                    cx + c1 * rBot, y0, s1 * rBot,
                    cx + c0 * rBot, y0, s0 * rBot,
                    cx, y0, 0.0f,
                    0.0f, -1.0f, 0.0f, r, g, b, alpha);
        }
    }

    private static void emitCylinderZ(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float cx,
            float cy,
            float z0,
            float z1,
            float radius,
            int segs,
            int rgb,
            int alpha
    ) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        for (int i = 0; i < segs; i++) {
            double a0 = 2.0 * Math.PI * i / segs;
            double a1 = 2.0 * Math.PI * (i + 1) / segs;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(vc, entry, light,
                    cx + c0 * radius, cy + s0 * radius, z0,
                    cx + c1 * radius, cy + s1 * radius, z0,
                    cx + c1 * radius, cy + s1 * radius, z1,
                    cx + c0 * radius, cy + s0 * radius, z1,
                    (c0 + c1) * 0.5f, (s0 + s1) * 0.5f, 0.0f, r, g, b, alpha);
            quad(vc, entry, light,
                    cx, cy, z1,
                    cx + c0 * radius, cy + s0 * radius, z1,
                    cx + c1 * radius, cy + s1 * radius, z1,
                    cx, cy, z1,
                    0.0f, 0.0f, 1.0f, r, g, b, alpha);
            quad(vc, entry, light,
                    cx, cy, z0,
                    cx + c1 * radius, cy + s1 * radius, z0,
                    cx + c0 * radius, cy + s0 * radius, z0,
                    cx, cy, z0,
                    0.0f, 0.0f, -1.0f, r, g, b, alpha);
        }
    }

    private static void emitGearZ(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float cx,
            float cy,
            float radius,
            float z0,
            float z1,
            int teeth,
            float spin,
            int rgb,
            int alpha
    ) {
        emitCylinderZ(vc, entry, light, cx, cy, z0, z1, radius * 0.78f, teeth * 2, rgb, alpha);
        int dark = darken(rgb, 0.78f);
        for (int i = 0; i < teeth; i++) {
            double a = spin + 2.0 * Math.PI * i / teeth;
            float tx = cx + (float) Math.cos(a) * radius * 0.92f;
            float ty = cy + (float) Math.sin(a) * radius * 0.92f;
            float sz = radius * 0.16f;
            emitBox(vc, entry, light, tx - sz, tx + sz, ty - sz, ty + sz, z0, z1, dark, alpha);
        }
    }

    private static void emitOctahedron(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float cx, float cy, float cz,
            float rx, float ry, float rz,
            int rgb,
            int alpha
    ) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float[][] eq = new float[][]{
                {cx + rx, cy, cz},
                {cx, cy, cz + rz},
                {cx - rx, cy, cz},
                {cx, cy, cz - rz}
        };
        for (int i = 0; i < 4; i++) {
            float[] p0 = eq[i];
            float[] p1 = eq[(i + 1) % 4];
            quad(vc, entry, light, cx, cy + ry, cz, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], cx, cy + ry, cz, 0.5f, 0.7f, 0.5f, r, g, b, alpha);
            quad(vc, entry, light, cx, cy - ry, cz, p1[0], p1[1], p1[2], p0[0], p0[1], p0[2], cx, cy - ry, cz, 0.5f, -0.7f, 0.5f, Math.max(0, r - 25), Math.max(0, g - 25), Math.max(0, b - 25), alpha);
        }
    }

    private static void emitBox(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float x0, float x1,
            float y0, float y1,
            float z0, float z1,
            int rgb,
            int alpha
    ) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        quad(vc, entry, light, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, r, g, b, alpha);
        quad(vc, entry, light, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, r, g, b, alpha);
        quad(vc, entry, light, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, r, g, b, alpha);
        quad(vc, entry, light, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, r, g, b, alpha);
        quad(vc, entry, light, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, r, g, b, alpha);
        quad(vc, entry, light, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, r, g, b, alpha);
    }

    private static void quad(
            VertexConsumer vc,
            MatrixStack.Entry entry,
            int light,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float nx, float ny, float nz,
            int r, int g, int b, int a
    ) {
        vc.vertex(entry, x0, y0, z0).color(r, g, b, a).texture(0.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
        vc.vertex(entry, x1, y1, z1).color(r, g, b, a).texture(1.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
        vc.vertex(entry, x2, y2, z2).color(r, g, b, a).texture(1.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
        vc.vertex(entry, x3, y3, z3).color(r, g, b, a).texture(0.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
    }

    private static int darken(int rgb, float factor) {
        int r = Math.max(0, Math.min(255, Math.round(((rgb >> 16) & 0xFF) * factor)));
        int g = Math.max(0, Math.min(255, Math.round(((rgb >> 8) & 0xFF) * factor)));
        int b = Math.max(0, Math.min(255, Math.round((rgb & 0xFF) * factor)));
        return (r << 16) | (g << 8) | b;
    }

    private static int lighten(int rgb, float factor) {
        return darken(rgb, factor);
    }

    private static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1.0f - Math.abs(((h * 6.0f) % 2.0f) - 1.0f));
        float m = v - c;
        float rf, gf, bf;
        int sector = (int) (h * 6.0f) % 6;
        switch (sector) {
            case 0 -> { rf = c; gf = x; bf = 0; }
            case 1 -> { rf = x; gf = c; bf = 0; }
            case 2 -> { rf = 0; gf = c; bf = x; }
            case 3 -> { rf = 0; gf = x; bf = c; }
            case 4 -> { rf = x; gf = 0; bf = c; }
            default -> { rf = c; gf = 0; bf = x; }
        }
        int r = Math.min(255, Math.max(0, Math.round((rf + m) * 255.0f)));
        int g = Math.min(255, Math.max(0, Math.round((gf + m) * 255.0f)));
        int b = Math.min(255, Math.max(0, Math.round((bf + m) * 255.0f)));
        return (r << 16) | (g << 8) | b;
    }
}

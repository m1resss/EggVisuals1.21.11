package delta.render;
import delta.render.RenderStateHelper;



import com.mojang.blaze3d.opengl.GlStateManager;

import com.mojang.blaze3d.systems.RenderSystem;

import static delta.core.Interface.aM_;



import net.minecraft.client.render.BufferBuilder;

import delta.render.ImmediateBufferRenderer;

import net.minecraft.client.render.Tessellator;

import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.render.VertexFormats;

import net.minecraft.client.util.math.MatrixStack;

import net.minecraft.entity.LivingEntity;

import net.minecraft.util.Identifier;

import net.minecraft.util.math.MathHelper;

import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;

import org.joml.Quaternionf;

import org.lwjgl.opengl.GL11;



import java.util.ArrayList;

import java.util.List;

import java.util.Random;



public class LightningRenderer {



    public static final Identifier BLOOM_TEXTURE = Identifier.of("delta", "pictures/bloom.png");



    public static class Point {

        public final double x, y, z;



        public Point(double x, double y, double z) {

            this.x = x;

            this.y = y;

            this.z = z;

        }

    }



    public static class Path {

        public final List<Point> points;

        public final float thicknessMultiplier;

        public final double seed;

        public final boolean isBranch;



        public Path(List<Point> points, float thicknessMultiplier, double seed, boolean isBranch) {

            this.points = points;

            this.thicknessMultiplier = thicknessMultiplier;

            this.seed = seed;

            this.isBranch = isBranch;

        }

    }



    public static class State {

        public long lastTimeNanos = 0L;

        public float animTime = 0.0f;

        public long lastMorphTime = 0L;

        public long morphStartTime = 0L;

        public List<Path> currentPaths = null;

        public List<Path> previousPaths = null;

    }



    private static float noise(int var0, int var1, int var2, int var3, float var4) {

        float var5 = hash(var0, var1, var3 * 4 + var2);

        float var6 = hash(var0, var1, (var3 + 1) * 4 + var2);

        float var7 = var4 * var4 * (3.0f - 2.0f * var4);

        return var5 + (var6 - var5) * var7;

    }



    private static float hash(int var0, int var1, int var2) {

        int var3 = var0 * 374761393 + var1 * 668265263 + var2 * 1274126177;

        var3 = (var3 ^ var3 >>> 13) * 1274126177;

        var3 ^= var3 >>> 16;

        return (var3 & 65535) / 32768.0f - 1.0f;

    }



    private static class LineSeg {

        final float x1, y1, z1;

        final float x2, y2, z2;

        final float r, g, b, a;



        LineSeg(float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {

            this.x1 = x1;

            this.y1 = y1;

            this.z1 = z1;

            this.x2 = x2;

            this.y2 = y2;

            this.z2 = z2;

            this.r = r;

            this.g = g;

            this.b = b;

            this.a = a;

        }

    }



    private static class Billboard {

        final double x, y, z;

        final float size;

        final float r, g, b, a;



        Billboard(double x, double y, double z, float size, float r, float g, float b, float a) {

            this.x = x;

            this.y = y;

            this.z = z;

            this.size = size;

            this.r = r;

            this.g = g;

            this.b = b;

            this.a = a;

        }

    }



    /**

     * Chaikin corner-cutting algorithm: smooths out harsh polygonal sawteeth

     * into soft, fluid, organic curves while keeping natural lightning flow.

     */

    public static List<Point> smoothPath(List<Point> pts, int iterations) {

        if (pts == null || pts.size() < 3) {

            return pts;

        }

        List<Point> current = pts;

        for (int iter = 0; iter < iterations; iter++) {

            List<Point> next = new ArrayList<>();

            next.add(current.get(0));

            for (int i = 0; i < current.size() - 1; i++) {

                Point p0 = current.get(i);

                Point p1 = current.get(i + 1);

                Point q = new Point(0.75 * p0.x + 0.25 * p1.x, 0.75 * p0.y + 0.25 * p1.y, 0.75 * p0.z + 0.25 * p1.z);

                Point r = new Point(0.25 * p0.x + 0.75 * p1.x, 0.25 * p0.y + 0.75 * p1.y, 0.25 * p0.z + 0.75 * p1.z);

                next.add(q);

                next.add(r);

            }

            next.add(current.get(current.size() - 1));

            current = next;

        }

        return current;

    }



    /**

     * Generates a curved, natural lightning tree branch that protrudes outwards into the air

     * like branches of a tree (not a straight stick, with organic bending segments and sub-twigs).

     */

    public static List<Path> generateTreeBranches(Point start, Vec3d travelDir, double length, double maxHeight, Random rand) {

        List<Path> branchPaths = new ArrayList<>();

        List<Point> branchPts = new ArrayList<>();

        branchPts.add(start);



        // Radial outward direction from the center vertical axis (x=0, z=0)

        double rDist = Math.sqrt(start.x * start.x + start.z * start.z);

        double radX = rDist > 0.05 ? start.x / rDist : (rand.nextDouble() * 2.0 - 1.0);

        double radZ = rDist > 0.05 ? start.z / rDist : (rand.nextDouble() * 2.0 - 1.0);



        // Natural electric fork: branches shoot forward at acute angle (~30-40 degrees) relative to parent path
        // rather than perpendicular 90-degree spikes!
        double tiltY = (rand.nextDouble() * 0.8 - 0.4);
        double flareSign = rand.nextBoolean() ? 1.0 : -1.0;
        Vec3d outVec = new Vec3d(radX, 0.0, radZ);
        Vec3d baseDir = travelDir.multiply(0.68).add(outVec.multiply(0.32 * flareSign)).add(0, tiltY, 0).normalize();

        // 4 to 6 bending segments for a non-straight natural branch
        int segCount = 4 + rand.nextInt(3);
        double segLen = length / segCount;
        Point curr = start;
        Vec3d curDir = baseDir;

        Point subTwigAnchor = null;
        Vec3d subTwigDir = null;

        for (int s = 0; s < segCount; s++) {
            // Organic direction bend at each node (curving softly like natural discharge forks)
            double defX = (rand.nextDouble() * 2.0 - 1.0) * 0.16;
            double defY = (rand.nextDouble() * 2.0 - 1.0) * 0.14;
            double defZ = (rand.nextDouble() * 2.0 - 1.0) * 0.16;
            curDir = curDir.add(defX, defY, defZ).normalize();

            double nextX = curr.x + curDir.x * segLen;
            double nextY = Math.max(0.04, Math.min(maxHeight, curr.y + curDir.y * segLen));
            double nextZ = curr.z + curDir.z * segLen;
            Point nextPt = new Point(nextX, nextY, nextZ);
            branchPts.add(nextPt);

            // Potential secondary sub-twig splitting off around the middle
            if (s == 2 && rand.nextFloat() < 0.55f) {
                subTwigAnchor = nextPt;
                double sideSign = rand.nextBoolean() ? 1.0 : -1.0;
                double sideX = -curDir.z * sideSign;
                double sideZ = curDir.x * sideSign;
                // Forward-angled sub-twig fork (~30 degrees), never orthogonal 90 degrees
                subTwigDir = curDir.multiply(0.72).add(sideX * 0.38, (rand.nextDouble() - 0.5) * 0.25, sideZ * 0.38).normalize();
            }

            curr = nextPt;
        }

        // Apply 2 passes of smoothing so the branch corners are soft, fluid, and natural
        List<Point> smoothedBranch = smoothPath(branchPts, 2);
        branchPaths.add(new Path(smoothedBranch, 0.70f, rand.nextDouble() * 100.0, true));

        // Sub-twig splitting off to the side (smaller twig)
        if (subTwigAnchor != null && subTwigDir != null) {
            List<Point> twigPts = new ArrayList<>();
            twigPts.add(subTwigAnchor);
            double twigLen = length * (0.35 + rand.nextDouble() * 0.25);
            int twigSegs = 3;
            double twigSegLen = twigLen / twigSegs;
            Point twigCurr = subTwigAnchor;
            Vec3d twigD = subTwigDir;
            for (int ts = 0; ts < twigSegs; ts++) {
                twigD = twigD.add((rand.nextDouble() - 0.5) * 0.18, (rand.nextDouble() - 0.5) * 0.18, (rand.nextDouble() - 0.5) * 0.18).normalize();
                Point nextTwig = new Point(
                        twigCurr.x + twigD.x * twigSegLen,
                        Math.max(0.04, Math.min(maxHeight, twigCurr.y + twigD.y * twigSegLen)),
                        twigCurr.z + twigD.z * twigSegLen
                );
                twigPts.add(nextTwig);
                twigCurr = nextTwig;
            }
            branchPaths.add(new Path(smoothPath(twigPts, 2), 0.50f, rand.nextDouble() * 100.0, true));
        }



        return branchPaths;

    }



    /**

     * Generates an electric bolt with steep diagonal angles, soft corners,

     * randomized close distance (not flush), and strictly capped height (max 0.1 above head).

     */

    public static List<Path> generateWrappingTorsoBolt(double targetHeight, Random rand, int boltIndex, double sweepFactor) {

        List<Path> paths = new ArrayList<>();



        // Maximum allowed height: at most 0.1 block above head, and only rarely (12% chance)

        double headTop = targetHeight;

        double maxAllowedY = headTop + (rand.nextFloat() < 0.12f ? (0.05 + rand.nextDouble() * 0.05) : 0.0);



        double yStart, yEnd;

        int mode = (boltIndex + rand.nextInt(4)) % 4;



        if (mode == 0) {

            // Steep diagonal slash downward: from shoulder/neck down across chest to opposite hip/leg

            yStart = targetHeight * (0.75 + rand.nextDouble() * 0.18);

            yEnd = targetHeight * (0.12 + rand.nextDouble() * 0.22);

        } else if (mode == 1) {

            // Steep diagonal slash upward: from thigh/waist diagonally up across torso to shoulder

            yStart = targetHeight * (0.15 + rand.nextDouble() * 0.20);

            yEnd = targetHeight * (0.72 + rand.nextDouble() * 0.20);

        } else if (mode == 2) {

            // High-to-mid diagonal traversal

            yStart = targetHeight * (0.65 + rand.nextDouble() * 0.25);

            yEnd = targetHeight * (0.28 + rand.nextDouble() * 0.25);

        } else {

            // Mid-to-ground diagonal traversal

            yStart = targetHeight * (0.45 + rand.nextDouble() * 0.30);

            yEnd = targetHeight * (0.06 + rand.nextDouble() * 0.18);

        }



        // Make sure start and end do not exceed maxAllowedY

        yStart = Math.min(maxAllowedY, yStart);

        yEnd = Math.min(maxAllowedY, yEnd);



        double dir = (boltIndex % 2 == 0) ? 1.0 : -1.0;

        double startAngle = rand.nextDouble() * Math.PI * 2.0;



        // Steep diagonal angle: sweep is significantly shorter (only 0.35 to 0.70 of a full turn)

        // so the bolt cuts vertically and diagonally across the body rather than wrapping in horizontal rings!

        double totalSweep = (0.40 + rand.nextDouble() * 0.38) * sweepFactor * Math.PI * 2.0 * dir;



        int numWaypoints = Math.max(5, (int) Math.round((12 + rand.nextInt(5)) * sweepFactor));

        List<Point> waypoints = new ArrayList<>();



        double yWaveAmp = targetHeight * (0.04 + rand.nextDouble() * 0.06) * Math.min(1.0, sweepFactor * 1.5);

        double phaseY = rand.nextDouble() * Math.PI * 2.0;

        double freqY = 1.0 + rand.nextDouble() * 1.0;



        // Randomized close radius: between 0.34m and 0.48m (close to player, but not flush / не впритык)

        double boltBaseRadius = 0.35 + rand.nextDouble() * 0.13;



        for (int i = 0; i <= numWaypoints; i++) {

            double t = (double) i / (double) numWaypoints;

            double theta = startAngle + (totalSweep * t);



            double baseY = yStart + (yEnd - yStart) * t * sweepFactor + (yWaveAmp * Math.sin((t * Math.PI * freqY) + phaseY));



            // Radius with slight side/arm adaptation and subtle organic breathing

            double sideFactor = Math.pow(Math.abs(Math.sin(theta)), 1.2);

            double baseRadius = boltBaseRadius + (0.07 * sideFactor) + (rand.nextDouble() * 0.06 - 0.03);



            // Gentle soft lateral deflection

            double jX = (rand.nextDouble() * 2.0 - 1.0) * 0.045;

            double jY = (rand.nextDouble() * 2.0 - 1.0) * 0.045;

            double jZ = (rand.nextDouble() * 2.0 - 1.0) * 0.045;



            double px = Math.cos(theta) * baseRadius + jX;

            // Strictly cap py: min 0.04 (just above ground), max maxAllowedY (at most 0.1 block above head)

            double py = Math.max(0.04, Math.min(maxAllowedY, baseY + jY));

            double pz = Math.sin(theta) * baseRadius + jZ;



            waypoints.add(new Point(px, py, pz));

        }



        // Apply Chaikin smoothing (2 passes) to guarantee soft, fluid, rounded corners

        List<Point> smoothed = smoothPath(waypoints, 2);



        // Subtle electric micro-ripple for crisp electric texture without sharp kinks

        List<Point> electricPoints = new ArrayList<>();

        double ripple = 0.018;

        for (Point sp : smoothed) {

            double rx = (rand.nextDouble() * 2.0 - 1.0) * ripple;

            double ry = (rand.nextDouble() * 2.0 - 1.0) * ripple;

            double rz = (rand.nextDouble() * 2.0 - 1.0) * ripple;

            double finalY = Math.max(0.04, Math.min(maxAllowedY, sp.y + ry));

            electricPoints.add(new Point(sp.x + rx, finalY, sp.z + rz));

        }



        // Add main bolt path

        paths.add(new Path(electricPoints, 0.95f, rand.nextDouble() * 100.0, false));



        // Generate prominent tree-like branches protruding outwards

        if (sweepFactor >= 0.30 && electricPoints.size() >= 5) {

            int branchCount = 1 + (rand.nextFloat() < 0.50f ? 1 : 0);

            for (int b = 0; b < branchCount; b++) {

                int anchorIdx = (electricPoints.size() / (branchCount + 1)) * (b + 1) + rand.nextInt(3) - 1;

                anchorIdx = MathHelper.clamp(anchorIdx, 1, electricPoints.size() - 2);

                Point anchor = electricPoints.get(anchorIdx);

                Point next = electricPoints.get(anchorIdx + 1);

                Vec3d travelDir = new Vec3d(next.x - anchor.x, next.y - anchor.y, next.z - anchor.z).normalize();



                // Branch extends 0.28m to 0.48m outwards into the air

                double branchLen = 0.28 + rand.nextDouble() * 0.20;

                paths.addAll(generateTreeBranches(anchor, travelDir, branchLen, maxAllowedY, rand));

            }

        }



        return paths;

    }



    public static List<Path> generateAllPaths(float count, double targetHeight) {

        List<Path> list = new ArrayList<>();

        Random rand = new Random();

        float clampedCount = Math.max(0.1f, Math.min(count, 5.0f));



        if (clampedCount < 1.0f) {

            list.addAll(generateWrappingTorsoBolt(targetHeight, rand, 0, clampedCount));

        } else {

            int fullBolts = (int) Math.floor(clampedCount);

            float remainder = clampedCount - fullBolts;



            for (int i = 0; i < fullBolts; i++) {

                list.addAll(generateWrappingTorsoBolt(targetHeight, rand, i, 1.0));

            }

            if (remainder >= 0.08f) {

                list.addAll(generateWrappingTorsoBolt(targetHeight, rand, fullBolts, remainder));

            }

        }

        return list;

    }



    public static void render(MatrixStack stack, State state, Vec3d targetPos, double targetHeight,

                              float alpha, float changeSpeedSec, float count, float fadeDurationSec,

                              float glowPower, boolean enableBloom, int colorInt) {

        render(stack, state, targetPos, targetHeight, null, alpha, changeSpeedSec, count, fadeDurationSec, glowPower, enableBloom, colorInt);

    }



    public static void render(MatrixStack stack, State state, Vec3d targetPos, double targetHeight,

                              LivingEntity target, float alpha, float changeSpeedSec, float count,

                              float fadeDurationSec, float glowPower, boolean enableBloom, int colorInt) {

        if (alpha <= 0.001f || aM_.getEntityRenderDispatcher() == null || aM_.gameRenderer == null) {

            return;

        }



        long nowNanos = System.nanoTime();

        if (state.lastTimeNanos == 0L || (nowNanos - state.lastTimeNanos) > 500_000_000L) {

            state.lastTimeNanos = nowNanos;

        }

        float deltaSec = Math.min((float) (nowNanos - state.lastTimeNanos) / 1.0E9f, 0.05f);

        state.lastTimeNanos = nowNanos;

        state.animTime += deltaSec;



        float cycleSpeed = 1.3f * (0.5f / Math.max(0.08f, changeSpeedSec));

        int boltCount = Math.max(1, (int) Math.ceil(count));



        // Hit reddening effect: when target is hurt, color turns violently red

        float hurtFactor = 0.0f;

        if (target != null && target.hurtTime > 0) {

            hurtFactor = MathHelper.clamp((float) target.hurtTime / 10.0f, 0.0f, 1.0f);

        }



        int[] rgb = ColorUtil.b(colorInt);

        float baseGlowR = rgb[0] / 255.0f;

        float baseGlowG = rgb[1] / 255.0f;

        float baseGlowB = rgb[2] / 255.0f;



        // Transition glow color to searing electric crimson on hit

        float redT = hurtFactor * 0.95f;

        float glowR = MathHelper.lerp(redT, baseGlowR, 1.0f);

        float glowG = MathHelper.lerp(redT, baseGlowG, 0.08f);

        float glowB = MathHelper.lerp(redT, baseGlowB, 0.08f);



        // Transition core color to blazing red-hot core on hit

        float coreR = 1.0f;

        float coreG = 1.0f - (hurtFactor * 0.75f);

        float coreB = 1.0f - (hurtFactor * 0.75f);



        List<LineSeg> coreLines = new ArrayList<>();

        List<LineSeg> glowLines = new ArrayList<>();

        List<Billboard> billboards = new ArrayList<>();



        float actualGlowPower = Math.max(0.0f, Math.min(glowPower, 5.0f));



        float speedRate = state.animTime * 12.0f;

        int tick = (int) speedRate;

        float frac = speedRate - tick;



        float fadeDurationNorm = MathHelper.clamp(fadeDurationSec * 1.5f, 0.10f, 0.55f);

        float sustainEnd = 1.0f - fadeDurationNorm;

        long nowMillis = System.currentTimeMillis();



        // Sadnes-style independent phased cycles per bolt:

        // Continuous, fluid organic lightning strokes instead of batch jumps

        for (int i = 0; i < boltCount; i++) {

            float cycle = state.animTime * cycleSpeed + (float) i / (float) boltCount;

            int cycleFloor = (int) Math.floor(cycle);

            float cycleFrac = cycle - cycleFloor;



            // Envelope from Sadnes: swift strike fade-in, sustain with micro-flicker, smooth discharge fade-out

            float fadeIn = MathHelper.clamp(cycleFrac / 0.08f, 0.0f, 1.0f);

            float fadeOut = 1.0f - MathHelper.clamp((cycleFrac - sustainEnd) / fadeDurationNorm, 0.0f, 1.0f);

            float intensityNoise = MathHelper.clamp(0.60f + 0.40f * noise(i, 900, 0, tick, frac), 0.0f, 1.0f);

            float boltAlpha = alpha * fadeIn * fadeOut * intensityNoise;



            if (boltAlpha <= 0.005f) {

                continue;

            }



            Random rand = new Random(((long) i * 73856093L) ^ ((long) cycleFloor * 19349663L) ^ 0x85EBCA6BL);

            float sweepFactor = (i < (int) count) ? 1.0f : Math.max(0.2f, count - (int) count);

            List<Path> paths = generateWrappingTorsoBolt(targetHeight, rand, i, sweepFactor);



            collectGeometry(paths, targetPos, nowMillis, boltAlpha, actualGlowPower,

                    coreR, coreG, coreB, glowR, glowG, glowB, enableBloom, coreLines, glowLines, billboards);

        }



        if (coreLines.isEmpty() && glowLines.isEmpty() && billboards.isEmpty()) {

            return;

        }



        Vec3d cam = aM_.getEntityRenderDispatcher().camera.getCameraPos();

        Matrix4f matrix = stack.peek().getPositionMatrix();



        // 1. Pass: Soft neon atmospheric bloom billboards

        if (!billboards.isEmpty()) {

            RenderStateHelper.enableBlend();

            RenderStateHelper.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);

            RenderStateHelper.disableCull();

            RenderStateHelper.disableDepthTest();

            RenderStateHelper.depthMask(false);

            RenderStateHelper.setShaderTexture(0, BLOOM_TEXTURE);

            // RenderSystem.setShader(...);

            Quaternionf camRot = aM_.gameRenderer.getCamera().getRotation();



            BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            for (Billboard b : billboards) {

                float half = b.size * 0.5f;

                stack.push();

                stack.translate((float) (b.x - cam.x), (float) (b.y - cam.y), (float) (b.z - cam.z));

                stack.multiply(camRot);

                Matrix4f m = stack.peek().getPositionMatrix();



                bb.vertex(m, -half, -half, 0.0f).texture(0.0f, 0.0f).color(b.r, b.g, b.b, b.a);

                bb.vertex(m, -half, half, 0.0f).texture(0.0f, 1.0f).color(b.r, b.g, b.b, b.a);

                bb.vertex(m, half, half, 0.0f).texture(1.0f, 1.0f).color(b.r, b.g, b.b, b.a);

                bb.vertex(m, half, -half, 0.0f).texture(1.0f, 0.0f).color(b.r, b.g, b.b, b.a);

                stack.pop();

            }

            ImmediateBufferRenderer.draw(bb.end());

            RenderStateHelper.setShaderTexture(0, 0);

        }



        // 2. Pass: Colored electric glow sheath lines (Additive blending)

        if (!glowLines.isEmpty()) {

            RenderStateHelper.enableBlend();

            RenderStateHelper.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);

            RenderStateHelper.disableCull();

            RenderStateHelper.disableDepthTest();

            RenderStateHelper.depthMask(false);



            GL11.glEnable(GL11.GL_LINE_SMOOTH);

            // RenderSystem.setShader(...);

            RenderStateHelper.lineWidth(1.5f);



            BufferBuilder gb = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

            for (LineSeg s : glowLines) {

                gb.vertex(matrix, (float) (s.x1 - cam.x), (float) (s.y1 - cam.y), (float) (s.z1 - cam.z)).color(s.r, s.g, s.b, s.a);

                gb.vertex(matrix, (float) (s.x2 - cam.x), (float) (s.y2 - cam.y), (float) (s.z2 - cam.z)).color(s.r, s.g, s.b, s.a);

            }

            ImmediateBufferRenderer.draw(gb.end());

            GL11.glDisable(GL11.GL_LINE_SMOOTH);

        }



        // 3. Pass: Searing Pure Core Line (Crisp, prominent electric core)

        if (!coreLines.isEmpty()) {

            RenderStateHelper.enableBlend();

            RenderStateHelper.defaultBlendFunc();

            RenderStateHelper.disableCull();

            RenderStateHelper.disableDepthTest();

            RenderStateHelper.depthMask(false);



            GL11.glEnable(GL11.GL_LINE_SMOOTH);

            // RenderSystem.setShader(...);

            RenderStateHelper.lineWidth(1.5f);



            BufferBuilder cb = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

            for (LineSeg s : coreLines) {

                cb.vertex(matrix, (float) (s.x1 - cam.x), (float) (s.y1 - cam.y), (float) (s.z1 - cam.z)).color(s.r, s.g, s.b, s.a);

                cb.vertex(matrix, (float) (s.x2 - cam.x), (float) (s.y2 - cam.y), (float) (s.z2 - cam.z)).color(s.r, s.g, s.b, s.a);

            }

            ImmediateBufferRenderer.draw(cb.end());

            GL11.glDisable(GL11.GL_LINE_SMOOTH);

        }



        // Restore OpenGL State cleanly

        RenderStateHelper.defaultBlendFunc();

        RenderStateHelper.depthMask(true);

        RenderStateHelper.enableDepthTest();

        RenderStateHelper.enableCull();

        RenderStateHelper.disableBlend();

    }



    private static void collectGeometry(List<Path> paths, Vec3d targetPos, long now, float pathAlpha,

                                        float glowPower, float coreR, float coreG, float coreB,

                                        float glowR, float glowG, float glowB, boolean enableBloom,

                                        List<LineSeg> coreLines, List<LineSeg> glowLines, List<Billboard> billboards) {

        if (pathAlpha <= 0.001f) {

            return;

        }



        double time = (now % 1000000L) / 1000.0;

        float actualGlowPower = Math.max(0.0f, Math.min(glowPower, 5.0f));



        for (Path path : paths) {

            List<Point> pts = path.points;

            if (pts.size() < 2) {

                continue;

            }



            float pathThick = path.thicknessMultiplier;

            float gap = 0.0012f * pathThick;

            double pulse = 1.0 + 0.03 * Math.sin(time * 8.0 + path.seed);



            for (int i = 0; i < pts.size() - 1; i++) {

                Point p1 = pts.get(i);

                Point p2 = pts.get(i + 1);



                double jx1 = Math.sin(time * 24.0 + i * 2.1 + path.seed) * 0.012;

                double jy1 = Math.cos(time * 20.0 + i * 1.7 + path.seed) * 0.010;

                double jz1 = Math.sin(time * 22.0 + i * 2.7 + path.seed) * 0.012;



                double jx2 = Math.sin(time * 24.0 + (i + 1) * 2.1 + path.seed) * 0.012;

                double jy2 = Math.cos(time * 20.0 + (i + 1) * 1.7 + path.seed) * 0.010;

                double jz2 = Math.sin(time * 22.0 + (i + 1) * 2.7 + path.seed) * 0.012;



                double x1 = targetPos.x + (p1.x * pulse) + jx1;

                double y1 = targetPos.y + p1.y + jy1;

                double z1 = targetPos.z + (p1.z * pulse) + jz1;



                double x2 = targetPos.x + (p2.x * pulse) + jx2;

                double y2 = targetPos.y + p2.y + jy2;

                double z2 = targetPos.z + (p2.z * pulse) + jz2;



                // 1. Core Line: Searing prominent core line (with hurt-induced color)

                coreLines.add(new LineSeg(

                        (float) x1, (float) y1, (float) z1,

                        (float) x2, (float) y2, (float) z2,

                        coreR, coreG, coreB, Math.min(1.0f, pathAlpha)

                ));



                // 2. Normal offsets for multi-layer glowing sheath

                double dx = x2 - x1;

                double dy = y2 - y1;

                double dz = z2 - z1;

                double lenSq = dx * dx + dy * dy + dz * dz;



                double px1, py1, pz1;

                if (lenSq > 0.00001) {

                    px1 = -dz;

                    py1 = 0.0;

                    pz1 = dx;

                    double pLen1 = Math.sqrt(px1 * px1 + pz1 * pz1);

                    if (pLen1 > 0.0001) {

                        px1 = (px1 / pLen1) * gap;

                        pz1 = (pz1 / pLen1) * gap;

                    } else {

                        px1 = gap;

                        pz1 = 0.0;

                    }

                } else {

                    px1 = gap;

                    py1 = 0.0;

                    pz1 = 0.0;

                }



                // Twin offset core line for thickness and daytime visibility

                coreLines.add(new LineSeg(

                        (float) (x1 + px1 * 0.4), (float) (y1 + py1 * 0.4), (float) (z1 + pz1 * 0.4),

                        (float) (x2 + px1 * 0.4), (float) (y2 + py1 * 0.4), (float) (z2 + pz1 * 0.4),

                        coreR, coreG, coreB, Math.min(1.0f, pathAlpha * 0.85f)

                ));



                // Glowing sheath lines

                float alpha1 = Math.min(0.85f, pathAlpha * 0.55f * (actualGlowPower * 0.45f));

                glowLines.add(new LineSeg(

                        (float) (x1 + px1), (float) (y1 + py1), (float) (z1 + pz1),

                        (float) (x2 + px1), (float) (y2 + py1), (float) (z2 + pz1),

                        glowR, glowG, glowB, alpha1

                ));

                glowLines.add(new LineSeg(

                        (float) (x1 - px1), (float) (y1 - py1), (float) (z1 - pz1),

                        (float) (x2 - px1), (float) (y2 - py1), (float) (z2 - pz1),

                        glowR, glowG, glowB, alpha1

                ));



                // Wide glow lines for volumetric electric neon sheath

                glowLines.add(new LineSeg(

                        (float) (x1 + px1 * 2.2), (float) (y1 + py1 * 2.2), (float) (z1 + pz1 * 2.2),

                        (float) (x2 + px1 * 2.2), (float) (y2 + py1 * 2.2), (float) (z2 + pz1 * 2.2),

                        glowR, glowG, glowB, alpha1 * 0.5f

                ));

                glowLines.add(new LineSeg(

                        (float) (x1 - px1 * 2.2), (float) (y1 - py1 * 2.2), (float) (z1 - pz1 * 2.2),

                        (float) (x2 - px1 * 2.2), (float) (y2 - py1 * 2.2), (float) (z2 - pz1 * 2.2),

                        glowR, glowG, glowB, alpha1 * 0.5f

                ));



                // Volumetric atmospheric bloom billboards

                if (enableBloom) {

                    double midX = (x1 + x2) * 0.5;

                    double midY = (y1 + y2) * 0.5;

                    double midZ = (z1 + z2) * 0.5;



                    float outerSize = (0.50f + 0.10f * actualGlowPower) * pathThick;

                    float outerAlpha = Math.min(0.38f, pathAlpha * 0.20f * (actualGlowPower / 3.0f));

                    billboards.add(new Billboard(midX, midY, midZ, outerSize, glowR, glowG, glowB, outerAlpha));



                    float innerSize = (0.24f + 0.05f * actualGlowPower) * pathThick;

                    float innerAlpha = Math.min(0.55f, pathAlpha * 0.32f * (actualGlowPower / 3.0f));

                    billboards.add(new Billboard(midX, midY, midZ, innerSize, glowR, glowG, glowB, innerAlpha));



                    if (i % 2 == 0) {

                        billboards.add(new Billboard(x1, y1, z1, outerSize * 0.90f, glowR, glowG, glowB, outerAlpha * 0.85f));

                    }

                }

            }

        }

    }

}


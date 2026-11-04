package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Custom Dynamic Skybox.
 * Procedural gradient night sky with multi-layer Aurora Borealis waves,
 * glowing cosmic nebulae, and soft twinkling star clusters.
 * Zero resource packs, zero chunk updates, zero FPS impact.
 */
public class CustomSkybox extends Module {

    private final BooleanSetting nightGradient = new BooleanSetting("Градиент ночи", true, "Глубокий процедурный градиент ночного небосвода");
    private final BooleanSetting aurora = new BooleanSetting("Северное сияние", true, "Многослойные волны Aurora Borealis на севере");
    private final SliderSetting auroraSpeed = new SliderSetting("Скорость сияния", 1.0f, 0.2f, 3.0f, 0.1f, "Скорость переливания и колыхания волн сияния");
    private final SliderSetting auroraIntensity = new SliderSetting("Яркость сияния", 1.0f, 0.2f, 2.0f, 0.05f, "Интенсивность неонового свечения волн");
    private final BooleanSetting nebulae = new BooleanSetting("Туманности", true, "Космические туманности с мягким объемным свечением");
    private final SliderSetting nebulaBrightness = new SliderSetting("Яркость туманностей", 0.85f, 0.2f, 2.0f, 0.05f, "Степень свечения газовых облаков");
    private final BooleanSetting stars = new BooleanSetting("Звёздные скопления", true, "Звёздные скопления и пояс Млечного пути");
    private final BooleanSetting starTwinkle = new BooleanSetting("Мерцание звёзд", true, "Индивидуальное мерцание и переливание звезд");
    private final SliderSetting starDensity = new SliderSetting("Плотность звёзд", 1.0f, 0.4f, 2.0f, 0.1f, "Множитель количества видимых звезд");
    private final BooleanSetting nightOnly = new BooleanSetting("Только ночью", true, "Плавное появление на закате и затухание на рассвете");

    private static final int TOTAL_STARS = 360;
    private final List<Star> starCatalog = new ArrayList<>(TOTAL_STARS);

    private static final float SKY_RADIUS = 84.0f;
    private static final float GRADIENT_RADIUS = 88.0f;

    public CustomSkybox() {
        super("Custom Skybox", "Процедурный динамический скайбокс с северным сиянием, туманностями и звездами", Category.ESP);
        addSettings(
                nightGradient,
                aurora,
                auroraSpeed,
                auroraIntensity,
                nebulae,
                nebulaBrightness,
                stars,
                starTwinkle,
                starDensity,
                nightOnly
        );
        initStars();
    }

    private void initStars() {
        Random rand = new Random(0x5CE7E);

        // 1. Milky Way Belt (~150 stars clustered in a tilted band)
        for (int i = 0; i < 150; i++) {
            float u = rand.nextFloat() * (float) Math.PI * 2.0f;
            float beltOffset = (float) (rand.nextGaussian() * 0.14);
            float x = MathHelper.cos(u);
            float y = beltOffset;
            float z = MathHelper.sin(u);

            // Tilt the galactic belt by ~36 degrees around X axis
            float tilt = (float) Math.toRadians(36.0);
            float tiltedY = y * MathHelper.cos(tilt) - z * MathHelper.sin(tilt);
            float tiltedZ = y * MathHelper.sin(tilt) + z * MathHelper.cos(tilt);

            // Push to upper hemisphere if below horizon
            if (tiltedY < 0.05f) tiltedY = Math.abs(tiltedY) + 0.08f;

            Vector3f dir = new Vector3f(x, tiltedY, tiltedZ).normalize();
            addStar(dir, rand, true);
        }

        // 2. Open Clusters (Pleiades & Hyades inspired, ~60 stars)
        Vector3f[] clusterCenters = new Vector3f[]{
                new Vector3f(0.55f, 0.65f, -0.52f).normalize(),
                new Vector3f(-0.62f, 0.58f, 0.53f).normalize(),
                new Vector3f(-0.10f, 0.78f, -0.62f).normalize()
        };

        for (Vector3f center : clusterCenters) {
            for (int i = 0; i < 20; i++) {
                float ox = (float) (rand.nextGaussian() * 0.065);
                float oy = (float) (rand.nextGaussian() * 0.065);
                float oz = (float) (rand.nextGaussian() * 0.065);
                Vector3f dir = new Vector3f(center.x + ox, center.y + oy, center.z + oz).normalize();
                if (dir.y < 0.08f) dir.y = 0.08f + rand.nextFloat() * 0.1f;
                addStar(dir.normalize(), rand, false);
            }
        }

        // 3. Ambient Celestial Sphere (~150 uniformly distributed stars)
        for (int i = 0; i < 150; i++) {
            float azimuth = rand.nextFloat() * (float) Math.PI * 2.0f;
            float elevation = 0.06f + rand.nextFloat() * 0.94f; // Upper dome
            float phi = elevation * (float) (Math.PI * 0.5);
            float x = MathHelper.cos(azimuth) * MathHelper.cos(phi);
            float y = MathHelper.sin(phi);
            float z = MathHelper.sin(azimuth) * MathHelper.cos(phi);
            Vector3f dir = new Vector3f(x, y, z).normalize();
            addStar(dir, rand, false);
        }
    }

    private void addStar(Vector3f dir, Random rand, boolean isMilkyWay) {
        float size = 0.28f + rand.nextFloat() * 0.35f;
        boolean supergiant = !isMilkyWay && rand.nextFloat() < 0.08f;
        if (supergiant) size = 0.85f + rand.nextFloat() * 0.40f;

        float brightness = 0.55f + rand.nextFloat() * 0.45f;
        float twinkleSpeed = 1.6f + rand.nextFloat() * 2.8f;
        float twinklePhase = rand.nextFloat() * (float) Math.PI * 2.0f;

        // Stellar Spectral Classes (Colors)
        int r, g, b;
        float colChoice = rand.nextFloat();
        if (colChoice < 0.35f) {
            // Hot Blue-White (Class O/B)
            r = 195; g = 225; b = 255;
        } else if (colChoice < 0.70f) {
            // Pure Diamond White (Class A)
            r = 245; g = 250; b = 255;
        } else if (colChoice < 0.88f) {
            // Golden Sun (Class G)
            r = 255; g = 238; b = 185;
        } else if (colChoice < 0.95f) {
            // Soft Rose-Amber (Class M)
            r = 255; g = 195; b = 165;
        } else {
            // Violet Mystic
            r = 220; g = 180; b = 255;
        }

        starCatalog.add(new Star(dir, size, brightness, twinkleSpeed, twinklePhase, r, g, b, supergiant));
    }

    public void render(VertexConsumerProvider immediate, Vec3d camPos, float tickDelta) {
        if (!isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        if (mc.world.getRegistryKey() != World.OVERWORLD) return;

        float nightFactor = 1.0f;
        if (this.nightOnly.get()) {
            long timeOfDay = (mc.world.getTimeOfDay() % 24000L + 24000L) % 24000L;
            float currentTime = (float) timeOfDay + tickDelta;
            if (currentTime >= 12200.0f && currentTime <= 23800.0f) {
                if (currentTime < 13400.0f) {
                    nightFactor = (currentTime - 12200.0f) / 1200.0f;
                } else if (currentTime > 22600.0f) {
                    nightFactor = (23800.0f - currentTime) / 1200.0f;
                } else {
                    nightFactor = 1.0f;
                }
            } else {
                nightFactor = 0.0f;
            }

            // Smooth attenuation during heavy storm/rain
            float rain = mc.world.getRainGradient(tickDelta);
            if (rain > 0.01f) {
                nightFactor *= Math.max(0.0f, 1.0f - rain * 0.70f);
            }
        }

        if (nightFactor <= 0.005f) return;

        long now = System.currentTimeMillis();
        float time = (float) ((now % 10_000_000L) * 0.001);

        // 1. Procedural Night Gradient Dome
        if (this.nightGradient.get()) {
            renderGradientDome(immediate, nightFactor);
        }

        // 2. Glowing Cosmic Nebulae
        if (this.nebulae.get()) {
            renderNebulae(immediate, time, nightFactor);
        }

        // 3. Multi-layer Aurora Borealis Waves
        if (this.aurora.get()) {
            renderAurora(immediate, time, nightFactor);
        }

        // 4. Soft Twinkling Star Clusters & Milky Way
        if (this.stars.get()) {
            renderStars(immediate, time, nightFactor);
        }
    }

    private void renderGradientDome(VertexConsumerProvider immediate, float nightFactor) {
        VertexConsumer buf = immediate.getBuffer(RenderLayers.debugQuads());

        float[] latAngles = { 0.0f, 16.0f, 35.0f, 58.0f, 85.0f };
        int[] latAlphas = {
                (int) (130 * nightFactor),
                (int) (165 * nightFactor),
                (int) (195 * nightFactor),
                (int) (225 * nightFactor),
                (int) (245 * nightFactor)
        };
        // Gradient color progression: deep horizon twilight -> velvety black zenith
        int[][] latColors = {
                { 8, 12, 28 },   // Horizon navy
                { 5, 8, 20 },    // Deep indigo
                { 4, 5, 14 },    // Dark cosmic blue
                { 2, 3, 9 },     // Deep space purple-black
                { 1, 1, 5 }      // Zenith void
        };

        int lonSteps = 20;
        float lonStepRad = (float) (Math.PI * 2.0 / lonSteps);

        for (int i = 0; i < latAngles.length - 1; i++) {
            float phi0 = (float) Math.toRadians(latAngles[i]);
            float phi1 = (float) Math.toRadians(latAngles[i + 1]);

            float cos0 = MathHelper.cos(phi0) * GRADIENT_RADIUS;
            float sin0 = MathHelper.sin(phi0) * GRADIENT_RADIUS;
            float cos1 = MathHelper.cos(phi1) * GRADIENT_RADIUS;
            float sin1 = MathHelper.sin(phi1) * GRADIENT_RADIUS;

            int[] c0 = latColors[i];
            int a0 = latAlphas[i];
            int[] c1 = latColors[i + 1];
            int a1 = latAlphas[i + 1];

            for (int j = 0; j < lonSteps; j++) {
                float theta0 = j * lonStepRad;
                float theta1 = (j + 1) * lonStepRad;

                float x00 = MathHelper.cos(theta0) * cos0;
                float z00 = MathHelper.sin(theta0) * cos0;
                float x01 = MathHelper.cos(theta1) * cos0;
                float z01 = MathHelper.sin(theta1) * cos0;

                float x10 = MathHelper.cos(theta0) * cos1;
                float z10 = MathHelper.sin(theta0) * cos1;
                float x11 = MathHelper.cos(theta1) * cos1;
                float z11 = MathHelper.sin(theta1) * cos1;

                buf.vertex(x00, sin0, z00).color(c0[0], c0[1], c0[2], a0);
                buf.vertex(x01, sin0, z01).color(c0[0], c0[1], c0[2], a0);
                buf.vertex(x11, sin1, z11).color(c1[0], c1[1], c1[2], a1);
                buf.vertex(x10, sin1, z10).color(c1[0], c1[1], c1[2], a1);
            }
        }
    }

    private void renderNebulae(VertexConsumerProvider immediate, float time, float nightFactor) {
        VertexConsumer buf = immediate.getBuffer(RenderLayers.lightning());
        float brightness = (float) this.nebulaBrightness.get() * nightFactor;

        // Nebula 1: The Northern Cosmic Veil (North-East, Magenta & Indigo)
        drawNebulaComplex(
                buf,
                new Vector3f(0.52f, 0.68f, -0.51f).normalize(),
                18.0f,
                time * 0.04f,
                brightness * 0.95f,
                217, 70, 239,  // Magenta
                99, 102, 241,  // Indigo
                0xCAFE1
        );

        // Nebula 2: Emerald Lagoon Nebula (Zenith/South-West, Cyan & Emerald)
        drawNebulaComplex(
                buf,
                new Vector3f(-0.58f, 0.72f, 0.38f).normalize(),
                22.0f,
                -time * 0.035f + 1.2f,
                brightness * 0.88f,
                6, 182, 212,   // Electric Cyan
                16, 185, 129,  // Emerald
                0xBEEF2
        );

        // Nebula 3: Orion's Flame (South-East, Rose & Deep Violet)
        drawNebulaComplex(
                buf,
                new Vector3f(0.42f, 0.48f, 0.77f).normalize(),
                16.0f,
                time * 0.05f + 2.5f,
                brightness * 0.82f,
                244, 63, 94,   // Warm Rose
                139, 92, 246,  // Deep Violet
                0xFEED3
        );
    }

    private void drawNebulaComplex(
            VertexConsumer buf,
            Vector3f dir,
            float radiusSpan,
            float rotAngle,
            float intensity,
            int r1, int g1, int b1,
            int r2, int g2, int b2,
            int seed
    ) {
        Vector3f centerPos = new Vector3f(dir).mul(SKY_RADIUS * 0.95f);

        // Orthonormal basis for billboarding perpendicular to line of sight
        Vector3f forward = new Vector3f(dir).normalize();
        Vector3f upApprox = Math.abs(forward.y) < 0.95f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f right = new Vector3f(forward).cross(upApprox).normalize();
        Vector3f up = new Vector3f(right).cross(forward).normalize();

        Random rand = new Random(seed);

        // Core glow
        drawSoftDiamond(buf, centerPos, right, up, radiusSpan * 0.75f, radiusSpan * 0.75f, r1, g1, b1, (int) (140 * intensity));

        // 8 orbiting soft gas lobes with breathing scale and drift
        for (int i = 0; i < 8; i++) {
            float baseAngle = (float) (i * Math.PI * 2.0 / 8.0) + rotAngle;
            float distFactor = 0.35f + rand.nextFloat() * 0.45f;
            float lobeX = MathHelper.cos(baseAngle) * radiusSpan * distFactor;
            float lobeY = MathHelper.sin(baseAngle) * radiusSpan * distFactor;

            Vector3f lobeCenter = new Vector3f(centerPos)
                    .add(new Vector3f(right).mul(lobeX))
                    .add(new Vector3f(up).mul(lobeY));

            float lobeW = radiusSpan * (0.45f + rand.nextFloat() * 0.35f);
            float lobeH = radiusSpan * (0.40f + rand.nextFloat() * 0.30f);

            // Interpolate colors between r1,g1,b1 and r2,g2,b2
            float blend = rand.nextFloat();
            int r = (int) (r1 * (1.0f - blend) + r2 * blend);
            int g = (int) (g1 * (1.0f - blend) + g2 * blend);
            int b = (int) (b1 * (1.0f - blend) + b2 * blend);
            int alpha = (int) ((80 + rand.nextInt(55)) * intensity);

            drawSoftDiamond(buf, lobeCenter, right, up, lobeW, lobeH, r, g, b, alpha);
        }
    }

    private void drawSoftDiamond(
            VertexConsumer buf,
            Vector3f center,
            Vector3f right,
            Vector3f up,
            float halfW,
            float halfH,
            int r, int g, int b,
            int alpha
    ) {
        if (alpha <= 1) return;

        // Diamond quad: Center has maximum alpha, corners have 0 alpha for soft bloom falloff
        float cx = center.x, cy = center.y, cz = center.z;

        float rx = right.x * halfW, ry = right.y * halfW, rz = right.z * halfW;
        float ux = up.x * halfH, uy = up.y * halfH, uz = up.z * halfH;

        float topX = cx + ux, topY = cy + uy, topZ = cz + uz;
        float botX = cx - ux, botY = cy - uy, botZ = cz - uz;
        float rgtX = cx + rx, rgtY = cy + ry, rgtZ = cz + rz;
        float lftX = cx - rx, lftY = cy - ry, lftZ = cz - rz;

        // Quad 1: Left -> Bottom -> Center -> Top (Left Half)
        buf.vertex(lftX, lftY, lftZ).color(r, g, b, 0);
        buf.vertex(botX, botY, botZ).color(r, g, b, 0);
        buf.vertex(cx, cy, cz).color(r, g, b, alpha);
        buf.vertex(topX, topY, topZ).color(r, g, b, 0);

        // Quad 2: Center -> Bottom -> Right -> Top (Right Half)
        buf.vertex(cx, cy, cz).color(r, g, b, alpha);
        buf.vertex(botX, botY, botZ).color(r, g, b, 0);
        buf.vertex(rgtX, rgtY, rgtZ).color(r, g, b, 0);
        buf.vertex(topX, topY, topZ).color(r, g, b, 0);
    }

    private void renderAurora(VertexConsumerProvider immediate, float time, float nightFactor) {
        VertexConsumer buf = immediate.getBuffer(RenderLayers.lightning());

        float spd = (float) this.auroraSpeed.get();
        float intensity = (float) this.auroraIntensity.get() * nightFactor;
        if (intensity <= 0.01f) return;

        // 3 Layered Curtains across the Northern celestial quadrant
        int curtains = 3;
        int segments = 42;
        float uMin = -1.25f; // ~ -72 deg
        float uMax = 1.25f;  // ~ +72 deg
        float uStep = (uMax - uMin) / segments;

        // Vertical slices from lower horizon to high altitude
        float[] baseAltitudes = { 18.0f, 32.0f, 52.0f, 74.0f };
        int[] sliceAlphas = {
                (int) (45 * intensity),   // Base emergence
                (int) (210 * intensity),  // Peak emission ribbon
                (int) (125 * intensity),  // Middle curtain wave
                0                         // Top wisp fadeout
        };

        // Curtain color bands: Neon Emerald -> Cyan/Turquoise -> Magenta fringe
        int[][] sliceColors = {
                { 0, 245, 120 },    // Neon Emerald base
                { 0, 230, 255 },    // Electric Cyan-Blue
                { 217, 70, 239 },   // Aurora Magenta
                { 168, 85, 247 }    // High ethereal Violet
        };

        for (int c = 0; c < curtains; c++) {
            float cRadius = SKY_RADIUS * (0.86f + c * 0.06f);
            float cPhase = c * 2.1f;
            float cSpeed = spd * (0.85f + c * 0.25f);
            float cCurvature = 0.90f + c * 0.15f;

            for (int i = 0; i < segments; i++) {
                float u0 = uMin + i * uStep;
                float u1 = uMin + (i + 1) * uStep;

                // Dynamic undulating waves
                float wave0 = MathHelper.sin(u0 * 3.4f + time * cSpeed * 0.4f + cPhase) * 6.5f
                        + MathHelper.cos(u0 * 7.5f - time * cSpeed * 0.25f) * 3.0f
                        + MathHelper.sin(u0 * 13.0f + time * cSpeed * 0.6f) * 1.2f;

                float wave1 = MathHelper.sin(u1 * 3.4f + time * cSpeed * 0.4f + cPhase) * 6.5f
                        + MathHelper.cos(u1 * 7.5f - time * cSpeed * 0.25f) * 3.0f
                        + MathHelper.sin(u1 * 13.0f + time * cSpeed * 0.6f) * 1.2f;

                float fold0 = MathHelper.sin(u0 * 5.2f + time * cSpeed * 0.5f) * 2.8f;
                float fold1 = MathHelper.sin(u1 * 5.2f + time * cSpeed * 0.5f) * 2.8f;

                // Shimmer pulse along the curtain
                float shimmer0 = 0.88f + 0.12f * MathHelper.sin(time * 1.4f + u0 * 4.5f);
                float shimmer1 = 0.88f + 0.12f * MathHelper.sin(time * 1.4f + u1 * 4.5f);

                for (int s = 0; s < 3; s++) {
                    float alt0A = baseAltitudes[s] + fold0;
                    float alt0B = baseAltitudes[s + 1] + fold0;
                    float alt1A = baseAltitudes[s] + fold1;
                    float alt1B = baseAltitudes[s + 1] + fold1;

                    Vector3f v00 = calculateAuroraVertex(u0, alt0A, cRadius + wave0, cCurvature);
                    Vector3f v01 = calculateAuroraVertex(u0, alt0B, cRadius + wave0, cCurvature);
                    Vector3f v10 = calculateAuroraVertex(u1, alt1A, cRadius + wave1, cCurvature);
                    Vector3f v11 = calculateAuroraVertex(u1, alt1B, cRadius + wave1, cCurvature);

                    int[] colA = sliceColors[s];
                    int[] colB = sliceColors[s + 1];

                    int a0A = Math.min(255, (int) (sliceAlphas[s] * shimmer0));
                    int a0B = Math.min(255, (int) (sliceAlphas[s + 1] * shimmer0));
                    int a1A = Math.min(255, (int) (sliceAlphas[s] * shimmer1));
                    int a1B = Math.min(255, (int) (sliceAlphas[s + 1] * shimmer1));

                    // Quad: v00 -> v10 -> v11 -> v01
                    buf.vertex(v00.x, v00.y, v00.z).color(colA[0], colA[1], colA[2], a0A);
                    buf.vertex(v10.x, v10.y, v10.z).color(colA[0], colA[1], colA[2], a1A);
                    buf.vertex(v11.x, v11.y, v11.z).color(colB[0], colB[1], colB[2], a1B);
                    buf.vertex(v01.x, v01.y, v01.z).color(colB[0], colB[1], colB[2], a0B);
                }
            }
        }
    }

    private Vector3f calculateAuroraVertex(float u, float altDegrees, float radius, float curvature) {
        float altRad = (float) Math.toRadians(altDegrees);
        float cosAlt = MathHelper.cos(altRad) * radius;
        float sinAlt = MathHelper.sin(altRad) * radius;

        // North direction is -Z. u represents East-West azimuth spread
        float x = MathHelper.sin(u) * cosAlt * curvature;
        float z = -MathHelper.cos(u) * cosAlt;
        float y = sinAlt;

        return new Vector3f(x, y, z);
    }

    private void renderStars(VertexConsumerProvider immediate, float time, float nightFactor) {
        VertexConsumer buf = immediate.getBuffer(RenderLayers.lightning());

        float density = (float) this.starDensity.get();
        boolean doTwinkle = this.starTwinkle.get();
        int starCount = Math.min(starCatalog.size(), (int) (starCatalog.size() * density));

        for (int i = 0; i < starCount; i++) {
            Star star = starCatalog.get(i);

            float tw = 1.0f;
            if (doTwinkle) {
                tw = 0.62f + 0.38f * MathHelper.sin(time * star.twinkleSpeed + star.twinklePhase);
            }

            int alpha = (int) (star.brightness * tw * 255.0f * nightFactor);
            if (alpha <= 3) continue;

            Vector3f pos = new Vector3f(star.direction).mul(SKY_RADIUS);

            // Billboarding vectors
            Vector3f forward = new Vector3f(star.direction).normalize();
            Vector3f upApprox = Math.abs(forward.y) < 0.95f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
            Vector3f right = new Vector3f(forward).cross(upApprox).normalize();
            Vector3f up = new Vector3f(right).cross(forward).normalize();

            float s = star.size * (0.85f + tw * 0.15f);

            // Emit diamond star quad
            float px = pos.x, py = pos.y, pz = pos.z;
            float rx = right.x * s, ry = right.y * s, rz = right.z * s;
            float ux = up.x * s, uy = up.y * s, uz = up.z * s;

            buf.vertex(px - rx, py - ry, pz - rz).color(star.r, star.g, star.b, 0);
            buf.vertex(px - ux, py - uy, pz - uz).color(star.r, star.g, star.b, 0);
            buf.vertex(px + rx, py + ry, pz + rz).color(star.r, star.g, star.b, alpha);
            buf.vertex(px + ux, py + uy, pz + uz).color(star.r, star.g, star.b, alpha);

            // 4-point cross diffraction flare for supergiant stars
            if (star.isSupergiant && alpha > 50) {
                float flareL = s * 2.8f;
                float flareW = s * 0.22f;

                float flrx = right.x * flareL, flry = right.y * flareL, flrz = right.z * flareL;
                float fLux = up.x * flareL, fLuy = up.y * flareL, fLuz = up.z * flareL;
                float fwrx = right.x * flareW, fwry = right.y * flareW, fwrz = right.z * flareW;
                float fwux = up.x * flareW, fwuy = up.y * flareW, fwuz = up.z * flareW;

                int flareAlpha = (int) (alpha * 0.75f);

                // Horizontal beam
                buf.vertex(px - flrx, py - flry, pz - flrz).color(star.r, star.g, star.b, 0);
                buf.vertex(px - fwux, py - fwuy, pz - fwuz).color(star.r, star.g, star.b, 0);
                buf.vertex(px + flrx, py + flry, pz + flrz).color(star.r, star.g, star.b, flareAlpha);
                buf.vertex(px + fwux, py + fwuy, pz + fwuz).color(star.r, star.g, star.b, flareAlpha);

                // Vertical beam
                buf.vertex(px - fwrx, py - fwry, pz - fwrz).color(star.r, star.g, star.b, 0);
                buf.vertex(px - fLux, py - fLuy, pz - fLuz).color(star.r, star.g, star.b, 0);
                buf.vertex(px + fwrx, py + fwry, pz + fwrz).color(star.r, star.g, star.b, flareAlpha);
                buf.vertex(px + fLux, py + fLuy, pz + fLuz).color(star.r, star.g, star.b, flareAlpha);
            }
        }
    }

    private static class Star {
        final Vector3f direction;
        final float size;
        final float brightness;
        final float twinkleSpeed;
        final float twinklePhase;
        final int r, g, b;
        final boolean isSupergiant;

        Star(Vector3f direction, float size, float brightness, float twinkleSpeed, float twinklePhase, int r, int g, int b, boolean isSupergiant) {
            this.direction = direction;
            this.size = size;
            this.brightness = brightness;
            this.twinkleSpeed = twinkleSpeed;
            this.twinklePhase = twinklePhase;
            this.r = r;
            this.g = g;
            this.b = b;
            this.isSupergiant = isSupergiant;
        }
    }
}

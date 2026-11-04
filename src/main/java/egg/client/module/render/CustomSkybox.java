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
 * Procedural low-horizon Aurora Borealis ribbons with zero z-fighting,
 * glowing cosmic nebulae, and soft twinkling star clusters.
 * Zero resource packs, zero chunk updates, zero FPS impact.
 */
public class CustomSkybox extends Module {

    private final BooleanSetting aurora = new BooleanSetting("Северное сияние", true, "Реалистичные волны Aurora Borealis над северным горизонтом");
    private final SliderSetting auroraSpeed = new SliderSetting("Скорость сияния", 1.0f, 0.2f, 3.0f, 0.1f, "Скорость переливания и колыхания волн сияния");
    private final SliderSetting auroraIntensity = new SliderSetting("Яркость сияния", 1.0f, 0.2f, 2.0f, 0.05f, "Интенсивность неонового свечения волн");
    private final SliderSetting auroraAltitude = new SliderSetting("Высота сияния", 1.0f, 0.5f, 2.0f, 0.1f, "Вертикальный подъем ленты над горизонтом");
    private final BooleanSetting nebulae = new BooleanSetting("Туманности", true, "Космические туманности с мягким объемным свечением");
    private final SliderSetting nebulaBrightness = new SliderSetting("Яркость туманностей", 0.85f, 0.2f, 2.0f, 0.05f, "Степень свечения газовых облаков");
    private final BooleanSetting stars = new BooleanSetting("Звёздные скопления", true, "Звёздные скопления и пояс Млечного пути");
    private final BooleanSetting starTwinkle = new BooleanSetting("Мерцание звёзд", true, "Индивидуальное мерцание и переливание звезд");
    private final SliderSetting starDensity = new SliderSetting("Плотность звёзд", 1.0f, 0.4f, 2.0f, 0.1f, "Множитель количества видимых звезд");
    private final BooleanSetting nightOnly = new BooleanSetting("Только ночью", true, "Плавное появление на закате и затухание на рассвете");

    private static final int TOTAL_STARS = 360;
    private final List<Star> starCatalog = new ArrayList<>(TOTAL_STARS);

    private static final float SKY_RADIUS = 84.0f;

    public CustomSkybox() {
        super("Custom Skybox", "Процедурный динамический скайбокс с северным сиянием, туманностями и звездами", Category.ESP);
        addSettings(
                aurora,
                auroraSpeed,
                auroraIntensity,
                auroraAltitude,
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

            // Tilt galactic belt by ~36 degrees around X axis
            float tilt = (float) Math.toRadians(36.0);
            float tiltedY = y * MathHelper.cos(tilt) - z * MathHelper.sin(tilt);
            float tiltedZ = y * MathHelper.sin(tilt) + z * MathHelper.cos(tilt);

            // Keep in upper dome
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

        // 1. Glowing Cosmic Nebulae (distant space: radius ~82m)
        if (this.nebulae.get()) {
            renderNebulae(immediate, time, nightFactor);
        }

        // 2. Soft Twinkling Star Clusters & Milky Way (radius ~85m)
        if (this.stars.get()) {
            renderStars(immediate, time, nightFactor);
        }

        // 3. Multi-layer Aurora Borealis Ribbons (closer northern horizon: radius 66-78m, no z-fighting)
        if (this.aurora.get()) {
            renderAurora(immediate, time, nightFactor);
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

        float cx = center.x, cy = center.y, cz = center.z;

        float rx = right.x * halfW, ry = right.y * halfW, rz = right.z * halfW;
        float ux = up.x * halfH, uy = up.y * halfH, uz = up.z * halfH;

        float topX = cx + ux, topY = cy + uy, topZ = cz + uz;
        float botX = cx - ux, botY = cy - uy, botZ = cz - uz;
        float rgtX = cx + rx, rgtY = cy + ry, rgtZ = cz + rz;
        float lftX = cx - rx, lftY = cy - ry, lftZ = cz - rz;

        // Quad 1: Left -> Bottom -> Center -> Top
        buf.vertex(lftX, lftY, lftZ).color(r, g, b, 0);
        buf.vertex(botX, botY, botZ).color(r, g, b, 0);
        buf.vertex(cx, cy, cz).color(r, g, b, alpha);
        buf.vertex(topX, topY, topZ).color(r, g, b, 0);

        // Quad 2: Center -> Bottom -> Right -> Top
        buf.vertex(cx, cy, cz).color(r, g, b, alpha);
        buf.vertex(botX, botY, botZ).color(r, g, b, 0);
        buf.vertex(rgtX, rgtY, rgtZ).color(r, g, b, 0);
        buf.vertex(topX, topY, topZ).color(r, g, b, 0);
    }

    private void renderAurora(VertexConsumerProvider immediate, float time, float nightFactor) {
        VertexConsumer buf = immediate.getBuffer(RenderLayers.lightning());

        float spd = (float) this.auroraSpeed.get();
        float intensity = (float) this.auroraIntensity.get() * nightFactor;
        float altMultiplier = (float) this.auroraAltitude.get();
        if (intensity <= 0.01f) return;

        // 2 strictly separated curtains with guaranteed physical distance gap (NO z-fighting)
        // Curtain 0 (Front): Radius ~66m, low-to-mid altitude (8° to 24°)
        // Curtain 1 (Back):  Radius ~78m, mid-to-upper altitude (16° to 32°)
        // Separation is 12 meters, max displacement is 2 meters -> impossible to intersect!
        drawAuroraCurtain(
                buf,
                time,
                spd,
                intensity * 1.05f,
                66.0f,
                altMultiplier,
                8.0f, 25.0f,
                0.0f,
                0.88f,
                1.0f
        );

        drawAuroraCurtain(
                buf,
                time,
                spd * 0.9f,
                intensity * 0.75f,
                78.0f,
                altMultiplier,
                16.0f, 33.0f,
                2.4f,
                0.95f,
                0.85f
        );
    }

    private void drawAuroraCurtain(
            VertexConsumer buf,
            float time,
            float spd,
            float intensity,
            float baseRadius,
            float altScale,
            float minAlt,
            float maxAlt,
            float phaseOffset,
            float curvature,
            float rayScale
    ) {
        int segments = 64; // High horizontal resolution for silky-smooth waves
        int slices = 4;    // 4 vertical altitude bands (5 vertex rows)

        float uMin = -1.35f; // ~ -77 degrees azimuth
        float uMax = 1.35f;  // ~ +77 degrees azimuth
        float uStep = (uMax - uMin) / segments;

        // Altitudes scaled by user setting
        float scaledMinAlt = minAlt * altScale;
        float scaledMaxAlt = maxAlt * altScale;
        float altStep = (scaledMaxAlt - scaledMinAlt) / slices;

        // Colors per slice (from bottom to top):
        // 0: Soft fade-in emerald (alpha=0 at base)
        // 1: Neon emerald / bright green (#00FF88)
        // 2: Turquoise / cyan (#00E5FF)
        // 3: Aurora magenta / violet (#D946EF)
        // 4: Top fade-out violet (#A855F7, alpha=0)
        int[][] sliceColors = {
                { 0, 240, 110 },
                { 0, 255, 160 },
                { 0, 225, 255 },
                { 217, 70, 239 },
                { 168, 85, 247 }
        };

        float[] baseAlphas = {
                0.0f,   // Soft bottom edge (no hard line against landscape)
                1.0f,   // Maximum core emerald luminescence
                0.75f,  // Vibrant cyan middle ribbon
                0.45f,  // Magenta upper fringe
                0.0f    // Soft top edge (smoothly dissolves into night sky)
        };

        // Grid storage: [segment][slice]
        Vector3f[][] grid = new Vector3f[segments + 1][slices + 1];
        int[][] alphaGrid = new int[segments + 1][slices + 1];

        // 1. Calculate all vertices into grid (ensures adjacent quads share IDENTICAL points)
        for (int i = 0; i <= segments; i++) {
            float u = uMin + i * uStep;

            // Gentle harmonic horizontal wave (max displacement ~ 2.2m)
            float wave = MathHelper.sin(u * 2.6f + time * spd * 0.40f + phaseOffset) * 1.8f
                    + MathHelper.cos(u * 5.2f - time * spd * 0.25f + phaseOffset * 0.5f) * 0.9f;

            // Smooth vertical undulation (max ~ 1.2 degrees)
            float fold = MathHelper.sin(u * 3.2f + time * spd * 0.35f + phaseOffset) * 1.2f;

            // Ray brightness variation (creates luminous vertical pillars of light)
            float ray1 = MathHelper.sin(u * 14.0f + time * spd * 0.8f + phaseOffset);
            float ray2 = MathHelper.cos(u * 28.0f - time * spd * 0.5f);
            float ray = 0.65f + 0.35f * (ray1 * 0.6f + ray2 * 0.4f);
            ray = Math.max(0.20f, ray * ray) * rayScale;

            // Edge fade at the extreme ends of the ribbon (smooth azimuth fadeout)
            float edgeDist = Math.abs(u) / uMax;
            float edgeFade = edgeDist > 0.70f ? Math.max(0.0f, (1.0f - edgeDist) / 0.30f) : 1.0f;

            float curRadius = baseRadius + wave;

            for (int s = 0; s <= slices; s++) {
                float altDeg = scaledMinAlt + s * altStep + fold;
                grid[i][s] = calculateAuroraVertex(u, altDeg, curRadius, curvature);

                float a = baseAlphas[s] * intensity * ray * edgeFade;
                alphaGrid[i][s] = Math.min(255, (int) (a * 220.0f));
            }
        }

        // 2. Emit quads connecting shared grid vertices
        for (int i = 0; i < segments; i++) {
            for (int s = 0; s < slices; s++) {
                Vector3f v00 = grid[i][s];
                Vector3f v10 = grid[i + 1][s];
                Vector3f v11 = grid[i + 1][s + 1];
                Vector3f v01 = grid[i][s + 1];

                int a00 = alphaGrid[i][s];
                int a10 = alphaGrid[i + 1][s];
                int a11 = alphaGrid[i + 1][s + 1];
                int a01 = alphaGrid[i][s + 1];

                // If entire quad is invisible, skip
                if (a00 == 0 && a10 == 0 && a11 == 0 && a01 == 0) continue;

                int[] col0 = sliceColors[s];
                int[] col1 = sliceColors[s + 1];

                buf.vertex(v00.x, v00.y, v00.z).color(col0[0], col0[1], col0[2], a00);
                buf.vertex(v10.x, v10.y, v10.z).color(col0[0], col0[1], col0[2], a10);
                buf.vertex(v11.x, v11.y, v11.z).color(col1[0], col1[1], col1[2], a11);
                buf.vertex(v01.x, v01.y, v01.z).color(col1[0], col1[1], col1[2], a01);
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
                buf.vertex(px + fwrx, py + fwry, pz + fwuz).color(star.r, star.g, star.b, flareAlpha);
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

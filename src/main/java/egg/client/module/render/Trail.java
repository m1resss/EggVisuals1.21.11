package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.ModuleManager;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.ui.theme.Theme;
import egg.client.util.AudioMeter;
import egg.client.util.ColorUtil;
import egg.client.util.MediaTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class Trail extends Module {
    private static Trail instance;

    private final ModeSetting colorMode = new ModeSetting("Режим цвета", "Тема", "Источник цвета нитей",
            "Тема", "Кастомный", "Радуга", "Обложка трека");
    private final ColorSetting customColor = new ColorSetting("Цвет нитей", 0xFF00E5FF, "Пользовательский цвет нитей");
    private final SliderSetting length = new SliderSetting("Длина нитей", 26.0, 8.0, 60.0, 1.0, "Количество звеньев в истории нити");
    private final SliderSetting thickness = new SliderSetting("Толщина", 1.0, 0.3, 3.0, 0.1, "Толщина светящихся нитей");
    private final SliderSetting glow = new SliderSetting("Яркость свечения", 1.5, 0.3, 3.0, 0.1, "Интенсивность неонового свечения и блума");
    private final SliderSetting waveDynamics = new SliderSetting("Плавность / Волна", 1.0, 0.0, 2.5, 0.1, "Сила шелкового волнового колыхания нитей");
    private final ModeSetting targetMode = new ModeSetting("Отображение", "Только я", "Для кого рендерить нити",
            "Только я", "Все игроки");
    private final BooleanSetting audioPulse = new BooleanSetting("Музыкальный пульс", true, "Пульсация нитей в такт музыке");
    private final BooleanSetting firstPerson = new BooleanSetting("Вид от 1 лица", true, "Отображать нити в режиме от первого лица");

    private final Map<UUID, EntityTrailState> trailStates = new HashMap<>();
    private float trackColorR = -1.0f;
    private float trackColorG = -1.0f;
    private float trackColorB = -1.0f;
    private long lastFrameNanos = 0L;
    private float simTimeSec = 0.0f;

    private static final AnchorDef[] ANCHORS = new AnchorDef[] {
            // 1: Head Left & Right
            new AnchorDef(-0.16f, 0.13f, 0.94f, true, 0.0f),
            new AnchorDef(0.16f, 0.13f, 0.94f, true, 0.5f),
            // 2: Shoulder Left & Right
            new AnchorDef(-0.28f, 0.14f, 0.76f, false, 1.0f),
            new AnchorDef(0.28f, 0.14f, 0.76f, false, 1.5f),
            // 3: Waist Left & Right (level of belly / waist)
            new AnchorDef(-0.22f, 0.15f, 0.50f, false, 2.0f),
            new AnchorDef(0.22f, 0.15f, 0.50f, false, 2.5f),
            // 4: Knees Left & Right
            new AnchorDef(-0.17f, 0.12f, 0.25f, false, 3.0f),
            new AnchorDef(0.17f, 0.12f, 0.25f, false, 3.5f)
    };

    public Trail() {
        super("Trail", "Шелковые светящиеся нити за спиной игрока", Category.ESP);
        instance = this;

        customColor.setVisible(() -> "Кастомный".equals(colorMode.get()));

        addSettings(colorMode, customColor, length, thickness, glow, waveDynamics, targetMode, audioPulse, firstPerson);
    }

    public static Trail getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        this.trailStates.clear();
        this.lastFrameNanos = 0L;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client.world == null) {
            this.trailStates.clear();
            return;
        }

        // Clean up entities no longer in the world
        Set<UUID> currentUuids = new HashSet<>();
        if ("Только я".equals(targetMode.get())) {
            if (client.player != null) currentUuids.add(client.player.getUuid());
        } else {
            for (PlayerEntity player : client.world.getPlayers()) {
                currentUuids.add(player.getUuid());
            }
        }
        this.trailStates.keySet().removeIf(uuid -> !currentUuids.contains(uuid));
    }

    public void render(MatrixStack matrices, Camera camera, float tickDelta, VertexConsumerProvider.Immediate immediate, Vec3d camPos) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        long nowNanos = System.nanoTime();
        float dt = (this.lastFrameNanos == 0L) ? 0.016f : MathHelper.clamp((float) ((nowNanos - this.lastFrameNanos) * 1e-9), 0.001f, 0.05f);
        this.lastFrameNanos = nowNanos;
        this.simTimeSec += dt;

        long nowMs = System.currentTimeMillis();
        int maxPoints = (int) this.length.get();
        float thickScale = (float) this.thickness.get();
        float glowIntensity = (float) this.glow.get();
        float waveStrength = (float) this.waveDynamics.get();

        // 1. Audio and music track reactivity
        float audioPeak = 0.0f;
        if (this.audioPulse.isEnabled()) {
            audioPeak = AudioMeter.getPeak();
        }
        float audioGlowScale = 1.0f + audioPeak * 0.45f;
        float audioWaveScale = 1.0f + audioPeak * 0.35f;

        // 2. Base color calculations and smooth track cover transitions
        int baseColor = Theme.getAccent();
        String cMode = this.colorMode.get();
        if ("Кастомный".equals(cMode)) {
            baseColor = this.customColor.get();
        } else if ("Обложка трека".equals(cMode)) {
            Interface iface = ModuleManager.getInstance().getModule(Interface.class);
            int targetTrackCol = Theme.getAccent();
            if (iface != null) {
                iface.ensureMediaUpdated();
                if (iface.hasCoverArt()) {
                    targetTrackCol = iface.getDominantAccentColor();
                } else {
                    targetTrackCol = Interface.enhanceAccentVibrancy(
                            (targetTrackCol >> 16) & 0xFF,
                            (targetTrackCol >> 8) & 0xFF,
                            targetTrackCol & 0xFF
                    );
                }
            }
            float trR = ((targetTrackCol >> 16) & 0xFF) / 255.0f;
            float trG = ((targetTrackCol >> 8) & 0xFF) / 255.0f;
            float trB = (targetTrackCol & 0xFF) / 255.0f;

            if (this.trackColorR < 0.0f) {
                this.trackColorR = trR;
                this.trackColorG = trG;
                this.trackColorB = trB;
            } else {
                float cLerp = Math.min(1.0f, dt * 3.5f);
                this.trackColorR = MathHelper.lerp(cLerp, this.trackColorR, trR);
                this.trackColorG = MathHelper.lerp(cLerp, this.trackColorG, trG);
                this.trackColorB = MathHelper.lerp(cLerp, this.trackColorB, trB);
            }
            baseColor = ColorUtil.rgb((int) (this.trackColorR * 255.0f), (int) (this.trackColorG * 255.0f), (int) (this.trackColorB * 255.0f));
        }

        // 3. Acquire render buffers
        // PASS 1: Emissive Additive Volumetric Bloom Halo (RenderLayers.lightning)
        VertexConsumer glowBuffer = immediate.getBuffer(RenderLayers.lightning());
        // PASS 2: Hyper-Luminous Laser Filament Core (RenderLayers.debugQuads)
        VertexConsumer coreBuffer = immediate.getBuffer(RenderLayers.debugQuads());

        // 4. Collect players to render
        List<PlayerEntity> playersToRender = new ArrayList<>();
        if ("Только я".equals(this.targetMode.get())) {
            playersToRender.add(mc.player);
        } else {
            playersToRender.addAll(mc.world.getPlayers());
        }

        boolean isFirstPerson = mc.options.getPerspective().isFirstPerson();

        for (PlayerEntity player : playersToRender) {
            if (!player.isAlive()) continue;
            boolean isLocal = (player == mc.player);
            if (isLocal && isFirstPerson && !this.firstPerson.isEnabled()) {
                continue;
            }

            EntityTrailState state = this.trailStates.computeIfAbsent(player.getUuid(), u -> new EntityTrailState());

            // Render all 8 symmetrical strands
            for (int sIdx = 0; sIdx < 8; sIdx++) {
                AnchorDef def = ANCHORS[sIdx];
                Vec3d anchorPos = computeAnchorPos(player, def, tickDelta);

                Strand strand = state.strands[sIdx];
                strand.update(anchorPos, maxPoints, nowMs);

                List<CurveSample> samples = strand.generateCurveSamples(maxPoints, waveStrength * audioWaveScale, this.simTimeSec, def.phaseOffset);
                if (samples.size() < 2) continue;

                renderVolumetricStrand(glowBuffer, coreBuffer, samples, camPos, cMode, baseColor,
                        sIdx, this.simTimeSec, thickScale, glowIntensity * audioGlowScale);
            }
        }
    }

    private void renderVolumetricStrand(VertexConsumer glowBuf, VertexConsumer coreBuf,
                                       List<CurveSample> samples, Vec3d camPos,
                                       String cMode, int baseColor, int strandIdx,
                                       float timeSec, float thickScale, float glowScale) {
        int count = samples.size();
        if (count < 2) return;

        float baseBloomWidth = 0.052f * thickScale;
        float baseCoreWidth = 0.009f * thickScale;

        // 1. Precalculate continuous RibbonNode for every curve vertex
        RibbonNode[] nodes = new RibbonNode[count];

        for (int i = 0; i < count; i++) {
            CurveSample cur = samples.get(i);

            // World coords relative to camera
            float cx = (float) (cur.x - camPos.x);
            float cy = (float) (cur.y - camPos.y);
            float cz = (float) (cur.z - camPos.z);

            // Smooth central-difference tangent at node i
            float tx, ty, tz;
            if (i == 0) {
                CurveSample next = samples.get(1);
                tx = (float) (next.x - cur.x);
                ty = (float) (next.y - cur.y);
                tz = (float) (next.z - cur.z);
            } else if (i == count - 1) {
                CurveSample prev = samples.get(count - 2);
                tx = (float) (cur.x - prev.x);
                ty = (float) (cur.y - prev.y);
                tz = (float) (cur.z - prev.z);
            } else {
                CurveSample prev = samples.get(i - 1);
                CurveSample next = samples.get(i + 1);
                tx = (float) (next.x - prev.x);
                ty = (float) (next.y - prev.y);
                tz = (float) (next.z - prev.z);
            }

            float tLen = MathHelper.sqrt(tx * tx + ty * ty + tz * tz);
            if (tLen > 1.0e-5f) {
                tx /= tLen; ty /= tLen; tz /= tLen;
            } else {
                tx = 0.0f; ty = 1.0f; tz = 0.0f;
            }

            // View vector from camera to node
            float vx = cx;
            float vy = cy;
            float vz = cz;
            float vLen = MathHelper.sqrt(vx * vx + vy * vy + vz * vz);
            if (vLen > 1.0e-5f) {
                vx /= vLen; vy /= vLen; vz /= vLen;
            }

            // Camera-facing normal: tangent cross view
            float nx = ty * vz - tz * vy;
            float ny = tz * vx - tx * vz;
            float nz = tx * vy - ty * vx;
            float nLen = MathHelper.sqrt(nx * nx + ny * ny + nz * nz);
            if (nLen > 1.0e-5f) {
                nx /= nLen; ny /= nLen; nz /= nLen;
            } else {
                nx = 0.0f; ny = 1.0f; nz = 0.0f;
            }

            // Secondary orthogonal normal: tangent cross camera normal (volumetric cross depth)
            float bx = ty * nz - tz * ny;
            float by = tz * nx - tx * nz;
            float bz = tx * ny - ty * nx;
            float bLen = MathHelper.sqrt(bx * bx + by * by + bz * bz);
            if (bLen > 1.0e-5f) {
                bx /= bLen; by /= bLen; bz /= bLen;
            } else {
                bx = 1.0f; by = 0.0f; bz = 0.0f;
            }

            float p = cur.progress;
            // Width tapering from player to tail
            float wBloom = baseBloomWidth * (1.0f - 0.75f * (p * p));
            float wCross = wBloom * 0.60f;
            float wCore = baseCoreWidth * (1.0f - 0.78f * (p * p));

            // Alpha dissipation towards tail tip
            float fade = p < 0.65f ? (1.0f - 0.12f * (p / 0.65f)) : (0.88f * (1.0f - (p - 0.65f) / 0.35f));
            fade = MathHelper.clamp(fade, 0.0f, 1.0f);

            // Color calculation
            int col = baseColor;
            if ("Радуга".equals(cMode)) {
                float hue = (timeSec * 0.35f + p * 0.75f + strandIdx * 0.125f) % 1.0f;
                col = hsvToRgb(hue, 0.88f, 1.0f);
            }

            int r = (col >> 16) & 0xFF;
            int g = (col >> 8) & 0xFF;
            int b = col & 0xFF;

            RibbonNode node = new RibbonNode();
            node.cx = cx; node.cy = cy; node.cz = cz;

            // Camera-facing left and right
            node.lx = cx - wBloom * nx;
            node.ly = cy - wBloom * ny;
            node.lz = cz - wBloom * nz;

            node.rx = cx + wBloom * nx;
            node.ry = cy + wBloom * ny;
            node.rz = cz + wBloom * nz;

            // Volumetric top and bottom fins
            node.ux = cx - wCross * bx;
            node.uy = cy - wCross * by;
            node.uz = cz - wCross * bz;

            node.dx = cx + wCross * bx;
            node.dy = cy + wCross * by;
            node.dz = cz + wCross * bz;

            // Laser core
            node.clx = cx - wCore * nx;
            node.cly = cy - wCore * ny;
            node.clz = cz - wCore * nz;

            node.crx = cx + wCore * nx;
            node.cry = cy + wCore * ny;
            node.crz = cz + wCore * nz;

            node.r = r; node.g = g; node.b = b;

            // Hot laser center
            node.coreR = (int) MathHelper.lerp(0.35f, r, 255.0f);
            node.coreG = (int) MathHelper.lerp(0.35f, g, 255.0f);
            node.coreB = (int) MathHelper.lerp(0.35f, b, 255.0f);

            node.alphaCenter = (int) MathHelper.clamp(230.0f * glowScale * fade, 0.0f, 255.0f);
            node.alphaCore = (int) MathHelper.clamp(255.0f * fade, 0.0f, 255.0f);

            nodes[i] = node;
        }

        // 2. Render seamless connected strips sharing identical node vertices
        for (int i = 0; i < count - 1; i++) {
            RibbonNode n0 = nodes[i];
            RibbonNode n1 = nodes[i + 1];

            int aC0 = n0.alphaCenter;
            int aC1 = n1.alphaCenter;

            // PASS 1A: Camera-aligned soft glow - Left Half (0 alpha at left edge -> max alpha at center)
            drawQuadDoubleSided(glowBuf,
                    n0.lx, n0.ly, n0.lz, n0.r, n0.g, n0.b, 0,
                    n0.cx, n0.cy, n0.cz, n0.r, n0.g, n0.b, aC0,
                    n1.cx, n1.cy, n1.cz, n1.r, n1.g, n1.b, aC1,
                    n1.lx, n1.ly, n1.lz, n1.r, n1.g, n1.b, 0);

            // PASS 1B: Camera-aligned soft glow - Right Half (max alpha at center -> 0 alpha at right edge)
            drawQuadDoubleSided(glowBuf,
                    n0.cx, n0.cy, n0.cz, n0.r, n0.g, n0.b, aC0,
                    n0.rx, n0.ry, n0.rz, n0.r, n0.g, n0.b, 0,
                    n1.rx, n1.ry, n1.rz, n1.r, n1.g, n1.b, 0,
                    n1.cx, n1.cy, n1.cz, n1.r, n1.g, n1.b, aC1);

            // PASS 1C: Volumetric 3D depth - Upper Fin (0 alpha at top edge -> max alpha at center)
            int aV0 = (int) (aC0 * 0.65f);
            int aV1 = (int) (aC1 * 0.65f);
            drawQuadDoubleSided(glowBuf,
                    n0.ux, n0.uy, n0.uz, n0.r, n0.g, n0.b, 0,
                    n0.cx, n0.cy, n0.cz, n0.r, n0.g, n0.b, aV0,
                    n1.cx, n1.cy, n1.cz, n1.r, n1.g, n1.b, aV1,
                    n1.ux, n1.uy, n1.uz, n1.r, n1.g, n1.b, 0);

            // PASS 1D: Volumetric 3D depth - Lower Fin (max alpha at center -> 0 alpha at bottom edge)
            drawQuadDoubleSided(glowBuf,
                    n0.cx, n0.cy, n0.cz, n0.r, n0.g, n0.b, aV0,
                    n0.dx, n0.dy, n0.dz, n0.r, n0.g, n0.b, 0,
                    n1.dx, n1.dy, n1.dz, n1.r, n1.g, n1.b, 0,
                    n1.cx, n1.cy, n1.cz, n1.r, n1.g, n1.b, aV1);

            // PASS 2: Continuous Hyper-Bright Laser Core (RenderLayers.debugQuads)
            drawQuadDoubleSided(coreBuf,
                    n0.clx, n0.cly, n0.clz, n0.coreR, n0.coreG, n0.coreB, n0.alphaCore,
                    n0.crx, n0.cry, n0.crz, n0.coreR, n0.coreG, n0.coreB, n0.alphaCore,
                    n1.crx, n1.cry, n1.crz, n1.coreR, n1.coreG, n1.coreB, n1.alphaCore,
                    n1.clx, n1.cly, n1.clz, n1.coreR, n1.coreG, n1.coreB, n1.alphaCore);
        }
    }

    private static void drawQuadDoubleSided(VertexConsumer buf,
                                           float x1, float y1, float z1, int r1, int g1, int b1, int a1,
                                           float x2, float y2, float z2, int r2, int g2, int b2, int a2,
                                           float x3, float y3, float z3, int r3, int g3, int b3, int a3,
                                           float x4, float y4, float z4, int r4, int g4, int b4, int a4) {
        if (a1 <= 0 && a2 <= 0 && a3 <= 0 && a4 <= 0) return;
        buf.vertex(x1, y1, z1).color(r1, g1, b1, a1);
        buf.vertex(x2, y2, z2).color(r2, g2, b2, a2);
        buf.vertex(x3, y3, z3).color(r3, g3, b3, a3);
        buf.vertex(x4, y4, z4).color(r4, g4, b4, a4);

        buf.vertex(x4, y4, z4).color(r4, g4, b4, a4);
        buf.vertex(x3, y3, z3).color(r3, g3, b3, a3);
        buf.vertex(x2, y2, z2).color(r2, g2, b2, a2);
        buf.vertex(x1, y1, z1).color(r1, g1, b1, a1);
    }

    private static Vec3d computeAnchorPos(PlayerEntity entity, AnchorDef def, float tickDelta) {
        double px = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
        double py = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
        double pz = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());

        if (def.isHead) {
            float headYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.lastHeadYaw, entity.headYaw);
            float pitch = entity.getPitch(tickDelta);
            double radYaw = Math.toRadians(headYaw);
            double radPitch = Math.toRadians(pitch);

            double cosYaw = Math.cos(radYaw);
            double sinYaw = Math.sin(radYaw);
            double cosPitch = Math.cos(radPitch);
            double sinPitch = Math.sin(radPitch);

            // Backward vector aligned with head pitch
            double backX = sinYaw * cosPitch;
            double backY = sinPitch;
            double backZ = -cosYaw * cosPitch;

            // Lateral right vector
            double rightX = cosYaw;
            double rightZ = sinYaw;

            // Up vector tilted with pitch
            double upX = -sinYaw * sinPitch;
            double upY = cosPitch;
            double upZ = cosYaw * sinPitch;

            double headY = py + entity.getEyeHeight(entity.getPose()) * 0.94;

            return new Vec3d(
                    px + def.latOffset * rightX + def.backOffset * backX + 0.03 * upX,
                    headY + def.backOffset * backY + 0.03 * upY,
                    pz + def.latOffset * rightZ + def.backOffset * backZ + 0.03 * upZ
            );
        } else {
            float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.lastBodyYaw, entity.bodyYaw);
            double radBody = Math.toRadians(bodyYaw);
            double cosBody = Math.cos(radBody);
            double sinBody = Math.sin(radBody);

            double rightX = cosBody;
            double rightZ = sinBody;
            double backX = sinBody;
            double backZ = -cosBody;

            double crouchOffset = entity.isSneaking() ? -0.16 : 0.0;
            double h = entity.getHeight() * def.heightRatio + crouchOffset;

            return new Vec3d(
                    px + def.latOffset * rightX + def.backOffset * backX,
                    py + h,
                    pz + def.latOffset * rightZ + def.backOffset * backZ
            );
        }
    }

    private static int hsvToRgb(float hue, float saturation, float value) {
        int h = (int) (hue * 6.0f);
        float f = hue * 6.0f - h;
        float p = value * (1.0f - saturation);
        float q = value * (1.0f - f * saturation);
        float t = value * (1.0f - (1.0f - f) * saturation);
        float r = 0, g = 0, b = 0;
        switch (h % 6) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            case 5 -> { r = value; g = p; b = q; }
        }
        return ColorUtil.rgb((int) (r * 255.0f), (int) (g * 255.0f), (int) (b * 255.0f));
    }

    private static class AnchorDef {
        final float latOffset;
        final float backOffset;
        final float heightRatio;
        final boolean isHead;
        final float phaseOffset;

        AnchorDef(float latOffset, float backOffset, float heightRatio, boolean isHead, float phaseOffset) {
            this.latOffset = latOffset;
            this.backOffset = backOffset;
            this.heightRatio = heightRatio;
            this.isHead = isHead;
            this.phaseOffset = phaseOffset;
        }
    }

    private static class EntityTrailState {
        final Strand[] strands = new Strand[8];

        EntityTrailState() {
            for (int i = 0; i < 8; i++) {
                strands[i] = new Strand();
            }
        }
    }

    private static class RibbonNode {
        float cx, cy, cz;
        float lx, ly, lz;
        float rx, ry, rz;
        float ux, uy, uz;
        float dx, dy, dz;
        float clx, cly, clz;
        float crx, cry, crz;

        int r, g, b;
        int coreR, coreG, coreB;
        int alphaCenter;
        int alphaCore;
    }

    private static class StrandPoint {
        double x, y, z;
        long time;

        StrandPoint(double x, double y, double z, long time) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.time = time;
        }
    }

    private static class CurveSample {
        final double x, y, z;
        final float progress;

        CurveSample(double x, double y, double z, float progress) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.progress = progress;
        }
    }

    private static class Strand {
        final List<StrandPoint> points = new ArrayList<>();

        void update(Vec3d anchorPos, int maxPoints, long nowMs) {
            if (points.isEmpty()) {
                points.add(new StrandPoint(anchorPos.x, anchorPos.y, anchorPos.z, nowMs));
                points.add(new StrandPoint(anchorPos.x, anchorPos.y, anchorPos.z, nowMs));
                return;
            }

            StrandPoint head = points.get(0);
            double dx = anchorPos.x - head.x;
            double dy = anchorPos.y - head.y;
            double dz = anchorPos.z - head.z;
            double distSq = dx * dx + dy * dy + dz * dz;

            // Teleport detection (> 16 blocks)
            if (distSq > 256.0) {
                points.clear();
                points.add(new StrandPoint(anchorPos.x, anchorPos.y, anchorPos.z, nowMs));
                points.add(new StrandPoint(anchorPos.x, anchorPos.y, anchorPos.z, nowMs));
                return;
            }

            if (distSq > 0.000625 || (nowMs - head.time) > 30) {
                points.add(0, new StrandPoint(anchorPos.x, anchorPos.y, anchorPos.z, nowMs));
            } else {
                head.x = anchorPos.x;
                head.y = anchorPos.y;
                head.z = anchorPos.z;
                head.time = nowMs;
            }

            while (points.size() > maxPoints) {
                points.remove(points.size() - 1);
            }

            while (points.size() > 2 && (nowMs - points.get(points.size() - 1).time) > 1700L) {
                points.remove(points.size() - 1);
            }

            // Elastic silk chain relaxation
            int size = points.size();
            for (int i = 1; i < size; i++) {
                StrandPoint prev = points.get(i - 1);
                StrandPoint curr = points.get(i);
                double sx = curr.x - prev.x;
                double sy = curr.y - prev.y;
                double sz = curr.z - prev.z;
                double slen = Math.sqrt(sx * sx + sy * sy + sz * sz);
                double maxSeg = 0.16;
                if (slen > maxSeg && slen > 1.0e-6) {
                    double f = maxSeg / slen;
                    curr.x = prev.x + sx * f;
                    curr.y = prev.y + sy * f;
                    curr.z = prev.z + sz * f;
                }
            }
        }

        List<CurveSample> generateCurveSamples(int maxPoints, float waveStrength, float timeSec, float phaseOffset) {
            List<CurveSample> samples = new ArrayList<>();
            int size = points.size();
            if (size < 2) return samples;

            int subDivs = 5;
            int totalExpected = (size - 1) * subDivs + 1;
            int curSampleIdx = 0;

            for (int i = 0; i < size - 1; i++) {
                StrandPoint p0 = (i == 0) ? points.get(0) : points.get(i - 1);
                StrandPoint p1 = points.get(i);
                StrandPoint p2 = points.get(i + 1);
                StrandPoint p3 = (i + 2 < size) ? points.get(i + 2) : points.get(i + 1);

                for (int step = 0; step < subDivs; step++) {
                    float t = (float) step / (float) subDivs;
                    float t2 = t * t;
                    float t3 = t2 * t;

                    double x = 0.5 * ((2.0 * p1.x) + (-p0.x + p2.x) * t + (2.0 * p0.x - 5.0 * p1.x + 4.0 * p2.x - p3.x) * t2 + (-p0.x + 3.0 * p1.x - 3.0 * p2.x + p3.x) * t3);
                    double y = 0.5 * ((2.0 * p1.y) + (-p0.y + p2.y) * t + (2.0 * p0.y - 5.0 * p1.y + 4.0 * p2.y - p3.y) * t2 + (-p0.y + 3.0 * p1.y - 3.0 * p2.y + p3.y) * t3);
                    double z = 0.5 * ((2.0 * p1.z) + (-p0.z + p2.z) * t + (2.0 * p0.z - 5.0 * p1.z + 4.0 * p2.z - p3.z) * t2 + (-p0.z + 3.0 * p1.z - 3.0 * p2.z + p3.z) * t3);

                    float progress = (float) curSampleIdx / (float) (totalExpected - 1);

                    // Fluid silk flutter dynamics
                    if (waveStrength > 0.01f && progress > 0.05f) {
                        float flutterAmp = waveStrength * 0.035f * MathHelper.sqrt(progress) * progress;
                        float phi = timeSec * 5.2f - progress * 7.5f + phaseOffset;
                        double wy = Math.sin(phi) * flutterAmp;
                        double wx = Math.cos(phi) * flutterAmp * 0.7;
                        double wz = Math.sin(phi + 1.2f) * flutterAmp * 0.7;
                        x += wx;
                        y += wy;
                        z += wz;
                    }

                    samples.add(new CurveSample(x, y, z, progress));
                    curSampleIdx++;
                }
            }

            // Append final point
            StrandPoint last = points.get(size - 1);
            samples.add(new CurveSample(last.x, last.y, last.z, 1.0f));
            return samples;
        }
    }
}

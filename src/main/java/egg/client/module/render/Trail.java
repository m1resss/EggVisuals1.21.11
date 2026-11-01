package egg.client.module.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.ModuleManager;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.ParticleBlurRenderer;
import egg.client.render.shader.Pipelines;
import egg.client.ui.theme.Theme;
import egg.client.util.AudioMeter;
import egg.client.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
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

        // 3. Collect players to render
        List<PlayerEntity> playersToRender = new ArrayList<>();
        if ("Только я".equals(this.targetMode.get())) {
            playersToRender.add(mc.player);
        } else {
            playersToRender.addAll(mc.world.getPlayers());
        }

        boolean isFirstPerson = mc.options.getPerspective().isFirstPerson();

        // 4. Update and generate curve samples for all players and strands
        List<List<CurveSample>> allStrands = new ArrayList<>();
        List<Integer> strandColors = new ArrayList<>();

        for (PlayerEntity player : playersToRender) {
            if (!player.isAlive()) continue;
            boolean isLocal = (player == mc.player);
            if (isLocal && isFirstPerson && !this.firstPerson.isEnabled()) {
                continue;
            }

            EntityTrailState state = this.trailStates.computeIfAbsent(player.getUuid(), u -> new EntityTrailState());

            for (int sIdx = 0; sIdx < 8; sIdx++) {
                AnchorDef def = ANCHORS[sIdx];
                Vec3d anchorPos = computeAnchorPos(player, def, tickDelta);

                Strand strand = state.strands[sIdx];
                strand.update(anchorPos, maxPoints, nowMs);

                List<CurveSample> samples = strand.generateCurveSamples(maxPoints, waveStrength * audioWaveScale, this.simTimeSec, def.phaseOffset);
                if (samples.size() < 2) continue;

                int sColor = baseColor;
                if ("Радуга".equals(cMode)) {
                    float hue = (this.simTimeSec * 0.35f + sIdx * 0.125f) % 1.0f;
                    sColor = hsvToRgb(hue, 0.88f, 1.0f);
                }

                allStrands.add(samples);
                strandColors.add(sColor);
            }
        }

        if (allStrands.isEmpty()) return;

        MatrixStack.Entry entry = matrices.peek();
        RenderPipeline pipeline = Pipelines.LIGHTNING_DEPTH;
        float effectiveGlow = glowIntensity * audioGlowScale;

        // PASS 1: Volumetric Gaussian Bloom via ParticleBlurRenderer (if glow enabled)
        if (effectiveGlow > 0.2f) {
            try (BufferAllocator allocBloom = new BufferAllocator(131072)) {
                BufferBuilder bufBloom = new BufferBuilder(allocBloom, DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);

                for (int i = 0; i < allStrands.size(); i++) {
                    List<CurveSample> samples = allStrands.get(i);
                    int col = strandColors.get(i);
                    int r = (col >> 16) & 0xFF;
                    int g = (col >> 8) & 0xFF;
                    int b = col & 0xFF;

                    buildStrandLines(bufBloom, entry, samples, camPos, r, g, b,
                            (12.0f + 16.0f * thickScale), 0.55f * effectiveGlow, 0.85f);
                }

                BuiltBuffer builtBloom = bufBloom.endNullable();
                if (builtBloom != null) {
                    float blurSigma = Math.clamp(2.5f * effectiveGlow, 1.5f, 6.0f);
                    float bloomAlpha = Math.clamp(0.85f * effectiveGlow, 0.1f, 1.0f);
                    ParticleBlurRenderer.INSTANCE.render(blurSigma, bloomAlpha, () -> MeshDrawUtil.drawLabeled(pipeline, builtBloom, "egg trail bloom"));
                }
            }
        }

        // PASS 2 & 3: Luminous Colored Halo + Razor-Sharp Laser Filament Core
        try (
                BufferAllocator allocHalo = new BufferAllocator(131072);
                BufferAllocator allocCore = new BufferAllocator(131072)
        ) {
            BufferBuilder bufHalo = new BufferBuilder(allocHalo, DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);
            BufferBuilder bufCore = new BufferBuilder(allocCore, DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);

            for (int i = 0; i < allStrands.size(); i++) {
                List<CurveSample> samples = allStrands.get(i);
                int col = strandColors.get(i);
                int r = (col >> 16) & 0xFF;
                int g = (col >> 8) & 0xFF;
                int b = col & 0xFF;

                // Luminous colored halo: width ~ 6..14px, vibrant color
                buildStrandLines(bufHalo, entry, samples, camPos, r, g, b,
                        (5.5f + 9.5f * thickScale), 0.45f * effectiveGlow, 0.70f);

                // Laser core: width ~ 1.6..3.5px, white-hot center
                int coreR = (int) MathHelper.lerp(0.70f, r, 255.0f);
                int coreG = (int) MathHelper.lerp(0.70f, g, 255.0f);
                int coreB = (int) MathHelper.lerp(0.70f, b, 255.0f);
                buildStrandLines(bufCore, entry, samples, camPos, coreR, coreG, coreB,
                        (1.6f + 2.0f * thickScale), 1.0f, 0.50f);
            }

            BuiltBuffer builtHalo = bufHalo.endNullable();
            if (builtHalo != null) {
                MeshDrawUtil.drawLabeled(pipeline, builtHalo, "egg trail halo");
            }

            BuiltBuffer builtCore = bufCore.endNullable();
            if (builtCore != null) {
                MeshDrawUtil.drawLabeled(pipeline, builtCore, "egg trail core");
            }
        }
    }

    private static void buildStrandLines(BufferBuilder builder, MatrixStack.Entry entry,
                                         List<CurveSample> samples, Vec3d camPos,
                                         int r, int g, int b,
                                         float baseLineWidth, float alphaMult, float taperAmount) {
        int count = samples.size();
        if (count < 2) return;

        for (int i = 0; i < count - 1; i++) {
            CurveSample s1 = samples.get(i);
            CurveSample s2 = samples.get(i + 1);

            float p1 = s1.progress;
            float p2 = s2.progress;

            // Tail fade (dissipation at end of ribbon)
            float fade1 = p1 < 0.65f ? 1.0f : (1.0f - (p1 - 0.65f) / 0.35f);
            float fade2 = p2 < 0.65f ? 1.0f : (1.0f - (p2 - 0.65f) / 0.35f);
            fade1 = MathHelper.clamp(fade1, 0.0f, 1.0f);
            fade2 = MathHelper.clamp(fade2, 0.0f, 1.0f);

            int a1 = MathHelper.clamp((int) (255.0f * alphaMult * fade1), 0, 255);
            int a2 = MathHelper.clamp((int) (255.0f * alphaMult * fade2), 0, 255);
            if (a1 <= 0 && a2 <= 0) continue;

            int c1 = (a1 << 24) | (r << 16) | (g << 8) | b;
            int c2 = (a2 << 24) | (r << 16) | (g << 8) | b;

            // Silk thickness tapering from player to tail
            float width = baseLineWidth * (1.0f - taperAmount * p1 * p1);
            if (width < 0.8f) width = 0.8f;

            float x1 = (float) (s1.x - camPos.x);
            float y1 = (float) (s1.y - camPos.y);
            float z1 = (float) (s1.z - camPos.z);

            float x2 = (float) (s2.x - camPos.x);
            float y2 = (float) (s2.y - camPos.y);
            float z2 = (float) (s2.z - camPos.z);

            emitLine(builder, entry, x1, y1, z1, x2, y2, z2, c1, c2, width);
        }
    }

    private static void emitLine(BufferBuilder builder, MatrixStack.Entry entry,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 int c1, int c2, float lineWidth) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-5F) return;
        float inv = 1.0F / len;
        float nx = dx * inv;
        float ny = dy * inv;
        float nz = dz * inv;
        builder.vertex(entry, x1, y1, z1).color(c1).normal(entry, nx, ny, nz).lineWidth(lineWidth);
        builder.vertex(entry, x2, y2, z2).color(c2).normal(entry, nx, ny, nz).lineWidth(lineWidth);
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

            if (distSq > 0.0004 || (nowMs - head.time) > 25) {
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

            while (points.size() > 2 && (nowMs - points.get(points.size() - 1).time) > 1800L) {
                points.remove(points.size() - 1);
            }
        }

        List<CurveSample> generateCurveSamples(int maxPoints, float waveStrength, float timeSec, float phaseOffset) {
            List<CurveSample> samples = new ArrayList<>();
            int size = points.size();
            if (size < 2) return samples;

            int subDivs = 6;
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
                    if (waveStrength > 0.01f && progress > 0.04f) {
                        float flutterAmp = waveStrength * 0.045f * progress * (float) Math.sqrt(progress);
                        float phi = timeSec * 6.2f - progress * 9.5f + phaseOffset;
                        double wy = Math.sin(phi) * flutterAmp;
                        double wx = Math.cos(phi * 0.85f) * flutterAmp * 0.65;
                        double wz = Math.sin(phi + 1.4f) * flutterAmp * 0.65;
                        x += wy * 0.4 + wx;
                        y += wy;
                        z += wz;
                    }

                    samples.add(new CurveSample(x, y, z, progress));
                    curSampleIdx++;
                }
            }

            StrandPoint last = points.get(size - 1);
            samples.add(new CurveSample(last.x, last.y, last.z, 1.0f));
            return samples;
        }
    }
}

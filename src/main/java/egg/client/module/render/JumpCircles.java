package egg.client.module.render;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.ParticleBlurRenderer;
import egg.client.render.shader.Pipelines;
import egg.client.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class JumpCircles extends Module {
    private static JumpCircles instance;

    private static final Identifier RIPPLE_TEXTURE = Identifier.of("egg", "textures/world/puddle_ripple.png");
    private static final Identifier BLOOM_TEXTURE = Identifier.of("delta", "pictures/bloom.png");
    private static final Identifier WHITE_TEXTURE = Identifier.of("delta", "textures/white.png");

    private final ColorSetting color = new ColorSetting("Цвет", 0xFF0096C7, "Цвет расходящихся волн");
    private final SliderSetting maxRadius = new SliderSetting("Радиус", 2.8, 1.0, 6.0, 0.1, "Размер расхождения волн");
    private final SliderSetting expandSpeed = new SliderSetting("Скорость", 1.1, 0.4, 3.0, 0.1, "Скорость распространения волн");
    private final SliderSetting waveRings = new SliderSetting("Количество волн", 3.0, 1.0, 4.0, 1.0, "Количество расходящихся кругов волны");
    private final SliderSetting thickness = new SliderSetting("Толщина", 0.35, 0.1, 0.8, 0.02, "Толщина линий кругов");
    private final BooleanSetting textureOverlay = new BooleanSetting("Водная рябь", true, "Реалистичная рябь на поверхности воды");
    private final BooleanSetting glow = new BooleanSetting("Свечение", true, "Мягкое неоновое свечение вокруг кругов");

    private final List<WaveCircle> waves = new ArrayList<>();
    private boolean wasOnGround = true;
    private double lastGroundY = 0.0;
    private long lastFrameNanos = 0L;

    private static class WavePalette {
        final int water;
        final int foam;
        final int crest;

        WavePalette(int water, int foam, int crest) {
            this.water = water;
            this.foam = foam;
            this.crest = crest;
        }
    }

    private static class WaveCircle {
        final double x, y, z;
        float age = 0.0f;
        final float maxLife;
        final WavePalette palette;

        WaveCircle(double x, double y, double z, float maxLife, WavePalette palette) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.maxLife = maxLife;
            this.palette = palette;
        }

        boolean update(float dt) {
            age += dt;
            return age < maxLife;
        }
    }

    public JumpCircles() {
        super("Jump Circles", "Создает реалистичные расходящиеся круги на земле при прыжке", Category.ESP);
        instance = this;

        addSettings(this.color, this.maxRadius, this.expandSpeed, this.waveRings, this.thickness, this.textureOverlay, this.glow);
    }

    public static JumpCircles getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.waves.clear();
        this.wasOnGround = true;
        this.lastGroundY = 0.0;
        this.lastFrameNanos = 0L;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client.world == null || client.player == null) {
            this.waves.clear();
            this.wasOnGround = true;
            this.lastGroundY = 0.0;
            return;
        }

        PlayerEntity player = client.player;
        if (player.isSpectator() || player.getAbilities().flying || player.isTouchingWater() || player.isClimbing()) {
            this.wasOnGround = player.isOnGround();
            this.lastGroundY = player.getY();
            return;
        }

        boolean onGround = player.isOnGround();
        double curY = player.getY();
        Vec3d vel = player.getVelocity();

        boolean jumped = this.wasOnGround && !onGround && vel.y > 0.05;

        if (jumped) {
            spawnWave(player.getX(), this.lastGroundY, player.getZ());
        }

        if (onGround) {
            this.lastGroundY = curY;
        }
        this.wasOnGround = onGround;
    }

    private void spawnWave(double x, double y, double z) {
        float life = Math.max(0.4f, 1.3f / expandSpeed.getValueFloat());
        WavePalette pal = resolvePalette();
        waves.add(new WaveCircle(x, y, z, life, pal));
    }

    private WavePalette resolvePalette() {
        int base = this.color.get();
        int foam = ColorUtil.lerp(base, 0xFFFFFFFF, 0.45f);
        int crest = ColorUtil.lerp(base, 0xFFFFFFFF, 0.80f);
        return new WavePalette(base, foam, crest);
    }

    public void renderWorld3D(MatrixStack matrices, Camera camera, float tickDelta, VertexConsumerProvider immediate, Vec3d camPos) {
        if (!isEnabled()) return;

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float rawDt = (float) ((now - lastFrameNanos) * 1e-9);
        lastFrameNanos = now;
        final float dt = (rawDt <= 0.0f || rawDt > 0.1f) ? 0.016f : rawDt;

        waves.removeIf(w -> !w.update(dt));
        if (waves.isEmpty()) return;

        float maxR = maxRadius.getValueFloat();
        float baseThick = thickness.getValueFloat();
        int ringsCount = Math.max(1, Math.min(4, Math.round(waveRings.getValueFloat())));

        if (textureOverlay.isEnabled()) {
            try (BufferAllocator alloc = new BufferAllocator(32768)) {
                BufferBuilder buf = new BufferBuilder(alloc, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                for (WaveCircle w : waves) {
                    float progress = w.age / w.maxLife;
                    float eased = 1.0f - (float) Math.pow(1.0f - progress, 2.2);
                    float r = Math.max(0.2f, eased * maxR * 1.15f);
                    float alpha = 1.0f - progress;
                    if (progress < 0.08f) alpha *= (progress / 0.08f);

                    int col = ColorUtil.withAlphaFloat(w.palette.water, alpha * 0.70f);
                    emitGroundQuad(buf, (float) (w.x - camPos.x), (float) (w.y - camPos.y + 0.012), (float) (w.z - camPos.z), r, col);
                }
                BuiltBuffer built = buf.endNullable();
                if (built != null) {
                    MeshDrawUtil.drawLabeledTextured(Pipelines.AURA_TEXTURE_DEPTH, built, "jump wave ripple sheet", RIPPLE_TEXTURE);
                }
            }
        }

        if (glow.isEnabled()) {
            try (BufferAllocator alloc = new BufferAllocator(32768)) {
                BufferBuilder buf = new BufferBuilder(alloc, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                float maxAlpha = 0.0f;
                for (WaveCircle w : waves) {
                    float progress = w.age / w.maxLife;
                    float eased = 1.0f - (float) Math.pow(1.0f - progress, 2.5);
                    float r = Math.max(0.2f, eased * maxR * 1.25f);
                    float alpha = (1.0f - progress) * 0.35f;
                    if (progress < 0.08f) alpha *= (progress / 0.08f);
                    maxAlpha = Math.max(maxAlpha, alpha);

                    int col = ColorUtil.withAlphaFloat(w.palette.foam, alpha);
                    emitGroundQuad(buf, (float) (w.x - camPos.x), (float) (w.y - camPos.y + 0.010), (float) (w.z - camPos.z), r, col);
                }
                BuiltBuffer built = buf.endNullable();
                if (built != null) {
                    float finalAlpha = maxAlpha;
                    ParticleBlurRenderer.INSTANCE.render(3.5f, finalAlpha, () ->
                            MeshDrawUtil.drawLabeledTextured(Pipelines.AURA_BLOOM_DEPTH, built, "jump wave glow", BLOOM_TEXTURE));
                }
            }
        }

        try (
                BufferAllocator allocBody = new BufferAllocator(131072);
                BufferAllocator allocSparkle = new BufferAllocator(65536)
        ) {
            BufferBuilder bufBody = new BufferBuilder(allocBody, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            BufferBuilder bufSparkle = new BufferBuilder(allocSparkle, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            for (WaveCircle w : waves) {
                float baseProg = w.age / w.maxLife;

                for (int i = 0; i < ringsCount; i++) {
                    float delay = i * 0.18f;
                    if (baseProg < delay) continue;
                    float ringProg = (baseProg - delay) / (1.0f - delay);
                    if (ringProg >= 1.0f) continue;

                    float eased = 1.0f - (float) Math.pow(1.0f - ringProg, 2.6);
                    float r = Math.max(0.12f, eased * maxR);
                    float ringAlpha = (1.0f - ringProg) * (1.0f - i * 0.22f);
                    if (ringProg < 0.08f) ringAlpha *= (ringProg / 0.08f);

                    float t = baseThick * (0.8f + 0.45f * eased);
                    float rInner = Math.max(0.02f, r - t);
                    float rCrest = r - t * 0.28f;
                    float rOuter = r;

                    float rx = (float) (w.x - camPos.x);
                    float ry = (float) (w.y - camPos.y + 0.016 + i * 0.002);
                    float rz = (float) (w.z - camPos.z);

                    int colWater = ColorUtil.withAlphaFloat(w.palette.water, ringAlpha * 0.50f);
                    int colFoam = ColorUtil.withAlphaFloat(w.palette.foam, ringAlpha * 0.88f);
                    int colCrest = ColorUtil.withAlphaFloat(w.palette.crest, ringAlpha * 0.95f);

                    emitWaveBand(bufBody, rx, ry, rz, rInner, rCrest, rOuter, colWater, colFoam, colCrest);

                    int sparkleCol = ColorUtil.withAlphaFloat(w.palette.crest, ringAlpha * 0.40f);
                    emitGroundRing(bufSparkle, rx, ry + 0.001f, rz, rOuter, t * 0.35f, sparkleCol);
                }
            }

            BuiltBuffer builtBody = bufBody.endNullable();
            if (builtBody != null) {
                MeshDrawUtil.drawLabeledTextured(Pipelines.AURA_TEXTURE_DEPTH, builtBody, "jump wave body", WHITE_TEXTURE);
            }

            BuiltBuffer builtSparkle = bufSparkle.endNullable();
            if (builtSparkle != null) {
                MeshDrawUtil.drawLabeledTextured(Pipelines.AURA_BLOOM_DEPTH, builtSparkle, "jump wave crest sparkle", BLOOM_TEXTURE);
            }
        }
    }

    private static void emitGroundQuad(BufferBuilder b, float rx, float ry, float rz, float r, int color) {
        if ((color & 0xFF000000) == 0 || r <= 0.001f) return;
        b.vertex(rx - r, ry, rz - r).texture(0.0f, 0.0f).color(color);
        b.vertex(rx - r, ry, rz + r).texture(0.0f, 1.0f).color(color);
        b.vertex(rx + r, ry, rz + r).texture(1.0f, 1.0f).color(color);
        b.vertex(rx + r, ry, rz - r).texture(1.0f, 0.0f).color(color);
    }

    private static void emitGroundRing(BufferBuilder b, float rx, float ry, float rz, float r, float t, int color) {
        if ((color & 0xFF000000) == 0 || r <= 0.001f) return;
        int segments = 48;
        float rInner = Math.max(0.02f, r - t);
        float rOuter = r;

        for (int i = 0; i < segments; i++) {
            float a0 = (float) (i * 2.0 * Math.PI / segments);
            float a1 = (float) ((i + 1) * 2.0 * Math.PI / segments);

            float c0 = MathHelper.cos(a0);
            float s0 = MathHelper.sin(a0);
            float c1 = MathHelper.cos(a1);
            float s1 = MathHelper.sin(a1);

            b.vertex(rx + c0 * rInner, ry, rz + s0 * rInner).texture(0.5f, 0.5f).color(color);
            b.vertex(rx + c0 * rOuter, ry, rz + s0 * rOuter).texture(0.5f, 1.0f).color(color);
            b.vertex(rx + c1 * rOuter, ry, rz + s1 * rOuter).texture(1.0f, 1.0f).color(color);
            b.vertex(rx + c1 * rInner, ry, rz + s1 * rInner).texture(1.0f, 0.5f).color(color);
        }
    }

    private static void emitWaveBand(BufferBuilder b, float rx, float ry, float rz, float rInner, float rCrest, float rOuter, int cWater, int cFoam, int cCrest) {
        if (rOuter <= 0.001f) return;
        int segments = 48;

        for (int i = 0; i < segments; i++) {
            float a0 = (float) (i * 2.0 * Math.PI / segments);
            float a1 = (float) ((i + 1) * 2.0 * Math.PI / segments);

            float c0 = MathHelper.cos(a0);
            float s0 = MathHelper.sin(a0);
            float c1 = MathHelper.cos(a1);
            float s1 = MathHelper.sin(a1);

            b.vertex(rx + c0 * rInner, ry, rz + s0 * rInner).texture(0.0f, 0.0f).color(cWater);
            b.vertex(rx + c0 * rCrest, ry, rz + s0 * rCrest).texture(0.5f, 0.0f).color(cFoam);
            b.vertex(rx + c1 * rCrest, ry, rz + s1 * rCrest).texture(0.5f, 1.0f).color(cFoam);
            b.vertex(rx + c1 * rInner, ry, rz + s1 * rInner).texture(0.0f, 1.0f).color(cWater);

            b.vertex(rx + c0 * rCrest, ry, rz + s0 * rCrest).texture(0.5f, 0.0f).color(cFoam);
            b.vertex(rx + c0 * rOuter, ry, rz + s0 * rOuter).texture(1.0f, 0.0f).color(cCrest);
            b.vertex(rx + c1 * rOuter, ry, rz + s1 * rOuter).texture(1.0f, 1.0f).color(cCrest);
            b.vertex(rx + c1 * rCrest, ry, rz + s1 * rCrest).texture(0.5f, 1.0f).color(cFoam);
        }
    }
}
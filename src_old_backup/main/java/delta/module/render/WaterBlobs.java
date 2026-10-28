package delta.module.render;

import com.mojang.blaze3d.systems.RenderSystem;
import delta.render.RenderStateHelper;
import static delta.core.Interface.aM_;
import delta.core.Category;
import delta.core.EventTarget;
import delta.core.Interface;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.DrawEvent;
import delta.render.SadnesMotionBlurCapture;
import delta.setting.BooleanSetting;
import delta.setting.ColorSetting;
import delta.setting.ModeSetting;
import delta.render.CustomUniform;
import delta.render.ImmediateBufferRenderer;
import delta.ui.shader.Shader;
import delta.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ModuleRegister(a = "WaterBlobs", b = "Водяные шары вокруг тебя: сливаются друг с другом и искажают мир", c = Category.Render)
public class WaterBlobs extends Module implements Interface {

    public static final Shader WATER_BLOBS_SHADER = new Shader(
            Identifier.of("delta", "core/water_blobs/water_blobs"),
            VertexFormats.POSITION
    ) {
        @Override
        protected void b() {}
        @Override
        public void uploadDefaultMatrices() {
            CustomUniform mv = a("ModelViewMat");
            if (mv != null) mv.set(new Matrix4f().identity());
            CustomUniform proj = a("ProjMat");
            if (proj != null) proj.set(new Matrix4f().identity());
        }
    };



    private final ModeSetting mode = new ModeSetting("Режим", "Шлейф", "Шлейф", "Орбита", "Шлейф и орбита");
    private final SliderSetting count = new SliderSetting("Сколько шаров", 5.0f, 1.0f, 8.0f, 1.0f);
    private final SliderSetting radius = new SliderSetting("Радиус", 0.55f, 0.2f, 1.5f, 0.05f);
    private final SliderSetting orbit = new SliderSetting("Радиус облёта", 1.9f, 0.8f, 6.0f, 0.1f);
    private final SliderSetting speed = new SliderSetting("Скорость", 0.8f, 0.1f, 3.0f, 0.1f);

    private final SliderSetting smooth = new SliderSetting("Слипание", 0.7f, 0.05f, 2.0f, 0.05f);
    private final SliderSetting refract = new SliderSetting("Преломление", 1.0f, 0.0f, 3.0f, 0.1f);
    private final SliderSetting density = new SliderSetting("Плотность цвета", 1.0f, 0.0f, 3.0f, 0.1f);
    private final ColorSetting waterColor = new ColorSetting("Цвет воды", 0xFF3C96DC); // RGB(60, 150, 220)

    private final SliderSetting quality = new SliderSetting("Качество", 56.0f, 16.0f, 96.0f, 8.0f);
    private final BooleanSetting follow = new BooleanSetting("Летать за игроком", true);
    private final BooleanSetting onlyMoving = new BooleanSetting("Только при движении", false);
    private final SliderSetting trailLifetime = new SliderSetting("Время жизни шлейфа", 2.0f, 0.5f, 5.0f, 0.1f);

    private SimpleFramebuffer captureFramebuffer;
    private final double[] anchor = new double[]{0.0, 0.0, 0.0};
    private final List<TrailBlob> trailBlobs = new ArrayList<>();

    private double lastPlayerX;
    private double lastPlayerY;
    private double lastPlayerZ;
    private boolean hasLastPlayerPos = false;
    private float distanceTraveledSinceLastSpawn = 0.0f;

    public WaterBlobs() {
        a(this.mode, this.count, this.radius, this.orbit, this.speed,
          this.smooth, this.refract, this.density, this.waterColor,
          this.quality, this.follow, this.onlyMoving, this.trailLifetime);
    }

    private static class TrailBlob {
        double x, y, z;
        double vx, vy, vz;
        float baseRadius;
        long spawnTime;
        long lifetimeMs;
        float phase;

        TrailBlob(double x, double y, double z, double vx, double vy, double vz, float baseRadius, long lifetimeMs) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.baseRadius = baseRadius;
            this.spawnTime = System.currentTimeMillis();
            this.lifetimeMs = lifetimeMs;
            this.phase = (float) (Math.random() * Math.PI * 2.0);
        }

        boolean isDead() {
            return System.currentTimeMillis() - this.spawnTime >= this.lifetimeMs;
        }

        float getCurrentRadius() {
            long age = System.currentTimeMillis() - this.spawnTime;
            if (age >= this.lifetimeMs || age < 0) return 0.0f;
            float progress = (float) age / (float) this.lifetimeMs;

            // Growth phase (pinching off player), wobble, and smooth decay
            float scale;
            if (progress < 0.2f) {
                scale = progress / 0.2f;
            } else if (progress > 0.65f) {
                scale = 1.0f - (progress - 0.65f) / 0.35f;
            } else {
                scale = 1.0f;
            }
            float wobble = 1.0f + 0.15f * (float) Math.sin(this.phase + progress * 12.0f);
            return Math.max(0.01f, this.baseRadius * scale * wobble);
        }
    }

    @Override
    public void c() {
        super.c();
        if (this.captureFramebuffer != null) {
            this.captureFramebuffer.delete();
            this.captureFramebuffer = null;
        }
        this.trailBlobs.clear();
        this.hasLastPlayerPos = false;
        this.anchor[0] = 0.0;
        this.anchor[1] = 0.0;
        this.anchor[2] = 0.0;
    }

    @EventTarget
    public void onRender3D(DrawEvent event) {
        if (!m() || !event.c()) {
            return;
        }

        MinecraftClient mc = aM_;
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }

        Framebuffer mainFb = mc.getFramebuffer();
        if (mainFb == null) {
            return;
        }

        PlayerEntity me = mc.player;
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();

        // 1. Process movement and trail generation ("баблсы которые появляются когда игрок идет кусочками")
        updateTrail(me);

        // 2. Collect blobs for the shader
        Vector4f[] blobs = new Vector4f[8];
        for (int i = 0; i < 8; i++) {
            blobs[i] = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);
        }

        int maxCount = (int) this.count.c().floatValue();
        maxCount = MathHelper.clamp(maxCount, 1, 8);
        String currentMode = this.mode.c();

        float baseRad = this.radius.c().floatValue();
        float orbitDist = this.orbit.c().floatValue();
        float clock = ((float) (System.currentTimeMillis() % 10000000L) / 1000.0f) * this.speed.c().floatValue();

        // Update anchor position for orbit
        if (this.follow.c().booleanValue()) {
            double targetX = me.getX();
            double targetY = me.getY() + 1.1;
            double targetZ = me.getZ();
            if (this.anchor[0] == 0.0 && this.anchor[1] == 0.0 && this.anchor[2] == 0.0) {
                this.anchor[0] = targetX;
                this.anchor[1] = targetY;
                this.anchor[2] = targetZ;
            } else {
                this.anchor[0] += (targetX - this.anchor[0]) * 0.12;
                this.anchor[1] += (targetY - this.anchor[1]) * 0.12;
                this.anchor[2] += (targetZ - this.anchor[2]) * 0.12;
            }
        } else if (this.anchor[0] == 0.0 && this.anchor[1] == 0.0 && this.anchor[2] == 0.0) {
            this.anchor[0] = me.getX();
            this.anchor[1] = me.getY() + 1.1;
            this.anchor[2] = me.getZ();
        }

        boolean isMoving = Math.hypot(me.getX() - this.lastPlayerX, me.getZ() - this.lastPlayerZ) > 0.01;
        if (this.onlyMoving.c().booleanValue() && !isMoving && this.trailBlobs.isEmpty()) {
            return;
        }

        int activeBlobCount = 0;
        if ("Шлейф".equals(currentMode)) {
            // Trailing bubble chunks behind player
            for (int i = this.trailBlobs.size() - 1; i >= 0 && activeBlobCount < maxCount; i--) {
                TrailBlob tb = this.trailBlobs.get(i);
                float curR = tb.getCurrentRadius();
                if (curR > 0.02f) {
                    blobs[activeBlobCount] = new Vector4f(
                            (float) (tb.x - camPos.x),
                            (float) (tb.y - camPos.y),
                            (float) (tb.z - camPos.z),
                            curR
                    );
                    activeBlobCount++;
                }
            }
        } else if ("Орбита".equals(currentMode)) {
            // Orbiting liquid spheres around player
            for (int i = 0; i < maxCount; i++) {
                float phase = clock * (0.7f + i * 0.13f) + i * 2.399f;
                float tilt = (float) Math.sin(clock * (0.5f + i * 0.09f) + i) * 0.9f;
                float span = orbitDist * (0.55f + 0.45f * (float) Math.sin(clock * 0.37f + i * 1.7f));

                double bx = this.anchor[0] + Math.cos(phase) * span;
                double by = this.anchor[1] + tilt * 0.6;
                double bz = this.anchor[2] + Math.sin(phase) * span;
                float br = baseRad * (0.75f + 0.25f * (float) Math.sin(clock * 1.3f + i));

                blobs[i] = new Vector4f(
                        (float) (bx - camPos.x),
                        (float) (by - camPos.y),
                        (float) (bz - camPos.z),
                        br
                );
                activeBlobCount++;
            }
        } else {
            // Combined: half orbit around player, half trailing pieces behind
            int orbitCount = Math.max(1, maxCount / 2);
            for (int i = 0; i < orbitCount; i++) {
                float phase = clock * (0.7f + i * 0.13f) + i * 2.399f;
                float tilt = (float) Math.sin(clock * (0.5f + i * 0.09f) + i) * 0.9f;
                float span = orbitDist * (0.55f + 0.45f * (float) Math.sin(clock * 0.37f + i * 1.7f));

                double bx = this.anchor[0] + Math.cos(phase) * span;
                double by = this.anchor[1] + tilt * 0.6;
                double bz = this.anchor[2] + Math.sin(phase) * span;
                float br = baseRad * (0.75f + 0.25f * (float) Math.sin(clock * 1.3f + i));

                blobs[activeBlobCount] = new Vector4f(
                        (float) (bx - camPos.x),
                        (float) (by - camPos.y),
                        (float) (bz - camPos.z),
                        br
                );
                activeBlobCount++;
            }
            for (int i = this.trailBlobs.size() - 1; i >= 0 && activeBlobCount < maxCount; i--) {
                TrailBlob tb = this.trailBlobs.get(i);
                float curR = tb.getCurrentRadius();
                if (curR > 0.02f) {
                    blobs[activeBlobCount] = new Vector4f(
                            (float) (tb.x - camPos.x),
                            (float) (tb.y - camPos.y),
                            (float) (tb.z - camPos.z),
                            curR
                    );
                    activeBlobCount++;
                }
            }
        }

        if (activeBlobCount == 0) {
            return;
        }

        // 3. Setup shader matrices and uniforms
        int width = Math.max(1, mainFb.textureWidth);
        int height = Math.max(1, mainFb.textureHeight);

        float fovDeg = aM_.options.getFov().getValue().floatValue();
        float fovRad = (float) Math.toRadians(fovDeg);
        float aspect = (float) width / (float) height;
        float nearPlane = 0.05f;
        float farPlane = aM_.gameRenderer.getFarPlaneDistance();

        Matrix4f projMatrix = new Matrix4f().setPerspective(fovRad, aspect, nearPlane, farPlane);
        Matrix4f viewMatrix = new Matrix4f().rotation(camera.getRotation().conjugate(new Quaternionf()));
        Matrix4f viewProj = new Matrix4f(projMatrix).mul(viewMatrix);
        Matrix4f invViewProj = new Matrix4f(viewProj).invert();

        ensureFramebuffer(width, height);

        RenderStateHelper.disableDepthTest();
        RenderStateHelper.depthMask(false);
        RenderStateHelper.disableCull();
        RenderStateHelper.disableBlend();

        // Pass 1: Blit mainFb into captureFramebuffer
        RenderStateHelper.bindFramebuffer(this.captureFramebuffer);
        RenderStateHelper.setShaderTexture(0, mainFb.getColorAttachment());
        SadnesMotionBlurCapture.BLIT_SHADER.a();
        drawFullscreenQuad();

        // Pass 2: Raymarch water blobs and refract captured world
        RenderStateHelper.bindFramebuffer(mainFb);
        RenderStateHelper.setShaderTexture(0, this.captureFramebuffer.getColorAttachment());
        RenderStateHelper.setShaderTexture(1, mainFb.getDepthAttachment());

        WATER_BLOBS_SHADER.a();
        CustomUniform uInvViewProj = WATER_BLOBS_SHADER.getUniform("InvViewProj");
        if (uInvViewProj != null) {
            uInvViewProj.set(invViewProj);
        }

        for (int i = 0; i < 8; i++) {
            CustomUniform uBlob = WATER_BLOBS_SHADER.getUniform("Blob" + i);
            if (uBlob != null) {
                Vector4f b = blobs[i];
                uBlob.set(b.x, b.y, b.z, b.w);
            }
        }

        CustomUniform uCount = WATER_BLOBS_SHADER.getUniform("Count");
        if (uCount != null) uCount.set((float) activeBlobCount);

        CustomUniform uSmooth = WATER_BLOBS_SHADER.getUniform("Smooth");
        if (uSmooth != null) uSmooth.set(this.smooth.c().floatValue());

        CustomUniform uRefract = WATER_BLOBS_SHADER.getUniform("Refract");
        if (uRefract != null) uRefract.set(this.refract.c().floatValue());

        CustomUniform uDensity = WATER_BLOBS_SHADER.getUniform("Density");
        if (uDensity != null) uDensity.set(this.density.c().floatValue());

        CustomUniform uSteps = WATER_BLOBS_SHADER.getUniform("Steps");
        if (uSteps != null) uSteps.set(this.quality.c().floatValue());

        CustomUniform uTint = WATER_BLOBS_SHADER.getUniform("Tint");
        if (uTint != null) {
            int col = this.waterColor.c().intValue();
            float r = ((col >> 16) & 0xFF) / 255.0f;
            float g = ((col >> 8) & 0xFF) / 255.0f;
            float b = (col & 0xFF) / 255.0f;
            uTint.set(r, g, b);
        }

        drawFullscreenQuad();

        RenderStateHelper.enableDepthTest();
        RenderStateHelper.depthMask(true);
        RenderStateHelper.enableCull();
        RenderStateHelper.enableBlend();

        RenderStateHelper.bindFramebuffer(mainFb);
    }

    private void updateTrail(PlayerEntity me) {
        double px = me.getX();
        double py = me.getY();
        double pz = me.getZ();

        if (!this.hasLastPlayerPos) {
            this.lastPlayerX = px;
            this.lastPlayerY = py;
            this.lastPlayerZ = pz;
            this.hasLastPlayerPos = true;
            return;
        }

        double dx = px - this.lastPlayerX;
        double dy = py - this.lastPlayerY;
        double dz = pz - this.lastPlayerZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        this.lastPlayerX = px;
        this.lastPlayerY = py;
        this.lastPlayerZ = pz;

        // When player moves, spawn droplets/bubbles behind player in clusters ("кусочками")
        if (dist > 0.02) {
            this.distanceTraveledSinceLastSpawn += (float) dist;
            if (this.distanceTraveledSinceLastSpawn >= 0.35f) {
                this.distanceTraveledSinceLastSpawn = 0.0f;
                spawnBlobCluster(px, py, pz, dx / dist, dy / dist, dz / dist);
            }
        }

        // Update active trail bubbles physics (floating, drifting, wobbling)
        Iterator<TrailBlob> it = this.trailBlobs.iterator();
        while (it.hasNext()) {
            TrailBlob b = it.next();
            if (b.isDead()) {
                it.remove();
                continue;
            }
            b.x += b.vx;
            b.y += b.vy;
            b.z += b.vz;

            // Horizontal drag and gentle settling - no upward floating!
            b.vx *= 0.94;
            b.vy *= 0.94;
            b.vz *= 0.94;
        }

        // Keep maximum trail blobs bounded
        if (this.trailBlobs.size() > 40) {
            this.trailBlobs.subList(0, this.trailBlobs.size() - 40).clear();
        }
    }

    private void spawnBlobCluster(double px, double py, double pz, double dirX, double dirY, double dirZ) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int pieces = rnd.nextInt(1, 3); // 1 to 2 droplets per step
        float baseRad = this.radius.c().floatValue();
        long lifeMs = (long) (this.trailLifetime.c().floatValue() * 1000.0f);

        for (int i = 0; i < pieces; i++) {
            // Offset strictly behind player at ground/foot height
            double backDist = 0.45 + rnd.nextDouble(0.45);
            double spreadX = (rnd.nextDouble() - 0.5) * 0.35;
            double spreadY = 0.08 + rnd.nextDouble(0.22); // low behind feet/ground
            double spreadZ = (rnd.nextDouble() - 0.5) * 0.35;

            double bx = px - dirX * backDist + spreadX;
            double by = py + spreadY;
            double bz = pz - dirZ * backDist + spreadZ;

            // Zero upward velocity so bubbles never fly up into the air
            double bvx = (rnd.nextDouble() - 0.5) * 0.008 - dirX * 0.008;
            double bvy = 0.0;
            double bvz = (rnd.nextDouble() - 0.5) * 0.008 - dirZ * 0.008;

            float r = baseRad * (0.6f + rnd.nextFloat() * 0.55f);
            long life = (long) (lifeMs * (0.8f + rnd.nextFloat() * 0.4f));

            this.trailBlobs.add(new TrailBlob(bx, by, bz, bvx, bvy, bvz, r, life));
        }
    }

    private void drawFullscreenQuad() {
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        builder.vertex(-1.0f, -1.0f, 0.0f);
        builder.vertex(-1.0f, 1.0f, 0.0f);
        builder.vertex(1.0f, 1.0f, 0.0f);
        builder.vertex(1.0f, -1.0f, 0.0f);
        ImmediateBufferRenderer.draw(builder.end());
    }

    private void ensureFramebuffer(int width, int height) {
        if (this.captureFramebuffer == null) {
            this.captureFramebuffer = new SimpleFramebuffer("delta_water_blobs", width, height, false);
        } else if (this.captureFramebuffer.textureWidth != width || this.captureFramebuffer.textureHeight != height) {
            this.captureFramebuffer.resize(width, height);
        }
    }
}

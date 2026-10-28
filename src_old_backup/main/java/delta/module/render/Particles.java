package delta.module.render;
import delta.render.RenderStateHelper;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import static delta.core.Interface.aM_;
import delta.config.ThemeInfo;
import delta.core.Category;
import delta.core.Delta;
import delta.core.EventTarget;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.AttackEvent;
import delta.event.DrawEvent;
import delta.event.PacketEvent;
import delta.event.TickEvent;
import delta.render.ColorUtil;
import delta.setting.BooleanSetting;
import delta.setting.ColorSetting;
import delta.setting.ModeSetting;
import delta.setting.SliderSetting;

import net.minecraft.client.render.BufferBuilder;
import delta.render.ImmediateBufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ModuleRegister(a = "Particles", b = "Рой частиц от событий: мир, удары, тотем, движение", c = Category.Render)
public class Particles extends Module {

    private static final Identifier BLOOM_TEXTURE = Identifier.of("delta", "pictures/bloom.png");

    private final ModeSetting shape = new ModeSetting("Форма", "Свечение", "Свечение", "Кубы", "Искры");
    private final ModeSetting colorMode = new ModeSetting("Цвет", "Клиент", "Клиент", "Кастом", "Радуга");
    private final ColorSetting customColor = new ColorSetting("Свой цвет", 0xFF8B5CF6);

    private final BooleanSetting worldGroup = new BooleanSetting("Мир", true);
    private final BooleanSetting attackGroup = new BooleanSetting("Удары", true);
    private final BooleanSetting totemGroup = new BooleanSetting("Тотем", true);
    private final BooleanSetting moveGroup = new BooleanSetting("Движение", false);

    private final SliderSetting count = new SliderSetting("Количество", 25.0f, 5.0f, 80.0f, 5.0f);
    private final SliderSetting size = new SliderSetting("Размер", 0.16f, 0.05f, 0.45f, 0.01f);
    private final SliderSetting speed = new SliderSetting("Скорость", 0.45f, 0.1f, 1.5f, 0.05f);
    private final SliderSetting lifetime = new SliderSetting("Время жизни (сек)", 1.4f, 0.4f, 4.0f, 0.1f);
    private final BooleanSetting bounce = new BooleanSetting("Отскок от блоков", true);

    private final List<Particle> particles = new ArrayList<>();
    private int tickCounter = 0;

    public Particles() {
        a(this.shape, this.colorMode, this.customColor, this.worldGroup, this.attackGroup, this.totemGroup, this.moveGroup, this.count, this.size, this.speed, this.lifetime, this.bounce);
    }

    @Override
    public void c() {
        super.c();
        this.particles.clear();
    }

    private static class Particle {
        double x, y, z;
        double prevX, prevY, prevZ;
        double vx, vy, vz;
        float pitch, yaw, roll;
        float rotSpeedX, rotSpeedY, rotSpeedZ;
        long spawnTime;
        long maxLifetimeMs;
        float baseSize;
        int color;
        int shapeType; // 0 = bloom, 1 = cube, 2 = spark

        Particle(double x, double y, double z, double vx, double vy, double vz, float baseSize, long maxLifetimeMs, int color, int shapeType) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.prevX = x;
            this.prevY = y;
            this.prevZ = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.baseSize = baseSize;
            this.maxLifetimeMs = maxLifetimeMs;
            this.spawnTime = System.currentTimeMillis();
            this.color = color;
            this.shapeType = shapeType;
            ThreadLocalRandom r = ThreadLocalRandom.current();
            this.pitch = r.nextFloat() * 360.0f;
            this.yaw = r.nextFloat() * 360.0f;
            this.roll = r.nextFloat() * 360.0f;
            this.rotSpeedX = (r.nextFloat() - 0.5f) * 12.0f;
            this.rotSpeedY = (r.nextFloat() - 0.5f) * 12.0f;
            this.rotSpeedZ = (r.nextFloat() - 0.5f) * 12.0f;
        }

        float getProgress() {
            long elapsed = System.currentTimeMillis() - this.spawnTime;
            return MathHelper.clamp((float) elapsed / (float) this.maxLifetimeMs, 0.0f, 1.0f);
        }

        boolean isDead() {
            return System.currentTimeMillis() - this.spawnTime >= this.maxLifetimeMs;
        }
    }

    private int getCurrentColor(float offset) {
        String mode = this.colorMode.c();
        if ("Кастом".equals(mode)) {
            return this.customColor.c();
        } else if ("Радуга".equals(mode)) {
            float hue = (float) ((System.currentTimeMillis() % 4000L) / 4000.0 + offset) % 1.0f;
            return java.awt.Color.HSBtoRGB(hue, 0.8f, 1.0f);
        } else {
            return Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
        }
    }

    private int getShapeTypeIndex() {
        String s = this.shape.c();
        if ("Кубы".equals(s)) return 1;
        if ("Искры".equals(s)) return 2;
        return 0; // Свечение
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.attackGroup.c().booleanValue() || event.b() == null) {
            return;
        }
        Entity target = event.b();
        Vec3d center = target.getEyePos().subtract(0.0, target.getHeight() * 0.35, 0.0);
        int spawnCount = this.count.c().intValue();
        float baseSpd = this.speed.c().floatValue();
        float pSize = this.size.c().floatValue();
        long lifeMs = (long) (this.lifetime.c().floatValue() * 1000.0f);
        int sType = getShapeTypeIndex();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        for (int i = 0; i < spawnCount; i++) {
            double angle = r.nextDouble(Math.PI * 2.0);
            double elevation = (r.nextDouble() - 0.4) * Math.PI;
            double spd = (0.2 + r.nextDouble(0.8)) * baseSpd;

            double vx = Math.cos(angle) * Math.cos(elevation) * spd;
            double vy = Math.sin(elevation) * spd + 0.05;
            double vz = Math.sin(angle) * Math.cos(elevation) * spd;

            int col = getCurrentColor((float) i / (float) spawnCount);
            this.particles.add(new Particle(
                    center.x + (r.nextDouble() - 0.5) * 0.4,
                    center.y + (r.nextDouble() - 0.5) * 0.4,
                    center.z + (r.nextDouble() - 0.5) * 0.4,
                    vx, vy, vz,
                    pSize * (0.7f + r.nextFloat() * 0.6f),
                    lifeMs + r.nextLong(300),
                    col, sType
            ));
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.totemGroup.c().booleanValue() || aM_.world == null) {
            return;
        }
        if (event.d() instanceof EntityStatusS2CPacket packet && packet.getStatus() == 35) {
            Entity entity = packet.getEntity(aM_.world);
            if (entity != null) {
                Vec3d pos = entity.getEyePos();
                float baseSpd = this.speed.c().floatValue() * 1.3f;
                float pSize = this.size.c().floatValue() * 1.2f;
                long lifeMs = (long) (this.lifetime.c().floatValue() * 1200.0f);
                int sType = getShapeTypeIndex();
                ThreadLocalRandom r = ThreadLocalRandom.current();

                for (int i = 0; i < 45; i++) {
                    double theta = r.nextDouble(Math.PI * 2.0);
                    double phi = r.nextDouble(Math.PI);
                    double spd = (0.3 + r.nextDouble(0.9)) * baseSpd;

                    double vx = Math.sin(phi) * Math.cos(theta) * spd;
                    double vy = Math.cos(phi) * spd + 0.1;
                    double vz = Math.sin(phi) * Math.sin(theta) * spd;

                    int col = (i % 2 == 0) ? 0xFFFFAA00 : 0xFF55FF55; // Classic totem gold & emerald
                    this.particles.add(new Particle(pos.x, pos.y, pos.z, vx, vy, vz, pSize, lifeMs, col, sType));
                }
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (aM_.player == null || aM_.world == null) {
            this.particles.clear();
            return;
        }
        this.tickCounter++;
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // Ambient world spawn
        if (this.worldGroup.c().booleanValue() && this.tickCounter % 2 == 0) {
            Vec3d playerPos = aM_.player.getEyePos();
            float pSize = this.size.c().floatValue() * 0.85f;
            long lifeMs = (long) (this.lifetime.c().floatValue() * 1500.0f);
            int sType = getShapeTypeIndex();

            for (int i = 0; i < 2; i++) {
                double ox = (r.nextDouble() - 0.5) * 8.0;
                double oy = (r.nextDouble() - 0.5) * 4.0;
                double oz = (r.nextDouble() - 0.5) * 8.0;

                double vx = (r.nextDouble() - 0.5) * 0.04;
                double vy = (r.nextDouble() - 0.3) * 0.05;
                double vz = (r.nextDouble() - 0.5) * 0.04;

                this.particles.add(new Particle(
                        playerPos.x + ox, playerPos.y + oy, playerPos.z + oz,
                        vx, vy, vz, pSize, lifeMs, getCurrentColor(r.nextFloat()), sType
                ));
            }
        }

        // Movement trail
        if (this.moveGroup.c().booleanValue() && aM_.player.getVelocity().horizontalLengthSquared() > 0.005) {
            Vec3d pos = aM_.player.getEntityPos();
            float pSize = this.size.c().floatValue() * 0.7f;
            long lifeMs = (long) (this.lifetime.c().floatValue() * 800.0f);
            int sType = getShapeTypeIndex();

            double ox = (r.nextDouble() - 0.5) * 0.4;
            double oz = (r.nextDouble() - 0.5) * 0.4;
            double vx = (r.nextDouble() - 0.5) * 0.03;
            double vy = 0.02 + r.nextDouble(0.04);
            double vz = (r.nextDouble() - 0.5) * 0.03;

            this.particles.add(new Particle(
                    pos.x + ox, pos.y + 0.1, pos.z + oz,
                    vx, vy, vz, pSize, lifeMs, getCurrentColor(r.nextFloat()), sType
            ));
        }

        // Update physics
        boolean canBounce = this.bounce.c().booleanValue();
        Iterator<Particle> it = this.particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            if (p.isDead()) {
                it.remove();
                continue;
            }

            p.prevX = p.x;
            p.prevY = p.y;
            p.prevZ = p.z;

            p.pitch += p.rotSpeedX;
            p.yaw += p.rotSpeedY;
            p.roll += p.rotSpeedZ;

            p.vy -= 0.006; // Gravity
            p.vx *= 0.965;
            p.vy *= 0.965;
            p.vz *= 0.965;

            double nextX = p.x + p.vx;
            double nextY = p.y + p.vy;
            double nextZ = p.z + p.vz;

            if (canBounce) {
                BlockPos bPos = BlockPos.ofFloored(nextX, nextY, nextZ);
                if (aM_.world.getBlockState(bPos).isSolidBlock(aM_.world, bPos)) {
                    p.vy = -p.vy * 0.55;
                    p.vx *= 0.65;
                    p.vz *= 0.65;
                    nextY = p.y;
                }
            }

            p.x = nextX;
            p.y = nextY;
            p.z = nextZ;
        }

        // Limit maximum particles
        if (this.particles.size() > 1200) {
            this.particles.subList(0, this.particles.size() - 1200).clear();
        }
    }

    @EventTarget
    public void onRender3D(DrawEvent event) {
        if (!m() || !event.c() || this.particles.isEmpty() || aM_.world == null) {
            return;
        }

        Camera camera = aM_.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        float tickDelta = event.g();
        MatrixStack matrices = event.h();
        Matrix4f posMatrix = matrices.peek().getPositionMatrix();
        Quaternionf camRot = camera.getRotation();

        // Split into billboard (bloom/spark) and 3D cube particles
        List<Particle> bloomList = new ArrayList<>();
        List<Particle> cubeList = new ArrayList<>();

        for (Particle p : this.particles) {
            if (p.shapeType == 1) {
                cubeList.add(p);
            } else {
                bloomList.add(p);
            }
        }

        // Render Bloom / Spark Billboards
        if (!bloomList.isEmpty()) {
            RenderStateHelper.enableBlend();
            RenderStateHelper.depthMask(false);
            RenderStateHelper.disableCull();
            RenderStateHelper.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // Additive blending for glow
            RenderStateHelper.setShaderTexture(0, BLOOM_TEXTURE);
            // RenderSystem.setShader(...);

            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            Vector3f v0 = new Vector3f();
            Vector3f v1 = new Vector3f();
            Vector3f v2 = new Vector3f();
            Vector3f v3 = new Vector3f();

            for (Particle p : bloomList) {
                float progress = p.getProgress();
                float alphaFactor = (progress < 0.15f) ? (progress / 0.15f) : (1.0f - progress);
                float curSize = p.baseSize * (p.shapeType == 2 ? 0.45f : 1.0f) * (0.8f + (1.0f - progress) * 0.4f);

                int alpha = (int) (255.0f * alphaFactor);
                if (alpha <= 2) continue;

                int col = ColorUtil.a(p.color, alpha);

                float px = (float) (MathHelper.lerp(tickDelta, p.prevX, p.x) - camPos.x);
                float py = (float) (MathHelper.lerp(tickDelta, p.prevY, p.y) - camPos.y);
                float pz = (float) (MathHelper.lerp(tickDelta, p.prevZ, p.z) - camPos.z);

                float half = curSize * 0.5f;

                v0.set(-half, -half, 0).rotate(camRot).add(px, py, pz);
                v1.set(half, -half, 0).rotate(camRot).add(px, py, pz);
                v2.set(half, half, 0).rotate(camRot).add(px, py, pz);
                v3.set(-half, half, 0).rotate(camRot).add(px, py, pz);

                buffer.vertex(posMatrix, v0.x, v0.y, v0.z).texture(0.0f, 0.0f).color(col);
                buffer.vertex(posMatrix, v1.x, v1.y, v1.z).texture(1.0f, 0.0f).color(col);
                buffer.vertex(posMatrix, v2.x, v2.y, v2.z).texture(1.0f, 1.0f).color(col);
                buffer.vertex(posMatrix, v3.x, v3.y, v3.z).texture(0.0f, 1.0f).color(col);
            }

            ImmediateBufferRenderer.draw(buffer.end());
            RenderStateHelper.defaultBlendFunc();
            RenderStateHelper.enableCull();
            RenderStateHelper.depthMask(true);
        }

        // Render 3D Cubes
        if (!cubeList.isEmpty()) {
            RenderStateHelper.enableBlend();
            RenderStateHelper.depthMask(false);
            RenderStateHelper.disableCull();
            RenderStateHelper.defaultBlendFunc();
            // RenderSystem.setShader(...);

            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            for (Particle p : cubeList) {
                float progress = p.getProgress();
                float alphaFactor = (progress < 0.15f) ? (progress / 0.15f) : (1.0f - progress);
                float curSize = p.baseSize * (1.0f - progress * 0.3f);

                int alpha = (int) (220.0f * alphaFactor);
                if (alpha <= 2) continue;

                int topCol = ColorUtil.a(p.color, alpha);
                int sideCol = ColorUtil.a(ColorUtil.b(p.color, 0.75f), alpha);
                int botCol = ColorUtil.a(ColorUtil.b(p.color, 0.5f), alpha);

                float px = (float) (MathHelper.lerp(tickDelta, p.prevX, p.x) - camPos.x);
                float py = (float) (MathHelper.lerp(tickDelta, p.prevY, p.y) - camPos.y);
                float pz = (float) (MathHelper.lerp(tickDelta, p.prevZ, p.z) - camPos.z);

                float h = curSize * 0.5f;

                // Simple 6-faced cube centered at (px, py, pz)
                // Top (+Y)
                buffer.vertex(posMatrix, px - h, py + h, pz - h).color(topCol);
                buffer.vertex(posMatrix, px - h, py + h, pz + h).color(topCol);
                buffer.vertex(posMatrix, px + h, py + h, pz + h).color(topCol);
                buffer.vertex(posMatrix, px + h, py + h, pz - h).color(topCol);

                // Bottom (-Y)
                buffer.vertex(posMatrix, px - h, py - h, pz - h).color(botCol);
                buffer.vertex(posMatrix, px + h, py - h, pz - h).color(botCol);
                buffer.vertex(posMatrix, px + h, py - h, pz + h).color(botCol);
                buffer.vertex(posMatrix, px - h, py - h, pz + h).color(botCol);

                // North (-Z)
                buffer.vertex(posMatrix, px - h, py - h, pz - h).color(sideCol);
                buffer.vertex(posMatrix, px - h, py + h, pz - h).color(sideCol);
                buffer.vertex(posMatrix, px + h, py + h, pz - h).color(sideCol);
                buffer.vertex(posMatrix, px + h, py - h, pz - h).color(sideCol);

                // South (+Z)
                buffer.vertex(posMatrix, px - h, py - h, pz + h).color(sideCol);
                buffer.vertex(posMatrix, px + h, py - h, pz + h).color(sideCol);
                buffer.vertex(posMatrix, px + h, py + h, pz + h).color(sideCol);
                buffer.vertex(posMatrix, px - h, py + h, pz + h).color(sideCol);

                // West (-X)
                buffer.vertex(posMatrix, px - h, py - h, pz - h).color(sideCol);
                buffer.vertex(posMatrix, px - h, py - h, pz + h).color(sideCol);
                buffer.vertex(posMatrix, px - h, py + h, pz + h).color(sideCol);
                buffer.vertex(posMatrix, px - h, py + h, pz - h).color(sideCol);

                // East (+X)
                buffer.vertex(posMatrix, px + h, py - h, pz - h).color(sideCol);
                buffer.vertex(posMatrix, px + h, py + h, pz - h).color(sideCol);
                buffer.vertex(posMatrix, px + h, py + h, pz + h).color(sideCol);
                buffer.vertex(posMatrix, px + h, py - h, pz + h).color(sideCol);
            }

            ImmediateBufferRenderer.draw(buffer.end());
            RenderStateHelper.enableCull();
            RenderStateHelper.depthMask(true);
        }
    }
}

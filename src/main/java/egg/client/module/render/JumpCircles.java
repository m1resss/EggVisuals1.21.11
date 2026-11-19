package egg.client.module.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.shader.CausticUniforms;
import egg.client.render.shader.CausticUniformStorage;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import egg.client.ui.theme.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class JumpCircles extends Module {
    private static JumpCircles instance;

    private final SliderSetting maxRadius = new SliderSetting("Размер", 2.8, 1.0, 6.0, 0.1, "Максимальный радиус раскрытия круга");
    private final SliderSetting expandSpeed = new SliderSetting("Скорость", 1.0, 0.4, 3.0, 0.1, "Скорость раскрытия круга");
    private final SliderSetting dispersion = new SliderSetting("Дисперсия", 1.0, 0.0, 2.5, 0.1, "Сила хроматической аберрации и расщепления лучей");
    private final SliderSetting brightness = new SliderSetting("Яркость лучей", 1.4, 0.5, 3.0, 0.1, "Яркость и насыщенность свечения лучей");
    private final ModeSetting palette = new ModeSetting("Палитра", "Радужная", "Радужная", "Акцент темы", "Кастомная");
    private final ColorSetting customColor = new ColorSetting("Цвет", 0xFF64B5F6, "Пользовательский цвет свечения");
    private final SliderSetting twist = new SliderSetting("Закручивание", 0.85, -2.0, 2.0, 0.05, "Угол спирального закручивания каустики");
    private final SliderSetting lifetime = new SliderSetting("Время жизни", 1.2, 0.5, 3.0, 0.1, "Длительность анимации круга в секундах");

    private final List<CausticCircle> circles = new ArrayList<>();
    private boolean wasOnGround = true;
    private double lastGroundY = 0.0;
    private long lastFrameNanos = 0L;

    private final BufferAllocator allocCaustic = createReusableAllocator(4096);

    private static BufferAllocator createReusableAllocator(int size) {
        return new BufferAllocator(size) {
            @Override
            public void close() {
                this.reset();
            }
        };
    }

    private static class CausticCircle {
        final double x, y, z;
        final float maxLife;
        final float maxRadius;
        final float timeOffset;
        float age = 0.0f;

        CausticCircle(double x, double y, double z, float maxLife, float maxRadius, float timeOffset) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.maxLife = maxLife;
            this.maxRadius = maxRadius;
            this.timeOffset = timeOffset;
        }

        boolean update(float dt) {
            age += dt;
            return age < maxLife;
        }
    }

    public JumpCircles() {
        super("Jump Circles", "Призматическая каустика затмения с хроматической дисперсией при прыжке", Category.ESP);
        instance = this;

        this.customColor.setVisible(() -> "Кастомная".equals(this.palette.get()));

        addSettings(
            this.maxRadius,
            this.expandSpeed,
            this.dispersion,
            this.brightness,
            this.palette,
            this.customColor,
            this.twist,
            this.lifetime
        );
    }

    public static JumpCircles getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.circles.clear();
        this.wasOnGround = true;
        this.lastGroundY = 0.0;
        this.lastFrameNanos = 0L;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client.world == null || client.player == null) {
            this.circles.clear();
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
            spawnCircle(player.getX(), this.lastGroundY, player.getZ());
        }

        if (onGround) {
            this.lastGroundY = curY;
        }
        this.wasOnGround = onGround;
    }

    private void spawnCircle(double x, double y, double z) {
        float life = Math.max(0.3f, this.lifetime.getValueFloat() / Math.max(0.1f, this.expandSpeed.getValueFloat()));
        float r = this.maxRadius.getValueFloat();
        float timeOffset = (float) (Math.random() * 100.0);
        this.circles.add(new CausticCircle(x, y, z, life, r, timeOffset));
    }

    public void renderWorld3D(MatrixStack matrices, Camera camera, float tickDelta, VertexConsumerProvider immediate, Vec3d camPos) {
        if (!isEnabled()) return;

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float rawDt = (float) ((now - lastFrameNanos) * 1e-9);
        lastFrameNanos = now;
        final float dt = (rawDt <= 0.0f || rawDt > 0.1f) ? 0.016f : rawDt;

        this.circles.removeIf(c -> !c.update(dt));
        if (this.circles.isEmpty()) return;

        float accentR = 1.0f;
        float accentG = 1.0f;
        float accentB = 1.0f;
        float accentWeight = 0.0f;
        String pal = this.palette.get();
        if ("Акцент темы".equals(pal)) {
            int acc = Theme.getAccent();
            accentR = ((acc >> 16) & 0xFF) / 255.0f;
            accentG = ((acc >> 8) & 0xFF) / 255.0f;
            accentB = (acc & 0xFF) / 255.0f;
            accentWeight = 1.0f;
        } else if ("Кастомная".equals(pal)) {
            int col = this.customColor.get();
            accentR = ((col >> 16) & 0xFF) / 255.0f;
            accentG = ((col >> 8) & 0xFF) / 255.0f;
            accentB = (col & 0xFF) / 255.0f;
            accentWeight = 1.0f;
        }

        float disp = this.dispersion.getValueFloat();
        float bright = this.brightness.getValueFloat();
        float tw = this.twist.getValueFloat();

        for (CausticCircle c : this.circles) {
            float prog = c.age / c.maxLife;
            if (prog >= 1.0f) continue;

            float alpha = 1.0f;
            if (prog < 0.06f) {
                alpha = prog / 0.06f;
            } else if (prog > 0.65f) {
                alpha = (1.0f - prog) / 0.35f;
            }

            float curR = c.maxRadius;
            float rx = (float) (c.x - camPos.x);
            float ry = (float) (c.y - camPos.y + 0.02);
            float rz = (float) (c.z - camPos.z);

            BufferBuilder buf = new BufferBuilder(this.allocCaustic, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            buf.vertex(rx - curR, ry, rz - curR).texture(-1.0f, -1.0f).color(0xFFFFFFFF);
            buf.vertex(rx - curR, ry, rz + curR).texture(-1.0f,  1.0f).color(0xFFFFFFFF);
            buf.vertex(rx + curR, ry, rz + curR).texture( 1.0f,  1.0f).color(0xFFFFFFFF);
            buf.vertex(rx + curR, ry, rz - curR).texture( 1.0f, -1.0f).color(0xFFFFFFFF);

            BuiltBuffer built = buf.endNullable();
            if (built != null) {
                CausticUniforms u = new CausticUniforms(
                    prog,
                    c.timeOffset + c.age * 1.2f,
                    disp,
                    bright,
                    accentR, accentG, accentB, accentWeight,
                    alpha,
                    tw,
                    0.0f, 0.0f
                );
                GpuBufferSlice slice = CausticUniformStorage.write(u);
                MeshDrawUtil.drawWithUniform(Pipelines.JUMP_CAUSTIC, built, "jump caustic", "CausticUniforms", slice);
            }
            this.allocCaustic.reset();
        }
    }
}

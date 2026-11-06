package egg.client.module.render;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.ParticleBlurRenderer;
import egg.client.render.shader.Pipelines;
import egg.client.ui.theme.Theme;
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

import java.awt.Color;
import java.util.*;

public class JumpCircles extends Module {
    private static JumpCircles instance;

    private static final Identifier RIPPLE_TEXTURE = Identifier.of("egg", "textures/world/puddle_ripple.png");
    private static final Identifier BLOOM_TEXTURE = Identifier.of("delta", "pictures/bloom.png");

    private final ModeSetting mode = new ModeSetting("Стиль", "Комбо", "Стиль отображения кругов", "Комбо", "Кольцо", "Волна");
    private final ModeSetting colorMode = new ModeSetting("Цвет", "Градиент HUD", "Источник цвета", "Градиент HUD", "Тема", "Кастом", "Радуга");
    private final ColorSetting customColor = new ColorSetting("Кастомный цвет", 0xFFA855F7, "Цвет кругов при кастомном режиме");
    private final SliderSetting maxRadius = new SliderSetting("Радиус", 2.2, 0.8, 5.0, 0.1, "Конечный радиус круга в блоках");
    private final SliderSetting expandSpeed = new SliderSetting("Скорость", 1.2, 0.4, 3.0, 0.1, "Скорость расширения круга");
    private final SliderSetting thickness = new SliderSetting("Толщина", 0.35, 0.1, 1.0, 0.05, "Толщина кольца");
    private final BooleanSetting glow = new BooleanSetting("Свечение", true, "Мягкое неоновое свечение вокруг круга");
    private final ModeSetting triggerMode = new ModeSetting("Событие", "Прыжок и приземление", "Когда создавать круги", "Прыжок и приземление", "Только прыжок", "Только приземление");
    private final ModeSetting targetMode = new ModeSetting("Игроки", "Только я", "Для кого создавать круги", "Только я", "Все игроки");

    private final List<Circle> circles = new ArrayList<>();
    private final Map<UUID, PlayerTrackState> trackStates = new HashMap<>();
    private long lastFrameNanos = 0L;

    private static class PlayerTrackState {
        boolean wasOnGround = true;
        double lastY = 0.0;
        double fallDist = 0.0;
    }

    private static class Circle {
        final double x, y, z;
        float age = 0.0f;
        final float maxLife;
        final int color;

        Circle(double x, double y, double z, float maxLife, int color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.maxLife = maxLife;
            this.color = color;
        }

        boolean update(float dt) {
            age += dt;
            return age < maxLife;
        }
    }

    public JumpCircles() {
        super("Jump Circles", "Создает расходящиеся неоновые круги на земле при прыжке и приземлении", Category.ESP);
        instance = this;

        customColor.setVisible(() -> "Кастом".equals(colorMode.get()));

        addSettings(mode, colorMode, customColor, maxRadius, expandSpeed, thickness, glow, triggerMode, targetMode);
    }

    public static JumpCircles getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.circles.clear();
        this.trackStates.clear();
        this.lastFrameNanos = 0L;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client.world == null) {
            this.circles.clear();
            this.trackStates.clear();
            return;
        }

        List<PlayerEntity> players = new ArrayList<>();
        if ("Только я".equals(targetMode.get())) {
            if (client.player != null) players.add(client.player);
        } else {
            players.addAll(client.world.getPlayers());
        }

        Set<UUID> currentUuids = new HashSet<>();
        for (PlayerEntity p : players) {
            currentUuids.add(p.getUuid());
            PlayerTrackState state = trackStates.computeIfAbsent(p.getUuid(), k -> {
                PlayerTrackState s = new PlayerTrackState();
                s.wasOnGround = p.isOnGround();
                s.lastY = p.getY();
                s.fallDist = p.fallDistance;
                return s;
            });

            if (p.isSpectator() || p.getAbilities().flying) {
                state.wasOnGround = p.isOnGround();
                state.lastY = p.getY();
                state.fallDist = 0.0;
                continue;
            }

            boolean onGround = p.isOnGround();
            double curY = p.getY();
            Vec3d vel = p.getVelocity();

            boolean jumped = state.wasOnGround && !onGround && vel.y > 0.05;
            boolean landed = !state.wasOnGround && onGround && (state.fallDist > 0.35f || (state.lastY - curY) > 0.35);

            String trg = triggerMode.get();
            boolean allowJump = "Прыжок и приземление".equals(trg) || "Только прыжок".equals(trg);
            boolean allowLand = "Прыжок и приземление".equals(trg) || "Только приземление".equals(trg);

            if (jumped && allowJump) {
                spawnCircle(p.getX(), curY, p.getZ());
            } else if (landed && allowLand) {
                spawnCircle(p.getX(), curY, p.getZ());
            }

            state.wasOnGround = onGround;
            state.lastY = curY;
            state.fallDist = p.fallDistance;
        }

        trackStates.keySet().removeIf(uuid -> !currentUuids.contains(uuid));
    }

    private void spawnCircle(double x, double y, double z) {
        float life = Math.max(0.3f, 1.0f / expandSpeed.getValueFloat());
        int col = resolveColor();
        circles.add(new Circle(x, y, z, life, col));
    }

    private int resolveColor() {
        String cm = colorMode.get();
        if ("Градиент HUD".equals(cm)) {
            Interface iface = Interface.getInstance();
            if (iface != null) {
                return iface.getHudGradientColor();
            }
            return 0xFFA855F7;
        } else if ("Тема".equals(cm)) {
            return Theme.getAccent();
        } else if ("Кастом".equals(cm)) {
            return customColor.get();
        } else if ("Радуга".equals(cm)) {
            float hue = (float) ((System.currentTimeMillis() % 4000L) / 4000.0);
            return Color.HSBtoRGB(hue, 0.85f, 1.0f);
        }
        return 0xFFA855F7;
    }

    public void renderWorld3D(MatrixStack matrices, Camera camera, float tickDelta, VertexConsumerProvider immediate, Vec3d camPos) {
        if (!isEnabled()) return;

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float rawDt = (float) ((now - lastFrameNanos) * 1e-9);
        lastFrameNanos = now;
        final float dt = (rawDt <= 0.0f || rawDt > 0.1f) ? 0.016f : rawDt;

        circles.removeIf(c -> !c.update(dt));
        if (circles.isEmpty()) return;

        String st = mode.get();
        boolean doRipple = "Комбо".equals(st) || "Волна".equals(st);
        boolean doRing = "Комбо".equals(st) || "Кольцо".equals(st);
        boolean doGlow = glow.isEnabled();

        float maxR = maxRadius.getValueFloat();
        float thick = thickness.getValueFloat();

        if (doRipple) {
            try (BufferAllocator alloc = new BufferAllocator(32768)) {
                BufferBuilder buf = new BufferBuilder(alloc, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                for (Circle c : circles) {
                    float progress = c.age / c.maxLife;
                    float eased = 1.0f - (float) Math.pow(1.0f - progress, 3.0);
                    float r = Math.max(0.1f, eased * maxR);
                    float alpha = 1.0f - progress;
                    if (progress < 0.08f) alpha *= (progress / 0.08f);

                    int col = ColorUtil.withAlphaFloat(c.color, alpha * 0.70f);
                    emitGroundQuad(buf, (float) (c.x - camPos.x), (float) (c.y - camPos.y + 0.018), (float) (c.z - camPos.z), r, col);
                }
                BuiltBuffer built = buf.endNullable();
                if (built != null) {
                    MeshDrawUtil.drawLabeledTextured(Pipelines.AURA_BLOOM_DEPTH, built, "jump circles ripple", RIPPLE_TEXTURE);
                }
            }
        }

        if (doGlow) {
            try (BufferAllocator alloc = new BufferAllocator(32768)) {
                BufferBuilder buf = new BufferBuilder(alloc, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                float avgAlpha = 0.0f;
                for (Circle c : circles) {
                    float progress = c.age / c.maxLife;
                    float eased = 1.0f - (float) Math.pow(1.0f - progress, 3.0);
                    float r = Math.max(0.1f, eased * maxR * 1.25f);
                    float alpha = (1.0f - progress) * 0.40f;
                    if (progress < 0.08f) alpha *= (progress / 0.08f);
                    avgAlpha = Math.max(avgAlpha, alpha);

                    int col = ColorUtil.withAlphaFloat(c.color, alpha);
                    emitGroundQuad(buf, (float) (c.x - camPos.x), (float) (c.y - camPos.y + 0.015), (float) (c.z - camPos.z), r, col);
                }
                BuiltBuffer built = buf.endNullable();
                if (built != null) {
                    float finalAlpha = avgAlpha;
                    ParticleBlurRenderer.INSTANCE.render(3.2f, finalAlpha, () ->
                            MeshDrawUtil.drawLabeledTextured(Pipelines.AURA_BLOOM_DEPTH, built, "jump circles glow", BLOOM_TEXTURE));
                }
            }
        }

        if (doRing) {
            try (BufferAllocator alloc = new BufferAllocator(65536)) {
                BufferBuilder buf = new BufferBuilder(alloc, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                for (Circle c : circles) {
                    float progress = c.age / c.maxLife;
                    float eased = 1.0f - (float) Math.pow(1.0f - progress, 3.0);
                    float r = Math.max(0.1f, eased * maxR);
                    float alpha = 1.0f - progress;
                    if (progress < 0.08f) alpha *= (progress / 0.08f);

                    int col = ColorUtil.withAlphaFloat(c.color, alpha * 0.95f);
                    emitGroundRing(buf, (float) (c.x - camPos.x), (float) (c.y - camPos.y + 0.022), (float) (c.z - camPos.z), r, thick, col);
                }
                BuiltBuffer built = buf.endNullable();
                if (built != null) {
                    MeshDrawUtil.drawLabeledTextured(Pipelines.AURA_BLOOM_DEPTH, built, "jump circles ring", BLOOM_TEXTURE);
                }
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
        int segments = 40;
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
}

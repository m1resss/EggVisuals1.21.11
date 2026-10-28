package delta.module.render;

import delta.core.Category;
import delta.core.Delta;
import delta.core.EventTarget;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.DrawEvent;
import delta.event.TickEvent;
import delta.render.ColorUtil;
import delta.render.Fonts;
import delta.setting.BooleanSetting;
import delta.setting.ColorSetting;
import delta.setting.SliderSetting;
import delta.util.ProjectUtil;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@ModuleRegister(a = "HP Counter", b = "Показывает урон и лечение видимых игроков", c = Category.Render)
public class HpCounter extends Module {
    private static final long LIFETIME_MS = 900L;
    private static final Vec3d[] OFFSETS = new Vec3d[]{
            new Vec3d(-0.36, -0.08, 0.12),
            new Vec3d(-0.12, 0.10, -0.30),
            new Vec3d(0.22, -0.14, -0.22),
            new Vec3d(0.40, 0.04, 0.09),
            new Vec3d(0.11, 0.16, 0.33),
            new Vec3d(-0.28, -0.18, 0.27),
            new Vec3d(0.34, -0.03, -0.12),
            new Vec3d(-0.05, 0.22, 0.05)
    };

    private final ColorSetting damageColor = new ColorSetting("Цвет урона", 0xFFFF4B4B);
    private final ColorSetting healColor = new ColorSetting("Цвет лечения", 0xFF3CC850);
    private final SliderSetting scale = new SliderSetting("Размер", 1.0f, 0.5f, 2.0f, 0.1f);
    private final BooleanSetting crosshairHp = new BooleanSetting("ХП у прицела", true);
    private final SliderSetting crosshairHpSize = (SliderSetting) new SliderSetting("Размер у прицела", 8.5f, 5.0f, 20.0f, 0.5f).a(() -> this.crosshairHp.c().booleanValue());
    private final SliderSetting crosshairHpOffset = (SliderSetting) new SliderSetting("Отступ от прицела", 12.0f, 2.0f, 35.0f, 1.0f).a(() -> this.crosshairHp.c().booleanValue());

    private final Map<Integer, Float> healthByEntity = new HashMap<>();
    private final List<FloatingNumber> numbers = new ArrayList<>();
    private int nextOffset = 0;

    public HpCounter() {
        a(this.damageColor, this.healColor, this.scale, this.crosshairHp, this.crosshairHpSize, this.crosshairHpOffset);
    }

    private static String format(float value) {
        if (Math.abs(value - (float) Math.round(value)) < 0.05f) {
            return Integer.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", Float.valueOf(value)).replace('.', ',');
    }

    private static float smooth(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    @Override
    public void c() {
        super.c();
        this.healthByEntity.clear();
        this.numbers.clear();
        this.nextOffset = 0;
    }

    @EventTarget
    public void onTick(TickEvent tickEvent) {
        if (aM_.player == null || aM_.world == null) {
            this.healthByEntity.clear();
            this.numbers.clear();
            return;
        }

        long now = System.currentTimeMillis();
        for (PlayerEntity player : aM_.world.getPlayers()) {
            if (player == aM_.player) {
                continue;
            }

            float currentHp = player.getHealth();
            Float prevHp = this.healthByEntity.put(player.getId(), currentHp);
            if (prevHp == null || Math.abs(currentHp - prevHp) < 0.01f || !isVisible(player)) {
                continue;
            }

            float diff = currentHp - prevHp;
            Vec3d offset = OFFSETS[this.nextOffset++ % OFFSETS.length];
            Vec3d spawnPos = player.getEntityPos().add(offset.x, (double) player.getHeight() * 0.55 + offset.y, offset.z);
            this.numbers.add(new FloatingNumber(spawnPos, Math.abs(diff), diff > 0.0f, now));
        }

        this.healthByEntity.keySet().removeIf(id -> aM_.world.getEntityById(id) == null);
        this.numbers.removeIf(n -> now - n.createdAtMs >= LIFETIME_MS);
    }

    private boolean isVisible(PlayerEntity player) {
        return player.isAlive() && !player.isInvisible() && aM_.player.canSee(player);
    }

    private LivingEntity getCrosshairTarget() {
        if (aM_.player == null || aM_.world == null) {
            return null;
        }
        if (TargetESP.getInstance() != null && TargetESP.getInstance().m() && TargetESP.getInstance().getTarget() != null) {
            LivingEntity t = TargetESP.getInstance().getTarget();
            if (t.isAlive() && aM_.player.canSee(t)) {
                return t;
            }
        }
        if (aM_.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living) {
            if (living != aM_.player && living.isAlive() && aM_.player.canSee(living)) {
                return living;
            }
        }
        if (aM_.targetedEntity instanceof LivingEntity living && living.isAlive() && aM_.player.canSee(living)) {
            return living;
        }
        Vec3d eyePos = aM_.player.getEyePos();
        Vec3d lookVec = aM_.player.getRotationVec(1.0f);
        double reach = 32.0;
        Vec3d endPos = eyePos.add(lookVec.multiply(reach));
        Box searchBox = aM_.player.getBoundingBox().stretch(lookVec.multiply(reach)).expand(1.0);
        LivingEntity closest = null;
        double closestDistSq = reach * reach;
        for (Entity entity : aM_.world.getOtherEntities(aM_.player, searchBox)) {
            if (entity instanceof LivingEntity living && living.isAlive() && aM_.player.canSee(living)) {
                if (living instanceof PlayerEntity player && Delta.h().d().e().d(player.getName().getString())) {
                    continue;
                }
                Box box = living.getBoundingBox().expand(0.35);
                Optional<Vec3d> hit = box.raycast(eyePos, endPos);
                if (hit.isPresent()) {
                    double dSq = eyePos.squaredDistanceTo(hit.get());
                    if (dSq < closestDistSq) {
                        closestDistSq = dSq;
                        closest = living;
                    }
                }
            }
        }
        return closest;
    }

    @EventTarget
    public void onDraw(DrawEvent drawEvent) {
        if (drawEvent.f() != DrawEvent.a.D2D) {
            return;
        }

        if (this.crosshairHp.c().booleanValue() && aM_.player != null) {
            LivingEntity target = getCrosshairTarget();
            if (target != null) {
                float hp = target.getHealth();
                String hpText = format(hp) + " HP";
                int hpColor = hp < 10.0f ? 0xFFFF3B3B : 0xFF3BFF65;
                float fontSize = this.crosshairHpSize.h().floatValue();
                float cx = aM_.getWindow().getScaledWidth() / 2.0f;
                float cy = aM_.getWindow().getScaledHeight() / 2.0f;
                float textY = cy + this.crosshairHpOffset.h().floatValue();

                Fonts.b.b(drawEvent.h(), hpText, cx + 0.6f, textY + 0.6f, fontSize, 0xCC000000);
                Fonts.b.b(drawEvent.h(), hpText, cx, textY, fontSize, hpColor);
            }
        }

        if (this.numbers.isEmpty()) {
            return;
        }

        Camera camera = aM_.getEntityRenderDispatcher().camera;
        if (camera == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Vec3d camPos = camera.getCameraPos();

        for (FloatingNumber num : this.numbers) {
            float progress = MathHelper.clamp((float) (now - num.createdAtMs) / (float) LIFETIME_MS, 0.0f, 1.0f);
            if (progress >= 1.0f) {
                continue;
            }

            Vec3d worldPos = num.position.add(0.0, (double) progress * 0.65, 0.0);
            Vector2f screenPos = ProjectUtil.a(worldPos.x, worldPos.y, worldPos.z);
            if (!ProjectUtil.a(screenPos)) {
                continue;
            }

            float alpha = smooth(MathHelper.clamp(progress / 0.14f, 0.0f, 1.0f))
                    * smooth(MathHelper.clamp((1.0f - progress) / 0.35f, 0.0f, 1.0f));
            if (alpha <= 0.01f) {
                continue;
            }

            float dist = (float) Math.sqrt(camPos.squaredDistanceTo(num.position));
            float distScale = MathHelper.clamp(7.0f / Math.max(dist, 0.1f), 0.38f, 1.05f);
            float fontSize = 7.0f * distScale * (0.88f + 0.12f * alpha) * this.scale.h().floatValue();

            String text = (num.heal ? "+" : "-") + format(num.amount);
            int baseColor = num.heal ? this.healColor.c() : this.damageColor.c();
            int color = ColorUtil.a(baseColor, alpha);
            int shadowColor = ColorUtil.a(0xFF000000, alpha * 0.55f);

            Fonts.b.b(drawEvent.h(), text, screenPos.x + 0.7f, screenPos.y + 0.7f, fontSize, shadowColor);
            Fonts.b.b(drawEvent.h(), text, screenPos.x, screenPos.y, fontSize, color);
        }
    }

    public static record FloatingNumber(Vec3d position, float amount, boolean heal, long createdAtMs) {
    }
}

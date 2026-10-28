package delta.module.render;

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
import delta.render.ColorUtil;
import delta.render.Draw2DProcessor;
import delta.setting.BooleanSetting;
import delta.setting.ColorSetting;
import delta.setting.ModeSetting;
import delta.setting.SliderSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

@ModuleRegister(a = "Hitmarker", b = "Отображает визуальный хитмаркер при попадании по противнику", c = Category.Render)
public class Hitmarker extends Module {
    private static final Identifier HITMARKER_TEXTURE = Identifier.of("delta", "textures/hitmarker.png");

    private final ModeSetting renderMode = new ModeSetting("Режим", "На прицеле", "На прицеле", "В мире");
    private final SliderSetting size = new SliderSetting("Размер", 16.0f, 8.0f, 36.0f, 1.0f);
    private final SliderSetting duration = new SliderSetting("Длительность", 250.0f, 100.0f, 800.0f, 25.0f);
    private final ModeSetting colorMode = new ModeSetting("Цвет", "Белый", "Белый", "Тема", "Кастом");
    private final ColorSetting customColor = (ColorSetting) new ColorSetting("Свой цвет", 0xFFFFFFFF).a(() -> this.colorMode.l("Кастом"));
    private final BooleanSetting dynamicScale = new BooleanSetting("Анимация отдачи", true);
    private final BooleanSetting sound = new BooleanSetting("Звук", true);
    private final ModeSetting soundType = (ModeSetting) new ModeSetting("Тип звука", "Стрела", "Стрела", "Опыт", "Колокольчик").a(() -> this.sound.c().booleanValue());

    private long lastHitTime = 0L;
    private int lastTargetId = -1;
    private double hitX = 0.0;
    private double hitY = 0.0;
    private double hitZ = 0.0;

    public Hitmarker() {
        a(this.renderMode, this.size, this.duration, this.colorMode, this.customColor, this.dynamicScale, this.sound, this.soundType);
    }

    private void recordHitPosition(LivingEntity entity) {
        if (entity != null) {
            // Check crosshairTarget first: exact intersection point where the player struck
            if (aM_.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult ehr && ehr.getEntity() == entity) {
                net.minecraft.util.math.Vec3d hitVec = ehr.getPos();
                this.hitX = hitVec.x;
                this.hitY = hitVec.y;
                this.hitZ = hitVec.z;
                return;
            }
            if (aM_.player != null) {
                net.minecraft.util.math.Vec3d eyePos = aM_.player.getEyePos();
                net.minecraft.util.math.Vec3d lookVec = aM_.player.getRotationVec(1.0f);
                net.minecraft.util.math.Vec3d reachEnd = eyePos.add(lookVec.multiply(6.0));
                java.util.Optional<net.minecraft.util.math.Vec3d> hitOpt = entity.getBoundingBox().raycast(eyePos, reachEnd);
                if (hitOpt.isPresent()) {
                    net.minecraft.util.math.Vec3d p = hitOpt.get();
                    this.hitX = p.x;
                    this.hitY = p.y;
                    this.hitZ = p.z;
                    return;
                }
            }
            // Fallback: point on entity bounding box closest to player eye
            if (aM_.player != null) {
                net.minecraft.util.math.Vec3d eyePos = aM_.player.getEyePos();
                net.minecraft.util.math.Box bb = entity.getBoundingBox();
                double cx = Math.max(bb.minX, Math.min(eyePos.x, bb.maxX));
                double cy = Math.max(bb.minY, Math.min(eyePos.y, bb.maxY));
                double cz = Math.max(bb.minZ, Math.min(eyePos.z, bb.maxZ));
                this.hitX = cx;
                this.hitY = cy;
                this.hitZ = cz;
                return;
            }
            this.hitX = entity.getX();
            this.hitY = entity.getY() + entity.getHeight() * 0.55;
            this.hitZ = entity.getZ();
        }
    }

    private void triggerHit(LivingEntity entity) {
        this.lastHitTime = System.currentTimeMillis();
        recordHitPosition(entity);
        if (this.sound.c().booleanValue() && aM_.player != null) {
            float pitch = 1.0f + (float) (Math.random() * 0.15f);
            if (this.soundType.l("Опыт")) {
                aM_.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.6f);
            } else if (this.soundType.l("Колокольчик")) {
                aM_.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.7f, 1.8f);
            } else {
                aM_.player.playSound(SoundEvents.ENTITY_ARROW_HIT_PLAYER, 0.8f, pitch);
            }
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (event.b() instanceof LivingEntity living && living != aM_.player) {
            this.lastTargetId = living.getId();
            triggerHit(living);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.d() instanceof EntityStatusS2CPacket packet && packet.getStatus() == 2) {
            if (aM_.world != null && this.lastTargetId != -1) {
                if (packet.getEntity(aM_.world) instanceof LivingEntity living && living.getId() == this.lastTargetId) {
                    long elapsed = System.currentTimeMillis() - this.lastHitTime;
                    if (elapsed > 120L) {
                        triggerHit(living);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onDraw(DrawEvent event) {
        if (!event.b() || aM_.options.hudHidden) {
            return;
        }

        long elapsed = System.currentTimeMillis() - this.lastHitTime;
        float maxDur = this.duration.c().floatValue();
        if (elapsed >= maxDur || this.lastHitTime == 0L) {
            return;
        }

        float progress = (float) elapsed / maxDur;
        float alpha = (1.0f - progress) * (1.0f - progress);
        if (alpha <= 0.005f) {
            return;
        }

        float scaleFactor = this.dynamicScale.c().booleanValue() ? (1.0f + (1.0f - progress) * 0.30f) : 1.0f;
        float renderSize = this.size.c().floatValue() * scaleFactor;

        float cx;
        float cy;

        if (this.renderMode.l("В мире")) {
            org.joml.Vector2f screenPos = delta.util.ProjectUtil.a(this.hitX, this.hitY, this.hitZ);
            if (!delta.util.ProjectUtil.a(screenPos)) {
                return;
            }
            cx = screenPos.x();
            cy = screenPos.y();
        } else {
            cx = aM_.getWindow().getScaledWidth() / 2.0f;
            cy = aM_.getWindow().getScaledHeight() / 2.0f;
        }

        float drawX = cx - (renderSize * 0.5f);
        float drawY = cy - (renderSize * 0.5f);

        int baseCol = -1;
        if (this.colorMode.l("Тема")) {
            baseCol = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
        } else if (this.colorMode.l("Кастом")) {
            baseCol = this.customColor.c().intValue();
        }

        int r = (baseCol >> 16) & 0xFF;
        int g = (baseCol >> 8) & 0xFF;
        int b = baseCol & 0xFF;
        int finalColor = ColorUtil.a(r, g, b, (int) (alpha * 255.0f));

        Draw2DProcessor draw = Delta.h().d().i();
        if (draw != null) {
            draw.a(event.h(), HITMARKER_TEXTURE, drawX, drawY, renderSize, renderSize, 0.0f, finalColor);
        }
    }
}

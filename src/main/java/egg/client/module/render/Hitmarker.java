package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.ProjectUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

import java.util.Optional;

/**
 * 1:1 Hitmarker port from egg 1.21.4 (delta.module.render.Hitmarker).
 */
public class Hitmarker extends Module {
    private static final Identifier HITMARKER_TEXTURE = Identifier.of("egg", "textures/hitmarker.png");

    private final ModeSetting renderMode = new ModeSetting("Режим", "На прицеле", "Позиция отрисовки", "На прицеле", "В мире");
    private final SliderSetting size = new SliderSetting("Размер", 16.0, 8.0, 36.0, 1.0, "Размер текстуры хитмаркера");
    private final SliderSetting duration = new SliderSetting("Длительность", 250.0, 100.0, 800.0, 25.0, "Время отображения в миллисекундах");
    private final ModeSetting colorMode = new ModeSetting("Цвет", "Белый", "Источник цвета", "Белый", "Тема", "Кастом");
    private final ColorSetting customColor = new ColorSetting("Свой цвет", 0xFFFFFFFF, "Пользовательский цвет хитмаркера");
    private final BooleanSetting dynamicScale = new BooleanSetting("Анимация отдачи", true, "Пульсация размера при появлении");
    private final BooleanSetting sound = new BooleanSetting("Звук", true, "Воспроизведение звука при попадании");
    private final ModeSetting soundType = new ModeSetting("Тип звука", "Стрела", "Тип звукового эффекта", "Стрела", "Опыт", "Колокольчик");

    private long lastHitTime = 0L;
    private int lastTargetId = -1;
    private double hitX = 0.0;
    private double hitY = 0.0;
    private double hitZ = 0.0;

    public Hitmarker() {
        super("Hitmarker", "Отображает визуальный хитмаркер при попадании по противнику", Category.ESP);
        addSettings(renderMode, size, duration, colorMode, customColor, dynamicScale, sound, soundType);
    }

    private void recordHitPosition(LivingEntity entity) {
        if (entity != null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() == entity) {
                Vec3d hitVec = ehr.getPos();
                this.hitX = hitVec.x;
                this.hitY = hitVec.y;
                this.hitZ = hitVec.z;
                return;
            }
            if (mc.player != null) {
                Vec3d eyePos = mc.player.getEyePos();
                Vec3d lookVec = mc.player.getRotationVec(1.0f);
                Vec3d reachEnd = eyePos.add(lookVec.multiply(6.0));
                Optional<Vec3d> hitOpt = entity.getBoundingBox().raycast(eyePos, reachEnd);
                if (hitOpt.isPresent()) {
                    Vec3d p = hitOpt.get();
                    this.hitX = p.x;
                    this.hitY = p.y;
                    this.hitZ = p.z;
                    return;
                }
            }
            if (mc.player != null) {
                Vec3d eyePos = mc.player.getEyePos();
                Box bb = entity.getBoundingBox();
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

    public void triggerHit(LivingEntity entity) {
        this.lastHitTime = System.currentTimeMillis();
        recordHitPosition(entity);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (this.sound.get() && mc != null && mc.player != null) {
            float pitch = 1.0f + (float) (Math.random() * 0.15f);
            if (this.soundType.is("Опыт")) {
                mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.6f);
            } else if (this.soundType.is("Колокольчик")) {
                mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.7f, 1.8f);
            } else {
                mc.player.playSound(SoundEvents.ENTITY_ARROW_HIT_PLAYER, 0.8f, pitch);
            }
        }
    }

    public void onAttack(Entity target) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (target instanceof LivingEntity living && (mc.player == null || living != mc.player)) {
            this.lastTargetId = living.getId();
            triggerHit(living);
        }
    }

    public void onPacket(Object packetObj) {
        if (!isEnabled()) return;
        if (packetObj instanceof EntityStatusS2CPacket packet && packet.getStatus() == 2) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world != null && this.lastTargetId != -1) {
                Entity ent = mc.world.getEntityById(this.lastTargetId);
                if (ent instanceof LivingEntity living) {
                    long elapsed = System.currentTimeMillis() - this.lastHitTime;
                    if (elapsed > 120L) {
                        triggerHit(living);
                    }
                }
            }
        }
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options.hudHidden) return;

        long elapsed = System.currentTimeMillis() - this.lastHitTime;
        float maxDur = (float) this.duration.get();
        if (elapsed >= maxDur || this.lastHitTime == 0L) {
            return;
        }

        float progress = (float) elapsed / maxDur;
        float alpha = (1.0f - progress) * (1.0f - progress);
        if (alpha <= 0.005f) {
            return;
        }

        float scaleFactor = this.dynamicScale.get() ? (1.0f + (1.0f - progress) * 0.30f) : 1.0f;
        float renderSize = (float) this.size.get() * scaleFactor;

        float cx;
        float cy;

        if (this.renderMode.is("В мире")) {
            Vector2f screenPos = ProjectUtil.toScreen(this.hitX, this.hitY, this.hitZ);
            if (!ProjectUtil.isOnScreen(screenPos)) {
                return;
            }
            cx = screenPos.x();
            cy = screenPos.y();
        } else {
            cx = mc.getWindow().getScaledWidth() / 2.0f;
            cy = mc.getWindow().getScaledHeight() / 2.0f;
        }

        int drawX = Math.round(cx - (renderSize * 0.5f));
        int drawY = Math.round(cy - (renderSize * 0.5f));
        int isize = Math.max(1, Math.round(renderSize));

        int baseCol = -1;
        if (this.colorMode.is("Тема")) {
            baseCol = Theme.getAccent();
        } else if (this.colorMode.is("Кастом")) {
            baseCol = this.customColor.get();
        }

        int r = (baseCol >> 16) & 0xFF;
        int g = (baseCol >> 8) & 0xFF;
        int b = baseCol & 0xFF;
        int a = Math.round(alpha * 255.0f);
        int finalColor = ColorUtil.rgba(r, g, b, a);

        context.drawTexture(RenderPipelines.GUI_TEXTURED, HITMARKER_TEXTURE, drawX, drawY, 0.0f, 0.0f, isize, isize, isize, isize, isize, isize, finalColor);
    }
}

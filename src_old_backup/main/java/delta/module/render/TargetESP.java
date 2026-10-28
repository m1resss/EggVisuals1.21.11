package delta.module.render;

import static delta.core.Interface.aM_;

import delta.core.Category;
import delta.core.EventTarget;
import delta.core.Interface;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.TickEvent;
import delta.render.ColorUtil;
import delta.setting.BooleanSetting;
import delta.setting.ColorSetting;
import delta.setting.ModeSetting;
import delta.setting.SliderSetting;
import lombok.Generated;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Optional;

@ModuleRegister(a = "Target ESP", b = "Визуализация цели при наведении прицела", c = Category.Render)
public class TargetESP extends Module implements Interface {
    private static TargetESP instance;

    private final ModeSetting mode = new ModeSetting("Визуализация цели", "Алмаз", "Алмаз", "Ромб", "Призраки", "Sadnes Молнии", "Круг", "Ghosts", "Scan");
    private final SliderSetting rhombusHeight = (SliderSetting) new SliderSetting("Высота ромба", 0.0f, -0.8f, 1.5f, 0.05f).a(() -> Boolean.valueOf(this.mode.l("Ромб")));
    private final SliderSetting rhombusSize = (SliderSetting) new SliderSetting("Размер ромба", 1.25f, 0.3f, 2.5f, 0.05f).a(() -> Boolean.valueOf(this.mode.l("Ромб")));
    private final ModeSetting lightningColor = (ModeSetting) new ModeSetting("Цвет молний", "Тема", "Тема", "Кастом", "Белый").a(() -> Boolean.valueOf(this.mode.l("Sadnes Молнии") || this.mode.l("sadnes Молнии")));
    private final ColorSetting lightningCustomColor = (ColorSetting) new ColorSetting("Кастом цвет молний", Integer.valueOf(ColorUtil.a(86, 180, 252, 255))).a(() -> Boolean.valueOf((this.mode.l("Sadnes Молнии") || this.mode.l("sadnes Молнии")) && this.lightningColor.l("Кастом")));
    private final SliderSetting lightningSpeed = (SliderSetting) new SliderSetting("Скорость смены", 0.5f, 0.1f, 1.0f, 0.05f).a(() -> Boolean.valueOf(this.mode.l("Sadnes Молнии")));
    private final SliderSetting lightningBranches = (SliderSetting) new SliderSetting("Количество молний", 1.0f, 0.1f, 5.0f, 0.1f).a(() -> Boolean.valueOf(this.mode.l("Sadnes Молнии")));
    private final SliderSetting lightningFade = (SliderSetting) new SliderSetting("Длительность фейда", 0.2f, 0.05f, 1.0f, 0.05f).a(() -> Boolean.valueOf(this.mode.l("Sadnes Молнии")));
    private final SliderSetting lightningGlowPower = (SliderSetting) new SliderSetting("Сила свечения", 2.0f, 0.0f, 5.0f, 0.1f).a(() -> Boolean.valueOf(this.mode.l("Sadnes Молнии")));
    private final BooleanSetting lightningBloom = (BooleanSetting) new BooleanSetting("Частицы блума", true).a(() -> Boolean.valueOf(this.mode.l("Sadnes Молнии")));
    private final SliderSetting scanSpeed = (SliderSetting) new SliderSetting("Скорость скана", 1.2f, 0.2f, 3.0f, 0.1f).a(() -> Boolean.valueOf(this.mode.l("Scan")));
    private final SliderSetting scanGlow = (SliderSetting) new SliderSetting("Свечение скана", 0.8f, 0.1f, 2.0f, 0.05f).a(() -> Boolean.valueOf(this.mode.l("Scan")));
    private final SliderSetting maxDistance = new SliderSetting("Дистанция наводки", 30.0f, 5.0f, 64.0f, 1.0f);
    private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", true);

    private LivingEntity target;
    private int loseTargetTicks = 0;

    public TargetESP() {
        instance = this;
        a(this.mode, this.rhombusHeight, this.rhombusSize, this.lightningColor, this.lightningCustomColor, this.lightningSpeed,
          this.lightningBranches, this.lightningFade, this.lightningGlowPower, this.lightningBloom,
          this.scanSpeed, this.scanGlow,
          this.maxDistance, this.onlyPlayers);
    }

    public SliderSetting getScanSpeed() {
        return this.scanSpeed;
    }

    public SliderSetting getScanGlow() {
        return this.scanGlow;
    }

    public static TargetESP getInstance() {
        return instance;
    }

    public LivingEntity getTarget() {
        return this.target;
    }

    @Generated
    public ModeSetting getMode() {
        return this.mode;
    }

    @Generated
    public SliderSetting getRhombusHeight() {
        return this.rhombusHeight;
    }

    @Generated
    public SliderSetting getRhombusSize() {
        return this.rhombusSize;
    }

    @Generated
    public ModeSetting getLightningColor() {
        return this.lightningColor;
    }

    @Generated
    public ColorSetting getLightningCustomColor() {
        return this.lightningCustomColor;
    }

    @Generated
    public SliderSetting getLightningSpeed() {
        return this.lightningSpeed;
    }

    @Generated
    public SliderSetting getLightningBranches() {
        return this.lightningBranches;
    }

    @Generated
    public SliderSetting getLightningFade() {
        return this.lightningFade;
    }

    @Generated
    public SliderSetting getLightningGlowPower() {
        return this.lightningGlowPower;
    }

    @Generated
    public BooleanSetting getLightningBloom() {
        return this.lightningBloom;
    }

    @Generated
    public SliderSetting getMaxDistance() {
        return this.maxDistance;
    }

    @Generated
    public BooleanSetting getOnlyPlayers() {
        return this.onlyPlayers;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (aM_.player == null || aM_.world == null) {
            this.target = null;
            return;
        }

        LivingEntity found = findCrosshairTarget();
        if (found != null) {
            this.target = found;
            this.loseTargetTicks = 0;
        } else {
            this.loseTargetTicks++;
            if (this.loseTargetTicks > 6 || (this.target != null && !aM_.player.canSee(this.target))) {
                this.target = null;
            }
        }
    }

    private LivingEntity findCrosshairTarget() {
        if (aM_.player == null || aM_.world == null) return null;

        // 1. Direct crosshair target
        if (aM_.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living) {
            if (isValidTarget(living)) {
                return living;
            }
        }

        // 2. Raycast in player looking direction
        Vec3d eyePos = aM_.player.getEyePos();
        Vec3d lookVec = aM_.player.getRotationVec(1.0f);
        double reach = this.maxDistance.c().doubleValue();
        Vec3d endPos = eyePos.add(lookVec.multiply(reach));

        // Obstruct raycast at nearest solid block so we never see through walls
        BlockHitResult blockHit = aM_.world.raycast(new RaycastContext(
                eyePos, endPos, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, aM_.player));
        double maxRayDistSq = reach * reach;
        if (blockHit.getType() != HitResult.Type.MISS) {
            maxRayDistSq = eyePos.squaredDistanceTo(blockHit.getPos());
            endPos = blockHit.getPos();
        }

        Box searchBox = aM_.player.getBoundingBox().stretch(lookVec.multiply(reach)).expand(1.0);

        LivingEntity closest = null;
        double closestDistSq = maxRayDistSq;

        for (Entity entity : aM_.world.getOtherEntities(aM_.player, searchBox)) {
            if (entity instanceof LivingEntity living && isValidTarget(living)) {
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

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == null || entity == aM_.player || !entity.isAlive() || entity.isInvisible() || entity.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.INVISIBILITY)) {
            return false;
        }
        if (this.onlyPlayers.c().booleanValue() && !(entity instanceof PlayerEntity)) {
            return false;
        }
        if (!aM_.player.canSee(entity)) {
            return false;
        }
        double distLimit = this.maxDistance.c().doubleValue();
        return aM_.player.getEyePos().squaredDistanceTo(entity.getEntityPos()) <= distLimit * distLimit;
    }

    @Override
    public void c() {
        super.c();
        this.target = null;
        this.loseTargetTicks = 0;
    }
}

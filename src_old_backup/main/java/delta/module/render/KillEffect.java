package delta.module.render;

import delta.core.Category;
import delta.core.Delta;
import delta.core.EventTarget;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.AttackEvent;
import delta.event.DrawEvent;
import delta.event.TickEvent;
import delta.module.render.killeffect.KillEffectEasing;
import delta.module.render.killeffect.KillEffectSoundQueue;
import delta.render.ColorUtil;
import delta.setting.BooleanSetting;
import delta.setting.ColorSetting;
import delta.setting.ModeSetting;
import delta.setting.MultiModeSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@ModuleRegister(a = "Kill Effect", b = "Эффект при убийстве с виньеткой, частицами, звуками и тряской", c = Category.Render)
public class KillEffect extends Module {
    private static final Identifier VIGNETTE_TEXTURE = Identifier.of("delta", "textures/frag/lightarroundscreen_alpha.png");

    public static final SoundEvent FRAG_EFFECT_ECHO_MAIN = SoundEvent.of(Identifier.of("delta", "frag_effect_echo_main"));
    public static final SoundEvent FRAG_EFFECT_KNOCK_MAIN = SoundEvent.of(Identifier.of("delta", "frag_effect_knock_main"));
    public static final SoundEvent FRAG_EFFECT_PULSE = SoundEvent.of(Identifier.of("delta", "frag_effect_pulse"));
    public static final SoundEvent FRAG_EFFECT_SPARKS_COLLISION = SoundEvent.of(Identifier.of("delta", "frag_effect_sparks_collision"));

    static {
        try {
            Registry.register(Registries.SOUND_EVENT, Identifier.of("delta", "frag_effect_echo_main"), FRAG_EFFECT_ECHO_MAIN);
            Registry.register(Registries.SOUND_EVENT, Identifier.of("delta", "frag_effect_knock_main"), FRAG_EFFECT_KNOCK_MAIN);
            Registry.register(Registries.SOUND_EVENT, Identifier.of("delta", "frag_effect_pulse"), FRAG_EFFECT_PULSE);
            Registry.register(Registries.SOUND_EVENT, Identifier.of("delta", "frag_effect_sparks_collision"), FRAG_EFFECT_SPARKS_COLLISION);
        } catch (Exception ignored) {
        }
    }

    private static KillEffect instance;

    private final ModeSetting colorMode = new ModeSetting("Режим цвета", "Свой", "Радуга", "Свой");
    private final ColorSetting customColor = new ColorSetting("Цвет", 0xFFFF3232);
    private final BooleanSetting spikes = new BooleanSetting("Шипы", true);
    private final BooleanSetting sound = new BooleanSetting("Звук", true);
    private final BooleanSetting cameraShake = new BooleanSetting("Тряска камеры", false);
    private final BooleanSetting zoomEffect = new BooleanSetting("Приближение", false);
    private final MultiModeSetting effectTargets = new MultiModeSetting("Цели",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Мобы", true),
            new BooleanSetting("Животные", true));

    private final KillEffectSoundQueue soundQueue = new KillEffectSoundQueue();
    private final Map<Integer, Long> recentlyAttacked = new HashMap<>();

    private long effectTimer;
    private boolean vignetteActive;
    private long cameraTimer;
    private boolean cameraActive;

    public KillEffect() {
        instance = this;
        a(this.colorMode, this.customColor, this.spikes, this.sound, this.cameraShake, this.zoomEffect, this.effectTargets);
    }

    public static KillEffect getInstance() {
        return instance;
    }

    private int getAccentColor() {
        if (this.colorMode.l("Радуга")) {
            return Color.HSBtoRGB((System.currentTimeMillis() % 3000L) / 3000.0f, 0.85f, 1.0f);
        }
        return this.customColor.c();
    }

    @Override
    public void c() {
        super.c();
        this.recentlyAttacked.clear();
        this.soundQueue.clear();
        this.vignetteActive = false;
        this.cameraActive = false;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (aM_.player == null || aM_.world == null) {
            this.recentlyAttacked.clear();
            this.soundQueue.clear();
            return;
        }
        this.soundQueue.tick();
        pruneAttackMemory();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (aM_.player == null) {
            return;
        }
        if (event.b() instanceof LivingEntity living && living != aM_.player) {
            this.recentlyAttacked.put(living.getId(), System.currentTimeMillis());
            pruneAttackMemory();
        }
    }

    private void pruneAttackMemory() {
        long expireBefore = System.currentTimeMillis() - 2500L;
        this.recentlyAttacked.values().removeIf(t -> t < expireBefore);
    }

    public static void notifyEntityDied(LivingEntity entity, DamageSource source) {
        KillEffect ke = getInstance();
        if (ke == null || !ke.m()) {
            return;
        }
        ke.handleDeath(entity, source);
    }

    private void handleDeath(LivingEntity entity, DamageSource source) {
        if (aM_.player == null || aM_.world == null || entity == null || entity == aM_.player) {
            return;
        }
        if (!matchesTarget(entity)) {
            return;
        }

        Long attackedTime = this.recentlyAttacked.remove(entity.getId());
        boolean recentlyHit = attackedTime != null && (System.currentTimeMillis() - attackedTime <= 2500L);
        boolean primeAdversary = entity.getPrimeAdversary() == aM_.player;
        boolean directAttacker = source != null && source.getAttacker() == aM_.player;

        if (recentlyHit || primeAdversary || directAttacker) {
            onTrackedKill(entity);
        }
    }

    private boolean matchesTarget(LivingEntity entity) {
        if (entity instanceof PlayerEntity) {
            BooleanSetting b = this.effectTargets.a("Игроки");
            return b != null && b.c().booleanValue();
        }
        if (entity instanceof AnimalEntity) {
            BooleanSetting b = this.effectTargets.a("Животные");
            return b != null && b.c().booleanValue();
        }
        if (entity instanceof MobEntity) {
            BooleanSetting b = this.effectTargets.a("Мобы");
            return b != null && b.c().booleanValue();
        }
        return false;
    }

    private void onTrackedKill(LivingEntity entity) {
        if (aM_.player == null || aM_.player.isDead()) {
            return;
        }

        Vec3d eyePos = entity.getEyePos();

        // Start vignette
        this.effectTimer = System.currentTimeMillis();
        this.vignetteActive = true;

        // Spawn particles burst
        if (aM_.world != null) {
            ThreadLocalRandom rand = ThreadLocalRandom.current();
            for (int i = 0; i < 24; i++) {
                double vx = rand.nextDouble(-0.35, 0.35);
                double vy = rand.nextDouble(0.05, 0.45);
                double vz = rand.nextDouble(-0.35, 0.35);
                aM_.world.addParticleClient(ParticleTypes.END_ROD, eyePos.x, eyePos.y, eyePos.z, vx, vy, vz);
            }
            for (int i = 0; i < 16; i++) {
                double vx = rand.nextDouble(-0.4, 0.4);
                double vy = rand.nextDouble(-0.1, 0.3);
                double vz = rand.nextDouble(-0.4, 0.4);
                aM_.world.addParticleClient(ParticleTypes.ELECTRIC_SPARK, eyePos.x, eyePos.y, eyePos.z, vx, vy, vz);
            }
            for (int i = 0; i < 12; i++) {
                double vx = rand.nextDouble(-0.25, 0.25);
                double vy = rand.nextDouble(0.1, 0.35);
                double vz = rand.nextDouble(-0.25, 0.25);
                aM_.world.addParticleClient(ParticleTypes.TOTEM_OF_UNDYING, eyePos.x, eyePos.y, eyePos.z, vx, vy, vz);
            }
        }

        // Schedule sounds
        if (this.sound.c().booleanValue()) {
            this.soundQueue.schedule(FRAG_EFFECT_PULSE, 1.0f, 0L);
            this.soundQueue.schedule(FRAG_EFFECT_KNOCK_MAIN, 1.0f, 150L);
            this.soundQueue.schedule(FRAG_EFFECT_SPARKS_COLLISION, 0.3f, 300L);
            this.soundQueue.schedule(FRAG_EFFECT_ECHO_MAIN, 0.6f, 450L);
            // Fallback experience orb pickup sound
            KillEffectSoundQueue.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f);
        }

        // Start camera shake and zoom
        if (this.cameraShake.c().booleanValue() || this.zoomEffect.c().booleanValue()) {
            this.cameraTimer = System.currentTimeMillis();
            this.cameraActive = true;
        }
    }

    @EventTarget
    public void onDraw(DrawEvent event) {
        if (event.f() != DrawEvent.a.D2D || !this.spikes.c().booleanValue() || !this.vignetteActive) {
            return;
        }

        long elapsed = System.currentTimeMillis() - this.effectTimer;
        if (elapsed >= 600L) {
            this.vignetteActive = false;
            return;
        }

        float progress = Math.min((float) elapsed / 600.0f, 1.0f);
        float sinProgress = (float) Math.sin((double) progress * Math.PI);
        float eased = KillEffectEasing.quintOut(sinProgress);
        float offsetRatio = 1.0f - eased;

        float width = aM_.getWindow().getScaledWidth();
        float height = aM_.getWindow().getScaledHeight();
        float offsetX = width * 0.75f * offsetRatio;
        float offsetY = height * 0.75f * offsetRatio;

        int baseColor = getAccentColor();
        int color = ColorUtil.a(baseColor, sinProgress * 0.7f);

        event.d().a(event.h(), VIGNETTE_TEXTURE, -offsetX, -offsetY, width + offsetX * 2.0f, height + offsetY * 2.0f, 0.0f, color);
    }

    public static float getKillShakeDegrees() {
        KillEffect ke = getInstance();
        if (ke == null || !ke.m() || !ke.cameraShake.c().booleanValue() || !ke.cameraActive) {
            return 0.0f;
        }

        long elapsed = System.currentTimeMillis() - ke.cameraTimer;
        if (elapsed >= 650L) {
            ke.cameraActive = false;
            return 0.0f;
        }

        float progress = (float) elapsed / 650.0f;
        float time = (float) elapsed / 1000.0f;
        float wave = (float) (Math.sin((double) time * 26.0) * 0.65 + Math.sin((double) time * 16.5) * 0.35);
        float falloff = (1.0f - progress) * (1.0f - progress);
        return wave * 0.8f * 2.5f * falloff;
    }

    public static float getKillZoomFovScale() {
        KillEffect ke = getInstance();
        if (ke == null || !ke.m() || !ke.zoomEffect.c().booleanValue() || !ke.cameraActive) {
            return 1.0f;
        }

        long elapsed = System.currentTimeMillis() - ke.cameraTimer;
        if (elapsed >= 650L) {
            return 1.0f;
        }

        float progress = (float) elapsed / 650.0f;
        float env = (float) Math.sin((double) progress * Math.PI);
        float fovDecrease = 15.0f * env;
        float scale = 1.0f - fovDecrease / 70.0f;
        return MathHelper.clamp(scale, 0.5f, 1.0f);
    }
}

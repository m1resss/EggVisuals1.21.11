package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.util.ColorUtil;
import egg.client.util.ProjectUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

import java.util.*;

public class DamageNumbers extends Module {

    private static DamageNumbers instance;

    private static final Identifier FORTNITE_ATLAS = Identifier.of("egg", "textures/fortnite_atlas.png");

    private final BooleanSetting stacking = new BooleanSetting("Стакать урон", true, "Суммировать урон при серии ударов в одну цель");
    private final BooleanSetting critExclamation = new BooleanSetting("Восклицание крита", true, "Добавлять восклицательный знак '!' при критах");
    private final SliderSetting size = new SliderSetting("Размер", 30.0, 16.0, 54.0, 2.0, "Базовый размер цифр урона");
    private final SliderSetting breakDuration = new SliderSetting("Время разлета", 0.42, 0.2, 0.8, 0.05, "Длительность разваливания цифр");

    public static class GlyphMeta {
        public final float x, y, w, h, advance;

        public GlyphMeta(float x, float y, float w, float h, float advance) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.advance = advance;
        }
    }

    public static class ActiveNumber {
        public final int entityId;
        public Vec3d worldPos;
        public float totalDamage;
        public boolean isCrit;
        public boolean isShield;
        public boolean isKill;
        public float age;
        public float punchTimer;
        public boolean shattered;

        public ActiveNumber(int entityId, Vec3d worldPos, float damage, boolean isCrit, boolean isShield, boolean isKill) {
            this.entityId = entityId;
            this.worldPos = worldPos;
            this.totalDamage = damage;
            this.isCrit = isCrit;
            this.isShield = isShield;
            this.isKill = isKill;
            this.age = 0.0f;
            this.punchTimer = 0.12f;
            this.shattered = false;
        }
    }

    public static class DigitShard {
        public final char ch;
        public float x;
        public float y;
        public float vx;
        public float vy;
        public float rotDeg;
        public float rotSpeed;
        public float scale;
        public int color;
        public float age;
        public float maxLife;

        public DigitShard(char ch, float x, float y, float vx, float vy, float rotDeg, float rotSpeed, float scale, int color, float maxLife) {
            this.ch = ch;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.rotDeg = rotDeg;
            this.rotSpeed = rotSpeed;
            this.scale = scale;
            this.color = color;
            this.age = 0.0f;
            this.maxLife = maxLife;
        }
    }

    private final List<ActiveNumber> activeNumbers = new ArrayList<>();
    private final List<DigitShard> shards = new ArrayList<>();
    private final Random random = new Random();
    private long lastFrameNanos = 0L;

    public DamageNumbers() {
        super("DamageNumbers", "Кинетические цифры урона в стиле Fortnite", Category.ESP);
        instance = this;
        addSettings(stacking, critExclamation, size, breakDuration);
    }

    public static DamageNumbers getInstance() {
        return instance;
    }

    public static GlyphMeta getGlyph(char c) {
        return switch (c) {
            case '0' -> new GlyphMeta(4f, 4f, 107f, 137f, 57f);
            case '1' -> new GlyphMeta(119f, 4f, 94f, 133f, 44f);
            case '2' -> new GlyphMeta(221f, 4f, 103f, 135f, 52f);
            case '3' -> new GlyphMeta(332f, 4f, 104f, 138f, 53f);
            case '4' -> new GlyphMeta(444f, 4f, 106f, 134f, 56f);
            case '5' -> new GlyphMeta(558f, 4f, 104f, 135f, 54f);
            case '6' -> new GlyphMeta(670f, 4f, 104f, 137f, 54f);
            case '7' -> new GlyphMeta(782f, 4f, 96f, 137f, 46f);
            case '8' -> new GlyphMeta(886f, 4f, 106f, 139f, 56f);
            case '9' -> new GlyphMeta(4f, 151f, 105f, 138f, 55f);
            case '.' -> new GlyphMeta(117f, 151f, 74f, 74f, 24f);
            case '+' -> new GlyphMeta(199f, 151f, 89f, 112f, 39f);
            case '-' -> new GlyphMeta(296f, 151f, 80f, 97f, 30f);
            case '!' -> new GlyphMeta(384f, 151f, 78f, 135f, 28f);
            case 'x' -> new GlyphMeta(470f, 151f, 106f, 113f, 56f);
            case 'C' -> new GlyphMeta(584f, 151f, 106f, 138f, 56f);
            case 'R' -> new GlyphMeta(698f, 151f, 107f, 139f, 55f);
            case 'I' -> new GlyphMeta(813f, 151f, 78f, 133f, 28f);
            case 'T' -> new GlyphMeta(899f, 151f, 98f, 135f, 48f);
            default -> new GlyphMeta(4f, 4f, 107f, 137f, 57f);
        };
    }

    public void onAttack(PlayerEntity player, Entity target) {
        if (!isEnabled() || target == null || !(target instanceof LivingEntity living)) return;

        float baseDmg = (float) player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        float cooldown = player.getAttackCooldownProgress(0.5f);
        boolean isCrit = HitParticles.isCriticalHit(player, target);

        float rawDmg = baseDmg * (0.2f + cooldown * cooldown * 0.8f);
        if (isCrit) {
            rawDmg *= 1.5f;
        }

        float armor = (float) living.getArmor();
        float toughness = 0.0f;
        try {
            toughness = (float) living.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        } catch (Throwable ignored) {}

        float f = 2.0f + toughness / 4.0f;
        float g = MathHelper.clamp(armor - rawDmg / f, armor * 0.2f, 20.0f);
        float finalDmg = rawDmg * (1.0f - g / 25.0f);

        if (living.hasStatusEffect(StatusEffects.RESISTANCE)) {
            int amp = living.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1;
            finalDmg *= Math.max(0.0f, 1.0f - amp * 0.2f);
        }

        finalDmg = Math.max(1.0f, (float) Math.round(finalDmg * 10.0f) / 10.0f);

        boolean isShield = armor > 0.0f;
        boolean isKill = living.getHealth() <= finalDmg;

        Vec3d hitPos = HitParticles.getHitPosition(player, target);
        recordDamage(target.getId(), hitPos, finalDmg, isCrit, isShield, isKill);
    }

    public void onEntityDamage(Entity entity) {
        if (!isEnabled() || entity == null || !(entity instanceof LivingEntity living)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.player.squaredDistanceTo(entity) > 36.0) return;

        float hp = living.getHealth();
        if (hp <= 0.0f) {
            for (ActiveNumber an : activeNumbers) {
                if (an.entityId == entity.getId()) {
                    an.isKill = true;
                }
            }
        }
    }

    private void recordDamage(int entityId, Vec3d hitPos, float damage, boolean isCrit, boolean isShield, boolean isKill) {
        if (stacking.isEnabled()) {
            for (ActiveNumber an : activeNumbers) {
                if (an.entityId == entityId && !an.shattered && an.age < 0.5f) {
                    an.totalDamage += damage;
                    if (isCrit) an.isCrit = true;
                    if (isKill) an.isKill = true;
                    an.age = 0.0f;
                    an.punchTimer = 0.12f;
                    an.worldPos = new Vec3d(
                            an.worldPos.x * 0.4 + hitPos.x * 0.6,
                            an.worldPos.y * 0.4 + hitPos.y * 0.6,
                            an.worldPos.z * 0.4 + hitPos.z * 0.6
                    );
                    return;
                }
            }
        }

        double rx = (random.nextDouble() - 0.5) * 0.25;
        double ry = (random.nextDouble() - 0.5) * 0.20;
        double rz = (random.nextDouble() - 0.5) * 0.25;
        Vec3d spawnPos = hitPos.add(rx, ry, rz);

        activeNumbers.add(new ActiveNumber(entityId, spawnPos, damage, isCrit, isShield, isKill));
    }

    public void render2D(DrawContext context) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float dt = (float) ((now - lastFrameNanos) * 1e-9);
        lastFrameNanos = now;
        if (dt <= 0.0f || dt > 0.1f) dt = 0.016f;

        float baseSize = size.getValueFloat();
        float shardMaxLife = breakDuration.getValueFloat();

        Iterator<ActiveNumber> itActive = activeNumbers.iterator();
        while (itActive.hasNext()) {
            ActiveNumber an = itActive.next();
            an.age += dt;
            if (an.punchTimer > 0.0f) {
                an.punchTimer = Math.max(0.0f, an.punchTimer - dt);
            }

            Vector2f screen = ProjectUtil.toScreen(an.worldPos.x, an.worldPos.y, an.worldPos.z);
            boolean onScreen = screen.x != Float.MAX_VALUE && screen.y != Float.MAX_VALUE
                    && screen.x >= -100.0f && screen.x <= mc.getWindow().getScaledWidth() + 100.0f
                    && screen.y >= -100.0f && screen.y <= mc.getWindow().getScaledHeight() + 100.0f;

            int dmgInt = Math.max(1, Math.round(an.totalDamage));
            String text = String.valueOf(dmgInt);
            if (an.isCrit && critExclamation.isEnabled()) {
                text = text + "!";
            }

            int color = resolveColor(an);

            float punchFactor = an.punchTimer > 0.0f ? (an.punchTimer / 0.12f) : 0.0f;
            float punchScale = 1.0f + 0.35f * (float) Math.sin(punchFactor * Math.PI);
            float renderHeight = baseSize * (an.isCrit ? 1.25f : 1.0f) * punchScale;

            float curY = screen.y - an.age * 18.0f;

            if (an.age >= 0.5f) {
                an.shattered = true;
                if (onScreen) {
                    spawnShards(text, screen.x, curY, renderHeight, color, shardMaxLife);
                }
                itActive.remove();
                continue;
            }

            if (onScreen) {
                renderText(text, screen.x, curY, renderHeight, color, 0.0f, 1.0f);
            }
        }

        Iterator<DigitShard> itShard = shards.iterator();
        while (itShard.hasNext()) {
            DigitShard ds = itShard.next();
            ds.age += dt;
            if (ds.age >= ds.maxLife) {
                itShard.remove();
                continue;
            }

            float frameStep = dt * 60.0f;
            ds.x += ds.vx * frameStep;
            ds.y += ds.vy * frameStep;
            ds.vy += 0.30f * frameStep;
            ds.rotDeg += ds.rotSpeed * frameStep;

            float fade = Math.max(0.0f, 1.0f - (ds.age / ds.maxLife));
            float alpha = fade * fade;

            renderSingleGlyph(ds.ch, ds.x, ds.y, ds.scale, ds.color, ds.rotDeg, alpha);
        }

        DrawBatcher.flushPending();
    }

    private void spawnShards(String text, float centerX, float centerY, float renderHeight, int color, float maxLife) {
        float fontScale = renderHeight / 136.0f;
        int n = text.length();

        float totalW = 0.0f;
        for (int i = 0; i < n; i++) {
            GlyphMeta g = getGlyph(text.charAt(i));
            totalW += g.advance * fontScale;
        }

        float cursorX = centerX - totalW / 2.0f;

        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            GlyphMeta g = getGlyph(c);
            float charW = g.w * fontScale;
            float charX = cursorX + charW * 0.5f;
            float charY = centerY;

            float spreadDir = (n == 1) ? ((float) random.nextDouble() - 0.5f) : ((float) i / (n - 1) - 0.5f);
            float vx = spreadDir * (2.8f + (float) random.nextDouble() * 1.6f) + ((float) random.nextDouble() - 0.5f) * 1.2f;
            float vy = -2.2f - (float) random.nextDouble() * 2.4f;
            float rotSpeed = ((float) random.nextDouble() - 0.5f) * 22.0f;
            float rotDeg = ((float) random.nextDouble() - 0.5f) * 12.0f;

            shards.add(new DigitShard(c, charX, charY, vx, vy, rotDeg, rotSpeed, renderHeight, color, maxLife));

            cursorX += g.advance * fontScale;
        }
    }

    private void renderText(String text, float centerX, float centerY, float renderHeight, int color, float rotDeg, float alpha) {
        float fontScale = renderHeight / 136.0f;
        int n = text.length();

        float totalW = 0.0f;
        for (int i = 0; i < n; i++) {
            GlyphMeta g = getGlyph(text.charAt(i));
            totalW += g.advance * fontScale;
        }

        float cursorX = centerX - totalW / 2.0f;

        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            GlyphMeta g = getGlyph(c);
            float drawW = g.w * fontScale;
            float drawH = g.h * fontScale;
            float drawX = cursorX + drawW * 0.5f;

            renderSingleGlyph(c, drawX, centerY, renderHeight, color, rotDeg, alpha);

            cursorX += g.advance * fontScale;
        }
    }

    private void renderSingleGlyph(char c, float centerX, float centerY, float renderHeight, int color, float rotDeg, float alpha) {
        if (alpha <= 0.01f) return;

        GlyphMeta g = getGlyph(c);
        float fontScale = renderHeight / 136.0f;
        float drawW = g.w * fontScale;
        float drawH = g.h * fontScale;

        float u0 = g.x / 1024.0f;
        float v0 = g.y / 512.0f;
        float u1 = (g.x + g.w) / 1024.0f;
        float v1 = (g.y + g.h) / 512.0f;

        int finalColor = ColorUtil.withAlphaFloat(color, alpha);

        Draw2DProcessor.INSTANCE.getTexturePipeline().drawTexture(
                FORTNITE_ATLAS,
                centerX - drawW * 0.5f,
                centerY - drawH * 0.5f,
                drawW,
                drawH,
                u0, v0, u1, v1,
                new int[]{finalColor},
                new float[]{0.0f, 0.0f, 0.0f, 0.0f},
                0.0f,
                rotDeg
        );
    }

    private int resolveColor(ActiveNumber an) {
        if (an.isKill) {
            return 0xFFFF2E4C;
        }
        if (an.isCrit) {
            return 0xFFFFD200;
        }
        if (an.isShield) {
            return 0xFF00B0FF;
        }
        return 0xFFFFFFFF;
    }
}

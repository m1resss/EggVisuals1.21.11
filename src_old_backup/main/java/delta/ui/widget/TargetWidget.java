package delta.ui.widget;

import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.core.GlobalEvent;
import delta.core.Interface;
import delta.event.DrawEvent;
import delta.render.EasingList;
import delta.render.Fonts;
import delta.render.ColorUtil;
import delta.util.InventoryUtil;
import delta.util.MathUtil;

import delta.config.ThemeInfo;
import delta.module.misc.StreamerMode;
import delta.ui.element.DragInfo;
import delta.setting.BooleanSetting;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TargetWidget extends Widget {
    private final BooleanSetting f;
    private final BooleanSetting g;
    private LivingEntity j;

    private int lastTargetId = -1;
    private float lastHp = 20.0f;
    private float animHp = 20.0f;
    private float waveAmplitude = 0.9f;
    private float waveSpeed = 4.2f;
    private float wavePhase = 0.0f;
    private float waterTilt = 0.0f;
    private float waterTiltVel = 0.0f;
    private boolean tiltLeft = false;
    private long lastTime = System.currentTimeMillis();
    private final List<DamagePopup> popups = new ArrayList<>();

    public TargetWidget() {
        super(new DragInfo("Таргет-худ", 0.0f, 0.0f, 0.0f, 0.0f));
        this.f = new BooleanSetting("Визуализация предметов", true);
        this.g = new BooleanSetting("Отображать при наводке", false);
        j().a(this);
        a(this.f);
    }

    private static class DamagePopup {
        final int dmg;
        final long spawnTime;
        final float x;
        final float y;

        DamagePopup(int dmg, long spawnTime, float x, float y) {
            this.dmg = dmg;
            this.spawnTime = spawnTime;
            this.x = x;
            this.y = y;
        }
    }

    @Override
    public void a(DrawEvent event) {
        d().a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());
        float anim = a();
        if (anim <= 0.0f || this.j == null) {
            super.a(event);
            return;
        }

        long now = System.currentTimeMillis();
        float dt = (now - this.lastTime) / 1000.0f;
        if (dt <= 0.0f || dt > 0.1f) {
            dt = 0.016f;
        }
        this.lastTime = now;

        // Compact & perfectly proportional dimensions matching Rockstar aesthetic (132x28)
        float cardW = 132.0f;
        float cardH = 28.0f;
        float radius = getRounding();
        float blurVal = getBlur();
        float opacityVal = getOpacity();

        j().c(cardW);
        j().d(cardH);
        float x = j().a();
        float y = j().b();

        // Wave and tilt physics simulation
        this.wavePhase += dt * this.waveSpeed;
        this.waveAmplitude += (0.9f - this.waveAmplitude) * Math.min(1.0f, dt * 5.0f);
        this.waveSpeed += (4.2f - this.waveSpeed) * Math.min(1.0f, dt * 5.0f);

        // Spring damped oscillation for liquid tilt (sloshing left/right upon damage)
        float springK = 54.0f;
        float damping = 7.0f;
        this.waterTiltVel += (-springK * this.waterTilt - damping * this.waterTiltVel) * dt;
        this.waterTilt += this.waterTiltVel * dt;
        this.waterTilt = MathUtil.b(this.waterTilt, -6.5f, 6.5f);

        // Target health calculation & hit detection
        boolean hasInvis = this.j.isInvisible();
        float curHp = hasInvis ? 0.0f : this.j.getHealth();
        float maxHp = Math.max(1.0f, this.j.getMaxHealth());
        this.animHp += (curHp - this.animHp) * Math.min(1.0f, dt * 9.0f);
        float hpPct = hasInvis ? 0.0f : MathUtil.b(this.animHp / maxHp, 0.0f, 1.0f);

        float outerR = 10.5f;
        float R = 9.2f;
        float cx = x + cardW - outerR - 4.0f;
        float cy = y + (cardH / 2.0f);

        int entId = this.j.getId();
        if (entId == this.lastTargetId) {
            if (!hasInvis && curHp < this.lastHp - 0.2f) {
                float dmg = this.lastHp - curHp;
                this.waveAmplitude = 2.8f;
                this.waveSpeed = 10.5f;
                // Alternate tilt direction (sloshes left or right upon damage)
                this.tiltLeft = !this.tiltLeft;
                float dir = this.tiltLeft ? -1.0f : 1.0f;
                this.waterTiltVel += dir * (26.0f + Math.min(22.0f, dmg * 4.0f));
                this.popups.add(new DamagePopup(Math.max(1, Math.round(dmg)), now, cx, cy - 10.0f));
            }
        } else {
            this.animHp = curHp;
            this.waterTilt = 0.0f;
            this.waterTiltVel = 0.0f;
            this.popups.clear();
        }
        this.lastTargetId = entId;
        this.lastHp = curHp;

        // Styling colors matching Rockstar aesthetic
        int hudBg = getHudBackgroundColor();
        float baseA = ((hudBg >> 24) & 0xFF) / 255.0f;
        if (baseA <= 0.0f) baseA = 0.85f;
        int cardBg = ColorUtil.a(hudBg, (int) (opacityVal * baseA * 255.0f * anim));
        int cardBorder = ColorUtil.a(255, 255, 255, (int) (0.12f * 255.0f * anim));
        int titleColor = ColorUtil.a(255, 255, 255, (int) (1.0f * 255.0f * anim));

        // 1. Frosted card background & outline border
        if (blurVal > 0.0f && !event.d().e().e().isEmpty()) {
            event.d().b(event.h(), x, y, cardW, cardH, radius, cardBg, anim);
        } else {
            event.d().a(event.h(), x, y, cardW, cardH, radius, cardBg);
        }
        Widget.drawHudThemeGradient(event.d(), event.h(), x, y, cardW, cardH, radius, anim);
        event.d().a(event.h(), x, y, cardW, cardH, radius, 1.0f, cardBorder);

        // 2. Left: Avatar / Head Container
        float headPad = 3.5f;
        float boxSz = 21.0f;
        float boxY = y + ((cardH - boxSz) / 2.0f);

        event.d().a(event.h(), x + headPad, boxY, boxSz, boxSz, 3.0f, ColorUtil.a(24, 26, 33, (int) (200 * anim)));
        if (this.j instanceof AbstractClientPlayerEntity player) {
            int skinTex = delta.render.RenderStateHelper.getTextureId(player.getSkin().body().id());
            event.d().a(event.h(), x + headPad, boxY, boxSz, boxSz, 3.0f, ColorUtil.a(-1, anim), 0.125f, 0.125f, 0.125f, 0.125f, skinTex);
            event.d().a(event.h(), x + headPad, boxY, boxSz, boxSz, 3.0f, ColorUtil.a(-1, anim), 0.625f, 0.125f, 0.125f, 0.125f, skinTex);
        } else {
            Fonts.a.a(event.h(), "B", x + headPad + ((boxSz - 14.0f) / 2.0f), boxY + ((boxSz - 14.0f) / 2.0f), 14.0f, ColorUtil.a(-1, anim));
        }

        // 3. Center: Target Name
        float contentX = x + headPad + boxSz + 5.0f;
        String name = this.j.getName().getString();
        StreamerMode streamerMode = Delta.h().d().t().aE();
        if (streamerMode.m() && streamerMode.r().c().booleanValue()) {
            name = streamerMode.a(name);
        }

        float maxNameW = (cx - outerR - 4.0f) - contentX;
        if (Fonts.e.a(name, 8.0f) > maxNameW) {
            Fonts.e.c(event.h(), name, contentX, y + 3.0f, 8.0f, titleColor, maxNameW);
        } else {
            Fonts.e.a(event.h(), name, contentX, y + 3.0f, 8.0f, titleColor);
        }

        // 4. Center bottom: 6 equipment slots (Head, Chest, Legs, Feet, Main Hand, Off Hand)
        if (this.f.c().booleanValue()) {
            ItemStack[] equipStacks = new ItemStack[]{
                this.j.getEquippedStack(EquipmentSlot.HEAD),
                this.j.getEquippedStack(EquipmentSlot.CHEST),
                this.j.getEquippedStack(EquipmentSlot.LEGS),
                this.j.getEquippedStack(EquipmentSlot.FEET),
                this.j.getMainHandStack(),
                this.j.getOffHandStack()
            };
            float slotSz = 10.5f;
            float slotGap = 2.0f;
            float slotsY = y + 14.0f;
            int slotBg = ColorUtil.a(36, 40, 48, (int) (190 * anim));

            for (int i = 0; i < 6; i++) {
                float sx = contentX + (i * (slotSz + slotGap));
                event.d().a(event.h(), sx, slotsY, slotSz, slotSz, 2.0f, slotBg);
                ItemStack stack = equipStacks[i];
                if (stack != null && !stack.isEmpty()) {
                    float itemScale = 0.50f;
                    float itemOffset = (slotSz - 16.0f * itemScale) / 2.0f;
                    event.e().a(event.i(), InventoryUtil.a(stack), sx + itemOffset, slotsY + itemOffset, 0, anim, itemScale, false);
                }
            }
        }

        // 5. Right: Spherical Flask with Animated Water, Tilt on Hit & Glass Glare
        // 5a. Outer Bezel / Socket (depth around the flask bulb)
        event.d().a(event.h(), cx - outerR, cy - outerR, 2.0f * outerR, 2.0f * outerR, outerR, ColorUtil.a(24, 28, 36, (int) (160 * anim)));
        event.d().a(event.h(), cx - outerR, cy - outerR, 2.0f * outerR, 2.0f * outerR, outerR, 1.0f, ColorUtil.a(255, 255, 255, (int) (18 * anim)));

        // 5b. Flask interior dark backdrop
        event.d().a(event.h(), cx - R, cy - R, 2.0f * R, 2.0f * R, R, ColorUtil.a(15, 17, 22, (int) (235 * anim)));

        // 5c. Water Color: Reacts to Interface theme, boosted to high saturation & max brightness so it glows vividly in any theme
        int primaryColor = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
        boolean isLowHp = (hpPct <= 0.33f);
        int brR, brG, brB;
        if (isLowHp) {
            brR = 255; brG = 45; brB = 60;
        } else {
            int pr = (primaryColor >> 16) & 0xFF;
            int pg = (primaryColor >> 8) & 0xFF;
            int pb = primaryColor & 0xFF;
            float[] hsb = java.awt.Color.RGBtoHSB(pr, pg, pb, null);
            int brightRgb = java.awt.Color.HSBtoRGB(hsb[0], Math.max(0.85f, hsb[1]), 1.0f) & 0x00FFFFFF;
            brR = (brightRgb >> 16) & 0xFF;
            brG = (brightRgb >> 8) & 0xFF;
            brB = brightRgb & 0xFF;
        }

        // Luminous internal liquid backlight glow inside the bulb
        event.d().a(event.h(), cx - R * 0.7f, cy, 1.4f * R, R * 0.7f, R * 0.35f, ColorUtil.a(brR, brG, brB, (int) (40 * anim)));

        float displayHpPct = Math.min(0.84f, Math.max(0.08f, hpPct));
        float yBase = cy + R - (2.0f * R * displayHpPct);
        float A = this.waveAmplitude;
        float k = 0.40f;
        float phase = this.wavePhase;
        int xStart = (int) Math.floor(cx - R);
        int xEnd = (int) Math.ceil(cx + R);

        for (int px = xStart; px <= xEnd; px++) {
            float dx = (px + 0.5f) - cx;
            if (dx * dx >= R * R) {
                continue;
            }
            float dy = (float) Math.sqrt(R * R - (dx * dx));
            float topC = cy - dy;
            float botC = cy + dy;

            // Tilt slope from impact impulse (tilts left or right upon damage)
            float tiltOffset = (dx / R) * this.waterTilt;

            float yWave = yBase + tiltOffset
                + (A * (float) Math.sin((k * dx) + phase))
                + (0.35f * A * (float) Math.cos((k * 1.8f * dx) - (phase * 1.4f)));
            float yTop = Math.max(topC, Math.min(botC, yWave));
            if (botC > yTop) {
                float colH = botC - yTop;
                int waterBody = ColorUtil.a(brR, brG, brB, (int) (245 * anim));
                event.d().a(event.h(), px, yTop, 1.0f, colH, 0.0f, waterBody);
                // Sparkling white crest line on water surface
                event.d().a(event.h(), px, yTop, 1.0f, 1.0f, 0.0f, ColorUtil.a(255, 255, 255, (int) (255 * anim)));
                if (colH > 2.0f) {
                    int foam = ColorUtil.a(Math.min(255, brR + 70), Math.min(255, brG + 70), Math.min(255, brB + 70), (int) (235 * anim));
                    event.d().a(event.h(), px, yTop + 1.0f, 1.0f, 1.0f, 0.0f, foam);
                }
            }
        }

        // 5d. Flask Glass Rim (inner outline)
        event.d().a(event.h(), cx - R, cy - R, 2.0f * R, 2.0f * R, R, 1.0f, ColorUtil.a(255, 255, 255, (int) (45 * anim)));

        // 5e. Flask Glass Glare: Semi-circular curved arc on the left side (полукруглый дугой слева)
        float glareR = R - 1.5f;
        float arcLimitY = R * 0.78f;
        int yStart = (int) Math.floor(cy - arcLimitY);
        int yEnd = (int) Math.ceil(cy + arcLimitY);

        for (int gy = yStart; gy <= yEnd; gy++) {
            float gdy = (gy + 0.5f) - cy;
            float radSq = (glareR * glareR) - (gdy * gdy);
            if (radSq <= 0.0f) continue;

            float gdx = -(float) Math.sqrt(radSq);
            float gx = cx + gdx;

            // Smooth cosine-like falloff towards the arc tips
            float normY = gdy / arcLimitY;
            float factor = (float) Math.cos(normY * (Math.PI / 2.0));
            if (factor <= 0.0f) continue;

            // Main bright arc line
            int arcAlpha = (int) (200.0f * factor * anim);
            event.d().a(event.h(), gx, gy, 1.3f, 1.1f, 0.5f, ColorUtil.a(255, 255, 255, arcAlpha));

            // Soft inner glow reflection
            int softAlpha = (int) (70.0f * factor * anim);
            event.d().a(event.h(), gx + 0.9f, gy, 1.0f, 1.0f, 0.5f, ColorUtil.a(255, 255, 255, softAlpha));
        }

        // Specular highlight accent at top-left curve of the arc
        float hlAngle = (float) Math.toRadians(140.0);
        float hlX = cx + (glareR * (float) Math.cos(hlAngle));
        float hlY = cy - (glareR * (float) Math.sin(hlAngle));
        event.d().a(event.h(), hlX - 1.0f, hlY - 1.0f, 2.2f, 2.2f, 1.1f, ColorUtil.a(255, 255, 255, (int) (160 * anim)));

        // 5f. Health text centered in the flask
        String hpStr = hasInvis ? "?" : String.valueOf((int) Math.round(curHp));
        Fonts.e.b(event.h(), hpStr, cx, cy - 3.5f, 8.0f, titleColor);

        // 6. Floating Damage Popups (red text with dark outline drifting upwards)
        Iterator<DamagePopup> popIt = this.popups.iterator();
        while (popIt.hasNext()) {
            DamagePopup p = popIt.next();
            float age = (now - p.spawnTime) / 1000.0f;
            if (age >= 1.1f) {
                popIt.remove();
                continue;
            }
            float prog = age / 1.1f;
            float driftY = -((float) Math.pow(prog, 0.65f)) * 16.0f;
            float pAlpha = (1.0f - prog) * anim;
            int redColor = ColorUtil.a(240, 45, 55, (int) (255 * pAlpha));
            int shadowColor = ColorUtil.a(0, 0, 0, (int) (200 * pAlpha));
            String dmgStr = "-" + p.dmg;
            float py = p.y + driftY;

            Fonts.e.b(event.h(), dmgStr, p.x + 0.5f, py + 0.5f, 8.0f, shadowColor);
            Fonts.e.b(event.h(), dmgStr, p.x - 0.5f, py, 8.0f, shadowColor);
            Fonts.e.b(event.h(), dmgStr, p.x, py - 0.5f, 8.0f, shadowColor);
            Fonts.e.b(event.h(), dmgStr, p.x, py, 8.0f, redColor);
        }

        super.a(event);
    }

    @Override
    public void a(GlobalEvent event) {
        LivingEntity crosshair = null;
        delta.module.render.TargetESP targetESP = delta.module.render.TargetESP.getInstance();
        LivingEntity espTarget = (targetESP != null && targetESP.m()) ? targetESP.getTarget() : null;

        if (this.g.c().booleanValue() || espTarget != null) {
            if (Interface.aM_.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living) {
                if (living instanceof PlayerEntity && living != Interface.aM_.player) {
                    crosshair = living;
                }
            }
        }

        LivingEntity target;
        if (espTarget != null) {
            target = espTarget;
        } else if (crosshair != null) {
            target = crosshair;
        } else {
            target = (Interface.aM_.currentScreen instanceof ChatScreen) ? Interface.aM_.player : null;
        }

        boolean visible = (target != null);
        if (target != null) {
            this.j = target;
        }
        d().a(visible);
        if (!visible && d().a() <= 0.0f) {
            this.j = null;
        }
        super.a(event);
    }
}

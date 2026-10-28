package delta.module.render;

import delta.core.Category;
import delta.core.EventTarget;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.ClickEvent;
import delta.event.DrawEvent;
import delta.event.HandViewEvent;
import delta.event.ScrollEvent;
import delta.render.Fonts;
import delta.setting.ButtonSetting;
import delta.setting.SliderSetting;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

@ModuleRegister(a = "View Model", b = "Изменяет положение и размер предметов в руке через чат и слайдеры", c = Category.Render)
public class ViewModel extends Module {
    private final SliderSetting b = new SliderSetting("Основная рука X", 0.0f, -3.0f, 3.0f, 0.05f);
    private final SliderSetting c = new SliderSetting("Основная рука Y", 0.0f, -3.0f, 3.0f, 0.05f);
    private final SliderSetting d = new SliderSetting("Основная рука Z", 0.0f, -3.0f, 3.0f, 0.05f);
    private final SliderSetting mainScaleSetting = new SliderSetting("Основная рука Размер", 1.0f, 0.2f, 5.0f, 0.05f);

    private final SliderSetting e = new SliderSetting("Вторая рука X", 0.0f, -3.0f, 3.0f, 0.05f);
    private final SliderSetting f = new SliderSetting("Вторая рука Y", 0.0f, -3.0f, 3.0f, 0.05f);
    private final SliderSetting g = new SliderSetting("Вторая рука Z", 0.0f, -3.0f, 3.0f, 0.05f);
    private final SliderSetting offScaleSetting = new SliderSetting("Вторая рука Размер", 1.0f, 0.2f, 5.0f, 0.05f);

    private final ButtonSetting h = new ButtonSetting("Сбросить позиции", this::resetLayout);

    // Interactive drag state (from Kimiko)
    private float mainOffsetX;
    private float mainOffsetY;
    private float offOffsetX;
    private float offOffsetY;
    private float mainTargetX;
    private float mainTargetY;
    private float offTargetX;
    private float offTargetY;
    private float mainScaleCurrent = 1.0f;
    private float offScaleCurrent = 1.0f;
    private float mainScaleTarget = 1.0f;
    private float offScaleTarget = 1.0f;

    private Hand activeHand;
    private boolean holding;
    private long lastClickMs;
    private long lastScrollTime;
    private float grabCursorX;
    private float grabCursorY;
    private float grabOffsetX;
    private float grabOffsetY;

    public ViewModel() {
        a(this.b, this.c, this.d, this.mainScaleSetting, this.e, this.f, this.g, this.offScaleSetting, this.h);
        syncFromSettings();
    }

    private void syncFromSettings() {
        this.mainOffsetX = this.mainTargetX = this.b.h().floatValue();
        this.mainOffsetY = this.mainTargetY = this.c.h().floatValue();
        this.mainScaleCurrent = this.mainScaleTarget = this.mainScaleSetting.h().floatValue();

        this.offOffsetX = this.offTargetX = this.e.h().floatValue();
        this.offOffsetY = this.offTargetY = this.f.h().floatValue();
        this.offScaleCurrent = this.offScaleTarget = this.offScaleSetting.h().floatValue();
    }

    private void syncToSettings() {
        this.b.a(this.mainTargetX);
        this.c.a(this.mainTargetY);
        this.mainScaleSetting.a(this.mainScaleTarget);

        this.e.a(this.offTargetX);
        this.f.a(this.offTargetY);
        this.offScaleSetting.a(this.offScaleTarget);
    }

    public void resetLayout() {
        this.holding = false;
        this.activeHand = null;
        this.mainTargetX = this.mainOffsetX = 0.0f;
        this.mainTargetY = this.mainOffsetY = 0.0f;
        this.offTargetX = this.offOffsetX = 0.0f;
        this.offTargetY = this.offOffsetY = 0.0f;
        this.mainScaleTarget = this.mainScaleCurrent = 1.0f;
        this.offScaleTarget = this.offScaleCurrent = 1.0f;
        this.d.a(0.0f);
        this.g.a(0.0f);
        syncToSettings();
    }

    private boolean inChat() {
        return aM_.currentScreen instanceof ChatScreen;
    }

    @Override
    public void c() {
        super.c();
        this.holding = false;
        this.activeHand = null;
    }

    @EventTarget
    public void onClick(ClickEvent event) {
        if (!m() || !inChat() || event.h() != 0) {
            return;
        }

        float mouseX = (float) aM_.mouse.getX() * (float) aM_.getWindow().getScaledWidth() / (float) aM_.getWindow().getWidth();
        float mouseY = (float) aM_.mouse.getY() * (float) aM_.getWindow().getScaledHeight() / (float) aM_.getWindow().getHeight();

        if (event.b()) { // PRESS
            long now = System.currentTimeMillis();
            boolean doubleClick = now - this.lastClickMs <= 300L;
            this.lastClickMs = now;
            if (!doubleClick) {
                return;
            }

            this.activeHand = pickHand(mouseX);
            this.holding = true;
            this.grabCursorX = mouseX;
            this.grabCursorY = mouseY;
            this.grabOffsetX = this.activeHand == Hand.MAIN_HAND ? this.mainTargetX : this.offTargetX;
            this.grabOffsetY = this.activeHand == Hand.MAIN_HAND ? this.mainTargetY : this.offTargetY;
        } else if (event.c()) { // RELEASE
            if (this.holding) {
                this.holding = false;
                syncToSettings();
            }
        }
    }

    @EventTarget
    public void onScroll(ScrollEvent event) {
        if (!m() || !inChat() || event.c() == 0.0) {
            return;
        }

        float mouseX = (float) aM_.mouse.getX() * (float) aM_.getWindow().getScaledWidth() / (float) aM_.getWindow().getWidth();
        Hand hand = this.holding && this.activeHand != null ? this.activeHand : pickHand(mouseX);
        if (hand == null) {
            hand = Hand.MAIN_HAND;
        }
        this.activeHand = hand;

        float step = event.c() > 0.0 ? 0.08f : -0.08f;
        if (hand == Hand.MAIN_HAND) {
            this.mainScaleTarget = MathHelper.clamp(this.mainScaleTarget + step, 0.2f, 5.0f);
        } else {
            this.offScaleTarget = MathHelper.clamp(this.offScaleTarget + step, 0.2f, 5.0f);
        }
        syncToSettings();

        this.lastScrollTime = System.currentTimeMillis();
        event.a(true);
    }

    private Hand pickHand(float mouseX) {
        float halfWidth = aM_.getWindow().getScaledWidth() * 0.5f;
        boolean rightSide = mouseX >= halfWidth;
        Arm mainArm = aM_.player != null ? aM_.player.getMainArm() : aM_.options.getMainArm().getValue();
        boolean mainIsRight = mainArm == Arm.RIGHT;
        return rightSide == mainIsRight ? Hand.MAIN_HAND : Hand.OFF_HAND;
    }

    @EventTarget
    public void onDraw(DrawEvent event) {
        if (event.f() != DrawEvent.a.D2D) {
            return;
        }

        boolean inChatScreen = inChat();
        if (!inChatScreen) {
            this.holding = false;
        }

        long timeSinceScroll = System.currentTimeMillis() - this.lastScrollTime;

        if (inChatScreen && this.holding && this.activeHand != null) {
            float mouseX = (float) aM_.mouse.getX() * (float) aM_.getWindow().getScaledWidth() / (float) aM_.getWindow().getWidth();
            float mouseY = (float) aM_.mouse.getY() * (float) aM_.getWindow().getScaledHeight() / (float) aM_.getWindow().getHeight();

            float screenH = Math.max(1.0f, aM_.getWindow().getScaledHeight());
            float factor = 3.2f / screenH;
            float newX = this.grabOffsetX + (mouseX - this.grabCursorX) * factor;
            float newY = this.grabOffsetY - (mouseY - this.grabCursorY) * factor;

            if (this.activeHand == Hand.MAIN_HAND) {
                this.mainTargetX = MathHelper.clamp(newX, -3.0f, 3.0f);
                this.mainTargetY = MathHelper.clamp(newY, -3.0f, 3.0f);
            } else {
                this.offTargetX = MathHelper.clamp(newX, -3.0f, 3.0f);
                this.offTargetY = MathHelper.clamp(newY, -3.0f, 3.0f);
            }
            syncToSettings();
        }

        // Show feedback badge near cursor when dragging or scrolling
        if (inChatScreen && (this.holding || timeSinceScroll < 2500L)) {
            float mouseX = (float) aM_.mouse.getX() * (float) aM_.getWindow().getScaledWidth() / (float) aM_.getWindow().getWidth();
            float mouseY = (float) aM_.mouse.getY() * (float) aM_.getWindow().getScaledHeight() / (float) aM_.getWindow().getHeight();

            Hand displayHand = this.activeHand != null ? this.activeHand : pickHand(mouseX);
            String handName = displayHand == Hand.MAIN_HAND ? "Основная рука" : "Вторая рука";
            float scaleVal = displayHand == Hand.MAIN_HAND ? this.mainScaleTarget : this.offScaleTarget;
            float posX = displayHand == Hand.MAIN_HAND ? this.mainTargetX : this.offTargetX;
            float posY = displayHand == Hand.MAIN_HAND ? this.mainTargetY : this.offTargetY;

            String info = String.format("%s: Размер %.2fx | X: %.2f, Y: %.2f", handName, scaleVal, posX, posY);
            Fonts.b.a(event.h(), info, mouseX + 12.0f, mouseY - 12.0f, 6.5f, 0xFFFFFFFF);
        }

        // Keep values in sync with GUI settings if adjusted from ClickGUI
        if (!inChatScreen && !this.holding && timeSinceScroll > 2000L) {
            this.mainScaleTarget = this.mainScaleSetting.h().floatValue();
            this.offScaleTarget = this.offScaleSetting.h().floatValue();
            this.mainTargetX = this.b.h().floatValue();
            this.mainTargetY = this.c.h().floatValue();
            this.offTargetX = this.e.h().floatValue();
            this.offTargetY = this.f.h().floatValue();
        }

        // Smooth follow
        float follow = this.holding ? 1.0f : 0.45f;
        this.mainOffsetX += (this.mainTargetX - this.mainOffsetX) * follow;
        this.mainOffsetY += (this.mainTargetY - this.mainOffsetY) * follow;
        this.offOffsetX += (this.offTargetX - this.offOffsetX) * follow;
        this.offOffsetY += (this.offTargetY - this.offOffsetY) * follow;

        this.mainScaleCurrent += (this.mainScaleTarget - this.mainScaleCurrent) * 0.35f;
        this.offScaleCurrent += (this.offScaleTarget - this.offScaleCurrent) * 0.35f;
    }

    @EventTarget
    public void onHandView(HandViewEvent event) {
        MatrixStack matrix = event.b();
        boolean isMain = event.d().equals(Hand.MAIN_HAND);

        float x = isMain ? this.mainOffsetX : this.offOffsetX;
        float y = isMain ? this.mainOffsetY : this.offOffsetY;
        float z = isMain ? this.d.h().floatValue() : this.g.h().floatValue();
        float scale = isMain ? this.mainScaleCurrent : this.offScaleCurrent;

        matrix.translate(x, y, z);
        if (scale != 1.0f) {
            Arm mainArm = aM_.player != null ? aM_.player.getMainArm() : aM_.options.getMainArm().getValue();
            Arm arm = isMain ? mainArm : mainArm.getOpposite();
            float px = (arm == Arm.RIGHT ? 0.56f : -0.56f);
            float py = -0.52f;
            float pz = -0.72f;

            matrix.translate(px, py, pz);
            matrix.scale(scale, scale, scale);
            matrix.translate(-px, -py, -pz);
        }
    }
}

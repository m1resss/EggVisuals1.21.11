package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.ButtonSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.Render2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

public class ViewModel extends Module {
    private final ModeSetting mode = new ModeSetting("Режим", "Ползунки", "Режим настройки ViewModel", "Ползунки", "Через чат");

    private final SliderSetting mainX = new SliderSetting("Основная рука X", 0.0, -3.0, 3.0, 0.05, "Смещение главной руки по X");
    private final SliderSetting mainY = new SliderSetting("Основная рука Y", 0.0, -3.0, 3.0, 0.05, "Смещение главной руки по Y");
    private final SliderSetting mainZ = new SliderSetting("Основная рука Z", 0.0, -3.0, 3.0, 0.05, "Смещение главной руки по Z");
    private final SliderSetting mainScaleSetting = new SliderSetting("Основная рука Размер", 1.0, 0.2, 5.0, 0.05, "Масштаб главной руки");

    private final SliderSetting offX = new SliderSetting("Вторая рука X", 0.0, -3.0, 3.0, 0.05, "Смещение второй руки по X");
    private final SliderSetting offY = new SliderSetting("Вторая рука Y", 0.0, -3.0, 3.0, 0.05, "Смещение второй руки по Y");
    private final SliderSetting offZ = new SliderSetting("Вторая рука Z", 0.0, -3.0, 3.0, 0.05, "Смещение второй руки по Z");
    private final SliderSetting offScaleSetting = new SliderSetting("Вторая рука Размер", 1.0, 0.2, 5.0, 0.05, "Масштаб второй руки");

    private final ButtonSetting resetBtn = new ButtonSetting("Сбросить позиции", "Сбросить все настройки рук", this::resetLayout);

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
        super("View Model", "Изменяет положение и размер предметов в руке через ползунки или интерактивно в чате", Category.ESP);
        addSettings(mode, mainX, mainY, mainZ, mainScaleSetting, offX, offY, offZ, offScaleSetting, resetBtn);
        syncFromSettings();
    }

    private void syncFromSettings() {
        this.mainOffsetX = this.mainTargetX = (float) this.mainX.get();
        this.mainOffsetY = this.mainTargetY = (float) this.mainY.get();
        this.mainScaleCurrent = this.mainScaleTarget = (float) this.mainScaleSetting.get();

        this.offOffsetX = this.offTargetX = (float) this.offX.get();
        this.offOffsetY = this.offTargetY = (float) this.offY.get();
        this.offScaleCurrent = this.offScaleTarget = (float) this.offScaleSetting.get();
    }

    private void syncToSettings() {
        this.mainX.set(this.mainTargetX);
        this.mainY.set(this.mainTargetY);
        this.mainScaleSetting.set(this.mainScaleTarget);

        this.offX.set(this.offTargetX);
        this.offY.set(this.offTargetY);
        this.offScaleSetting.set(this.offScaleTarget);
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
        this.mainZ.set(0.0);
        this.offZ.set(0.0);
        syncToSettings();
    }

    private boolean inChat() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc != null && mc.currentScreen instanceof ChatScreen;
    }

    public boolean isMouseOverChat(float mouseX, float mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return false;

        float screenH = mc.getWindow().getScaledHeight();

        if (mouseY >= screenH - 24.0f) {
            return true;
        }

        int rawW = 320;
        int rawH = 140;
        try {
            if (mc.options != null) {
                rawW = ChatHud.getWidth(mc.options.getChatWidth().getValue());
                rawH = ChatHud.getHeight(mc.options.getChatHeightFocused().getValue());
            }
        } catch (Throwable ignored) {
        }

        float chatW = (float) Math.max(280.0, rawW);
        float chatH = (float) Math.max(120.0, rawH);
        float chatTop = Math.max(0.0f, screenH - 40.0f - chatH);
        float chatBottom = screenH;
        float chatLeft = 0.0f;
        float chatRight = chatW + 16.0f;

        return mouseX >= chatLeft && mouseX <= chatRight && mouseY >= chatTop && mouseY <= chatBottom;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.holding = false;
        this.activeHand = null;
    }

    public boolean onClick(int button, int action) {
        if (!isEnabled() || !"Через чат".equals(this.mode.get()) || !inChat() || button != 0) {
            return false;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        float mouseX = (float) mc.mouse.getX() * (float) mc.getWindow().getScaledWidth() / (float) mc.getWindow().getWidth();
        float mouseY = (float) mc.mouse.getY() * (float) mc.getWindow().getScaledHeight() / (float) mc.getWindow().getHeight();

        if (action == 1) {

            if (isMouseOverChat(mouseX, mouseY)) {
                return false;
            }

            long now = System.currentTimeMillis();
            boolean doubleClick = now - this.lastClickMs <= 300L;
            this.lastClickMs = now;
            if (!doubleClick) {
                return false;
            }

            this.activeHand = pickHand(mouseX);
            this.holding = true;
            this.grabCursorX = mouseX;
            this.grabCursorY = mouseY;
            this.grabOffsetX = this.activeHand == Hand.MAIN_HAND ? this.mainTargetX : this.offTargetX;
            this.grabOffsetY = this.activeHand == Hand.MAIN_HAND ? this.mainTargetY : this.offTargetY;
            return true;
        } else if (action == 0) {
            if (this.holding) {
                this.holding = false;
                syncToSettings();
                return true;
            }
        }
        return false;
    }

    public boolean onScroll(double vertical) {
        if (!isEnabled() || !"Через чат".equals(this.mode.get()) || !inChat() || vertical == 0.0) {
            return false;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        float mouseX = (float) mc.mouse.getX() * (float) mc.getWindow().getScaledWidth() / (float) mc.getWindow().getWidth();
        float mouseY = (float) mc.mouse.getY() * (float) mc.getWindow().getScaledHeight() / (float) mc.getWindow().getHeight();

        if (isMouseOverChat(mouseX, mouseY)) {
            return false;
        }

        Hand hand = this.holding && this.activeHand != null ? this.activeHand : pickHand(mouseX);
        if (hand == null) {
            hand = Hand.MAIN_HAND;
        }
        this.activeHand = hand;

        float step = vertical > 0.0 ? 0.08f : -0.08f;
        if (hand == Hand.MAIN_HAND) {
            this.mainScaleTarget = MathHelper.clamp(this.mainScaleTarget + step, 0.2f, 5.0f);
        } else {
            this.offScaleTarget = MathHelper.clamp(this.offScaleTarget + step, 0.2f, 5.0f);
        }
        syncToSettings();

        this.lastScrollTime = System.currentTimeMillis();
        return true;
    }

    private Hand pickHand(float mouseX) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float halfWidth = mc.getWindow().getScaledWidth() * 0.5f;
        boolean rightSide = mouseX >= halfWidth;
        Arm mainArm = mc.player != null ? mc.player.getMainArm() : mc.options.getMainArm().getValue();
        boolean mainIsRight = mainArm == Arm.RIGHT;
        return rightSide == mainIsRight ? Hand.MAIN_HAND : Hand.OFF_HAND;
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        boolean isChatMode = "Через чат".equals(this.mode.get());
        boolean inChatScreen = inChat();
        if (!inChatScreen || !isChatMode) {
            this.holding = false;
        }

        long timeSinceScroll = System.currentTimeMillis() - this.lastScrollTime;

        if (isChatMode && inChatScreen && this.holding && this.activeHand != null) {
            float mouseX = (float) mc.mouse.getX() * (float) mc.getWindow().getScaledWidth() / (float) mc.getWindow().getWidth();
            float mouseY = (float) mc.mouse.getY() * (float) mc.getWindow().getScaledHeight() / (float) mc.getWindow().getHeight();

            float screenH = Math.max(1.0f, mc.getWindow().getScaledHeight());
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

        if (isChatMode && inChatScreen && (this.holding || timeSinceScroll < 2500L)) {
            float mouseX = (float) mc.mouse.getX() * (float) mc.getWindow().getScaledWidth() / (float) mc.getWindow().getWidth();
            float mouseY = (float) mc.mouse.getY() * (float) mc.getWindow().getScaledHeight() / (float) mc.getWindow().getHeight();

            Hand displayHand = this.activeHand != null ? this.activeHand : pickHand(mouseX);
            String handName = displayHand == Hand.MAIN_HAND ? "Основная рука" : "Вторая рука";
            float scaleVal = displayHand == Hand.MAIN_HAND ? this.mainScaleTarget : this.offScaleTarget;
            float posX = displayHand == Hand.MAIN_HAND ? this.mainTargetX : this.offTargetX;
            float posY = displayHand == Hand.MAIN_HAND ? this.mainTargetY : this.offTargetY;

            String info = String.format("%s: Размер %.2fx | X: %.2f, Y: %.2f", handName, scaleVal, posX, posY);
            Render2D.drawText(context, info, mouseX + 12.0f, mouseY - 12.0f, 0xFFFFFFFF);
        }

        if (!isChatMode || (!inChatScreen && !this.holding && timeSinceScroll > 2000L)) {
            this.mainScaleTarget = (float) this.mainScaleSetting.get();
            this.offScaleTarget = (float) this.offScaleSetting.get();
            this.mainTargetX = (float) this.mainX.get();
            this.mainTargetY = (float) this.mainY.get();
            this.offTargetX = (float) this.offX.get();
            this.offTargetY = (float) this.offY.get();
        }

        float follow = this.holding ? 1.0f : 0.45f;
        this.mainOffsetX += (this.mainTargetX - this.mainOffsetX) * follow;
        this.mainOffsetY += (this.mainTargetY - this.mainOffsetY) * follow;
        this.offOffsetX += (this.offTargetX - this.offOffsetX) * follow;
        this.offOffsetY += (this.offTargetY - this.offOffsetY) * follow;

        this.mainScaleCurrent += (this.mainScaleTarget - this.mainScaleCurrent) * 0.35f;
        this.offScaleCurrent += (this.offScaleTarget - this.offScaleCurrent) * 0.35f;
    }

    public void applyTransform(MatrixStack matrix, Hand hand) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        boolean isMain = hand == Hand.MAIN_HAND;
        float x = isMain ? this.mainOffsetX : this.offOffsetX;
        float y = isMain ? this.mainOffsetY : this.offOffsetY;
        float z = isMain ? (float) this.mainZ.get() : (float) this.offZ.get();
        float scale = isMain ? this.mainScaleCurrent : this.offScaleCurrent;

        matrix.translate(x, y, z);
        if (scale != 1.0f) {
            Arm mainArm = mc.player != null ? mc.player.getMainArm() : mc.options.getMainArm().getValue();
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

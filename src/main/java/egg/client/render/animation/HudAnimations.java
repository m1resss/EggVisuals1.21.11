package egg.client.render.animation;

import egg.client.module.render.Animations;
import egg.client.util.animation.Animation;
import egg.client.util.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;

public final class HudAnimations {
    public static final HudAnimations INSTANCE = new HudAnimations();

    private final Animation tabListAnimation = new Animation(0.0f, 200L, Easing.EASE_OUT_QUART);
    private boolean tabListClosing = false;
    private long lastChatMessageTime = 0L;
    private long screenOpenTime = 0L;

    private HudAnimations() {
    }

    public void setTabListVisible(boolean visible) {
        Animations animations = Animations.getInstance();
        boolean animEnabled = animations != null && animations.isTabEnabled();
        float speed = (animations != null) ? animations.getSpeed() : 1.0f;
        long duration = Math.max(50L, (long) (200.0f / speed));

        if (visible) {
            this.tabListClosing = false;
            if (!animEnabled) {
                this.tabListAnimation.setValue(1.0f);
            } else {
                this.tabListAnimation.setValue(0.0f);
                this.tabListAnimation.setTarget(1.0f, duration, Easing.EASE_OUT_QUART);
            }
        } else {
            if (!animEnabled) {
                this.tabListAnimation.setValue(0.0f);
                this.tabListClosing = false;
            } else {
                this.tabListClosing = true;
                this.tabListAnimation.setTarget(0.0f, duration, Easing.EASE_OUT_QUART);
            }
        }
    }

    public boolean beginTabListTransform(DrawContext drawContext, float scaledWidth) {
        Animations animations = Animations.getInstance();
        if (animations == null || !animations.isTabEnabled()) return false;

        this.tabListAnimation.update();
        float progress = this.tabListAnimation.get();
        if (progress >= 1.0f) return false;

        float cx = scaledWidth * 0.5f;
        Matrix3x2fStack matrices = drawContext.getMatrices();
        matrices.pushMatrix();
        matrices.translate(cx, 0.0f);
        matrices.scale(progress, progress);
        matrices.translate(-cx, 0.0f);
        return true;
    }

    public void endTabListTransform(DrawContext drawContext) {
        drawContext.getMatrices().popMatrix();
    }

    public void renderClosingTabList(DrawContext drawContext) {
        if (!this.tabListClosing) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            this.tabListClosing = false;
            return;
        }

        Animations animations = Animations.getInstance();
        if (animations == null || !animations.isTabEnabled()) {
            this.tabListClosing = false;
            return;
        }

        this.tabListAnimation.update();
        if (this.tabListAnimation.isFinished()) {
            this.tabListClosing = false;
            return;
        }

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = scoreboard != null ? scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST) : null;
        mc.inGameHud.getPlayerListHud().render(drawContext, mc.getWindow().getScaledWidth(), scoreboard, objective);
    }

    public void onChatMessage() {
        this.lastChatMessageTime = System.currentTimeMillis();
    }

    public float getChatMessageOffset() {
        Animations animations = Animations.getInstance();
        if (animations == null || !animations.isChatEnabled()) return 0.0f;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.inGameHud == null) return 0.0f;

        float speed = animations.getSpeed();
        float elapsed = Math.min((float) (System.currentTimeMillis() - this.lastChatMessageTime) / (170.0f / speed), 1.0f);
        if (elapsed >= 1.0f) return 0.0f;

        float lineSpacing = (float) (mc.options.getChatLineSpacing().getValue() + 1.0);
        int lineHeight = (int) (9.0f * lineSpacing);
        return 0.85f * lineHeight * (1.0f - elapsed);
    }

    public float getChatOpenOffset(long openedAt) {
        Animations animations = Animations.getInstance();
        if (animations == null || !animations.isChatOpenEnabled()) return 0.0f;

        float speed = animations.getSpeed();
        float elapsed = Math.min((float) (System.currentTimeMillis() - openedAt) / (170.0f / speed), 1.0f);
        float f = 1.0f - elapsed;
        if (f <= 0.0f) return 0.0f;
        return (2.70158f * f * f * f - 1.70158f * f * f) * 12.0f;
    }

    public void onScreenChange(Screen oldScreen, Screen newScreen) {
        if (newScreen != null && (oldScreen == null || oldScreen.getClass() != newScreen.getClass())) {
            this.screenOpenTime = System.currentTimeMillis();
        }
    }

    public boolean beginContainerTransform(DrawContext context, Screen screen) {
        Animations animations = Animations.getInstance();
        if (animations == null || !animations.isContainersEnabled() || screen == null) return false;
        if (screen instanceof ChatScreen) return false;

        float speed = animations.getSpeed();
        float elapsed = Math.min((float) (System.currentTimeMillis() - this.screenOpenTime) / (180.0f / speed), 1.0f);
        if (elapsed >= 1.0f) return false;

        float t = elapsed - 1.0f;
        float progress = 1.0f + 2.70158f * t * t * t + 1.70158f * t * t;
        float scale = MathHelper.clamp(0.88f + 0.12f * progress, 0.7f, 1.05f);

        MinecraftClient mc = MinecraftClient.getInstance();
        float cx = mc.getWindow().getScaledWidth() * 0.5f;
        float cy = mc.getWindow().getScaledHeight() * 0.5f;

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(cx, cy);
        matrices.scale(scale, scale);
        matrices.translate(-cx, -cy);
        return true;
    }

    public void endContainerTransform(DrawContext context) {
        context.getMatrices().popMatrix();
    }
}

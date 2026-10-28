package egg.client.util.animation;

import egg.client.util.ColorUtil;

public class ColorAnimation {
    private final Animation animation;
    private int fromColor;
    private int targetColor;

    public ColorAnimation(int initialColor, long durationMs, Easing easing) {
        this.fromColor = initialColor;
        this.targetColor = initialColor;
        this.animation = new Animation(1.0f, durationMs, easing);
    }

    public ColorAnimation(int initialColor, long durationMs) {
        this(initialColor, durationMs, Easing.EASE_OUT_CUBIC);
    }

    public ColorAnimation(int initialColor) {
        this(initialColor, 200L, Easing.EASE_OUT_CUBIC);
    }

    public void setTarget(int newTargetColor) {
        if (this.targetColor == newTargetColor) return;
        this.fromColor = getColor();
        this.targetColor = newTargetColor;
        this.animation.setValue(0.0f);
        this.animation.setTarget(1.0f);
    }

    public int getColor() {
        float progress = animation.getValue();
        if (progress >= 1.0f) {
            return targetColor;
        }
        return ColorUtil.interpolate(fromColor, targetColor, progress);
    }

    public void setColor(int color) {
        this.fromColor = color;
        this.targetColor = color;
        this.animation.setValue(1.0f);
    }
}

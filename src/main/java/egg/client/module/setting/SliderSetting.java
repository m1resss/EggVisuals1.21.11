package egg.client.module.setting;

import egg.client.util.animation.Animation;
import egg.client.util.animation.Easing;

public class SliderSetting extends Setting {
    private double value;
    private final double min;
    private final double max;
    private final double step;
    private final Animation animation;

    public SliderSetting(String name, double defaultValue, double min, double max, double step, String description) {
        super(name, description);
        this.value = defaultValue;
        this.min = min;
        this.max = max;
        this.step = step;
        this.animation = new Animation((float) getPercent(), 160L, Easing.EASE_OUT_CUBIC);
    }

    public double getValue() {
        return value;
    }

    public double get() {
        return value;
    }

    public float getValueFloat() {
        return (float) value;
    }

    public int getValueInt() {
        return (int) Math.round(value);
    }

    public void setValue(double value) {
        double clamped = Math.clamp(value, min, max);
        if (step > 0.0) {
            clamped = Math.round(clamped / step) * step;
        }
        this.value = clamped;
        this.animation.setTarget((float) getPercent());
    }

    public void set(double value) {
        setValue(value);
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getPercent() {
        if (max <= min) return 0.0;
        return (value - min) / (max - min);
    }

    public void setPercent(double percent) {
        percent = Math.clamp(percent, 0.0, 1.0);
        setValue(min + percent * (max - min));
    }

    public Animation getAnimation() {
        return animation;
    }
}

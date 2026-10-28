package egg.client.module.setting;

import egg.client.util.animation.Animation;
import egg.client.util.animation.Easing;

public class BooleanSetting extends Setting {
    private boolean enabled;
    private final Animation animation;

    public BooleanSetting(String name, boolean defaultValue, String description) {
        super(name, description);
        this.enabled = defaultValue;
        this.animation = new Animation(defaultValue ? 1.0f : 0.0f, 180L, Easing.EASE_OUT_CUBIC);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean get() {
        return enabled;
    }

    public void set(boolean enabled) {
        this.enabled = enabled;
        this.animation.setTarget(enabled ? 1.0f : 0.0f);
    }

    public void setEnabled(boolean enabled) {
        set(enabled);
    }

    public void toggle() {
        set(!enabled);
    }

    public Animation getAnimation() {
        return animation;
    }
}

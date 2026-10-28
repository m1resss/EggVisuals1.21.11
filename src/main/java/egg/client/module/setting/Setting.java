package egg.client.module.setting;

import egg.client.util.animation.Animation;
import egg.client.util.animation.Easing;

import java.util.function.BooleanSupplier;

public abstract class Setting {
    private final String name;
    private final String description;
    private BooleanSupplier visibleSupplier = () -> true;
    private final Animation visibilityAnim = new Animation(1.0f, 220L, Easing.EASE_OUT_CUBIC);

    public Setting(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isVisible() {
        return visibleSupplier.getAsBoolean();
    }

    @SuppressWarnings("unchecked")
    public <T extends Setting> T setVisible(BooleanSupplier visibleSupplier) {
        this.visibleSupplier = visibleSupplier;
        boolean vis = visibleSupplier.getAsBoolean();
        this.visibilityAnim.setValue(vis ? 1.0f : 0.0f);
        this.visibilityAnim.setTarget(vis ? 1.0f : 0.0f);
        return (T) this;
    }

    public float getVisibilityProgress() {
        boolean vis = isVisible();
        visibilityAnim.setTarget(vis ? 1.0f : 0.0f);
        return visibilityAnim.getValue();
    }
}

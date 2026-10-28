package egg.client.module;

import egg.client.module.setting.Setting;
import egg.client.util.animation.Animation;
import egg.client.util.animation.Easing;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    protected final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private int key;
    private boolean enabled;
    private boolean expanded;
    private boolean favorite;
    private boolean blocked;

    private final List<Setting> settings = new ArrayList<>();

    // UI Animations
    private final Animation switchAnimation = new Animation(0.0f, 220L, Easing.EASE_OUT_BACK);
    private final Animation expandAnimation = new Animation(0.0f, 240L, Easing.EASE_IN_OUT_QUART);
    private final Animation hoverAnimation = new Animation(0.0f, 160L, Easing.EASE_OUT_CUBIC);

    public Module(String name, String description, Category category, int defaultKey) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.key = defaultKey;
        this.enabled = false;
        this.expanded = false;
        this.favorite = false;
    }

    public Module(String name, String description, Category category) {
        this(name, description, category, 0);
    }

    public void addSettings(Setting... settings) {
        for (Setting s : settings) {
            this.settings.add(s);
        }
    }

    public void setEnabled(boolean enabled) {
        if (enabled && this.blocked) {
            return;
        }
        if (this.enabled != enabled) {
            this.enabled = enabled;
            this.switchAnimation.setTarget(enabled ? 1.0f : 0.0f);
            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
        }
    }

    public void toggle() {
        if (!blocked) {
            setEnabled(!enabled);
        }
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
        if (blocked && this.enabled) {
            setEnabled(false);
        }
    }

    public void onEnable() {}

    public void onDisable() {}

    public void onTick(MinecraftClient client) {}

    // Getters & Setters
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        this.expandAnimation.setTarget(expanded ? 1.0f : 0.0f);
    }

    public void toggleExpanded() {
        setExpanded(!expanded);
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public void toggleFavorite() {
        setFavorite(!favorite);
    }

    public List<Setting> getSettings() {
        return settings;
    }

    public Animation getSwitchAnimation() {
        return switchAnimation;
    }

    public Animation getExpandAnimation() {
        return expandAnimation;
    }

    public Animation getHoverAnimation() {
        return hoverAnimation;
    }
}

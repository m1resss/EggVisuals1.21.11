package delta.cosmetics;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class CosmeticItem {
    private final String id;
    private final String name;
    private final CosmeticCategory category;
    private final boolean animated;
    private final int glowColor;
    private final ItemStack previewStack;

    public CosmeticItem(String id, String name, CosmeticCategory category, boolean animated, int glowColor, ItemStack previewStack) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.animated = animated;
        this.glowColor = glowColor;
        this.previewStack = previewStack != null ? previewStack : new ItemStack(Items.NETHERITE_SWORD);
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public CosmeticCategory getCategory() {
        return this.category;
    }

    public boolean isAnimated() {
        return this.animated;
    }

    public int getGlowColor() {
        return this.glowColor;
    }

    public ItemStack getPreviewStack() {
        return this.previewStack;
    }
}

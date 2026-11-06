package com.holdmylua.source.global;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;

public class DispatcherStorage {
    private static final List<ItemStack> renderedItems = new ArrayList<ItemStack>();

    public static void setItem(ItemStack item) {
        if (!item.isEmpty() && item.getUseAction() != UseAction.BLOCK && item.getUseAction() != UseAction.TRIDENT) {
            renderedItems.add(item);
        }
    }

    public static ItemStack getRenderedItem() {
        if (!renderedItems.isEmpty()) {
            ItemStack stack = renderedItems.getFirst();
            renderedItems.remove(stack);
            return stack;
        }
        return Items.AIR.getDefaultStack();
    }

    public static void clear() {
        renderedItems.clear();
    }
}

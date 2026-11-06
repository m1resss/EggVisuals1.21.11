package com.holdmylua.source.global.item_model;

import com.holdmylua.source.LuaTestHMI;
import com.holdmylua.source.global.item_model.ItemModelContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.Hand;

public class ItemModelStorage {
    private static final List<ItemModelContext> data = new ArrayList<ItemModelContext>();

    public static void addData(ItemModelContext info, ItemStack item) {
        if (!item.isEmpty() && item.getUseAction() != UseAction.BLOCK && item.getUseAction() != UseAction.TRIDENT) {
            data.add(info);
        }
    }

    public static ItemModelContext get() {
        if (!data.isEmpty()) {
            ItemModelContext record = data.getFirst();
            data.remove(record);
            return record;
        }
        return new ItemModelContext(false, 0.0f, (AbstractClientPlayerEntity)MinecraftClient.getInstance().player, Hand.MAIN_HAND, false, LuaTestHMI.deltaTime, 0.0f, 0.0f, 0.0f, false, false, false, false, false, false, Items.AIR.getDefaultStack());
    }

    public static void clear() {
        data.clear();
    }
}

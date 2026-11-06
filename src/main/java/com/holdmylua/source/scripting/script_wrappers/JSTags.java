package com.holdmylua.source.scripting.script_wrappers;

import com.holdmylua.source.annotation.Safe;
import net.fabricmc.fabric.impl.tag.convention.v2.TagRegistration;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public class JSTags {
    @Safe
    public TagKey<Item> getVanillaTag(String id) {
        return TagKey.of((RegistryKey)RegistryKeys.ITEM, (Identifier)Identifier.ofVanilla((String)id));
    }

    @Safe
    public TagKey<Item> getFabricTag(String id) {
        return TagRegistration.ITEM_TAG.registerC(id);
    }
}

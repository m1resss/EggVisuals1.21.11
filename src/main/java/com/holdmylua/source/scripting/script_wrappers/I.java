package com.holdmylua.source.scripting.script_wrappers;

import com.google.gson.JsonElement;
import com.holdmylua.source.access.ItemStackAccessor;
import com.holdmylua.source.annotation.Safe;
import com.holdmylua.source.data_structures.SpearData;
import com.holdmylua.source.global.GlobalsStorage;
import com.holdmylua.source.scripting.script_wrappers.ComponentWrapper;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.KineticWeaponComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ProjectileItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public class I {
    @Safe
    public float getAttackDamage(ItemStack stack) {
        AttributeModifiersComponent modifiers = (AttributeModifiersComponent)stack.getComponents().get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) {
            return 0.0f;
        }
        float totalDamage = 0.0f;
        for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().value() != EntityAttributes.ATTACK_DAMAGE.value()) continue;
            totalDamage += (float)entry.modifier().value();
        }
        return totalDamage;
    }

    @Safe
    public boolean isOf(ItemStack itemStack, Item item) {
        return itemStack.isOf(item);
    }

    @Safe
    public boolean isIn(ItemStack itemStack, TagKey<Item> tag) {
        return itemStack.isIn(tag);
    }

    @Safe
    public boolean isEmpty(ItemStack itemStack) {
        return itemStack.isEmpty();
    }

    @Safe
    public String getUseAction(ItemStack item) {
        return item.getUseAction().asString();
    }

    @Safe
    public String getName(ItemStack item) {
        return item.getItem().toString();
    }

    @Safe
    public String getActualName(ItemStack item) {
        if (item.getCustomName() != null) {
            return item.getCustomName().getString();
        }
        return item.getName().getString();
    }

    @Safe
    public boolean isChargedCrossbow(ItemStack item) {
        return CrossbowItem.isCharged((ItemStack)item);
    }

    @Safe
    public ItemStack getDefaultStack(Item item) {
        return item.getDefaultStack();
    }

    @Safe
    public boolean isBlock(ItemStack item) {
        return Block.getBlockFromItem((Item)item.getItem()) != Blocks.AIR;
    }

    @Safe
    public boolean shouldTranslateItem(ItemStack item) {
        int t = ((ItemStackAccessor)(Object)item).hMI5_0$getTransform();
        return (t != 0 || t == -1) && (!(item.getItem() instanceof FishingRodItem) && !item.isIn(ConventionalItemTags.RODS) && !item.isIn(ConventionalItemTags.TOOLS) && !item.isIn(ItemTags.SWORDS) && !item.isIn(ConventionalItemTags.MACE_TOOLS) && item.getUseAction() != UseAction.BLOCK && !(this.getAttackDamage(item) > 0.0f) || item.getUseAction() == UseAction.EAT || item.getUseAction() == UseAction.DRINK || item.getUseAction() == UseAction.SPYGLASS);
    }

    @Safe
    public boolean isCustomTranslate(ItemStack item) {
        return GlobalsStorage.translateItem.getOrDefault(this.getName(item), false);
    }

    @Safe
    public void setTranslate(ItemStack item, boolean translate) {
        ((ItemStackAccessor)(Object)item).hMI5_0$setTransform(translate);
    }

    @Safe
    public void setRenderAsBlock(ItemStack item, boolean render) {
        if (Block.getBlockFromItem((Item)item.getItem()) != Blocks.AIR) {
            ((ItemStackAccessor)(Object)item).hMI5_0$setRenderAsBlock(render);
        }
    }

    @Safe
    public boolean shouldRenderAsBlock(ItemStack item) {
        if (Block.getBlockFromItem((Item)item.getItem()) != Blocks.AIR) {
            int t = ((ItemStackAccessor)(Object)item).hMI5_0$getRenderAsBlock();
            return t == 1 || t == -1;
        }
        return false;
    }

    @Safe
    public boolean isLantern(ItemStack item) {
        return Block.getBlockFromItem((Item)item.getItem()) instanceof LanternBlock;
    }

    @Safe
    public boolean isThrowable(ItemStack item) {
        return item.getItem() instanceof SplashPotionItem || item.getItem() instanceof ProjectileItem;
    }

    @Safe
    public void setSwingSpeed(ItemStack item, double value) {
        ((ItemStackAccessor)(Object)item).hMI5_0$setSwingSpeed((int)value);
    }

    @Safe
    public boolean isEnchanted(ItemStack item) {
        return item.hasGlint();
    }

    @Safe
    public static ComponentWrapper getComponents(ItemStack stack) {
        RegistryOps ops = RegistryOps.of((DynamicOps)JsonOps.INSTANCE, (RegistryWrapper.WrapperLookup)MinecraftClient.getInstance().world.getRegistryManager());
        ComponentMap components = stack.getComponents();
        JsonElement json = (JsonElement)ComponentMap.CODEC.encodeStart((DynamicOps)ops, components).getOrThrow();
        return new ComponentWrapper(json.getAsJsonObject());
    }

    @Safe
    public void copyAppearanceComponents(ItemStack source) {
        ItemStack target = source.getItem().getDefaultStack();
        if (source.contains(DataComponentTypes.ENCHANTMENTS)) {
            target.set(DataComponentTypes.ENCHANTMENTS, ((ItemEnchantmentsComponent)source.get(DataComponentTypes.ENCHANTMENTS)));
        }
        if (source.contains(DataComponentTypes.ITEM_MODEL)) {
            target.set(DataComponentTypes.ITEM_MODEL, ((Identifier)source.get(DataComponentTypes.ITEM_MODEL)));
        }
        if (source.contains(DataComponentTypes.CUSTOM_MODEL_DATA)) {
            target.set(DataComponentTypes.CUSTOM_MODEL_DATA, ((CustomModelDataComponent)source.get(DataComponentTypes.CUSTOM_MODEL_DATA)));
        }
        if (source.contains(DataComponentTypes.CUSTOM_DATA)) {
            target.set(DataComponentTypes.CUSTOM_DATA, ((NbtComponent)source.get(DataComponentTypes.CUSTOM_DATA)));
        }
        source = target;
    }

    @Safe
    public void setMainStack(Item item) {
        GlobalsStorage.mainHandItem = item.getDefaultStack();
    }

    @Safe
    public void setOffStack(Item item) {
        GlobalsStorage.offHandItem = item.getDefaultStack();
    }

    @Safe
    public SpearData getSpearData(ItemStack item) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        KineticWeaponComponent kineticWeaponComponent = (KineticWeaponComponent)item.get(DataComponentTypes.KINETIC_WEAPON);
        int spearUseDuration = item.getMaxUseTime((LivingEntity)player) - (player.getItemUseTimeLeft() + 1);
        int i = kineticWeaponComponent.delayTicks();
        boolean canDismount = spearUseDuration < kineticWeaponComponent.dismountConditions().map(KineticWeaponComponent.Condition::maxDurationTicks).orElse(0) + i;
        boolean canKnockBack = spearUseDuration < kineticWeaponComponent.knockbackConditions().map(KineticWeaponComponent.Condition::maxDurationTicks).orElse(0) + i;
        boolean canDamage = spearUseDuration < kineticWeaponComponent.damageConditions().map(KineticWeaponComponent.Condition::maxDurationTicks).orElse(0) + i;
        boolean hitImpact = player.getTimeSinceLastKineticAttack(0.0f) == 1.0f;
        return new SpearData(canDamage, canKnockBack, canDismount, hitImpact);
    }
}

package com.holdmylua.source.scripting.script_wrappers;

import com.holdmylua.source.access.LivingEntityAccessor;
import com.holdmylua.source.annotation.Safe;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public class P {
    @Safe
    public double getHealth(AbstractClientPlayerEntity player) {
        return player.getHealth();
    }

    @Safe
    public boolean isSneaking(AbstractClientPlayerEntity player) {
        return player.isSneaking();
    }

    @Safe
    public boolean isOnGround(AbstractClientPlayerEntity player) {
        return player.isOnGround();
    }

    @Safe
    public boolean isSwimming(AbstractClientPlayerEntity player) {
        return player.isSwimming();
    }

    @Safe
    public boolean isClimbing(AbstractClientPlayerEntity player) {
        return player.isClimbing();
    }

    @Safe
    public boolean isCrawling(AbstractClientPlayerEntity player) {
        return player.isCrawling();
    }

    @Safe
    public boolean isSubmergedInWater(AbstractClientPlayerEntity player) {
        return player.isSubmergedInWater();
    }

    @Safe
    public boolean isTouchingWater(AbstractClientPlayerEntity player) {
        return player.isTouchingWater();
    }

    @Safe
    public boolean isUsingSpyglass(AbstractClientPlayerEntity player) {
        return player.isUsingSpyglass();
    }

    @Safe
    public boolean isUsingRiptide(AbstractClientPlayerEntity player) {
        return player.isUsingRiptide();
    }

    @Safe
    public double getX(AbstractClientPlayerEntity player) {
        return player.getX();
    }

    @Safe
    public double getY(AbstractClientPlayerEntity player) {
        return player.getY();
    }

    @Safe
    public double getZ(AbstractClientPlayerEntity player) {
        return player.getZ();
    }

    @Safe
    public double getXSpeed(AbstractClientPlayerEntity player) {
        return player.getVelocity().getX();
    }

    @Safe
    public double getYSpeed(AbstractClientPlayerEntity player) {
        return player.getVelocity().getY();
    }

    @Safe
    public double getZSpeed(AbstractClientPlayerEntity player) {
        return player.getVelocity().getZ();
    }

    @Safe
    public double getSpeed(AbstractClientPlayerEntity player) {
        return player.getVelocity().length();
    }

    @Safe
    public boolean isUsingItem(AbstractClientPlayerEntity player) {
        return player.isUsingItem();
    }

    @Safe
    public double getYaw(AbstractClientPlayerEntity player) {
        return player.getHeadYaw();
    }

    @Safe
    public double getPitch(AbstractClientPlayerEntity player) {
        return player.getPitch();
    }

    @Safe
    public ItemStack getMainItem(AbstractClientPlayerEntity player) {
        return player.getMainHandStack();
    }

    @Safe
    public ItemStack getOffhandItem(AbstractClientPlayerEntity player) {
        return player.getOffHandStack();
    }

    @Safe
    public Hand getActiveHand(AbstractClientPlayerEntity player) {
        return player.getActiveHand();
    }

    @Safe
    public int getAge(AbstractClientPlayerEntity player) {
        return player.age;
    }

    @Safe
    public boolean isItemCoolingDown(ItemStack item, AbstractClientPlayerEntity player) {
        return player.getItemCooldownManager().isCoolingDown(item);
    }

    @Safe
    public double getSwingCount(AbstractClientPlayerEntity player) {
        if (player instanceof LivingEntityAccessor) {
            LivingEntityAccessor access = (LivingEntityAccessor)player;
            return access.hMI5_0$getSwingCount();
        }
        return 0.0;
    }

    @Safe
    public String getStandingBlock(AbstractClientPlayerEntity player) {
        return player.getEntityWorld().getBlockState(player.getBlockPos().down()).getRegistryEntry().getIdAsString();
    }

    @Safe
    public String getBlockBelow(AbstractClientPlayerEntity player, int steps) {
        return player.getEntityWorld().getBlockState(player.getBlockPos().down().down(steps)).getRegistryEntry().getIdAsString();
    }

    @Safe
    public String getBlockAbove(AbstractClientPlayerEntity player, int steps) {
        return player.getEntityWorld().getBlockState(player.getBlockPos().up().up().up(steps)).getRegistryEntry().getIdAsString();
    }

    @Safe
    public boolean hasVehicle(AbstractClientPlayerEntity player, int steps) {
        return player.hasVehicle();
    }
}

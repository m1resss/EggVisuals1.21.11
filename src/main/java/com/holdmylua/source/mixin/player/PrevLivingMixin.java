package com.holdmylua.source.mixin.player;

import com.holdmylua.source.access.LivingEntityAccessor;
import com.holdmylua.source.lua_runtime.LuaScriptCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={LivingEntity.class})
public abstract class PrevLivingMixin
implements LivingEntityAccessor {
    @Shadow
    private int handSwingTicks;
    @Shadow
    public float handSwingProgress;
    @Shadow
    public float lastHandSwingProgress;
    @Shadow
    private boolean handSwinging;
    @Shadow
    public Hand preferredHand;
    @Unique
    private int offHandSwingTicks;
    @Unique
    private boolean offHandSwinging;
    @Unique
    public float offHandSwingProgress;
    @Unique
    public float lastOffHandSwingProgress;
    @Unique
    private int mainHandSwingTicks;
    @Unique
    private boolean mainHandSwinging;
    @Unique
    public float mainHandSwingProgress;
    @Unique
    public float lastMainHandSwingProgress;
    private boolean swingMHand = false;
    private boolean swingOHand = false;

    @Shadow
    protected abstract int getHandSwingDuration();

    @Shadow
    public abstract void swingHand(Hand var1);

    @Override
    public float hMI5_0$getMainHandSwingProgress(float tickDelta) {
        float f = this.mainHandSwingProgress - this.lastMainHandSwingProgress;
        if (f < 0.0f) {
            f += 1.0f;
        }
        return this.lastMainHandSwingProgress + f * tickDelta;
    }

    @Override
    public float hMI5_0$getOffHandSwingProgress(float tickDelta) {
        float f = this.offHandSwingProgress - this.lastOffHandSwingProgress;
        if (f < 0.0f) {
            f += 1.0f;
        }
        return this.lastOffHandSwingProgress + f * tickDelta;
    }

    @Override
    public boolean hMI5_0$getMHandEvent() {
        return this.swingMHand;
    }

    @Override
    public boolean hMI5_0$getOHandEvent() {
        return this.swingOHand;
    }

    @Inject(method={"swingHand(Lnet/minecraft/util/Hand;)V"}, at={@At(value="HEAD")}, cancellable=true)
    private void onSwingHand(Hand hand, CallbackInfo ci) {
        if (hand == Hand.OFF_HAND) {
            int duration = LuaScriptCache.swingSpeed;
            if (!this.offHandSwinging || this.offHandSwingTicks >= duration / 2) {
                this.offHandSwingTicks = 0;
                this.offHandSwinging = true;
                this.swingOHand = !this.swingOHand;
            }
        } else {
            int duration = LuaScriptCache.swingSpeed;
            if (!this.mainHandSwinging || this.mainHandSwingTicks >= duration / 2) {
                this.mainHandSwingTicks = 0;
                this.mainHandSwinging = true;
                this.swingMHand = !this.swingMHand;
            }
        }
    }

    @Inject(method={"tickHandSwing"}, at={@At(value="HEAD")}, cancellable=true)
    private void onTickHandSwing(CallbackInfo ci) {
        this.lastOffHandSwingProgress = this.offHandSwingProgress;
        int offHandDuration = LuaScriptCache.swingSpeed;
        if (this.offHandSwinging) {
            ++this.offHandSwingTicks;
            if (this.offHandSwingTicks >= offHandDuration) {
                this.offHandSwinging = false;
                this.offHandSwingTicks = 0;
            }
        } else {
            this.offHandSwingTicks = 0;
        }
        this.offHandSwingProgress = (float)this.offHandSwingTicks / (float)offHandDuration;
        this.lastMainHandSwingProgress = this.mainHandSwingProgress;
        int mainHandDuration = LuaScriptCache.swingSpeed;
        if (this.mainHandSwinging) {
            ++this.mainHandSwingTicks;
            if (this.mainHandSwingTicks >= mainHandDuration) {
                this.mainHandSwinging = false;
                this.mainHandSwingTicks = 0;
            }
        } else {
            this.mainHandSwingTicks = 0;
        }
        this.mainHandSwingProgress = (float)this.mainHandSwingTicks / (float)mainHandDuration;
    }

    @ModifyConstant(method={"getHandSwingDuration()I"}, constant={@Constant(intValue=6)})
    private int modifySwingDuration(int original) {
        if ((Object)this == MinecraftClient.getInstance().player) {
            return LuaScriptCache.swingSpeed;
        }
        return original;
    }
}

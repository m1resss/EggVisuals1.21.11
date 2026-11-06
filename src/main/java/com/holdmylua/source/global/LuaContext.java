package com.holdmylua.source.global;

import com.holdmylua.source.patricles.Particle;
import java.util.List;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public class LuaContext {
    public MatrixStack matrices;
    public boolean bl;
    public float swingProgress;
    public ItemStack item;
    public AbstractClientPlayerEntity player;
    public Hand hand;
    public boolean mainHand;
    public float deltaTime;
    public float equipProgress;
    public float mainHandSwingProgress;
    public float offHandSwingProgress;
    public boolean mainHandSwitchEvent;
    public boolean offHandSwitchEvent;
    public boolean swingMHand;
    public boolean swingOHand;
    public boolean interact;
    public boolean blockBreaking;
    public List<Particle> particles;

    public void update(MatrixStack matrices, boolean bl, float swingProgress, ItemStack item, AbstractClientPlayerEntity player, Hand hand, boolean mainHand, float deltaTime, float equipProgress, float mainHandSwingProgress, float offHandSwingProgress, boolean mainHandSwitchEvent, boolean offHandSwitchEvent, boolean swingMHand, boolean swingOHand, boolean interact, boolean blockBreaking, List<Particle> particles) {
        this.matrices = matrices;
        this.bl = bl;
        this.swingProgress = swingProgress;
        this.item = item;
        this.player = player;
        this.hand = hand;
        this.mainHand = mainHand;
        this.deltaTime = deltaTime;
        this.equipProgress = equipProgress;
        this.mainHandSwingProgress = mainHandSwingProgress;
        this.offHandSwingProgress = offHandSwingProgress;
        this.mainHandSwitchEvent = mainHandSwitchEvent;
        this.offHandSwitchEvent = offHandSwitchEvent;
        this.swingMHand = swingMHand;
        this.swingOHand = swingOHand;
        this.interact = interact;
        this.blockBreaking = blockBreaking;
        this.particles = particles;
    }
}

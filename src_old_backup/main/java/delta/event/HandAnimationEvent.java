package delta.event;

import delta.core.Event;
import delta.core.IEvent;

import lombok.Generated;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.client.util.math.MatrixStack;

public class HandAnimationEvent extends Event implements IEvent {
    private final MatrixStack a;
    private final Hand b;
    private final float c;
    private final int d;
    private final float equipProgress;
    private final Arm arm;

    @Generated
    public HandAnimationEvent(MatrixStack matrices, Hand hand, float swingProgress, int armX) {
        this(matrices, hand, swingProgress, armX, 0.0f, armX == 1 ? Arm.RIGHT : Arm.LEFT);
    }

    @Generated
    public HandAnimationEvent(MatrixStack matrices, Hand hand, float swingProgress, int armX, float equipProgress, Arm arm) {
        this.a = matrices;
        this.b = hand;
        this.c = swingProgress;
        this.d = armX;
        this.equipProgress = equipProgress;
        this.arm = arm != null ? arm : (armX == 1 ? Arm.RIGHT : Arm.LEFT);
    }

    @Generated
    public MatrixStack b() {
        return this.a;
    }

    @Generated
    public Hand c() {
        return this.b;
    }

    @Generated
    public float d() {
        return this.c;
    }

    @Generated
    public int e() {
        return this.d;
    }

    @Generated
    public float getEquipProgress() {
        return this.equipProgress;
    }

    @Generated
    public Arm getArm() {
        return this.arm;
    }
}

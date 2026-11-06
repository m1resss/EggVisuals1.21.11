package com.holdmylua.source.model;

import com.holdmylua.source.model.interfaces.Poses;
import com.holdmylua.source.model.parents.AbstractPose;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionfc;

public class RotationZ
extends AbstractPose
implements Poses {
    private float x;
    private float y;
    private float z;
    private float amount;

    public RotationZ(float amount, float x, float y, float z) {
        this.amount = amount;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void applyPose(MatrixStack matrices) {
        matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_Z.rotationDegrees(this.amount), this.x, this.y, this.z);
    }
}

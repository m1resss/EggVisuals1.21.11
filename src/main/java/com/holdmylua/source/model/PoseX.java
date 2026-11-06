package com.holdmylua.source.model;

import com.holdmylua.source.model.interfaces.Poses;
import com.holdmylua.source.model.parents.AbstractPose;
import net.minecraft.client.util.math.MatrixStack;

public class PoseX
extends AbstractPose
implements Poses {
    float amount;

    public PoseX(float amount) {
        this.amount = amount;
    }

    @Override
    public void applyPose(MatrixStack matrices) {
        matrices.translate(this.amount, 0.0f, 0.0f);
    }
}

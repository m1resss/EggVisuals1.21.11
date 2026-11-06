package com.holdmylua.source.model;

import com.holdmylua.source.model.interfaces.Poses;
import com.holdmylua.source.model.parents.AbstractPose;
import net.minecraft.client.util.math.MatrixStack;

public class PoseY
extends AbstractPose
implements Poses {
    float amount;

    public PoseY(float amount) {
        this.amount = amount;
    }

    @Override
    public void applyPose(MatrixStack matrices) {
        matrices.translate(0.0f, this.amount, 0.0f);
    }
}

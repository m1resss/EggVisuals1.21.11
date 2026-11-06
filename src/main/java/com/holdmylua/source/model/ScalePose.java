package com.holdmylua.source.model;

import com.holdmylua.source.model.interfaces.Poses;
import com.holdmylua.source.model.parents.AbstractPose;
import net.minecraft.client.util.math.MatrixStack;

public class ScalePose
extends AbstractPose
implements Poses {
    float x;
    float y;
    float z;

    public ScalePose(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void applyPose(MatrixStack matrices) {
        matrices.scale(this.x, this.y, this.z);
    }
}

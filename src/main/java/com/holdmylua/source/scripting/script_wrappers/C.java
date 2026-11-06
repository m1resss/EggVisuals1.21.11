package com.holdmylua.source.scripting.script_wrappers;

import com.holdmylua.source.access.CameraAccessor;
import com.holdmylua.source.annotation.Safe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;

public class C {
    @Safe
    public void setCamPos(double x, double y, double z) {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        if (camera instanceof CameraAccessor) {
            CameraAccessor camera2 = (CameraAccessor)camera;
            camera2.hMI5_0$setPosValues((float)x, (float)y, (float)z);
        }
    }

    @Safe
    public void setCamRot(double x, double y, double z) {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        if (camera instanceof CameraAccessor) {
            CameraAccessor camera2 = (CameraAccessor)camera;
            camera2.hMI5_0$setRotationValues((float)x, (float)y, (float)z);
        }
    }
}

package com.holdmylua.source.scripting.custom_api;

import com.holdmylua.source.annotation.Safe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public class KeyBindManager {
    KeyBinding key;

    @Safe
    public boolean isKeyPressed(int keyCode) {
        if (keyCode != 0) {
            long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
            return GLFW.glfwGetKey((long)windowHandle, (int)keyCode) == 1;
        }
        return false;
    }
}

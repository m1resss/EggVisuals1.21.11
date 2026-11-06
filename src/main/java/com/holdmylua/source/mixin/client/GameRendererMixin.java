package com.holdmylua.source.mixin.client;

import com.holdmylua.source.LuaTestHMI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={GameRenderer.class})
public class GameRendererMixin {
    @Inject(method={"render"}, at={@At(value="HEAD")})
    private void deltaTime(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        float currentTime = (float)GLFW.glfwGetTime();
        LuaTestHMI.deltaTime = currentTime - LuaTestHMI.prevTime;
        LuaTestHMI.prevTime = currentTime;
        LuaTestHMI.deltaTime = MinecraftClient.getInstance().isPaused() ? 0.0f : (float)Math.min(0.05, (double)LuaTestHMI.deltaTime);
    }
}

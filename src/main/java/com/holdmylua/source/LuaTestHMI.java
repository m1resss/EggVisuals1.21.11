package com.holdmylua.source;

import com.holdmylua.source.lua_runtime.resource_controller.LuaAnimationResourceLoader;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuaTestHMI {
    public static final String MOD_ID = "egg";
    public static Matrix4f matricesMain = new Matrix4f();
    public static Matrix4f matricesOff = new Matrix4f();
    public static float tickProgress = 0.0f;
    public static final Logger LOGGER = LoggerFactory.getLogger("holdmyitems");
    public static MinecraftClient client = MinecraftClient.getInstance();
    public static float prevTime = 0.0f;
    public static float deltaTime = 0.0f;

    public static void init() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener((IdentifiableResourceReloadListener) new LuaAnimationResourceLoader());
        Identifier packIdentifier = Identifier.of(MOD_ID, "pack_test");
        FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(container -> {
            ResourceLoader.registerBuiltinPack(packIdentifier, container, Text.of("HMI Pack"), PackActivationType.DEFAULT_ENABLED);
        });
    }
}

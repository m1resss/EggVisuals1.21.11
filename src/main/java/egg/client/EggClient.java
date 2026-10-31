package egg.client;

import egg.client.module.ModuleManager;
import egg.client.module.render.HitSparks;
import egg.client.module.render.ItemRadius;
import egg.client.module.render.TargetESP;
import egg.client.module.render.TotemPopConfetti;
import egg.client.module.render.Trajectories;
import egg.client.module.render.WorldTweaks;
import egg.client.render.shader.FrameBufferPool;
import egg.client.render.shader.GlStateSnapshot;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EggClient implements ClientModInitializer {
    public static final String MOD_ID = "egg";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static EggClient instance;

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("[Egg Visual] Initializing client...");

        ModuleManager.getInstance();
        egg.client.config.ConfigManager.load();
        egg.client.liteapi.LiteApiFeatureControl.getInstance().init();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ModuleManager.getInstance().onTick(client);
        });

        WorldRenderEvents.END_MAIN.register(context -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null || mc.world == null) return;
            Camera camera = mc.gameRenderer != null ? mc.gameRenderer.getCamera() : null;
            if (camera == null) return;

            float tickDelta = mc.getRenderTickCounter() != null ? mc.getRenderTickCounter().getTickProgress(false) : 1.0f;
            MatrixStack matrices = context.matrices();
            Vec3d camPos = camera.getCameraPos();
            VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

            GlStateSnapshot snapshot = GlStateSnapshot.capture();
            try {
                WorldTweaks wt = ModuleManager.getInstance().getModule(WorldTweaks.class);
                if (wt != null && wt.isEnabled()) {
                    wt.renderWorld3D(immediate, camPos);
                    wt.renderWeatherPass(immediate, camPos);
                }

                TotemPopConfetti confetti = ModuleManager.getInstance().getModule(TotemPopConfetti.class);
                if (confetti != null && confetti.isEnabled()) {
                    confetti.render(immediate, camPos);
                }

                Trajectories traj = ModuleManager.getInstance().getModule(Trajectories.class);
                if (traj != null && traj.isEnabled()) {
                    traj.render(immediate, camPos);
                }

                ItemRadius itemRadius = ModuleManager.getInstance().getModule(ItemRadius.class);
                if (itemRadius != null && itemRadius.isEnabled()) {
                    itemRadius.render(immediate, camPos);
                }

                HitSparks hitSparks = ModuleManager.getInstance().getModule(HitSparks.class);
                if (hitSparks != null && hitSparks.isEnabled()) {
                    hitSparks.render(immediate, camPos);
                }

                immediate.draw();

                TargetESP targetESP = ModuleManager.getInstance().getModule(TargetESP.class);
                if (targetESP != null && targetESP.isEnabled()) {
                    targetESP.renderWorld3D(matrices, camera, tickDelta, immediate, camPos);
                }



                immediate.draw();
            } finally {
                snapshot.restore();
                FrameBufferPool.endFrame();
            }
        });

        LOGGER.info("[Egg Visual] Successfully loaded! Press Right Shift to open ClickGUI.");
    }

    public static EggClient getInstance() {
        return instance;
    }

    public ModuleManager getModuleManager() {
        return ModuleManager.getInstance();
    }
}

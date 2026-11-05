package egg.client;

import egg.client.module.ModuleManager;
import egg.client.module.render.HitParticles;
import egg.client.module.render.KillEffect;
import egg.client.module.render.ItemRadius;
import egg.client.module.render.TargetESP;
import egg.client.module.render.Trail;
import egg.client.module.render.Lyrics;
import egg.client.module.render.TotemPopConfetti;
import egg.client.module.render.CustomHitbox;
import egg.client.module.render.Trajectories;
import egg.client.module.render.WorldTweaks;
import egg.client.render.shader.FrameBufferPool;
import egg.client.render.shader.GlStateSnapshot;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
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

        LOGGER.info("[Egg Visual] Successfully loaded! Press Right Shift to open ClickGUI.");
    }

    public static void renderWorld3DPostClouds(RenderTickCounter tickCounter, Camera camera) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;
        if (camera == null) {
            camera = mc.gameRenderer != null ? mc.gameRenderer.getCamera() : null;
        }
        if (camera == null) return;

        float tickDelta = tickCounter != null ? tickCounter.getTickProgress(false) : 1.0f;
        MatrixStack matrices = new MatrixStack();
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
                traj.render(immediate, camPos, tickDelta);
            }

            CustomHitbox customHitbox = ModuleManager.getInstance().getModule(CustomHitbox.class);
            if (customHitbox != null && customHitbox.isEnabled()) {
                customHitbox.render(immediate, camPos, tickDelta);
            }

            ItemRadius itemRadius = ModuleManager.getInstance().getModule(ItemRadius.class);
            if (itemRadius != null && itemRadius.isEnabled()) {
                itemRadius.render(immediate, camPos);
            }

            HitParticles hitParticles = ModuleManager.getInstance().getModule(HitParticles.class);
            if (hitParticles != null && hitParticles.isEnabled()) {
                hitParticles.render(immediate, camPos);
            }

            KillEffect killEffect = ModuleManager.getInstance().getModule(KillEffect.class);
            if (killEffect != null && killEffect.isEnabled()) {
                killEffect.render(immediate, camPos);
            }

            immediate.draw();

            TargetESP targetESP = ModuleManager.getInstance().getModule(TargetESP.class);
            if (targetESP != null && targetESP.isEnabled()) {
                targetESP.renderWorld3D(matrices, camera, tickDelta, immediate, camPos);
            }

            Trail trail = ModuleManager.getInstance().getModule(Trail.class);
            if (trail != null && trail.isEnabled()) {
                trail.render(matrices, camera, tickDelta, immediate, camPos);
            }

            Lyrics lyrics = ModuleManager.getInstance().getModule(Lyrics.class);
            if (lyrics != null && lyrics.isEnabled()) {
                lyrics.renderWorld3D(matrices, camera, tickDelta, immediate, camPos);
            }

            egg.client.module.render.BlockOverlay blockOverlay = ModuleManager.getInstance().getModule(egg.client.module.render.BlockOverlay.class);
            if (blockOverlay != null && blockOverlay.isEnabled()) {
                blockOverlay.renderWorld3D(matrices, camera, tickDelta, immediate, camPos);
            }

            immediate.draw();
        } finally {
            snapshot.restore();
            FrameBufferPool.endFrame();
        }
    }

    public static EggClient getInstance() {
        return instance;
    }

    public ModuleManager getModuleManager() {
        return ModuleManager.getInstance();
    }
}

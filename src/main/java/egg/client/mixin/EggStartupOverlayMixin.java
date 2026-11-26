package egg.client.mixin;

import egg.client.render.EggStartupAnimation;
import egg.client.render.GuiOverlays;
import egg.client.render.StartupCurtainRenderer;
import egg.client.render.StartupOverlayControl;
import egg.client.render.msdf.pipeline.DrawBatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SplashOverlay.class, priority = 900)
public abstract class EggStartupOverlayMixin implements StartupOverlayControl {
    @Unique private static final long HOLD_AFTER_LOAD_MS = 650L;
    @Unique private static final long CURTAIN_SPLIT_MS = 1050L;

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private ResourceReload reload;
    @Shadow @Final private boolean reloading;
    @Shadow private long reloadCompleteTime;

    @Unique private static boolean egg$startupShown;
    @Unique private boolean egg$active;
    @Unique private long egg$lastTick = -1L;
    @Unique private long egg$animMs;
    @Unique private long egg$holdMs;
    @Unique private long egg$openMs;
    @Unique private boolean egg$opening;
    @Unique private float egg$smoothProgress;

    @Override
    public boolean egg$skipStartup() {
        if (!egg$active || reloadCompleteTime < 0 || egg$opening) return false;
        egg$opening = true;
        return true;
    }

    @Override
    public float egg$getMenuRevealOpacity() {
        return !egg$active ? 1.0f : (egg$opening ? 1.0f : 0.0f);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void egg$prepareStartup(CallbackInfo ci) {
        if (!reloading && !egg$startupShown) {
            egg$startupShown = true;
            egg$active = true;
            if (client != null) {
                EggStartupAnimation.ensureAtlas(client);
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void egg$renderStartup(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!egg$active) return;

        long now = Util.getMeasuringTimeMs();
        long rawDt = egg$lastTick < 0 ? 0L : Math.max(0L, now - egg$lastTick);
        egg$lastTick = now;
        long dt = Math.min(rawDt, 33L);

        egg$animMs += dt;
        boolean loaded = reloadCompleteTime >= 0;
        float targetProgress = loaded ? 1.0f : MathHelper.clamp(reload.getProgress(), 0.0f, 1.0f);
        egg$smoothProgress = MathHelper.lerp(1.0f - (float) Math.exp(-dt / 120.0f), egg$smoothProgress, targetProgress);

        if (loaded && !egg$opening) {
            egg$holdMs += dt;
            if (egg$holdMs >= HOLD_AFTER_LOAD_MS) {
                egg$opening = true;
            }
        }

        if (egg$opening) {
            egg$openMs += dt;
        }

        float split = egg$opening ? MathHelper.clamp(egg$openMs / (float) CURTAIN_SPLIT_MS, 0.0f, 1.0f) : 0.0f;

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();

        if (egg$opening && client.currentScreen != null) {
            client.currentScreen.renderWithTooltip(context, mouseX, mouseY, delta);
        } else {
            context.fill(0, 0, width, height, 0xFF000000);
        }

        final float timeSec = egg$animMs * 0.001f;
        final float splitFinal = split;
        final float introFinal = egg$fade((egg$animMs + 20.0f) / 280.0f);
        final boolean showProgress = !loaded || splitFinal < 0.05f;
        final float progressVal = egg$smoothProgress;

        GuiOverlays.defer(() -> {
            DrawBatcher.flushPending();
            DrawBatcher.setEnabledSilent(false);
            StartupCurtainRenderer.render(client, timeSec, splitFinal, introFinal, showProgress, progressVal, width, height);
        });

        if (egg$opening && egg$openMs >= CURTAIN_SPLIT_MS && client.getOverlay() == (Object) this) {
            client.setOverlay(null);
            StartupCurtainRenderer.dispose();
        }
        ci.cancel();
    }

    @Unique
    private static float egg$fade(float progress) {
        float time = MathHelper.clamp(progress, 0.0f, 1.0f);
        return time * time * (3.0f - 2.0f * time);
    }
}

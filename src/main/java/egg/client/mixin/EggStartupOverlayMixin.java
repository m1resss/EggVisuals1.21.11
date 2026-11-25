package egg.client.mixin;

import egg.client.render.EggStartupAnimation;
import egg.client.render.StartupOverlayControl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SplashOverlay.class, priority = 1200)
public abstract class EggStartupOverlayMixin implements StartupOverlayControl {
    @Unique private static final long SHOWCASE_TIME = 3750;
    @Unique private static final long SCATTER_TIME = 1230;
    @Unique private static final long LOOP_TIME = SHOWCASE_TIME + SCATTER_TIME;
    @Unique private static final long SKIP_FADE_TIME = 420;
    @Unique private static final Text CONTINUE_HINT = Text.literal("Любая клавиша или кнопка мыши");

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private ResourceReload reload;
    @Shadow @Final private boolean reloading;
    @Shadow private long reloadCompleteTime;

    @Unique private static boolean egg$startupShown;
    @Unique private EggStartupAnimation egg$animation;
    @Unique private long egg$firstFrame = -1;
    @Unique private long egg$skipTime = -1;
    @Unique private long egg$menuFadeStart = -1;

    @Override
    public boolean egg$skipStartup() {
        if (egg$animation == null) return false;
        if (egg$skipTime < 0) egg$skipTime = Util.getMeasuringTimeMs();
        return true;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void egg$prepareStartup(CallbackInfo ci) {
        if (!reloading && !egg$startupShown) {
            egg$startupShown = true;
            egg$animation = new EggStartupAnimation();
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void egg$renderStartup(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (egg$animation == null) return;

        long now = Util.getMeasuringTimeMs();
        if (egg$firstFrame < 0) egg$firstFrame = now;
        boolean skipped = egg$skipTime >= 0;
        boolean loaded = reloadCompleteTime >= 0;
        boolean revealMenu = skipped && loaded;
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();

        float menuOpacity = 0f;
        if (revealMenu) {
            if (egg$menuFadeStart < 0) egg$menuFadeStart = now;
            menuOpacity = egg$fade((now - egg$menuFadeStart) / (float) SKIP_FADE_TIME);
            if (client.currentScreen != null) {
                client.currentScreen.renderWithTooltip(context, 0, 0, delta);
            } else {
                client.inGameHud.renderDeferredSubtitles();
            }
            context.createNewRootLayer();
        }
        int backgroundAlpha = Math.round(255 * (1f - menuOpacity));
        context.fill(0, 0, width, height, backgroundAlpha << 24);

        float visibility = skipped ? 1f - egg$fade((now - egg$skipTime) / (float) SKIP_FADE_TIME) : 1f;
        if (visibility > 0f) {
            long phase = Math.max(0, (skipped ? egg$skipTime : now) - egg$firstFrame) % LOOP_TIME;
            float scatter = Math.max(0, phase - SHOWCASE_TIME) / (float) SCATTER_TIME;
            egg$animation.draw(client, context, phase, scatter, visibility * egg$fade(phase / 140f));
        }

        if (!loaded) {
            egg$animation.drawProgress(context, now, reload.getProgress());
        } else if (!skipped) {
            int x = (width - client.textRenderer.getWidth(CONTINUE_HINT)) / 2;
            int alpha = Math.round(190 * egg$fade((now - reloadCompleteTime) / 300f));
            if (alpha > 3) {
                context.drawText(client.textRenderer, CONTINUE_HINT, x, Math.round(height * 0.89f), alpha << 24 | 0xD8DADE, false);
            }
        }

        if (revealMenu && backgroundAlpha == 0 && client.getOverlay() == (Object) this) {
            client.setOverlay(null);
        }
        ci.cancel();
    }

    @Unique
    private static float egg$fade(float progress) {
        float time = MathHelper.clamp(progress, 0f, 1f);
        return time * time * (3f - 2f * time);
    }
}

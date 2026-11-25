package egg.client.mixin;

import egg.client.render.EggStartupAnimation;
import egg.client.render.StartupOverlayControl;
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

@Mixin(value = SplashOverlay.class, priority = 1200)
public abstract class EggStartupOverlayMixin implements StartupOverlayControl {
    @Unique private static final long SHOWCASE_TIME = 3750;
    @Unique private static final long SCATTER_TIME = 1230;
    @Unique private static final long MENU_FADE_TIME = 670;
    @Unique private static final long SKIP_FADE_TIME = 420;

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
        if (egg$skipTime < 0 && egg$menuFadeStart < 0) egg$skipTime = Util.getMeasuringTimeMs();
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
        long age = now - egg$firstFrame;
        long outro = reloadCompleteTime < 0 ? -1 : now - Math.max(reloadCompleteTime, egg$firstFrame + SHOWCASE_TIME);
        boolean skipped = egg$skipTime >= 0;

        if (reloadCompleteTime >= 0 && (skipped || outro >= SCATTER_TIME)) {
            if (egg$menuFadeStart < 0) egg$menuFadeStart = now;
            float menuOpacity = egg$fade((now - egg$menuFadeStart) / (float) (skipped ? SKIP_FADE_TIME : MENU_FADE_TIME));
            if (client.currentScreen != null) {
                client.currentScreen.renderWithTooltip(context, 0, 0, delta);
            } else {
                client.inGameHud.renderDeferredSubtitles();
            }
            context.createNewRootLayer();
            int alpha = Math.round(255 * (1f - menuOpacity));
            context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), alpha << 24);
            if (alpha == 0 && client.getOverlay() == (Object) this) {
                client.setOverlay(null);
            }
        } else if (!skipped) {
            egg$animation.render(client, context, age, Math.max(0, outro) / (float) SCATTER_TIME, reload.getProgress());
        } else {
            context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 0xFF000000);
        }

        if (skipped) {
            float opacity = 1f - egg$fade((now - egg$skipTime) / (float) SKIP_FADE_TIME);
            if (opacity > 0f) {
                egg$animation.draw(client, context, Math.max(0, egg$skipTime - egg$firstFrame), MathHelper.clamp(outro / (float) SCATTER_TIME, 0f, 1f), opacity);
            }
        }
        ci.cancel();
    }

    @Unique
    private static float egg$fade(float progress) {
        float time = MathHelper.clamp(progress, 0f, 1f);
        return time * time * (3f - 2f * time);
    }
}

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

@Mixin(value = SplashOverlay.class, priority = 900)
public abstract class EggStartupOverlayMixin implements StartupOverlayControl {
    @Unique private static final long SCATTER_TIME = 1230;
    @Unique private static final long MENU_FADE_TIME = 420;
    @Unique private static final Text CONTINUE_HINT = Text.literal("Любая клавиша или кнопка мыши");

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private ResourceReload reload;
    @Shadow @Final private boolean reloading;
    @Shadow private long reloadCompleteTime;
    @Shadow private long reloadStartTime;

    @Unique private static boolean egg$startupShown;
    @Unique private EggStartupAnimation egg$animation;
    @Unique private boolean egg$initialStartup;
    @Unique private long egg$lastTick = -1;
    @Unique private long egg$animMs;
    @Unique private boolean egg$skipped;
    @Unique private long egg$scatterMs;
    @Unique private boolean egg$menuFading;
    @Unique private long egg$fadeMs;
    @Unique private long egg$hintMs;
    @Unique private float egg$revealOpacity = 0.0f;

    @Override
    public boolean egg$skipStartup() {
        if (egg$animation == null || reloadCompleteTime < 0) return false;
        egg$skipped = true;
        return true;
    }

    @Override
    public float egg$getMenuRevealOpacity() {
        return egg$revealOpacity;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void egg$prepareStartup(CallbackInfo ci) {
        egg$initialStartup = !reloading && !egg$startupShown;
        egg$startupShown = true;
        egg$animation = new EggStartupAnimation();
        if (client != null) {
            EggStartupAnimation.ensureAtlas(client);
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void egg$renderStartup(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (egg$animation == null) return;

        long now = Util.getMeasuringTimeMs();
        if (reloading && reloadStartTime == -1L) {
            reloadStartTime = now;
        }
        long rawDt = egg$lastTick < 0 ? 0L : Math.max(0L, now - egg$lastTick);
        egg$lastTick = now;
        long dt = Math.min(rawDt, 33L);

        boolean loaded = reloadCompleteTime >= 0;
        if (!egg$initialStartup && loaded && !egg$skipped) {
            egg$skipped = true;
        }
        if (!egg$skipped) {
            egg$animMs += dt;
        } else {
            egg$scatterMs += dt;
        }
        if (loaded && !egg$skipped) {
            egg$hintMs += dt;
        }

        boolean revealMenu = egg$skipped && egg$scatterMs >= SCATTER_TIME && loaded;
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();

        float menuOpacity = 0f;
        if (revealMenu) {
            if (!egg$menuFading) {
                egg$menuFading = true;
            } else {
                egg$fadeMs += dt;
            }
            menuOpacity = egg$fade(egg$fadeMs / (float) MENU_FADE_TIME);
        }
        egg$revealOpacity = menuOpacity;
        if (revealMenu) {
            if (client.currentScreen != null) {
                client.currentScreen.renderWithTooltip(context, 0, 0, delta);
            } else {
                client.inGameHud.renderDeferredSubtitles();
            }
            context.createNewRootLayer();
        }
        int backgroundAlpha = Math.round(255 * (1f - menuOpacity));
        context.fill(0, 0, width, height, backgroundAlpha << 24);

        if (!egg$skipped || egg$scatterMs < SCATTER_TIME) {
            float scatter = Math.min(1f, egg$scatterMs / (float) SCATTER_TIME);
            egg$animation.draw(client, context, egg$animMs, scatter, egg$fade((egg$animMs + 20f) / 140f));
        }

        if (!loaded) {
            egg$animation.drawProgress(context, now, reload.getProgress());
        } else if (!egg$skipped && egg$initialStartup) {
            int x = (width - client.textRenderer.getWidth(CONTINUE_HINT)) / 2;
            int alpha = Math.round(190 * egg$fade(egg$hintMs / 300f));
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

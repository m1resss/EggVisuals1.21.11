package egg.client.cosmetic.watch;

import egg.client.mixin.HeldItemRendererAccessor;
import egg.client.module.cosmetic.CosmeticsModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class WatchAnimator {
    private static final WatchAnimator INSTANCE = new WatchAnimator();

    private static final Vector3f IDLE_POS = new Vector3f(0.73597F, -0.43693F, -0.85587F);
    private static final Vector3f TARGET_POS = new Vector3f(0.0F, -0.12F, -0.5F);
    private static final Quaternionf TARGET_ROT = new Quaternionf(0.12325F, 0.18889F, 0.71204F, 0.66493F);
    private static final Vector3f AXIS_ROT = new Vector3f(-0.8F, 0.25F, -0.45F).normalize();
    private static final Vector3f PIVOT_OFFSET = calculatePivotOffset();

    private static final float[][] KEYFRAMES = new float[][]{
        {0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
        {0.45F, -10.0F, 0.004F, 0.018F, 0.008F},
        {0.85F, 0.0F, 0.0F, 0.006F, 0.0F},
        {1.55F, 58.0F, 0.012F, 0.024F, 0.022F},
        {2.05F, 52.0F, 0.01F, 0.02F, 0.02F},
        {2.7F, -24.0F, -0.006F, 0.01F, 0.0F},
        {3.25F, 2.0F, 0.0F, 0.003F, 0.0F},
        {3.7F, 0.0F, 0.0F, 0.0F, 0.0F},
        {4.2F, 0.0F, 0.0F, 0.0F, 0.0F}
    };

    private long startTimeNanos = -1L;
    private boolean hadItemInHand;
    private int originalSlot = -1;

    private WatchAnimator() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> this.tick());
    }

    public static WatchAnimator getInstance() {
        return INSTANCE;
    }

    private static Vector3f calculatePivotOffset() {
        Vector3f v0 = new Vector3f(TARGET_POS).negate().normalize();
        return v0.sub(new Vector3f(AXIS_ROT).mul(v0.dot(AXIS_ROT))).normalize();
    }

    public boolean isActive() {
        return this.startTimeNanos >= 0L;
    }

    public void trigger() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null || !mc.options.getPerspective().isFirstPerson() || mc.player.isInvisible()) {
            return;
        }
        CosmeticsModule mod = CosmeticsModule.getInstance();
        if (mod == null || mod.getSelectedWatchStyle() == WatchStyle.NONE) {
            return;
        }
        if (this.startTimeNanos >= 0L) {
            this.stop();
            return;
        }
        Hand hand = mc.player.getMainArm() == Arm.RIGHT ? Hand.MAIN_HAND : Hand.OFF_HAND;
        this.hadItemInHand = !mc.player.getStackInHand(hand).isEmpty();
        this.originalSlot = mc.player.getInventory().getSelectedSlot();
        this.startTimeNanos = System.nanoTime();
    }

    public void stop() {
        this.startTimeNanos = -1L;
        this.hadItemInHand = false;
        this.originalSlot = -1;
    }

    private float getElapsedSeconds() {
        if (this.startTimeNanos < 0L) return 0.0F;
        return (float) (System.nanoTime() - this.startTimeNanos) / 1.0E9F;
    }

    public void tick() {
        if (this.startTimeNanos >= 0L) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null
                    || mc.currentScreen != null
                    || !mc.options.getPerspective().isFirstPerson()
                    || mc.player.isInvisible()
                    || mc.player.handSwinging
                    || mc.player.isUsingItem()
                    || mc.player.getInventory().getSelectedSlot() != this.originalSlot
                    || this.getElapsedSeconds() >= KEYFRAMES[KEYFRAMES.length - 1][0]) {
                this.stop();
            }
        }
    }

    public boolean renderWatchInspection(
            HeldItemRenderer renderer,
            AbstractClientPlayerEntity player,
            Hand hand,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light
    ) {
        if (this.startTimeNanos < 0L) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (player != mc.player) return false;

        Hand expectedHand = player.getMainArm() == Arm.RIGHT ? Hand.MAIN_HAND : Hand.OFF_HAND;
        if (hand != expectedHand) return false;

        float maxDuration = KEYFRAMES[KEYFRAMES.length - 1][0];
        float elapsed = Math.min(this.getElapsedSeconds(), maxDuration);
        float blend = smoothstep(elapsed / 0.6F) * smoothstep((maxDuration - elapsed) / 0.55F);
        float axisAngle = evalSpline(elapsed, 1);
        float breath = (float) Math.sin(elapsed * 2.4F) * 0.0035F * blend;

        Vector3f targetWithNoise = new Vector3f(TARGET_POS).add(evalSpline(elapsed, 2), evalSpline(elapsed, 3) + breath, evalSpline(elapsed, 4));
        Vector3f startPos = new Vector3f(IDLE_POS);
        if (this.hadItemInHand) {
            startPos.y -= 0.75F;
        }

        Vector3f curPos = startPos.lerp(targetWithNoise, blend);
        Quaternionf curRot = new Quaternionf().slerp(TARGET_ROT, blend);
        Vector3f offset = new Vector3f(PIVOT_OFFSET).mul(-0.15F);

        matrices.push();
        matrices.translate(curPos.x, curPos.y, curPos.z);
        matrices.translate(offset.x, offset.y, offset.z);
        matrices.multiply(new Quaternionf().rotationAxis(axisAngle * (float) (Math.PI / 180.0), AXIS_ROT));
        matrices.translate(-offset.x, -offset.y, -offset.z);
        matrices.multiply(curRot);
        matrices.translate(-IDLE_POS.x, -IDLE_POS.y, -IDLE_POS.z);

        ((HeldItemRendererAccessor) renderer).egg$renderArmHoldingItem(matrices, queue, light, 0.0F, 0.0F, Arm.RIGHT);
        matrices.pop();
        return true;
    }

    private static float smoothstep(float var0) {
        float var1 = MathHelper.clamp(var0, 0.0F, 1.0F);
        return var1 * var1 * var1 * (var1 * (var1 * 6.0F - 15.0F) + 10.0F);
    }

    private static float evalSpline(float var0, int var1) {
        int var2 = 0;
        while (var2 < KEYFRAMES.length - 2 && var0 > KEYFRAMES[var2 + 1][0]) {
            var2++;
        }
        float var3 = KEYFRAMES[var2][0];
        float var4 = KEYFRAMES[var2 + 1][0];
        float var5 = var4 - var3;
        float var6 = MathHelper.clamp((var0 - var3) / var5, 0.0F, 1.0F);
        float var7 = KEYFRAMES[var2][var1];
        float var8 = KEYFRAMES[var2 + 1][var1];
        float var9 = slope(var2, var1) * var5;
        float var10 = slope(var2 + 1, var1) * var5;
        float var11 = var6 * var6;
        float var12 = var11 * var6;
        return (2.0F * var12 - 3.0F * var11 + 1.0F) * var7
                + (var12 - 2.0F * var11 + var6) * var9
                + (-2.0F * var12 + 3.0F * var11) * var8
                + (var12 - var11) * var10;
    }

    private static float slope(int var0, int var1) {
        if (var0 > 0 && var0 < KEYFRAMES.length - 1) {
            float[] var2 = KEYFRAMES[var0 - 1];
            float[] var3 = KEYFRAMES[var0 + 1];
            return (var3[var1] - var2[var1]) / (var3[0] - var2[0]);
        }
        return 0.0F;
    }
}

package egg.client.render.shader;

import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

public final class DischargeEffect {
    private float visibility = 0.0F;
    private long lastFrameNanos;
    private LivingEntity target;
    private float rotation;
    private int trackedEntityId = -1;
    private int lastHurtTime;
    private int deadEntityId = -1;
    private float pulse;

    public void render(MatrixStack matrices, Camera camera, float tickDelta, LivingEntity livingEntity, int color) {
        long l = System.nanoTime();
        float f = this.lastFrameNanos == 0L ? 0.016666668F : Math.min((float)(l - this.lastFrameNanos) / 1.0E9F, 0.05F);
        this.lastFrameNanos = l;

        float targetVis = livingEntity == null ? 0.0F : 1.0F;
        this.visibility = MathHelper.stepTowards(this.visibility, targetVis, f * 4.0F);

        if (livingEntity != null) {
            if (this.target == null || livingEntity.getId() != this.trackedEntityId) {
                this.target = livingEntity;
                this.trackedEntityId = livingEntity.getId();
                this.pulse = 0.0F;
            } else {
                this.target = livingEntity;
            }
        } else if (this.visibility <= 0.0F) {
            this.target = null;
            this.trackedEntityId = -1;
        }

        if (this.target != null && !this.target.isRemoved()) {
            int hurt = this.target.hurtTime;
            if (this.trackedEntityId != this.target.getId()) {
                this.trackedEntityId = this.target.getId();
                this.lastHurtTime = hurt;
            }

            if (hurt > this.lastHurtTime) {
                this.pulse = 1.0F;
            }

            this.lastHurtTime = hurt;
            if (this.target.isDead() && this.deadEntityId != this.target.getId()) {
                this.deadEntityId = this.target.getId();
                this.pulse = 1.0F;
            }

            float speedMult = 1.0F;
            egg.client.module.render.TargetESP esp = egg.client.module.render.TargetESP.getInstance();
            if (esp != null) {
                speedMult = (float) (esp.getDischargeSpeed() / 2.2);
            }
            this.rotation = (this.rotation + f * (float) (Math.PI * 5.0 / 6.0) * (1.0F + 2.0F * this.pulse) * speedMult) % (float) (Math.PI * 2);
            this.pulse = Math.max(0.0F, this.pulse - f * 3.2F);
            float f2 = this.visibility;
            if (!(f2 <= 0.01F)) {
                float f4 = 0.0F;
                float f5 = this.target.getHeight();
                float f6 = Math.min(0.12F, (f5 - f4) * 0.2F);

                int a = (int)(((color >> 24) & 0xFF) * f2);
                int finalColor = (color & 0x00FFFFFF) | (a << 24);

                DischargeRenderer.INSTANCE.render(
                    matrices,
                    camera,
                    tickDelta,
                    this.target,
                    finalColor,
                    this.pulse,
                    f4 + f6,
                    f5 - f6,
                    this.rotation
                );
            }
        }
    }

    public void reset() {
        this.visibility = 0.0F;
        this.target = null;
        this.trackedEntityId = -1;
        this.pulse = 0.0F;
        DischargeRenderer.INSTANCE.reset();
    }
}

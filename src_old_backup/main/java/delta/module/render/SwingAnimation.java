package delta.module.render;

import delta.core.Category;
import delta.core.EventTarget;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.HandAnimationEvent;
import delta.setting.ModeSetting;
import delta.setting.SliderSetting;
import lombok.Generated;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

@ModuleRegister(a = "Swing Animation", b = "Настраивает анимацию взмаха руки", c = Category.Render)
public class SwingAnimation extends Module {
    private final ModeSetting c = new ModeSetting("Режим анимации", "Мод 1", "Мод 1", "Мод 2", "Мод 3", "Мод 4", "Мод 5", "Мод 6", "Мод 7", "Мод 8", "Мод 9", "Сдвиг", "Слом", "Выпад");
    private final SliderSetting d = (SliderSetting) new SliderSetting("Угол поворота", 75.0f, 0.0f, 360.0f, 1.0f).a(() -> {
        return Boolean.valueOf(this.c.l("Мод 1"));
    });
    private final SliderSetting mod3Angle = (SliderSetting) new SliderSetting("Градус поворота", 30.0f, -180.0f, 180.0f, 1.0f).a(() -> {
        return Boolean.valueOf(this.c.l("Мод 3"));
    });
    private final SliderSetting e = (SliderSetting) new SliderSetting("Наклон кончика", -20.0f, -90.0f, 90.0f, 1.0f).a(() -> {
        return Boolean.valueOf(this.c.l("Мод 1") || this.c.l("Мод 2") || this.c.l("Мод 3") || this.c.l("Мод 4"));
    });
    private final SliderSetting f = new SliderSetting("Интенсивность взмаха", 5.0f, 1.0f, 10.0f, 1.0f);
    private final SliderSetting returnSpeed = new SliderSetting("Скорость возвращения", 5.0f, 1.0f, 10.0f, 0.5f);

    @Generated
    public ModeSetting r() {
        return this.c;
    }

    @Generated
    public SliderSetting s() {
        return this.d;
    }

    @Generated
    public SliderSetting getMod3Angle() {
        return this.mod3Angle;
    }

    @Generated
    public SliderSetting t() {
        return this.e;
    }

    @Generated
    public SliderSetting u() {
        return this.f;
    }

    @Generated
    public SliderSetting getReturnSpeed() {
        return this.returnSpeed;
    }

    public SwingAnimation() {
        a(this.c, this.d, this.mod3Angle, this.e, this.f, this.returnSpeed);
    }

    @EventTarget
    public void a(HandAnimationEvent event) {
        if (event.c() == Hand.MAIN_HAND) {
            MatrixStack matrices = event.b();
            float rawP = MathHelper.clamp(event.d(), 0.0f, 1.0f);
            float retVal = this.returnSpeed.c().floatValue();

            float split;
            if (retVal <= 5.0f) {
                float ratio = (retVal - 1.0f) / 4.0f;
                split = 0.18f + (ratio * 0.32f);
            } else {
                float ratio = (retVal - 5.0f) / 5.0f;
                split = 0.50f + (ratio * 0.32f);
            }

            float anim;
            float progress;
            if (rawP <= split) {
                float fwd = split > 0.001f ? (rawP / split) : 1.0f;
                float easeFwd = (float) (0.5d * (1.0d - Math.cos(((double) fwd) * Math.PI)));
                anim = easeFwd;
                progress = easeFwd * 0.5f;
            } else {
                float ret = (1.0f - split) > 0.001f ? ((rawP - split) / (1.0f - split)) : 1.0f;
                ret = MathHelper.clamp(ret, 0.0f, 1.0f);
                float easeRet = (float) (0.5d * (1.0d + Math.cos(((double) ret) * Math.PI)));
                anim = easeRet;
                progress = 0.5f + ((1.0f - easeRet) * 0.5f);
            }

            float power = this.f.c().floatValue() * 10.0f;
            int arm = event.e();
            String mode = this.c.c();

            boolean isLegacy = mode.equals("Мод 1") || mode.equals("Мод 2") || mode.equals("Мод 3") || mode.equals("Мод 4");
            if (isLegacy) {
                matrices.translate(arm * 0.72f, -0.5f, -1.0f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-this.e.c().floatValue()));
            }

            switch (mode) {
                case "Мод 1":
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(arm * 90));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(arm * (-70)));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((-this.d.c().floatValue()) - (power * anim)));
                    break;
                case "Мод 2":
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(arm * 90));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(arm * (-65)));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((-65.0f) + (power * anim)));
                    break;
                case "Мод 3":
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(arm * (-90)));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(arm * 60));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.mod3Angle.c().floatValue()));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(arm * power * anim));
                    break;
                case "Мод 4":
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(arm * 90));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(arm * (-75)));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((-45.0f) - (power * anim)));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(arm * power * anim * 0.5f));
                    break;
                case "Мод 5":
                    matrices.translate(arm * 0.5f, -0.5f, -0.72f);
                    float strength = power / 80.0f;
                    float swing = anim * anim;
                    float twist = (float) Math.sin(((double) (progress * progress)) * 3.1415936112270124d);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(arm * (45.0f + (twist * (-20.0f) * strength))));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(arm * swing * (-22.0f) * strength));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swing * (-85.0f) * strength));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(arm * (-45.0f)));
                    break;
                case "Мод 6": {
                    float thrust = (float) Math.sin(progress * Math.PI);
                    float thrustScale = power / 50.0f;
                    matrices.translate(arm * (0.56f - thrust * 0.16f), -0.46f + thrust * 0.06f, -0.78f - thrust * 0.45f * thrustScale);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(arm * (45.0f + thrust * 12.0f)));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(arm * (-20.0f + thrust * 10.0f)));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-25.0f - thrust * 18.0f * thrustScale));
                    break;
                }
                case "Мод 7": {
                    float spin = progress * 360.0f * (power / 50.0f);
                    float bounce = (float) Math.sin(progress * Math.PI);
                    matrices.translate(arm * 0.60f, -0.48f - bounce * 0.10f, -0.82f - bounce * 0.14f);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(arm * 60.0f));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30.0f));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(arm * (-35.0f + spin)));
                    break;
                }
                case "Мод 8": {
                    float slide = (float) Math.sin(progress * Math.PI) * (power / 50.0f);
                    matrices.translate(arm * (0.54f - slide * 0.22f), -0.44f - slide * 0.14f, -0.78f - slide * 0.12f);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(arm * (65.0f + slide * 24.0f)));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(arm * (-48.0f - slide * 18.0f)));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-32.0f + slide * 32.0f));
                    break;
                }
                case "Мод 9": {
                    float cleave = (float) Math.sin(progress * Math.PI);
                    float cleaveScale = power / 50.0f;
                    matrices.translate(arm * (0.48f - cleave * 0.10f), -0.38f - cleave * 0.28f * cleaveScale, -0.75f - cleave * 0.18f);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(arm * (20.0f + cleave * 14.0f)));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(arm * (-10.0f + cleave * 6.0f)));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(40.0f - (cleave * 115.0f * cleaveScale)));
                    break;
                }
                case "Сдвиг": {
                    float f4 = anim;
                    float str = power / 50.0f;
                    matrices.translate(arm * 0.56f, -0.52f, -0.72f);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(arm * 90.0f));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(arm * -60.0f));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f - 20.0f * f4 * str));
                    break;
                }
                case "Слом": {
                    float f4 = anim;
                    float str = power / 50.0f;
                    matrices.translate(arm * 0.56f, -0.52f, -0.72f);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(arm * 90.0f));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(arm * -30.0f));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f - 20.0f * f4 * str));
                    break;
                }
                case "Выпад": {
                    float f2 = (float) Math.sin(((double) (progress * progress)) * Math.PI);
                    float f3 = (float) Math.sin(Math.sqrt((double) progress) * Math.PI);
                    float f4 = anim;
                    float str = power / 50.0f;
                    matrices.translate(arm * 0.56f, -0.52f, -0.72f);
                    matrices.translate(0.0f, 0.0f, -f4 * 0.2f);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(arm * 45.0f));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(arm * f2 * -20.0f));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(arm * f3 * -20.0f));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(f3 * -MathHelper.clamp(str * 55.0f, 55.0f, 80.0f)));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(arm * 10.0f));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80.0f));
                    break;
                }
            }
            event.a(true);
        }
    }
}

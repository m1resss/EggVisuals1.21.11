package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;

public class SwingAnimation extends Module {
    private static SwingAnimation instance;

    private static final String[] MODES = new String[]{
            "Обычная",
            "Блок-хит",
            "Боковой",
            "Срез",
            "Укол",
            "Удар вниз",
            "Плавный",
            "Орбита",
            "Отдача"
    };

    private final ModeSetting mode = new ModeSetting("Режим", "Блок-хит", "Стиль анимации взмаха руки", MODES);
    private final SliderSetting duration = new SliderSetting("Длительность", 2.0, 0.5, 5.0, 0.1, "Множитель длительности взмаха");
    private final BooleanSetting bothHands = new BooleanSetting("Обе руки", false, "Применять анимацию взмаха к обеим рукам");

    public SwingAnimation() {
        super("Swing Animation", "Изменяет анимацию взмаха и скорость удара рукой от 1-го лица", Category.ESP);
        instance = this;
        addSettings(mode, duration, bothHands);
    }

    public static SwingAnimation getInstance() {
        return instance;
    }

    public boolean isBothHands() {
        return bothHands.get();
    }

    public int modifySwingDuration(int baseDuration) {
        float mult = (float) duration.get();
        if (!Float.isFinite(mult)) {
            mult = 2.0f;
        }
        return Math.max(1, (int) (baseDuration * MathHelper.clamp(mult, 0.5f, 5.0f)));
    }

    private int getModeIndex() {
        String current = mode.get();
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i].equals(current)) {
                return i;
            }
        }
        return 0;
    }

    public void applySwing(MatrixStack matrices, float swingProgress, float equipProgress, Arm arm) {
        int armSign = (arm == Arm.RIGHT) ? 1 : -1;
        int idx = MathHelper.clamp(getModeIndex(), 0, 8);
        float p = MathHelper.clamp(swingProgress, 0.0f, 1.0f);
        float sSqrt = MathHelper.sin(MathHelper.sqrt(p) * (float) Math.PI);
        float sLin = MathHelper.sin(p * (float) Math.PI);

        if (idx <= 6) {
            matrices.translate(-armSign * 0.56f, 0.52f + equipProgress * 0.6f, 0.72f);
        }

        switch (idx) {
            case 1 -> {
                if (p > 0.0f) {
                    matrices.translate(armSign * 0.56f, equipProgress * -0.2f - 0.5f, -0.7f);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * 45.0f));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sSqrt * -85.0f));
                    matrices.translate(armSign * -0.1f, 0.28f, 0.2f);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-85.0f));
                } else {
                    applyBaseEquip(matrices, armSign, equipProgress);
                    applyVanillaSwing(matrices, p, armSign);
                }
            }
            case 2 -> {
                applyBaseEquip(matrices, armSign, 0.0f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(50.0f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * -60.0f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * (110.0f + 20.0f * sSqrt)));
            }
            case 3 -> {
                applyBaseEquip(matrices, armSign, 0.0f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(50.0f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * (-30.0f * (1.0f - sSqrt) - 30.0f)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * 110.0f));
            }
            case 4 -> {
                applyBaseEquip(matrices, armSign, 0.0f);
                matrices.translate(armSign * 0.1f, -0.2f, -0.3f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30.0f * sLin - 36.0f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * 25.0f * sLin));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * 12.0f));
            }
            case 5 -> {
                applyBaseEquip(matrices, armSign, 0.0f);
                matrices.translate(0.0f, -0.2f, -0.4f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-120.0f * sSqrt - 3.0f));
            }
            case 6 -> {
                applyBaseEquip(matrices, armSign, equipProgress);
                matrices.translate(0.0f, 0.12f * sLin, 0.0f);
                applyVanillaSwing(matrices, p, armSign);
            }
            case 7 -> {
                float orbit = MathHelper.sin(p * (float) Math.PI * 2.0f) * sLin;
                applyPivotTransform(matrices, armSign, 0.0f, 0.2f, -0.1f, -0.16f * orbit, 0.1f * sLin, -0.12f * sLin, -35.0f * sLin, 55.0f * orbit, -45.0f * orbit);
            }
            case 8 -> {
                float shake = MathHelper.sin(p * (float) Math.PI * 3.0f) * sLin * (1.0f - p);
                applyPivotTransform(matrices, armSign, 0.0f, -0.1f, 0.05f, -0.05f * sSqrt, 0.06f * sSqrt, -0.22f * sSqrt, -65.0f * sSqrt + 12.0f * shake, 10.0f * shake, 15.0f * sSqrt);
            }
            default -> {
                applyBaseEquip(matrices, armSign, 0.0f);
                applyVanillaSwing(matrices, p, armSign);
            }
        }
    }

    private static void applyBaseEquip(MatrixStack matrices, int armSign, float equipProgress) {
        matrices.translate(armSign * 0.56f, -0.52f - equipProgress * 0.6f, -0.72f);
    }

    private static void applyVanillaSwing(MatrixStack matrices, float swingProgress, int armSign) {
        float sSq = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float sSqrt = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * (45.0f - sSq * 20.0f)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * sSqrt * -20.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sSqrt * -80.0f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * -45.0f));
    }

    private static void applyPivotTransform(
            MatrixStack matrices,
            int armSign,
            float px, float py, float pz,
            float tx, float ty, float tz,
            float rx, float ry, float rz
    ) {
        matrices.translate(armSign * px, py, pz);
        matrices.translate(armSign * tx, ty, tz);
        float degToRad = (float) (Math.PI / 180.0);
        matrices.multiply(new Quaternionf().rotationXYZ(rx * degToRad, armSign * ry * degToRad, armSign * rz * degToRad));
        matrices.translate(-armSign * px, -py, -pz);
    }
}

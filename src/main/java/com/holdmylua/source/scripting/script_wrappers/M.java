package com.holdmylua.source.scripting.script_wrappers;

import com.holdmylua.source.annotation.Safe;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;

public class M {
    public double PI = 3.1415927410125732;

    @Safe
    public void scale(MatrixStack matrices, double x, double y, double z) {
        matrices.scale((float)x, (float)y, (float)z);
    }

    @Safe
    public void push(MatrixStack matrices) {
        matrices.push();
    }

    @Safe
    public void pop(MatrixStack matrices) {
        matrices.pop();
    }

    @Safe
    public void moveX(MatrixStack matrices, double amount) {
        matrices.translate(amount, 0.0, 0.0);
    }

    @Safe
    public void moveY(MatrixStack matrices, double amount) {
        matrices.translate(0.0, amount, 0.0);
    }

    @Safe
    public void moveZ(MatrixStack matrices, double amount) {
        matrices.translate(0.0, 0.0, amount);
    }

    @Safe
    public void translate(MatrixStack matrices, double x, double y, double z) {
        matrices.translate(x, y, z);
    }

    @Safe
    public void rotateX(MatrixStack matrices, double amount) {
        matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_X.rotationDegrees((float)amount));
    }

    @Safe
    public void rotateY(MatrixStack matrices, double amount) {
        matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_Y.rotationDegrees((float)amount));
    }

    @Safe
    public void rotateZ(MatrixStack matrices, double amount) {
        matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_Z.rotationDegrees((float)amount));
    }

    @Safe
    public void rotateX(MatrixStack matrices, double amount, double x, double y, double z) {
        matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_X.rotationDegrees((float)amount), (float)x, (float)y, (float)z);
    }

    @Safe
    public void rotateY(MatrixStack matrices, double amount, double x, double y, double z) {
        matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_Y.rotationDegrees((float)amount), (float)x, (float)y, (float)z);
    }

    @Safe
    public void rotateZ(MatrixStack matrices, double amount, double x, double y, double z) {
        matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_Z.rotationDegrees((float)amount), (float)x, (float)y, (float)z);
    }

    @Safe
    public double sin(double a) {
        return Math.sin(a);
    }

    @Safe
    public double cos(double a) {
        return Math.cos(a);
    }

    @Safe
    public double clamp(double a, double min, double max) {
        return Math.clamp(a, min, max);
    }

    @Safe
    public double floor(double a) {
        return Math.floor(a);
    }

    @Safe
    public double abs(double a) {
        return Math.abs(a);
    }

    @Safe
    public double lerp(double a, double start, double end) {
        return MathHelper.lerp((double)a, (double)start, (double)end);
    }

    @Safe
    public double pow(double a, double b) {
        return Math.pow(a, b);
    }

    @Safe
    public double ceil(double a) {
        return Math.ceil(a);
    }

    @Safe
    public double round(double a) {
        return Math.round(a);
    }

    @Safe
    public void shear(MatrixStack matrices, double shearX, double shearY, double shearZ) {
        Matrix4f shearMatrix = new Matrix4f(1.0f, (float)shearX, (float)shearX, 0.0f, (float)shearY, 1.0f, (float)shearY, 0.0f, (float)shearZ, (float)shearZ, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        matrices.peek().getPositionMatrix().mul((Matrix4fc)shearMatrix);
    }
}

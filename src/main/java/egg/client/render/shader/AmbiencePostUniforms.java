package egg.client.render.shader;

import java.nio.ByteBuffer;
import net.minecraft.client.gl.DynamicUniformStorage.Uploadable;

public record AmbiencePostUniforms(
    float proj00,
    float proj11,
    float near,
    float far,
    float blurStart,
    float blurEnd,
    float bloomThreshold,
    float bloomIntensity,
    float upViewX,
    float upViewY,
    float upViewZ,
    float reflStrength,
    float reflDistance,
    float skyZenithR,
    float skyZenithG,
    float skyZenithB,
    float skyHorizonR,
    float skyHorizonG,
    float skyHorizonB,
    float sunDirX,
    float sunDirY,
    float sunDirZ,
    float axisXx,
    float axisXy,
    float axisXz,
    float axisZx,
    float axisZy,
    float axisZz,
    float camX,
    float camZ,
    float waveTime,
    float fogR,
    float fogG,
    float fogB,
    float fogDensity,
    float fogStart,
    float fogHeight,
    float fogFalloff,
    float fogNoise,
    float fogCamX,
    float fogCamY,
    float fogCamZ,
    int shaderQuality,
    int fogMode,
    float fogSpeed,
    float screenOriginTop,
    float cloudCamX,
    float cloudCamZ,
    float cloudBottom,
    float cloudThickness,
    float cloudThreshold,
    float cloudExtinction,
    float cloudDistance,
    float cloudLift,
    float cloudR,
    float cloudG,
    float cloudB,
    float cloudA,
    float cloudNight,
    float gradeSaturation,
    float gradeContrast,
    float gradeBrightness,
    float gradeTemperature
) implements Uploadable {
    public static final int BLOCK_SIZE = 272;

    @Override
    public void write(ByteBuffer byteBuffer) {
        byteBuffer.putFloat(this.proj00);
        byteBuffer.putFloat(this.proj11);
        byteBuffer.putFloat(this.near);
        byteBuffer.putFloat(this.far);
        byteBuffer.putFloat(this.blurStart);
        byteBuffer.putFloat(this.blurEnd);
        byteBuffer.putFloat(this.bloomThreshold);
        byteBuffer.putFloat(this.bloomIntensity);
        byteBuffer.putFloat(this.upViewX);
        byteBuffer.putFloat(this.upViewY);
        byteBuffer.putFloat(this.upViewZ);
        byteBuffer.putFloat(this.reflStrength);
        byteBuffer.putFloat(this.reflDistance);
        byteBuffer.putFloat(this.skyZenithR);
        byteBuffer.putFloat(this.skyZenithG);
        byteBuffer.putFloat(this.skyZenithB);
        byteBuffer.putFloat(this.skyHorizonR);
        byteBuffer.putFloat(this.skyHorizonG);
        byteBuffer.putFloat(this.skyHorizonB);
        byteBuffer.putFloat(this.sunDirX);
        byteBuffer.putFloat(this.sunDirY);
        byteBuffer.putFloat(this.sunDirZ);
        byteBuffer.putFloat(this.axisXx);
        byteBuffer.putFloat(this.axisXy);
        byteBuffer.putFloat(this.axisXz);
        byteBuffer.putFloat(this.axisZx);
        byteBuffer.putFloat(this.axisZy);
        byteBuffer.putFloat(this.axisZz);
        byteBuffer.putFloat(this.camX);
        byteBuffer.putFloat(this.camZ);
        byteBuffer.putFloat(this.waveTime);
        byteBuffer.putFloat(this.fogR);
        byteBuffer.putFloat(this.fogG);
        byteBuffer.putFloat(this.fogB);
        byteBuffer.putFloat(this.fogDensity);
        byteBuffer.putFloat(this.fogStart);
        byteBuffer.putFloat(this.fogHeight);
        byteBuffer.putFloat(this.fogFalloff);
        byteBuffer.putFloat(this.fogNoise);
        byteBuffer.putFloat(this.fogCamX);
        byteBuffer.putFloat(this.fogCamY);
        byteBuffer.putFloat(this.fogCamZ);
        byteBuffer.putFloat(0.0F);
        byteBuffer.putFloat(0.0F);
        byteBuffer.putInt(this.shaderQuality);
        byteBuffer.putInt(this.fogMode);
        byteBuffer.putFloat(this.fogSpeed);
        byteBuffer.putFloat(this.screenOriginTop);
        byteBuffer.putFloat(this.cloudCamX);
        byteBuffer.putFloat(this.cloudCamZ);
        byteBuffer.putFloat(this.cloudBottom);
        byteBuffer.putFloat(this.cloudThickness);
        byteBuffer.putFloat(this.cloudThreshold);
        byteBuffer.putFloat(this.cloudExtinction);
        byteBuffer.putFloat(this.cloudDistance);
        byteBuffer.putFloat(this.cloudLift);
        byteBuffer.putFloat(this.cloudR);
        byteBuffer.putFloat(this.cloudG);
        byteBuffer.putFloat(this.cloudB);
        byteBuffer.putFloat(this.cloudA);
        byteBuffer.putFloat(this.cloudNight);
        byteBuffer.putFloat(this.gradeSaturation);
        byteBuffer.putFloat(this.gradeContrast);
        byteBuffer.putFloat(this.gradeBrightness);
        byteBuffer.putFloat(this.gradeTemperature);
        byteBuffer.putFloat(0.0F);
        byteBuffer.putFloat(0.0F);
        byteBuffer.putFloat(0.0F);
    }
}

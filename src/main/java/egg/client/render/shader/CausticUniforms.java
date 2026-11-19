package egg.client.render.shader;

import java.nio.ByteBuffer;
import net.minecraft.client.gl.DynamicUniformStorage.Uploadable;

public record CausticUniforms(
    float progress,
    float time,
    float dispersion,
    float brightness,
    float accentR,
    float accentG,
    float accentB,
    float accentWeight,
    float alpha,
    float twist,
    float pad1,
    float pad2
) implements Uploadable {
    public static final int BLOCK_SIZE = 48;

    @Override
    public void write(ByteBuffer byteBuffer) {
        byteBuffer.putFloat(this.progress);
        byteBuffer.putFloat(this.time);
        byteBuffer.putFloat(this.dispersion);
        byteBuffer.putFloat(this.brightness);
        byteBuffer.putFloat(this.accentR);
        byteBuffer.putFloat(this.accentG);
        byteBuffer.putFloat(this.accentB);
        byteBuffer.putFloat(this.accentWeight);
        byteBuffer.putFloat(this.alpha);
        byteBuffer.putFloat(this.twist);
        byteBuffer.putFloat(0.0f);
        byteBuffer.putFloat(0.0f);
    }
}

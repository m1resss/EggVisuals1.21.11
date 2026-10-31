package egg.client.render.shader;

import java.nio.ByteBuffer;
import net.minecraft.client.gl.DynamicUniformStorage.Uploadable;

public record BlurRectUniforms(float minU, float minV, float maxU, float maxV, float radiusPx) implements Uploadable {
    public static final int BLOCK_SIZE = 32;

    @Override
    public void write(ByteBuffer byteBuffer) {
        byteBuffer.putFloat(this.minU);
        byteBuffer.putFloat(this.minV);
        byteBuffer.putFloat(this.maxU);
        byteBuffer.putFloat(this.maxV);
        byteBuffer.putFloat(this.radiusPx);
        byteBuffer.putFloat(0.0F);
        byteBuffer.putFloat(0.0F);
        byteBuffer.putFloat(0.0F);
    }
}

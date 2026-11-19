package egg.client.render.shader;

import java.nio.ByteBuffer;
import net.minecraft.client.gl.DynamicUniformStorage.Uploadable;

public record AccumBlurUniforms(float factor) implements Uploadable {
    public static final int BLOCK_SIZE = 16;

    @Override
    public void write(ByteBuffer byteBuffer) {
        byteBuffer.putFloat(this.factor);
        byteBuffer.putFloat(0.0F);
        byteBuffer.putFloat(0.0F);
        byteBuffer.putFloat(0.0F);
    }
}

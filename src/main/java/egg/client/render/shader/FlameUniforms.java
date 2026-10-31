package egg.client.render.shader;

import java.nio.ByteBuffer;
import net.minecraft.client.gl.DynamicUniformStorage.Uploadable;

public record FlameUniforms(
    float time,
    float speed,
    float rise,
    float decay,
    float sway,
    float camShiftX,
    float camShiftY,
    float texColor,
    float sharpFlip,
    float scanPhase,
    float scanSpan
) implements Uploadable {
    public static final int BLOCK_SIZE = 48;

    public FlameUniforms(float f, float f2, float f3, float f4, float f5, float f6, float f7, float f8, float f9) {
        this(f, f2, f3, f4, f5, f6, f7, f8, f9, 0.0F, 0.0F);
    }

    @Override
    public void write(ByteBuffer byteBuffer) {
        byteBuffer.putFloat(this.time);
        byteBuffer.putFloat(this.speed);
        byteBuffer.putFloat(this.rise);
        byteBuffer.putFloat(this.decay);
        byteBuffer.putFloat(this.sway);
        byteBuffer.putFloat(this.camShiftX);
        byteBuffer.putFloat(this.camShiftY);
        byteBuffer.putFloat(this.texColor);
        byteBuffer.putFloat(this.sharpFlip);
        byteBuffer.putFloat(this.scanPhase);
        byteBuffer.putFloat(this.scanSpan);
        byteBuffer.putFloat(0.0F);
    }
}

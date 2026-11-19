package egg.client.render.shader;

import java.nio.ByteBuffer;
import net.minecraft.client.gl.DynamicUniformStorage.Uploadable;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

public record MotionBlurUniforms(
    Matrix4fc projection,
    Matrix4fc invProjection,
    Matrix4fc deltaView,
    Matrix4fc invViewRotation,
    Vector3fc selfMin,
    Vector3fc selfMax,
    boolean selfMask,
    float nativeFlip,
    float maxVelocity,
    boolean zeroToOneDepth
) implements Uploadable {
    public static final int BLOCK_SIZE = 304;

    @Override
    public void write(ByteBuffer byteBuffer) {
        putMatrix(byteBuffer, this.projection);
        putMatrix(byteBuffer, this.invProjection);
        putMatrix(byteBuffer, this.deltaView);
        putMatrix(byteBuffer, this.invViewRotation);
        byteBuffer.putFloat(this.selfMin.x()).putFloat(this.selfMin.y()).putFloat(this.selfMin.z()).putFloat(0.0F);
        byteBuffer.putFloat(this.selfMax.x()).putFloat(this.selfMax.y()).putFloat(this.selfMax.z()).putFloat(0.0F);
        ByteBuffer byteBuffer2 = byteBuffer.putFloat(this.nativeFlip).putFloat(this.maxVelocity).putFloat(this.selfMask ? 1.0F : 0.0F);
        if (this.zeroToOneDepth) {
            byteBuffer2.putFloat(1.0F);
        } else {
            byteBuffer2.putFloat(0.0F);
        }
    }

    private static void putMatrix(ByteBuffer byteBuffer, Matrix4fc matrix4fc) {
        for (float f : matrix4fc.get(new float[16])) {
            byteBuffer.putFloat(f);
        }
    }
}

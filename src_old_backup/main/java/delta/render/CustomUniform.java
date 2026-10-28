package delta.render;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

public class CustomUniform {
    private final int location;
    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);

    public CustomUniform(int location) {
        this.location = location;
    }

    public int getLocation() {
        return this.location;
    }

    public void set(float x) {
        if (location != -1) {
            GL20.glUniform1f(location, x);
        }
    }

    public void set(float x, float y) {
        if (location != -1) {
            GL20.glUniform2f(location, x, y);
        }
    }

    public void set(float x, float y, float z) {
        if (location != -1) {
            GL20.glUniform3f(location, x, y, z);
        }
    }

    public void set(float x, float y, float z, float w) {
        if (location != -1) {
            GL20.glUniform4f(location, x, y, z, w);
        }
    }

    public void set(int x) {
        if (location != -1) {
            GL20.glUniform1i(location, x);
        }
    }

    public void set(int x, int y) {
        if (location != -1) {
            GL20.glUniform2i(location, x, y);
        }
    }

    public void set(Matrix4f matrix) {
        if (location != -1 && matrix != null) {
            synchronized (MATRIX_BUFFER) {
                MATRIX_BUFFER.clear();
                matrix.get(MATRIX_BUFFER);
                GL20.glUniformMatrix4fv(location, false, MATRIX_BUFFER);
            }
        }
    }

    public void set(float[] values) {
        if (location != -1 && values != null) {
            switch (values.length) {
                case 1 -> GL20.glUniform1f(location, values[0]);
                case 2 -> GL20.glUniform2f(location, values[0], values[1]);
                case 3 -> GL20.glUniform3f(location, values[0], values[1], values[2]);
                case 4 -> GL20.glUniform4f(location, values[0], values[1], values[2], values[3]);
                case 16 -> {
                    synchronized (MATRIX_BUFFER) {
                        MATRIX_BUFFER.clear();
                        MATRIX_BUFFER.put(values);
                        MATRIX_BUFFER.flip();
                        GL20.glUniformMatrix4fv(location, false, MATRIX_BUFFER);
                    }
                }
            }
        }
    }
}

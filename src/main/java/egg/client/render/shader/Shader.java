package egg.client.render.shader;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Shader {
    private final int programId;
    private final Map<String, Integer> uniformLocationCache = new HashMap<>();
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    public Shader(String folder, String name) {
        String vertSource = loadSource("/assets/egg/shaders/" + folder + "/" + name + ".vsh");
        String fragSource = loadSource("/assets/egg/shaders/" + folder + "/" + name + ".fsh");

        int vertShader = compileShader(GL20.GL_VERTEX_SHADER, vertSource);
        int fragShader = compileShader(GL20.GL_FRAGMENT_SHADER, fragSource);

        this.programId = GL20.glCreateProgram();
        GL20.glAttachShader(this.programId, vertShader);
        GL20.glAttachShader(this.programId, fragShader);
        GL20.glLinkProgram(this.programId);

        if (GL20.glGetProgrami(this.programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(this.programId);
            GL20.glDeleteShader(vertShader);
            GL20.glDeleteShader(fragShader);
            GL20.glDeleteProgram(this.programId);
            throw new RuntimeException("Shader program link failed (" + folder + "/" + name + "): " + log);
        }

        GL20.glDeleteShader(vertShader);
        GL20.glDeleteShader(fragShader);
    }

    private static String loadSource(String path) {
        try (InputStream in = Shader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new RuntimeException("Shader resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read shader: " + path, e);
        }
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new RuntimeException("Shader compilation failed (" + (type == GL20.GL_VERTEX_SHADER ? "VERT" : "FRAG") + "): " + log);
        }
        return shader;
    }

    public void bind() {
        GL20.glUseProgram(this.programId);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public void delete() {
        if (this.programId != 0) {
            GL20.glDeleteProgram(this.programId);
        }
    }

    private int getUniformLocation(String name) {
        return uniformLocationCache.computeIfAbsent(name, n -> GL20.glGetUniformLocation(this.programId, n));
    }

    public void setUniform1i(String name, int value) {
        int loc = getUniformLocation(name);
        if (loc != -1) GL20.glUniform1i(loc, value);
    }

    public void setUniform1f(String name, float value) {
        int loc = getUniformLocation(name);
        if (loc != -1) GL20.glUniform1f(loc, value);
    }

    public void setUniform2f(String name, float x, float y) {
        int loc = getUniformLocation(name);
        if (loc != -1) GL20.glUniform2f(loc, x, y);
    }

    public void setUniform3f(String name, float x, float y, float z) {
        int loc = getUniformLocation(name);
        if (loc != -1) GL20.glUniform3f(loc, x, y, z);
    }

    public void setUniformMatrix4f(String name, Matrix4f matrix) {
        int loc = getUniformLocation(name);
        if (loc != -1) {
            matrixBuffer.clear();
            matrix.get(matrixBuffer);
            matrixBuffer.rewind();
            GL20.glUniformMatrix4fv(loc, false, matrixBuffer);
        }
    }
}

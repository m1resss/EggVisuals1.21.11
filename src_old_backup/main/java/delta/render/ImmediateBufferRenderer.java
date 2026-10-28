package delta.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltBuffer;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class ImmediateBufferRenderer {
    private static int vao = 0;
    private static int vbo = 0;
    private static int quadEbo = 0;
    private static int quadEboCapacity = 0;

    private static int defaultColorProgram = 0;
    private static int defaultTexColorProgram = 0;

    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);

    public static void draw(BuiltBuffer buffer) {
        if (buffer == null) {
            return;
        }

        try {
            BuiltBuffer.DrawParameters params = buffer.getDrawParameters();
            int vertexCount = params.vertexCount();
            if (vertexCount <= 0) {
                return;
            }

            VertexFormat format = params.format();
            VertexFormat.DrawMode mode = params.mode();
            ByteBuffer byteBuffer = buffer.getBuffer();
            if (byteBuffer == null || byteBuffer.remaining() <= 0) {
                return;
            }

            int currentProgram = delta.ui.shader.Shader.getActiveProgramId();
            if (currentProgram == 0) {
                currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            }

            boolean boundInternalProgram = false;
            if (currentProgram == 0) {
                currentProgram = getOrCreateDefaultProgram(format);
                GlStateManager._glUseProgram(currentProgram);
                boundInternalProgram = true;
                uploadMatrices(currentProgram);
            }

            if (vao == 0) {
                vao = GL30.glGenVertexArrays();
            }
            if (vbo == 0) {
                vbo = GL15.glGenBuffers();
            }

            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

            byteBuffer.rewind();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, byteBuffer, GL15.GL_DYNAMIC_DRAW);

            setupAttributes(currentProgram, format);

            if (mode == VertexFormat.DrawMode.QUADS) {
                int quadCount = vertexCount / 4;
                int indexCount = quadCount * 6;
                ensureQuadIndices(indexCount);
                GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, quadEbo);
                GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0);
            } else if (mode == VertexFormat.DrawMode.LINES || mode == VertexFormat.DrawMode.DEBUG_LINES) {
                GL11.glDrawArrays(GL11.GL_LINES, 0, vertexCount);
            } else if (mode == VertexFormat.DrawMode.TRIANGLES) {
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
            } else if (mode == VertexFormat.DrawMode.TRIANGLE_FAN) {
                GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, vertexCount);
            } else if (mode == VertexFormat.DrawMode.DEBUG_LINE_STRIP || mode == VertexFormat.DrawMode.TRIANGLE_STRIP) {
                GL11.glDrawArrays(GL11.GL_LINE_STRIP, 0, vertexCount);
            } else {
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
            }

            disableAttributes(currentProgram, format);

            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

            if (boundInternalProgram) {
                GlStateManager._glUseProgram(0);
            }
        } finally {
            buffer.close();
        }
    }

    private static void setupAttributes(int program, VertexFormat format) {
        int stride = format.getVertexSize();
        for (VertexFormatElement elem : format.getElements()) {
            int offset = format.getOffset(elem);
            int loc = getAttributeLocation(program, elem);
            if (loc != -1) {
                GL20.glEnableVertexAttribArray(loc);
                int glType = getGlType(elem.type());
                boolean normalized = elem.usage() == VertexFormatElement.Usage.COLOR || elem.type() == VertexFormatElement.Type.UBYTE;
                GL20.glVertexAttribPointer(loc, elem.count(), glType, normalized, stride, offset);
            }
        }
    }

    private static void disableAttributes(int program, VertexFormat format) {
        for (VertexFormatElement elem : format.getElements()) {
            int loc = getAttributeLocation(program, elem);
            if (loc != -1) {
                GL20.glDisableVertexAttribArray(loc);
            }
        }
    }

    private static int getAttributeLocation(int program, VertexFormatElement elem) {
        VertexFormatElement.Usage usage = elem.usage();
        if (usage == VertexFormatElement.Usage.POSITION) {
            int loc = GL20.glGetAttribLocation(program, "Position");
            if (loc == -1) loc = GL20.glGetAttribLocation(program, "position");
            return loc;
        } else if (usage == VertexFormatElement.Usage.COLOR) {
            int loc = GL20.glGetAttribLocation(program, "Color");
            if (loc == -1) loc = GL20.glGetAttribLocation(program, "color");
            return loc;
        } else if (usage == VertexFormatElement.Usage.UV) {
            int loc = GL20.glGetAttribLocation(program, "UV0");
            if (loc == -1) loc = GL20.glGetAttribLocation(program, "TexCoord");
            if (loc == -1) loc = GL20.glGetAttribLocation(program, "uv0");
            return loc;
        } else if (usage == VertexFormatElement.Usage.NORMAL) {
            int loc = GL20.glGetAttribLocation(program, "Normal");
            if (loc == -1) loc = GL20.glGetAttribLocation(program, "normal");
            return loc;
        }
        return -1;
    }

    private static int getGlType(VertexFormatElement.Type type) {
        return switch (type) {
            case FLOAT -> GL11.GL_FLOAT;
            case UBYTE -> GL11.GL_UNSIGNED_BYTE;
            case BYTE -> GL11.GL_BYTE;
            case USHORT -> GL11.GL_UNSIGNED_SHORT;
            case SHORT -> GL11.GL_SHORT;
            case UINT -> GL11.GL_UNSIGNED_INT;
            case INT -> GL11.GL_INT;
        };
    }

    private static void ensureQuadIndices(int requiredIndices) {
        if (quadEbo != 0 && quadEboCapacity >= requiredIndices) {
            return;
        }

        int newCapacity = Math.max(requiredIndices, Math.max(quadEboCapacity * 2, 4096));
        if (quadEbo == 0) {
            quadEbo = GL15.glGenBuffers();
            quadEboCapacity = 0;
        }

        IntBuffer ib = BufferUtils.createIntBuffer(newCapacity);
        int quadCount = newCapacity / 6;
        for (int i = 0; i < quadCount; i++) {
            int base = i * 4;
            ib.put(base);
            ib.put(base + 1);
            ib.put(base + 2);
            ib.put(base + 2);
            ib.put(base + 3);
            ib.put(base);
        }
        ib.flip();

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, quadEbo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ib, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        quadEboCapacity = newCapacity;
    }

    private static void uploadMatrices(int program) {
        int mvLoc = GL20.glGetUniformLocation(program, "ModelViewMat");
        if (mvLoc != -1) {
            Matrix4f mv = RenderSystem.getModelViewMatrix();
            if (mv == null) mv = new Matrix4f().identity();
            setUniformMatrix(mvLoc, mv);
        }

        int projLoc = GL20.glGetUniformLocation(program, "ProjMat");
        if (projLoc != -1) {
            MinecraftClient client = MinecraftClient.getInstance();
            Matrix4f proj;
            if (client != null && client.gameRenderer != null && client.world != null && client.options != null && RenderSystem.getProjectionType() == com.mojang.blaze3d.systems.ProjectionType.PERSPECTIVE) {
                proj = client.gameRenderer.getBasicProjectionMatrix(client.options.getFov().getValue().floatValue());
            } else if (client != null && client.getWindow() != null) {
                float w = (float) client.getWindow().getScaledWidth();
                float h = (float) client.getWindow().getScaledHeight();
                proj = new Matrix4f().setOrtho(0.0f, w, h, 0.0f, -1000.0f, 1000.0f);
            } else {
                proj = new Matrix4f().identity();
            }
            setUniformMatrix(projLoc, proj);
        }
    }

    private static void setUniformMatrix(int loc, Matrix4f mat) {
        synchronized (MATRIX_BUFFER) {
            MATRIX_BUFFER.clear();
            mat.get(MATRIX_BUFFER);
            GL20.glUniformMatrix4fv(loc, false, MATRIX_BUFFER);
        }
    }

    private static int getOrCreateDefaultProgram(VertexFormat format) {
        boolean hasTexture = false;
        for (VertexFormatElement elem : format.getElements()) {
            if (elem.usage() == VertexFormatElement.Usage.UV) {
                hasTexture = true;
                break;
            }
        }

        if (hasTexture) {
            if (defaultTexColorProgram == 0) {
                defaultTexColorProgram = createSimpleProgram(true);
            }
            return defaultTexColorProgram;
        } else {
            if (defaultColorProgram == 0) {
                defaultColorProgram = createSimpleProgram(false);
            }
            return defaultColorProgram;
        }
    }

    private static int createSimpleProgram(boolean textured) {
        String vsh;
        String fsh;
        if (textured) {
            vsh = """
                #version 150
                in vec3 Position;
                in vec2 UV0;
                in vec4 Color;
                uniform mat4 ModelViewMat;
                uniform mat4 ProjMat;
                out vec2 texCoord;
                out vec4 vertexColor;
                void main() {
                    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                    texCoord = UV0;
                    vertexColor = Color;
                }
                """;
            fsh = """
                #version 150
                uniform sampler2D Sampler0;
                in vec2 texCoord;
                in vec4 vertexColor;
                out vec4 fragColor;
                void main() {
                    fragColor = texture(Sampler0, texCoord) * vertexColor;
                }
                """;
        } else {
            vsh = """
                #version 150
                in vec3 Position;
                in vec4 Color;
                uniform mat4 ModelViewMat;
                uniform mat4 ProjMat;
                out vec4 vertexColor;
                void main() {
                    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                    vertexColor = Color;
                }
                """;
            fsh = """
                #version 150
                in vec4 vertexColor;
                out vec4 fragColor;
                void main() {
                    fragColor = vertexColor;
                }
                """;
        }

        int vs = GlStateManager.glCreateShader(GL20.GL_VERTEX_SHADER);
        GlStateManager.glShaderSource(vs, vsh);
        GlStateManager.glCompileShader(vs);

        int fs = GlStateManager.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GlStateManager.glShaderSource(fs, fsh);
        GlStateManager.glCompileShader(fs);

        int prog = GlStateManager.glCreateProgram();
        GlStateManager.glAttachShader(prog, vs);
        GlStateManager.glAttachShader(prog, fs);

        GL20.glBindAttribLocation(prog, 0, "Position");
        if (textured) {
            GL20.glBindAttribLocation(prog, 1, "UV0");
            GL20.glBindAttribLocation(prog, 2, "Color");
        } else {
            GL20.glBindAttribLocation(prog, 1, "Color");
        }

        GlStateManager.glLinkProgram(prog);
        GlStateManager.glDeleteShader(vs);
        GlStateManager.glDeleteShader(fs);
        return prog;
    }
}

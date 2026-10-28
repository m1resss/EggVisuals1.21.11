package delta.ui.shader;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import delta.render.CustomUniform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class Shader {
    protected final Identifier identifier;
    protected final VertexFormat vertexFormat;
    protected int programId = -1;
    private final Map<String, CustomUniform> uniformCache = new HashMap<>();

    public Shader(Identifier identifier, VertexFormat vertexFormat) {
        this.identifier = identifier;
        this.vertexFormat = vertexFormat;
    }

    public Identifier getIdentifier() {
        return this.identifier;
    }

    public VertexFormat getVertexFormat() {
        return this.vertexFormat;
    }

    public int getProgramId() {
        if (this.programId == -1) {
            initProgram();
        }
        return this.programId;
    }

    protected abstract void b();

    private static int activeProgramId = 0;

    public static int getActiveProgramId() {
        return activeProgramId;
    }

    public static void resetActiveProgramId() {
        activeProgramId = 0;
    }

    public void a() {
        int id = getProgramId();
        if (id <= 0) {
            return;
        }
        GlStateManager._glUseProgram(id);
        activeProgramId = id;
        uploadDefaultMatrices();
        b();
    }

    public void uploadDefaultMatrices() {
        CustomUniform mv = a("ModelViewMat");
        if (mv != null) {
            mv.set(new Matrix4f().identity());
        }

        CustomUniform proj = a("ProjMat");
        if (proj != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getWindow() != null) {
                float w = (float) client.getWindow().getScaledWidth();
                float h = (float) client.getWindow().getScaledHeight();
                Matrix4f ortho = new Matrix4f().setOrtho(0.0f, w, h, 0.0f, -1000.0f, 1000.0f);
                proj.set(ortho);
            }
        }
    }

    protected CustomUniform a(String name) {
        int id = getProgramId();
        if (id <= 0) {
            return null;
        }
        return uniformCache.computeIfAbsent(name, k -> {
            int loc = GL20.glGetUniformLocation(id, k);
            return new CustomUniform(loc);
        });
    }

    public CustomUniform getUniform(String name) {
        return a(name);
    }

    private void initProgram() {
        try {
            String path = identifier.getPath(); // e.g. "core/rect/rect"
            String vshPath = "/assets/" + identifier.getNamespace() + "/shaders/" + path + ".vsh";
            String fshPath = "/assets/" + identifier.getNamespace() + "/shaders/" + path + ".fsh";

            String vshSource = readResource(vshPath);
            if (vshSource == null) {
                if (path.contains("blur/")) {
                    vshSource = readResource("/assets/" + identifier.getNamespace() + "/shaders/core/blur/kawase.vsh");
                } else if (path.contains("puddles/") || path.contains("water_blobs/") || path.contains("motion_blur/")) {
                    vshSource = readResource("/assets/" + identifier.getNamespace() + "/shaders/core/" + path.substring(0, path.indexOf('/')) + "/fullscreen.vsh");
                    if (vshSource == null) {
                        vshSource = readResource("/assets/delta/shaders/core/motion_blur/fullscreen.vsh");
                    }
                }
            }

            String fshSource = readResource(fshPath);

            if (vshSource == null || fshSource == null) {
                System.err.println("Failed to find shader sources for " + identifier + " (vsh=" + (vshSource != null) + ", fsh=" + (fshSource != null) + ")");
                this.programId = 0;
                return;
            }

            vshSource = processImports(vshSource);
            fshSource = processImports(fshSource);

            int vShader = GlStateManager.glCreateShader(GL20.GL_VERTEX_SHADER);
            GlStateManager.glShaderSource(vShader, vshSource);
            GlStateManager.glCompileShader(vShader);
            if (GlStateManager.glGetShaderi(vShader, GL20.GL_COMPILE_STATUS) == 0) {
                System.err.println("Error compiling vertex shader " + identifier + ": " + GlStateManager.glGetShaderInfoLog(vShader, 1024));
            }

            int fShader = GlStateManager.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GlStateManager.glShaderSource(fShader, fshSource);
            GlStateManager.glCompileShader(fShader);
            if (GlStateManager.glGetShaderi(fShader, GL20.GL_COMPILE_STATUS) == 0) {
                System.err.println("Error compiling fragment shader " + identifier + ": " + GlStateManager.glGetShaderInfoLog(fShader, 1024));
            }

            int program = GlStateManager.glCreateProgram();
            GlStateManager.glAttachShader(program, vShader);
            GlStateManager.glAttachShader(program, fShader);

            GL20.glBindAttribLocation(program, 0, "Position");
            GL20.glBindAttribLocation(program, 1, "UV0");
            GL20.glBindAttribLocation(program, 2, "Color");
            GL20.glBindAttribLocation(program, 3, "Normal");

            GlStateManager.glLinkProgram(program);
            if (GlStateManager.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                System.err.println("Error linking shader program " + identifier + ": " + GlStateManager.glGetProgramInfoLog(program, 1024));
            }

            GlStateManager.glDeleteShader(vShader);
            GlStateManager.glDeleteShader(fShader);

            this.programId = program;
        } catch (Exception e) {
            e.printStackTrace();
            this.programId = 0;
        }
    }

    private String readResource(String resourcePath) {
        try (InputStream in = Shader.class.getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String processImports(String source) {
        if (!source.contains("#moj_import")) {
            return source;
        }
        String common = readResource("/assets/delta/shaders/include/common.glsl");
        if (common == null) {
            common = "";
        }
        return source.replace("#moj_import <delta:common.glsl>", common);
    }
}

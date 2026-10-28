package egg.client.render.msdf;

import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class PanelBlurUtil {

    private static int program = 0;
    private static int vao = 0;
    private static int vbo = 0;
    private static int texId = 0;
    private static int texWidth = 0;
    private static int texHeight = 0;
    private static int helperFbo = 0;

    private static int uRectLoc = -1;
    private static int uScreenSizeLoc = -1;
    private static int uSizeLoc = -1;
    private static int uRadiusLoc = -1;
    private static int uTexLoc = -1;

    private static boolean initFailed = false;

    private PanelBlurUtil() {}

    private static void init() {
        if (initFailed || program != 0) return;
        try {
            String vsh = """
                #version 150
                out vec2 vPos;
                out vec2 vTexCoord;
                uniform vec4 uRect;
                uniform vec2 uScreenSize;
                void main() {
                    vec2 pos[6] = vec2[](
                        vec2(0.0, 0.0),
                        vec2(1.0, 0.0),
                        vec2(1.0, 1.0),
                        vec2(0.0, 0.0),
                        vec2(1.0, 1.0),
                        vec2(0.0, 1.0)
                    );
                    vec2 p = pos[gl_VertexID];
                    vTexCoord = p;
                    vPos = p * uRect.zw;
                    vec2 pixelPos = uRect.xy + p * uRect.zw;
                    vec2 ndc = (pixelPos / uScreenSize) * 2.0 - 1.0;
                    gl_Position = vec4(ndc, 0.0, 1.0);
                }
                """;

            String fsh = """
                #version 150
                in vec2 vPos;
                in vec2 vTexCoord;
                out vec4 fragColor;
                uniform sampler2D uTex;
                uniform vec2 uSize;
                uniform float uRadius;

                float roundedBoxSDF(vec2 p, vec2 b, float r) {
                    vec2 q = abs(p) - b + r;
                    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
                }

                void main() {
                    vec2 halfSize = uSize * 0.5;
                    vec2 center = vPos - halfSize;
                    float r = min(uRadius, min(halfSize.x, halfSize.y));
                    float dist = roundedBoxSDF(center, halfSize, r);
                    float alpha = smoothstep(-0.5, 0.5, dist);
                    if (alpha <= 0.0) {
                        discard;
                    }
                    vec4 texColor = texture(uTex, vTexCoord);
                    fragColor = vec4(texColor.rgb, alpha);
                }
                """;

            int vs = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(vs, vsh);
            GL20.glCompileShader(vs);
            if (GL20.glGetShaderi(vs, GL20.GL_COMPILE_STATUS) == 0) {
                System.err.println("[Delta] PanelBlurUtil VS error: " + GL20.glGetShaderInfoLog(vs));
                initFailed = true;
                return;
            }

            int fs = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(fs, fsh);
            GL20.glCompileShader(fs);
            if (GL20.glGetShaderi(fs, GL20.GL_COMPILE_STATUS) == 0) {
                System.err.println("[Delta] PanelBlurUtil FS error: " + GL20.glGetShaderInfoLog(fs));
                initFailed = true;
                return;
            }

            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vs);
            GL20.glAttachShader(program, fs);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                System.err.println("[Delta] PanelBlurUtil Program link error: " + GL20.glGetProgramInfoLog(program));
                initFailed = true;
                return;
            }

            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);

            uRectLoc = GL20.glGetUniformLocation(program, "uRect");
            uScreenSizeLoc = GL20.glGetUniformLocation(program, "uScreenSize");
            uSizeLoc = GL20.glGetUniformLocation(program, "uSize");
            uRadiusLoc = GL20.glGetUniformLocation(program, "uRadius");
            uTexLoc = GL20.glGetUniformLocation(program, "uTex");

            vao = GL30.glGenVertexArrays();
            vbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            ByteBuffer dummy = MemoryUtil.memAlloc(6 * 4);
            for (int i = 0; i < 6; i++) {
                dummy.putFloat(0.0f);
            }
            dummy.flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, dummy, GL15.GL_STATIC_DRAW);
            MemoryUtil.memFree(dummy);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 1, GL11.GL_FLOAT, false, 0, 0L);
            GL30.glBindVertexArray(0);

            texId = GL11.glGenTextures();
            helperFbo = GL30.glGenFramebuffers();
        } catch (Throwable t) {
            t.printStackTrace();
            initFailed = true;
        }
    }

    public static void blurRoundedPanel(MinecraftClient client, float panelX, float panelY, float panelW, float panelH, float radius) {
        if (client == null || client.gameRenderer == null || client.getFramebuffer() == null) return;
        init();
        try {
            double scaleFactor = client.getWindow().getScaleFactor();
            int fbWidth = client.getWindow().getFramebufferWidth();
            int fbHeight = client.getWindow().getFramebufferHeight();

            int sx = (int) Math.max(0, Math.min(fbWidth, Math.round(panelX * scaleFactor)));
            int sy = (int) Math.max(0, Math.min(fbHeight, Math.round(fbHeight - (panelY + panelH) * scaleFactor)));
            int sw = (int) Math.max(0, Math.min(fbWidth - sx, Math.round(panelW * scaleFactor)));
            int sh = (int) Math.max(0, Math.min(fbHeight - sy, Math.round(panelH * scaleFactor)));

            if (sw <= 0 || sh <= 0) return;

            float physicalRadius = radius * (float) scaleFactor;

            GpuTexture colorAttachment = client.getFramebuffer().getColorAttachment();
            if (!(colorAttachment instanceof GlTexture glTexture)) return;
            int mainTexId = glTexture.getGlId();
            if (mainTexId <= 0) return;

            int prevDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int[] prevViewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);

            try {

                if (!initFailed && program != 0 && texId != 0) {
                    if (sw != texWidth || sh != texHeight) {
                        texWidth = sw;
                        texHeight = sh;
                        GL13.glActiveTexture(GL13.GL_TEXTURE0);
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
                        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, texWidth, texHeight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
                    }

                    boolean copied = false;
                    try {
                        GL43.glCopyImageSubData(
                                mainTexId, GL11.GL_TEXTURE_2D, 0, sx, sy, 0,
                                texId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                                sw, sh, 1
                        );
                        copied = true;
                    } catch (Throwable ignored) {
                    }

                    if (!copied && helperFbo != 0) {
                        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, helperFbo);
                        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, mainTexId, 0);
                        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                        GL13.glActiveTexture(GL13.GL_TEXTURE0);
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
                        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, sx, sy, sw, sh);
                    }
                }

                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(sx, sy, sw, sh);
                client.gameRenderer.renderBlur();
                GL11.glDisable(GL11.GL_SCISSOR_TEST);

                if (!initFailed && program != 0 && texId != 0 && vao != 0 && helperFbo != 0) {
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, helperFbo);
                    GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, mainTexId, 0);
                    GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
                    GL11.glViewport(0, 0, fbWidth, fbHeight);

                    GL20.glUseProgram(program);
                    GL20.glUniform4f(uRectLoc, (float) sx, (float) sy, (float) sw, (float) sh);
                    GL20.glUniform2f(uScreenSizeLoc, (float) fbWidth, (float) fbHeight);
                    GL20.glUniform2f(uSizeLoc, (float) sw, (float) sh);
                    GL20.glUniform1f(uRadiusLoc, physicalRadius);
                    GL20.glUniform1i(uTexLoc, 0);

                    GL13.glActiveTexture(GL13.GL_TEXTURE0);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
                    GL30.glBindVertexArray(vao);

                    boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
                    boolean cullFace = GL11.glIsEnabled(GL11.GL_CULL_FACE);
                    boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);

                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    GL11.glDisable(GL11.GL_CULL_FACE);
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);
                    GL11.glDepthMask(false);

                    GL11.glEnable(GL11.GL_BLEND);
                    GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);

                    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

                    if (depthTest) GL11.glEnable(GL11.GL_DEPTH_TEST);
                    if (cullFace) GL11.glEnable(GL11.GL_CULL_FACE);
                    GL11.glDepthMask(true);
                    if (!blend) GL11.glDisable(GL11.GL_BLEND);

                    GL30.glBindVertexArray(0);
                    GL20.glUseProgram(0);
                }
            } finally {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
                GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}

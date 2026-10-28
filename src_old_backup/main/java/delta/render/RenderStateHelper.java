package delta.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.util.Identifier;
import static delta.core.Interface.aM_;

/**
 * Direct OpenGL state management helper for Minecraft 1.21.11 Fabric.
 * Replaces removed Blaze3D RenderSystem fixed-function wrappers.
 */
public final class RenderStateHelper {
    private RenderStateHelper() {}

    public static void enableBlend() {
        GL11.glEnable(GL11.GL_BLEND);
    }

    public static void disableBlend() {
        GL11.glDisable(GL11.GL_BLEND);
    }

    public static void defaultBlendFunc() {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void blendFunc(int src, int dst) {
        GL11.glBlendFunc(src, dst);
    }

    public static void enableCull() {
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    public static void disableCull() {
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    public static void enableDepthTest() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    public static void disableDepthTest() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    public static void depthMask(boolean flag) {
        GL11.glDepthMask(flag);
    }

    public static void depthFunc(int func) {
        GL11.glDepthFunc(func);
    }

    public static void lineWidth(float width) {
        GL11.glLineWidth(width);
    }

    public static void setShaderTexture(int unit, int textureId) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, Math.max(0, textureId));
    }

    public static void setShaderTexture(int unit, GpuTexture gpuTexture) {
        setShaderTexture(unit, getTextureId(gpuTexture));
    }

    public static void setShaderTexture(int unit, AbstractTexture texture) {
        setShaderTexture(unit, getTextureId(texture));
    }

    public static void setShaderTexture(int unit, Identifier identifier) {
        if (aM_ != null && aM_.getTextureManager() != null) {
            setShaderTexture(unit, aM_.getTextureManager().getTexture(identifier));
        } else {
            setShaderTexture(unit, 0);
        }
    }

    public static int getTextureId(GpuTexture gpuTexture) {
        if (gpuTexture instanceof GlTexture glTex) {
            return glTex.getGlId();
        }
        return 0;
    }

    public static int getTextureId(AbstractTexture texture) {
        if (texture != null) {
            try {
                if (texture.getGlTexture() instanceof GlTexture glTex) {
                    return glTex.getGlId();
                }
            } catch (Throwable ignored) {
                return 0;
            }
        }
        return 0;
    }

    public static int getTextureId(Identifier identifier) {
        if (aM_ != null && aM_.getTextureManager() != null) {
            try {
                return getTextureId(aM_.getTextureManager().getTexture(identifier));
            } catch (Throwable ignored) {
                return 0;
            }
        }
        return 0;
    }

    public static void bindFramebuffer(net.minecraft.client.gl.Framebuffer fb) {
        if (fb != null && fb.getColorAttachment() instanceof GlTexture glTex) {
            com.mojang.blaze3d.systems.GpuDevice device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
            if (device instanceof net.minecraft.client.gl.GlBackend backend) {
                int fbo = glTex.getOrCreateFramebuffer(backend.getBufferManager(), fb.getDepthAttachment());
                com.mojang.blaze3d.opengl.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, fbo);
                com.mojang.blaze3d.opengl.GlStateManager._viewport(0, 0, fb.textureWidth, fb.textureHeight);
            }
        }
    }

    public static void unbindFramebuffer() {
        if (aM_ != null && aM_.getFramebuffer() != null) {
            bindFramebuffer(aM_.getFramebuffer());
        } else {
            com.mojang.blaze3d.opengl.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, 0);
        }
    }
}

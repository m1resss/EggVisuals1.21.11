package egg.client.render.shader;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GlBackend;
import org.lwjgl.opengl.GL11;

public final class GlStateSnapshot {
    private final boolean openGl = isOpenGl();
    private final int drawFramebuffer;
    private final int readFramebuffer;
    private final int[] viewport = new int[4];

    private GlStateSnapshot() {
        if (!this.openGl) {
            this.drawFramebuffer = 0;
            this.readFramebuffer = 0;
        } else {
            this.drawFramebuffer = GL11.glGetInteger(36006);
            this.readFramebuffer = GL11.glGetInteger(36010);
            GL11.glGetIntegerv(2978, this.viewport);
        }
    }

    public static boolean isOpenGl() {
        return RenderSystem.tryGetDevice() instanceof GlBackend;
    }

    public static GlStateSnapshot capture() {
        return new GlStateSnapshot();
    }

    public void restore() {
        if (this.openGl) {
            GlStateManager._glBindFramebuffer(36009, this.drawFramebuffer);
            GlStateManager._glBindFramebuffer(36008, this.readFramebuffer);
            GlStateManager._viewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);
        }
    }
}

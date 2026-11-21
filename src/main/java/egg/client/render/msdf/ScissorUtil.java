package egg.client.render.msdf;

import egg.client.render.msdf.pipeline.DrawBatcher;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public class ScissorUtil {
    private static final Deque<a> b = new ArrayDeque<>();
    private static final Matrix4f c = new Matrix4f();

    private ScissorUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static a getActiveScissor() {
        return b.peek();
    }

    public static boolean hasActiveScissor() {
        return !b.isEmpty();
    }

    public static void reset() {
        DrawBatcher.flushPending();
        b.clear();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static void a(Matrix3x2f matrixStack, float x, float y, float width, float height) {
        DrawBatcher.flushPending();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;
        float scaleFactor = (float) client.getWindow().getScaleFactor();
        float actualX = x;
        float actualY = y;
        float actualW = width;
        float actualH = height;
        if (matrixStack != null) {
            float x1 = (matrixStack.m00 * x) + (matrixStack.m10 * y) + matrixStack.m20;
            float y1 = (matrixStack.m01 * x) + (matrixStack.m11 * y) + matrixStack.m21;
            float x2 = (matrixStack.m00 * (x + width)) + (matrixStack.m10 * (y + height)) + matrixStack.m20;
            float y2 = (matrixStack.m01 * (x + width)) + (matrixStack.m11 * (y + height)) + matrixStack.m21;
            actualX = Math.min(x1, x2);
            actualY = Math.min(y1, y2);
            actualW = Math.abs(x2 - x1);
            actualH = Math.abs(y2 - y1);
        }
        int fbHeight = client.getWindow().getFramebufferHeight();
        int fbWidth = client.getWindow().getFramebufferWidth();
        int sx = (int) Math.round(actualX * scaleFactor);
        int sy = fbHeight - (int) Math.round((actualY + actualH) * scaleFactor);
        int sw = (int) Math.round(actualW * scaleFactor);
        int sh = (int) Math.round(actualH * scaleFactor);
        sx = Math.max(0, Math.min(fbWidth, sx));
        sy = Math.max(0, Math.min(fbHeight, sy));
        sw = Math.max(0, Math.min(fbWidth - sx, sw));
        sh = Math.max(0, Math.min(fbHeight - sy, sh));
        a scissorBox = new a(sx, sy, sw, sh);
        if (!b.isEmpty()) {
            scissorBox = scissorBox.a(b.peek());
        }
        b.push(scissorBox);
        if (matrixStack instanceof Matrix3x2fStack stack) {
            stack.pushMatrix();
        }
        a(scissorBox);
    }

    public static void a(Matrix3x2f matrixStack) {
        DrawBatcher.flushPending();
        if (!b.isEmpty()) {
            b.pop();
        }
        if (b.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            a(b.peek());
        }
        if (matrixStack instanceof Matrix3x2fStack stack) {
            stack.popMatrix();
        }
    }

    public static void a(MatrixStack matrixStack, float x, float y, float width, float height) {
        DrawBatcher.flushPending();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;
        float scaleFactor = (float) client.getWindow().getScaleFactor();
        int fbHeight = client.getWindow().getFramebufferHeight();
        int fbWidth = client.getWindow().getFramebufferWidth();
        int sx = (int) Math.round(x * scaleFactor);
        int sy = fbHeight - (int) Math.round((y + height) * scaleFactor);
        int sw = (int) Math.round(width * scaleFactor);
        int sh = (int) Math.round(height * scaleFactor);
        sx = Math.max(0, Math.min(fbWidth, sx));
        sy = Math.max(0, Math.min(fbHeight, sy));
        sw = Math.max(0, Math.min(fbWidth - sx, sw));
        sh = Math.max(0, Math.min(fbHeight - sy, sh));
        a scissorBox = new a(sx, sy, sw, sh);
        if (!b.isEmpty()) {
            scissorBox = scissorBox.a(b.peek());
        }
        b.push(scissorBox);
        if (matrixStack != null) {
            matrixStack.push();
        }
        a(scissorBox);
    }

    public static void a(MatrixStack matrixStack) {
        DrawBatcher.flushPending();
        if (!b.isEmpty()) {
            b.pop();
        }
        if (b.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            a(b.peek());
        }
        if (matrixStack != null) {
            matrixStack.pop();
        }
    }

    private static void a(a box) {
    }

    public static final class a {
        public final int a;
        public final int b;
        public final int c;
        public final int d;

        public a(int x, int y, int w, int h) {
            this.a = x;
            this.b = y;
            this.c = w;
            this.d = h;
        }

        public int a() {
            return this.a;
        }

        public int b() {
            return this.b;
        }

        public int c() {
            return this.c;
        }

        public int d() {
            return this.d;
        }

        public a a(a p) {
            int nx = Math.max(this.a, p.a);
            int ny = Math.max(this.b, p.b);
            int rx = Math.min(this.a + this.c, p.a + p.c);
            int ry = Math.min(this.b + this.d, p.b + p.d);
            return new a(nx, ny, Math.max(0, rx - nx), Math.max(0, ry - ny));
        }
    }
}

package egg.client.render.msdf;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class Primitive2D {
    private Primitive2D() {}

    public static void drawCircle(float cx, float cy, float radius, int color) {
        if (radius <= 0.0f) return;
        int a = (color >> 24) & 0xFF;
        if (a <= 0) return;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int segments = Math.max(16, (int) (radius * 3.5f));
        int vertexCount = segments + 2;
        ByteBuffer buffer = MemoryUtil.memAlloc(vertexCount * 16);
        try {

            ImmediateRenderer.putVertexColor(buffer, cx, cy, 0.0f, r, g, b, a);
            for (int i = 0; i <= segments; i++) {
                float angle = (float) (i * 2.0 * Math.PI / segments);
                float vx = cx + (float) Math.cos(angle) * radius;
                float vy = cy + (float) Math.sin(angle) * radius;
                ImmediateRenderer.putVertexColor(buffer, vx, vy, 0.0f, r, g, b, a);
            }
            ImmediateRenderer.draw2D(VertexFormat.DrawMode.TRIANGLE_FAN, buffer, vertexCount);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    public static void drawRingArc(float cx, float cy, float innerRadius, float outerRadius, float startAngleDeg, float sweepAngleDeg, int color) {
        if (sweepAngleDeg <= 0.0f || outerRadius <= innerRadius) return;
        int a = (color >> 24) & 0xFF;
        if (a <= 0) return;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int segments = Math.max(8, (int) (sweepAngleDeg / 3.0f));
        int vertexCount = (segments + 1) * 2;
        ByteBuffer buffer = MemoryUtil.memAlloc(vertexCount * 16);
        try {
            for (int i = 0; i <= segments; i++) {
                float angle = (float) Math.toRadians(startAngleDeg + (sweepAngleDeg * (float) i / (float) segments));
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                ImmediateRenderer.putVertexColor(buffer, cx + cos * outerRadius, cy + sin * outerRadius, 0.0f, r, g, b, a);
                ImmediateRenderer.putVertexColor(buffer, cx + cos * innerRadius, cy + sin * innerRadius, 0.0f, r, g, b, a);
            }
            ImmediateRenderer.draw2D(VertexFormat.DrawMode.TRIANGLE_STRIP, buffer, vertexCount);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    public static void drawProgressRing(float cx, float cy, float innerRadius, float outerRadius, float progress, int trackColor, int activeColor) {
        float ringR = outerRadius;
        float trackThickness = outerRadius - innerRadius;
        float midR = innerRadius + trackThickness * 0.5f;
        float capR = trackThickness * 0.5f;

        drawRingArc(cx, cy, innerRadius, outerRadius, 0.0f, 360.0f, trackColor);

        float sweep = Math.clamp(progress, 0.0f, 1.0f) * 360.0f;
        if (sweep > 0.5f) {
            drawRingArc(cx, cy, innerRadius, outerRadius, -90.0f, sweep, activeColor);

            drawCircle(cx, cy - midR, capR, activeColor);

            float endRad = (float) Math.toRadians(-90.0f + sweep);
            float endX = cx + (float) Math.cos(endRad) * midR;
            float endY = cy + (float) Math.sin(endRad) * midR;
            drawCircle(endX, endY, capR, activeColor);
        }
    }

    public static void drawNeedle(float cx, float cy, float angleDeg, float length, float baseWidth, int color) {
        float rad = (float) Math.toRadians(angleDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        float tipX = cx + cos * length;
        float tipY = cy + sin * length;

        float perpX = -sin;
        float perpY = cos;
        float halfW = baseWidth * 0.5f;

        float p1X = cx + perpX * halfW;
        float p1Y = cy + perpY * halfW;
        float p2X = cx - perpX * halfW;
        float p2Y = cy - perpY * halfW;

        float headBack = 8.0f;
        float headBaseX = cx + cos * Math.max(0.0f, length - headBack);
        float headBaseY = cy + sin * Math.max(0.0f, length - headBack);

        float wingW = baseWidth * 1.8f;
        float w1X = headBaseX + perpX * wingW;
        float w1Y = headBaseY + perpY * wingW;
        float w2X = headBaseX - perpX * wingW;
        float w2Y = headBaseY - perpY * wingW;

        int a = (color >> 24) & 0xFF;
        if (a <= 0) return;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        ByteBuffer buffer = MemoryUtil.memAlloc(9 * 16);
        try {
            ImmediateRenderer.putVertexColor(buffer, p1X, p1Y, 0.0f, r, g, b, a);
            ImmediateRenderer.putVertexColor(buffer, p2X, p2Y, 0.0f, r, g, b, a);
            ImmediateRenderer.putVertexColor(buffer, headBaseX + perpX * halfW, headBaseY + perpY * halfW, 0.0f, r, g, b, a);

            ImmediateRenderer.putVertexColor(buffer, headBaseX + perpX * halfW, headBaseY + perpY * halfW, 0.0f, r, g, b, a);
            ImmediateRenderer.putVertexColor(buffer, p2X, p2Y, 0.0f, r, g, b, a);
            ImmediateRenderer.putVertexColor(buffer, headBaseX - perpX * halfW, headBaseY - perpY * halfW, 0.0f, r, g, b, a);

            ImmediateRenderer.putVertexColor(buffer, w1X, w1Y, 0.0f, r, g, b, a);
            ImmediateRenderer.putVertexColor(buffer, tipX, tipY, 0.0f, r, g, b, a);
            ImmediateRenderer.putVertexColor(buffer, w2X, w2Y, 0.0f, r, g, b, a);

            ImmediateRenderer.draw2D(VertexFormat.DrawMode.TRIANGLES, buffer, 9);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }
}

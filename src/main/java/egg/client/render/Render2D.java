package egg.client.render;

import egg.client.render.font.ClientFont;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.ScissorUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Vector4f;

public final class Render2D {
    private Render2D() {}

    public static Matrix3x2f getMatrix(DrawContext context) {
        if (context == null) return null;
        return new Matrix3x2f(context.getMatrices());
    }

    public static void enableScissor(DrawContext context, float x1, float y1, float x2, float y2) {
        float minX = Math.min(x1, x2);
        float minY = Math.min(y1, y2);
        float maxX = Math.max(x1, x2);
        float maxY = Math.max(y1, y2);
        if (context != null) {
            context.enableScissor((int) minX, (int) minY, (int) maxX, (int) maxY);
        }
        ScissorUtil.a(getMatrix(context), minX, minY, Math.max(0.0f, maxX - minX), Math.max(0.0f, maxY - minY));
    }

    public static void disableScissor(DrawContext context) {
        if (context != null) {
            try {
                context.disableScissor();
            } catch (Exception ignored) {}
        }
        ScissorUtil.a(getMatrix(context));
    }

    public static void drawRect(DrawContext context, float x, float y, float width, float height, int color) {
        int x1 = Math.round(x);
        int y1 = Math.round(y);
        int x2 = Math.round(x + width);
        int y2 = Math.round(y + height);
        context.fill(x1, y1, x2, y2, color);
    }

    public static void drawNineSlice(DrawContext context, Identifier texture, float x, float y, float width, float height, float radius, int texCorner, int texSize, int color) {
        if (width <= 0 || height <= 0) return;
        int alpha = (color >> 24) & 0xFF;
        if (alpha <= 0) return;

        float r = Math.min(radius, Math.min(width, height) / 2.0f);
        if (r <= 0.5f) {
            drawRect(context, x, y, width, height, color);
            return;
        }

        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.max(1, Math.round(width));
        int ih = Math.max(1, Math.round(height));
        int ir = Math.max(1, Math.round(r));

        int midW = iw - ir * 2;
        int midH = ih - ir * 2;

        int tc = texCorner;
        int ts = texSize;
        int midTex = ts - tc * 2;

        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix, iy, 0.0f, 0.0f, ir, ir, tc, tc, ts, ts, color);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + iw - ir, iy, (float) (ts - tc), 0.0f, ir, ir, tc, tc, ts, ts, color);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix, iy + ih - ir, 0.0f, (float) (ts - tc), ir, ir, tc, tc, ts, ts, color);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + iw - ir, iy + ih - ir, (float) (ts - tc), (float) (ts - tc), ir, ir, tc, tc, ts, ts, color);

        if (midW > 0) {
            if (midTex > 0) {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + ir, iy, (float) tc, 0.0f, midW, ir, midTex, tc, ts, ts, color);
                context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + ir, iy + ih - ir, (float) tc, (float) (ts - tc), midW, ir, midTex, tc, ts, ts, color);
            } else {
                drawRect(context, ix + ir, iy, midW, ir, color);
                drawRect(context, ix + ir, iy + ih - ir, midW, ir, color);
            }
        }

        if (midH > 0) {
            if (midTex > 0) {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix, iy + ir, 0.0f, (float) tc, ir, midH, tc, midTex, ts, ts, color);
                context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + iw - ir, iy + ir, (float) (ts - tc), (float) tc, ir, midH, tc, midTex, ts, ts, color);
            } else {
                drawRect(context, ix, iy + ir, ir, midH, color);
                drawRect(context, ix + iw - ir, iy + ir, ir, midH, color);
            }
        }

        if (midW > 0 && midH > 0) {
            drawRect(context, ix + ir, iy + ir, midW, midH, color);
        }
    }

    public static void drawNineSliceOutline(DrawContext context, Identifier texture, float x, float y, float width, float height, float radius, int texCorner, int texSize, int color) {
        if (width <= 0 || height <= 0) return;
        int alpha = (color >> 24) & 0xFF;
        if (alpha <= 0) return;

        float r = Math.min(radius, Math.min(width, height) / 2.0f);
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.max(1, Math.round(width));
        int ih = Math.max(1, Math.round(height));
        int ir = Math.max(1, Math.round(r));

        int midW = iw - ir * 2;
        int midH = ih - ir * 2;

        int tc = texCorner;
        int ts = texSize;
        int midTex = ts - tc * 2;

        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix, iy, 0.0f, 0.0f, ir, ir, tc, tc, ts, ts, color);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + iw - ir, iy, (float) (ts - tc), 0.0f, ir, ir, tc, tc, ts, ts, color);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix, iy + ih - ir, 0.0f, (float) (ts - tc), ir, ir, tc, tc, ts, ts, color);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + iw - ir, iy + ih - ir, (float) (ts - tc), (float) (ts - tc), ir, ir, tc, tc, ts, ts, color);

        if (midW > 0) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + ir, iy, (float) tc, 0.0f, midW, ir, midTex, tc, ts, ts, color);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + ir, iy + ih - ir, (float) tc, (float) (ts - tc), midW, ir, midTex, tc, ts, ts, color);
        }
        if (midH > 0) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix, iy + ir, 0.0f, (float) tc, ir, midH, tc, midTex, ts, ts, color);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + iw - ir, iy + ir, (float) (ts - tc), (float) tc, ir, midH, tc, midTex, ts, ts, color);
        }
    }

    public static void drawRoundedRect(DrawContext context, float x, float y, float width, float height, float radius, int color) {
        if (width <= 0 || height <= 0 || ((color >> 24) & 0xFF) <= 0) return;
        Draw2DProcessor.INSTANCE.drawRect(getMatrix(context), x, y, width, height, radius, color);
    }

    public static void drawRoundedRect(DrawContext context, float x, float y, float width, float height, Vector4f radius, int color) {
        if (width <= 0 || height <= 0 || ((color >> 24) & 0xFF) <= 0) return;
        Draw2DProcessor.INSTANCE.a(getMatrix(context), x, y, width, height, radius, color);
    }

    public static void drawRoundedOutline(DrawContext context, float x, float y, float width, float height, float radius, float thickness, int color) {
        if (width <= 0 || height <= 0 || ((color >> 24) & 0xFF) <= 0) return;
        Draw2DProcessor.INSTANCE.drawOutline(getMatrix(context), x, y, width, height, radius, thickness, color);
    }

    public static void drawShadow(DrawContext context, float x, float y, float width, float height, float radius, float spread, int color) {
        if (spread <= 0.0f || width <= 0 || height <= 0 || ((color >> 24) & 0xFF) <= 0) return;
        Draw2DProcessor.INSTANCE.drawGlow(getMatrix(context), x, y, width, height, radius, color, spread);
    }

    public static void drawCheckmark(DrawContext context, float x, float y, float size, int color) {
        if (size <= 0.0f) return;
        int alpha = (color >> 24) & 0xFF;
        if (alpha <= 0) return;
        int isize = Math.max(1, Math.round(size));
        Identifier tex = DynamicGuiTextures.getCheckmark(isize);
        int scale = DynamicGuiTextures.getScale();
        int texSize = isize * scale;
        IconRenderer.drawTextureScaled(context, tex, x, y, size, size, texSize, texSize, color);
    }

    public static void drawCircle(DrawContext context, float cx, float cy, float radius, int color) {
        if (radius <= 0.0f || ((color >> 24) & 0xFF) <= 0) return;
        float d = radius * 2.0f;
        Draw2DProcessor.INSTANCE.drawRect(getMatrix(context), cx - radius, cy - radius, d, d, radius, color);
    }

    public static void drawCircleShadow(DrawContext context, float cx, float cy, float radius, float spread, int color) {
        if (spread <= 0.0f || radius <= 0.0f || ((color >> 24) & 0xFF) <= 0) return;
        float d = radius * 2.0f;
        Draw2DProcessor.INSTANCE.drawGlow(getMatrix(context), cx - radius, cy - radius, d, d, radius, color, spread);
    }

    public static void drawWheelSector(DrawContext context, float cx, float cy, float radius, int sectorIndex, int color) {
        if (radius <= 0.0f || sectorIndex < 0 || sectorIndex > 2) return;
        int alpha = (color >> 24) & 0xFF;
        if (alpha <= 0) return;
        int r = Math.max(1, Math.round(radius));
        Identifier tex = DynamicGuiTextures.getWheelSector(r, sectorIndex);
        int scale = DynamicGuiTextures.getScale();
        int isize = Math.max(1, Math.round(radius * 2.0f));
        int ix = Math.round(cx - radius);
        int iy = Math.round(cy - radius);
        int texSize = r * 2 * scale;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, tex, ix, iy, 0.0f, 0.0f, isize, isize, texSize, texSize, texSize, texSize, color);
    }

    public static void drawWheelBase(DrawContext context, float cx, float cy, float radius, int themeColor) {
        if (radius <= 0.0f) return;
        int r = Math.max(1, Math.round(radius));
        Identifier tex = DynamicGuiTextures.getWheelBase(r, themeColor);
        int scale = DynamicGuiTextures.getScale();
        int isize = Math.max(1, Math.round(radius * 2.0f));
        int ix = Math.round(cx - radius);
        int iy = Math.round(cy - radius);
        int texSize = r * 2 * scale;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, tex, ix, iy, 0.0f, 0.0f, isize, isize, texSize, texSize, texSize, texSize, 0xFFFFFFFF);
    }

    public static void drawWheelLines(DrawContext context, float cx, float cy, float radius) {
        if (radius <= 0.0f) return;
        int r = Math.max(1, Math.round(radius));
        Identifier tex = DynamicGuiTextures.getWheelLines(r);
        int scale = DynamicGuiTextures.getScale();
        int isize = Math.max(1, Math.round(radius * 2.0f));
        int ix = Math.round(cx - radius);
        int iy = Math.round(cy - radius);
        int texSize = r * 2 * scale;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, tex, ix, iy, 0.0f, 0.0f, isize, isize, texSize, texSize, texSize, texSize, 0xFFFFFFFF);
    }

    public static void drawCircleOutline(DrawContext context, float cx, float cy, float radius, float thickness, int color) {
        if (radius <= 0.0f || ((color >> 24) & 0xFF) <= 0) return;
        float d = radius * 2.0f;
        Draw2DProcessor.INSTANCE.drawOutline(getMatrix(context), cx - radius, cy - radius, d, d, radius, thickness, color);
    }

    public static void drawCapsule(DrawContext context, float x, float y, float width, float height, int color) {
        if (width <= 0 || height <= 0 || ((color >> 24) & 0xFF) <= 0) return;
        float r = Math.min(width, height) / 2.0f;
        Draw2DProcessor.INSTANCE.drawRect(getMatrix(context), x, y, width, height, r, color);
    }

    public static void drawCapsuleOutline(DrawContext context, float x, float y, float width, float height, int color) {
        if (width <= 0 || height <= 0 || ((color >> 24) & 0xFF) <= 0) return;
        float r = Math.min(width, height) / 2.0f;
        Draw2DProcessor.INSTANCE.drawOutline(getMatrix(context), x, y, width, height, r, 1.0f, color);
    }

    public static void drawGradientRect(DrawContext context, float x, float y, float width, float height, float radius, int colTL, int colTR, int colBL, int colBR) {
        if (width <= 0 || height <= 0) return;
        Draw2DProcessor.INSTANCE.drawGradient(getMatrix(context), x, y, width, height, radius, colTL, colTR, colBL, colBR);
    }

    public static void drawRoundedGradientRect(DrawContext context, float x, float y, float width, float height, float radius, int colTop, int colBot) {
        if (width <= 0 || height <= 0) return;
        Draw2DProcessor.INSTANCE.drawGradient(getMatrix(context), x, y, width, height, radius, colTop, colTop, colBot, colBot);
    }

    public static void drawTexture(DrawContext context, Identifier texture, float x, float y, float width, float height, int color) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.max(1, Math.round(width));
        int ih = Math.max(1, Math.round(height));
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix, iy, 0.0f, 0.0f, iw, ih, iw, ih, iw, ih, color);
    }

    public static void drawDynamicTexture(DrawContext context, Identifier texture, float x, float y, float width, float height, int color) {
        if (width <= 0 || height <= 0 || texture == null) return;
        int alpha = (color >> 24) & 0xFF;
        if (alpha <= 0) return;
        int iw = Math.max(1, Math.round(width));
        int ih = Math.max(1, Math.round(height));
        int ix = Math.round(x);
        int iy = Math.round(y);
        int scale = DynamicGuiTextures.getScale();
        int tw = iw * scale;
        int th = ih * scale;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix, iy, 0.0f, 0.0f, iw, ih, tw, th, tw, th, color);
    }

    public static void drawLine(DrawContext context, float x1, float y1, float x2, float y2, int color, float thickness) {
        drawLineRotated(context, x1, y1, x2, y2, color, thickness);
    }

    public static void drawLineRotated(DrawContext context, float x1, float y1, float x2, float y2, int color, float thickness) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        if (Math.abs(dy) < 0.01f) {
            float minX = Math.min(x1, x2);
            float maxX = Math.max(x1, x2);
            float half = Math.max(0.5f, thickness * 0.5f);
            drawRect(context, minX, y1 - half, maxX - minX, thickness, color);
            return;
        }
        if (Math.abs(dx) < 0.01f) {
            float minY = Math.min(y1, y2);
            float maxY = Math.max(y1, y2);
            float half = Math.max(0.5f, thickness * 0.5f);
            drawRect(context, x1 - half, minY, thickness, maxY - minY, color);
            return;
        }
        float dist = (float) Math.hypot(dx, dy);
        if (dist <= 0.5f) return;

        float angle = (float) Math.atan2(dy, dx);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x1, y1);
        context.getMatrices().rotate(angle);
        int d = Math.max(1, Math.round(dist));
        if (thickness <= 1.2f) {
            context.fill(0, 0, d, 1, color);
        } else {
            int half = Math.max(1, Math.round(thickness * 0.5f));
            context.fill(0, -half, d, half, color);
        }
        context.getMatrices().popMatrix();
    }

    public static void drawText(DrawContext context, String text, float x, float y, int color) {
        ClientFont.draw(context, text, x, y, ClientFont.SF_REGULAR, color);
    }

    public static void drawCenteredText(DrawContext context, String text, float cx, float cy, int color) {
        ClientFont.drawCentered(context, text, cx, cy, ClientFont.SF_REGULAR, color);
    }

    public static void drawDiagonalSheen(DrawContext context, float x, float y, float width, float height, float radius, long cycleOffset) {
        drawDiagonalSheen(context, x, y, width, height, radius, cycleOffset, 1.0f);
    }

    public static void drawDiagonalSheen(DrawContext context, float x, float y, float width, float height, float radius, long cycleOffset, float alphaFactor) {
        if (width <= 0 || height <= 0 || context == null || alphaFactor <= 0.01f) return;
        long cycle = 5500L;
        long duration = 1400L;
        long t = (System.currentTimeMillis() + cycleOffset) % cycle;
        if (t > duration) return;

        float progress = (float) t / (float) duration;
        float p = (float) (0.5 - 0.5 * Math.cos(progress * Math.PI));

        float bandW = 34.0f;
        float halfW = bandW * 0.5f;

        float slantX = height * 0.65f;
        float totalTravel = width + slantX + bandW * 2.0f;
        float startX = x - slantX - bandW;
        float curXTop = startX + p * totalTravel;

        float r = Math.min(Math.max(0.0f, radius), Math.min(width, height) / 2.0f);
        float topCapCenter = y + r;
        float botCapCenter = y + height - r;

        int startY = (int) Math.floor(y);
        int endY = (int) Math.ceil(y + height);

        for (int py = startY; py < endY; py++) {
            float lineMidY = py + 0.5f;
            if (lineMidY < y || lineMidY > y + height) continue;

            float xMin, xMax;
            if (r <= 0.5f) {
                xMin = x;
                xMax = x + width;
            } else if (lineMidY < topCapCenter) {
                float dy = topCapCenter - lineMidY;
                if (dy >= r) continue;
                float dx = (float) Math.sqrt(r * r - dy * dy);
                xMin = (x + r) - dx;
                xMax = (x + width - r) + dx;
            } else if (lineMidY > botCapCenter) {
                float dy = lineMidY - botCapCenter;
                if (dy >= r) continue;
                float dx = (float) Math.sqrt(r * r - dy * dy);
                xMin = (x + r) - dx;
                xMax = (x + width - r) + dx;
            } else {
                xMin = x;
                xMax = x + width;
            }

            float v = (lineMidY - y) / height;
            float xCenter = curXTop + v * slantX;

            float s1 = xCenter - halfW;
            float s2 = xCenter + halfW;

            float segStart = Math.max(xMin, s1);
            float segEnd = Math.min(xMax, s2);
            if (segStart >= segEnd) continue;

            int minIx = (int) Math.ceil(segStart - 0.5f);
            int maxIx = (int) Math.floor(segEnd - 0.5f);
            if (minIx > maxIx) continue;

            float stepX = 2.0f;
            for (float px = minIx; px <= maxIx; px += stepX) {
                float pxEnd = Math.min(px + stepX, (float) maxIx + 1.0f);
                float midX = (px + pxEnd) * 0.5f;
                float dist = Math.abs(midX - xCenter);
                float u = dist / halfW;
                if (u >= 1.0f) continue;

                float intensity = (1.0f - u) * (1.0f - u);
                int alpha = (int) (intensity * 48.0f * alphaFactor);
                if (alpha <= 0) continue;

                int color = (alpha << 24) | 0x00FFFFFF;
                int ix1 = Math.round(px);
                int ix2 = Math.max(ix1 + 1, Math.round(pxEnd));
                context.fill(ix1, py, ix2, py + 1, color);
            }
        }
    }
}

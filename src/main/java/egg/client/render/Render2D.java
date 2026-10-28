package egg.client.render;

import egg.client.render.font.ClientFont;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Vector4f;

public final class Render2D {
    private Render2D() {}

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

        // 1. Top-left corner
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix, iy, 0.0f, 0.0f, ir, ir, tc, tc, ts, ts, color);
        // 2. Top-right corner
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + iw - ir, iy, (float) (ts - tc), 0.0f, ir, ir, tc, tc, ts, ts, color);
        // 3. Bottom-left corner
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix, iy + ih - ir, 0.0f, (float) (ts - tc), ir, ir, tc, tc, ts, ts, color);
        // 4. Bottom-right corner
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + iw - ir, iy + ih - ir, (float) (ts - tc), (float) (ts - tc), ir, ir, tc, tc, ts, ts, color);

        // 5. Top edge
        if (midW > 0) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + ir, iy, (float) tc, 0.0f, midW, ir, midTex, tc, ts, ts, color);
        }
        // 6. Bottom edge
        if (midW > 0) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + ir, iy + ih - ir, (float) tc, (float) (ts - tc), midW, ir, midTex, tc, ts, ts, color);
        }
        // 7. Left edge
        if (midH > 0) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix, iy + ir, 0.0f, (float) tc, ir, midH, tc, midTex, ts, ts, color);
        }
        // 8. Right edge
        if (midH > 0) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + iw - ir, iy + ir, (float) (ts - tc), (float) tc, ir, midH, tc, midTex, ts, ts, color);
        }
        // 9. Center
        if (midW > 0 && midH > 0) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + ir, iy + ir, (float) tc, (float) tc, midW, midH, midTex, midTex, ts, ts, color);
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

        // Corners
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix, iy, 0.0f, 0.0f, ir, ir, tc, tc, ts, ts, color);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + iw - ir, iy, (float) (ts - tc), 0.0f, ir, ir, tc, tc, ts, ts, color);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix, iy + ih - ir, 0.0f, (float) (ts - tc), ir, ir, tc, tc, ts, ts, color);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix + iw - ir, iy + ih - ir, (float) (ts - tc), (float) (ts - tc), ir, ir, tc, tc, ts, ts, color);

        // Edges
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
        int r = Math.max(1, Math.round(radius));
        Identifier tex = DynamicGuiTextures.getRoundedRect(r);
        int texSize = r * 2 * 2; // scale 2 default
        drawNineSlice(context, tex, x, y, width, height, radius, r * 2, texSize, color);
    }

    public static void drawRoundedRect(DrawContext context, float x, float y, float width, float height, Vector4f radius, int color) {
        drawRoundedRect(context, x, y, width, height, radius.x, color);
    }

    public static void drawRoundedOutline(DrawContext context, float x, float y, float width, float height, float radius, float thickness, int color) {
        int r = Math.max(1, Math.round(radius));
        Identifier tex = DynamicGuiTextures.getRoundedOutline(r);
        int texSize = r * 2 * 2;
        drawNineSliceOutline(context, tex, x, y, width, height, radius, r * 2, texSize, color);
    }

    public static void drawShadow(DrawContext context, float x, float y, float width, float height, float radius, float spread, int color) {
        if (spread <= 0.0f) return;
        int alpha = (color >> 24) & 0xFF;
        if (alpha <= 0) return;
        float sx = x - spread;
        float sy = y - spread;
        float sw = width + spread * 2.0f;
        float sh = height + spread * 2.0f;
        float sr = radius + spread;

        int r = Math.max(1, Math.round(radius));
        int sp = Math.max(1, Math.round(spread));
        Identifier tex = DynamicGuiTextures.getRoundedShadow(r, sp);
        int corner = (r + sp) * 2;
        int texSize = corner * 2;
        drawNineSliceOutline(context, tex, sx, sy, sw, sh, sr, corner, texSize, color);
    }

    public static void drawCircle(DrawContext context, float cx, float cy, float radius, int color) {
        if (radius <= 0.0f) return;
        int alpha = (color >> 24) & 0xFF;
        if (alpha <= 0) return;
        int r = Math.max(1, Math.round(radius));
        Identifier tex = DynamicGuiTextures.getCircle(r);
        int isize = Math.max(1, Math.round(radius * 2.0f));
        int ix = Math.round(cx - radius);
        int iy = Math.round(cy - radius);
        int texSize = r * 2 * 2;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, tex, ix, iy, 0.0f, 0.0f, isize, isize, texSize, texSize, texSize, texSize, color);
    }

    public static void drawWheelSector(DrawContext context, float cx, float cy, float radius, int sectorIndex, int color) {
        if (radius <= 0.0f || sectorIndex < 0 || sectorIndex > 2) return;
        int alpha = (color >> 24) & 0xFF;
        if (alpha <= 0) return;
        int r = Math.max(1, Math.round(radius));
        Identifier tex = DynamicGuiTextures.getWheelSector(r, sectorIndex);
        int isize = Math.max(1, Math.round(radius * 2.0f));
        int ix = Math.round(cx - radius);
        int iy = Math.round(cy - radius);
        int texSize = r * 2 * 2;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, tex, ix, iy, 0.0f, 0.0f, isize, isize, texSize, texSize, texSize, texSize, color);
    }

    public static void drawWheelBase(DrawContext context, float cx, float cy, float radius, int themeColor) {
        if (radius <= 0.0f) return;
        int r = Math.max(1, Math.round(radius));
        Identifier tex = DynamicGuiTextures.getWheelBase(r, themeColor);
        int isize = Math.max(1, Math.round(radius * 2.0f));
        int ix = Math.round(cx - radius);
        int iy = Math.round(cy - radius);
        int texSize = r * 2 * 2;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, tex, ix, iy, 0.0f, 0.0f, isize, isize, texSize, texSize, texSize, texSize, 0xFFFFFFFF);
    }

    public static void drawWheelLines(DrawContext context, float cx, float cy, float radius) {
        if (radius <= 0.0f) return;
        int r = Math.max(1, Math.round(radius));
        Identifier tex = DynamicGuiTextures.getWheelLines(r);
        int isize = Math.max(1, Math.round(radius * 2.0f));
        int ix = Math.round(cx - radius);
        int iy = Math.round(cy - radius);
        int texSize = r * 2 * 2;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, tex, ix, iy, 0.0f, 0.0f, isize, isize, texSize, texSize, texSize, texSize, 0xFFFFFFFF);
    }

    public static void drawCircleOutline(DrawContext context, float cx, float cy, float radius, float thickness, int color) {
        drawRoundedOutline(context, cx - radius, cy - radius, radius * 2.0f, radius * 2.0f, radius, thickness, color);
    }

    public static void drawGradientRect(DrawContext context, float x, float y, float width, float height, float radius, int colTL, int colTR, int colBL, int colBR) {
        int x1 = Math.round(x);
        int y1 = Math.round(y);
        int x2 = Math.round(x + width);
        int y2 = Math.round(y + height);
        int startColor = (colTL != 0) ? colTL : colTR;
        int endColor = (colBR != 0) ? colBR : colBL;
        context.fillGradient(x1, y1, x2, y2, startColor, endColor);
    }


    public static void drawRoundedGradientRect(DrawContext context, float x, float y, float width, float height, float radius, int colTop, int colBot) {
        drawRoundedRect(context, x, y, width, height, radius, colTop);
    }

    public static void drawTexture(DrawContext context, Identifier texture, float x, float y, float width, float height, int color) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.max(1, Math.round(width));
        int ih = Math.max(1, Math.round(height));
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix, iy, 0.0f, 0.0f, iw, ih, iw, ih, iw, ih, color);
    }

    public static void drawLine(DrawContext context, float x1, float y1, float x2, float y2, int color, float thickness) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dist = (float) Math.hypot(dx, dy);
        if (dist <= 0.001f) return;
        float halfThick = thickness * 0.5f;
        int steps = Math.max(1, Math.round(dist * 1.5f));
        float stepX = dx / steps;
        float stepY = dy / steps;
        for (int i = 0; i <= steps; i++) {
            float cx = x1 + stepX * i;
            float cy = y1 + stepY * i;
            drawRect(context, cx - halfThick, cy - halfThick, thickness, thickness, color);
        }
    }

    public static void drawText(DrawContext context, String text, float x, float y, int color) {
        ClientFont.draw(context, text, x, y, ClientFont.SF_REGULAR, color);
    }

    public static void drawCenteredText(DrawContext context, String text, float cx, float cy, int color) {
        ClientFont.drawCentered(context, text, cx, cy, ClientFont.SF_REGULAR, color);
    }

    public static void drawDiagonalSheen(DrawContext context, float x, float y, float width, float height, float radius, long cycleOffset) {
        if (width <= 0 || height <= 0 || context == null) return;
        long cycle = 5500L;
        long duration = 1400L;
        long t = (System.currentTimeMillis() + cycleOffset) % cycle;
        if (t > duration) return;

        float progress = (float) t / (float) duration;
        float p = (float) (0.5 - 0.5 * Math.cos(progress * Math.PI));

        float bandW = 34.0f;
        float halfW = bandW / 2.0f;
        // Slant along the other diagonal (/): top is to the left, bottom is to the right
        // As it advances, it sweeps from top-left downwards and across (сверху вниз)
        float slantX = height * 0.65f;
        float totalTravel = width + slantX + bandW * 2.0f;
        float startX = x - slantX - bandW;
        float curXTop = startX + p * totalTravel;

        float r = Math.min(radius, Math.min(width, height) / 2.0f);
        float topCapCenter = y + r;
        float botCapCenter = y + height - r;

        float stepY = height <= 45.0f ? 1.0f : 2.0f;
        float stepX = 2.5f;

        for (float py = y; py < y + height; py += stepY) {
            float xMin, xMax;
            if (r <= 0.5f) {
                xMin = x;
                xMax = x + width;
            } else if (py + 0.5f < topCapCenter) {
                float dy = topCapCenter - (py + 0.5f);
                float dx = (float) Math.sqrt(Math.max(0.0f, r * r - dy * dy));
                xMin = (x + r) - dx;
                xMax = (x + width - r) + dx;
            } else if (py + 0.5f > botCapCenter) {
                float dy = (py + 0.5f) - botCapCenter;
                float dx = (float) Math.sqrt(Math.max(0.0f, r * r - dy * dy));
                xMin = (x + r) - dx;
                xMax = (x + width - r) + dx;
            } else {
                xMin = x;
                xMax = x + width;
            }

            float v = (py - y) / height;
            float xCenter = curXTop + v * slantX;

            float s1 = xCenter - halfW;
            float s2 = xCenter + halfW;

            float segStart = Math.max(xMin, s1);
            float segEnd = Math.min(xMax, s2);
            if (segStart >= segEnd) continue;

            for (float px = segStart; px < segEnd; px += stepX) {
                float pxEnd = Math.min(px + stepX, segEnd);
                float midX = (px + pxEnd) / 2.0f;
                float dist = Math.abs(midX - xCenter);
                float u = dist / halfW;
                if (u >= 1.0f) continue;

                float intensity = (1.0f - u) * (1.0f - u);
                int alpha = (int) (intensity * 48.0f);
                if (alpha <= 0) continue;

                int color = (alpha << 24) | 0x00FFFFFF;
                int ix1 = Math.round(px);
                int iy1 = Math.round(py);
                int ix2 = Math.max(ix1 + 1, Math.round(pxEnd));
                int iy2 = Math.max(iy1 + 1, Math.round(py + stepY));
                context.fill(ix1, iy1, ix2, iy2, color);
            }
        }
    }
}

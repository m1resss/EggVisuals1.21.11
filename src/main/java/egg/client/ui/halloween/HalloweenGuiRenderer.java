package egg.client.ui.halloween;

import egg.client.module.misc.ClientSettings;
import egg.client.render.Render2D;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

public final class HalloweenGuiRenderer {
    private HalloweenGuiRenderer() {}

    private static float spiderRetractAnim = 0.0f;
    private static long lastFrameTime = System.currentTimeMillis();

    public static void renderEmbers(DrawContext context, float gx, float gy, float w, float h, float alpha) {
        if (!ClientSettings.isHalloweenEmbers() || alpha <= 0.01f) return;

        long now = System.currentTimeMillis();
        float timeSec = (now % 10000000L) / 1000.0f;

        for (int i = 0; i < 18; i++) {
            float seedX = (float) ((i * 37.19 + 13.7) % 1.0);
            float speed = 10.0f + (i % 5) * 3.5f;
            float lifeProgress = ((timeSec * speed + i * 43.1f) % h) / h;

            float px = gx + seedX * (w - 20.0f) + 10.0f + (float) Math.sin(timeSec * 1.8f + i * 1.3f) * 8.0f;
            float py = gy + h - lifeProgress * h;

            float emberFade = (float) Math.sin(lifeProgress * Math.PI);
            float pulse = 0.75f + 0.25f * (float) Math.sin(timeSec * 4.0f + i * 2.0f);
            int emberAlpha = (int) (140 * emberFade * pulse * alpha);
            if (emberAlpha <= 5) continue;

            int color;
            if (i % 3 == 0) {
                color = ColorUtil.rgba(186, 104, 200, emberAlpha);
            } else if (i % 3 == 1) {
                color = ColorUtil.rgba(255, 140, 20, emberAlpha);
            } else {
                color = ColorUtil.rgba(255, 215, 64, emberAlpha);
            }

            float radius = (i % 2 == 0) ? 1.6f : 1.2f;
            Render2D.drawCircle(context, px, py, radius, color);
        }
    }

    public static void renderWebs(DrawContext context, float gx, float gy, float w, float h, float alpha) {
        if (!ClientSettings.isHalloweenWebs() || alpha <= 0.01f) return;

        int silkCol = ColorUtil.rgba(235, 230, 255, (int) (115 * alpha));
        int arcCol = ColorUtil.rgba(215, 210, 245, (int) (85 * alpha));
        int dewCol = ColorUtil.rgba(255, 255, 255, (int) (180 * alpha));

        float cornerRadius = Theme.WINDOW_RADIUS;

        renderRoundedCornerWeb(context,
                gx + cornerRadius, gy + cornerRadius, cornerRadius,
                (float) Math.PI, (float) (3.0 * Math.PI / 2.0),
                0.0f, 1.0f,
                1.0f, 0.0f,
                40.0f, 5, 4, 0.78f, silkCol, arcCol, dewCol);

        renderRoundedCornerWeb(context,
                gx + w - cornerRadius, gy + cornerRadius, cornerRadius,
                (float) (3.0 * Math.PI / 2.0), (float) (2.0 * Math.PI),
                -1.0f, 0.0f,
                0.0f, 1.0f,
                48.0f, 5, 4, 0.78f, silkCol, arcCol, dewCol);

        renderRoundedCornerWeb(context,
                gx + w - cornerRadius, gy + h - cornerRadius, cornerRadius,
                0.0f, (float) (Math.PI / 2.0),
                0.0f, -1.0f,
                -1.0f, 0.0f,
                40.0f, 5, 3, 0.76f, silkCol, arcCol, dewCol);
    }

    private static void renderRoundedCornerWeb(DrawContext context,
                                               float cornerCenterX, float cornerCenterY, float cornerRadius,
                                               float startArcAngle, float endArcAngle,
                                               float rayDirX1, float rayDirY1, float rayDirX2, float rayDirY2,
                                               float webLength, int rayCount, int arcCount, float sagFactor,
                                               int silkCol, int arcCol, int dewCol) {
        float innerBorderR = cornerRadius - 1.5f;

        float[] anchorX = new float[rayCount];
        float[] anchorY = new float[rayCount];
        float[] dirX = new float[rayCount];
        float[] dirY = new float[rayCount];

        for (int i = 0; i < rayCount; i++) {
            float u = (float) i / (rayCount - 1);
            float phi = startArcAngle + u * (endArcAngle - startArcAngle);

            anchorX[i] = cornerCenterX + (float) Math.cos(phi) * innerBorderR;
            anchorY[i] = cornerCenterY + (float) Math.sin(phi) * innerBorderR;

            float rawDx = rayDirX1 + u * (rayDirX2 - rayDirX1);
            float rawDy = rayDirY1 + u * (rayDirY2 - rayDirY1);
            float len = (float) Math.hypot(rawDx, rawDy);
            dirX[i] = rawDx / len;
            dirY[i] = rawDy / len;
        }

        for (int i = 0; i < rayCount - 1; i++) {
            Render2D.drawLine(context, anchorX[i], anchorY[i], anchorX[i + 1], anchorY[i + 1], silkCol, 1.0f);
        }

        for (int i = 0; i < rayCount; i++) {
            float rx = anchorX[i] + dirX[i] * webLength;
            float ry = anchorY[i] + dirY[i] * webLength;
            Render2D.drawLine(context, anchorX[i], anchorY[i], rx, ry, silkCol, 1.0f);
        }

        for (int ring = 1; ring <= arcCount; ring++) {
            float t = (float) ring / arcCount;
            float dist = webLength * t;

            for (int i = 0; i < rayCount - 1; i++) {
                float p1x = anchorX[i] + dirX[i] * dist;
                float p1y = anchorY[i] + dirY[i] * dist;

                float p2x = anchorX[i + 1] + dirX[i + 1] * dist;
                float p2y = anchorY[i + 1] + dirY[i + 1] * dist;

                float midAnchorX = (anchorX[i] + anchorX[i + 1]) * 0.5f;
                float midAnchorY = (anchorY[i] + anchorY[i + 1]) * 0.5f;

                float rawMidX = (p1x + p2x) * 0.5f;
                float rawMidY = (p1y + p2y) * 0.5f;

                float mx = rawMidX * sagFactor + midAnchorX * (1.0f - sagFactor);
                float my = rawMidY * sagFactor + midAnchorY * (1.0f - sagFactor);

                Render2D.drawLine(context, p1x, p1y, mx, my, arcCol, 1.0f);
                Render2D.drawLine(context, mx, my, p2x, p2y, arcCol, 1.0f);

                if ((ring + i) % 2 == 0) {
                    Render2D.drawCircle(context, p1x, p1y, 1.15f, dewCol);
                }
            }
        }
    }

    public static void renderSpider(DrawContext context, float gx, float gy, float mouseX, float mouseY, float alpha) {
        if (!ClientSettings.isHalloweenWebs() || alpha <= 0.01f) return;

        long now = System.currentTimeMillis();
        float delta = Math.min(0.1f, (now - lastFrameTime) / 1000.0f);
        lastFrameTime = now;

        float timeSec = (now % 10000000L) / 1000.0f;

        float threadAnchorX = gx + 28.0f;
        float threadAnchorY = gy + 4.0f;
        float baseLen = 22.0f;

        float defaultSpiderX = threadAnchorX;
        float defaultSpiderY = threadAnchorY + baseLen;
        float distToMouse = (float) Math.hypot(mouseX - defaultSpiderX, mouseY - defaultSpiderY);

        float targetRetract = (distToMouse < 40.0f) ? MathHelper.clamp((40.0f - distToMouse) / 40.0f, 0.0f, 1.0f) * 14.0f : 0.0f;
        spiderRetractAnim += (targetRetract - spiderRetractAnim) * Math.min(1.0f, delta * 8.0f);

        float currentLen = Math.max(10.0f, baseLen - spiderRetractAnim);

        float swingAngle = (float) Math.sin(timeSec * 2.2f) * 0.10f;
        float spiderX = threadAnchorX + (float) Math.sin(swingAngle) * currentLen;
        float spiderY = threadAnchorY + (float) Math.cos(swingAngle) * currentLen;

        int threadCol = ColorUtil.rgba(240, 235, 255, (int) (125 * alpha));
        Render2D.drawLine(context, threadAnchorX, threadAnchorY, spiderX, spiderY, threadCol, 1.0f);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(spiderX, spiderY);
        context.getMatrices().rotate(swingAngle);

        int spiderBodyCol = ColorUtil.rgba(28, 24, 34, (int) (240 * alpha));
        int spiderHighlight = ColorUtil.rgba(52, 44, 64, (int) (240 * alpha));
        int legCol = ColorUtil.rgba(36, 30, 44, (int) (230 * alpha));
        int eyeCol = ColorUtil.rgba(255, 64, 32, (int) (245 * alpha));

        float legTwitch = (float) Math.sin(timeSec * 5.0f) * 1.0f;
        for (int leg = 0; leg < 4; leg++) {
            float yOffset = -1.6f + leg * 1.2f;

            float lkX = -4.2f - leg * 0.4f;
            float lkY = yOffset - 2.8f + legTwitch * (leg % 2 == 0 ? 1 : -1);
            float ltX = -6.8f - leg * 0.5f;
            float ltY = yOffset + 2.0f;
            Render2D.drawLine(context, 0.0f, yOffset, lkX, lkY, legCol, 1.0f);
            Render2D.drawLine(context, lkX, lkY, ltX, ltY, legCol, 1.0f);

            float rkX = 4.2f + leg * 0.4f;
            float rkY = yOffset - 2.8f - legTwitch * (leg % 2 == 0 ? 1 : -1);
            float rtX = 6.8f + leg * 0.5f;
            float rtY = yOffset + 2.0f;
            Render2D.drawLine(context, 0.0f, yOffset, rkX, rkY, legCol, 1.0f);
            Render2D.drawLine(context, rkX, rkY, rtX, rtY, legCol, 1.0f);
        }

        Render2D.drawRoundedRect(context, -2.6f, -0.8f, 5.2f, 6.4f, 2.6f, spiderBodyCol);
        Render2D.drawRoundedRect(context, -1.4f, 0.4f, 2.8f, 4.0f, 1.4f, spiderHighlight);

        Render2D.drawCircle(context, 0.0f, -2.4f, 2.1f, spiderBodyCol);

        Render2D.drawCircle(context, -0.8f, -2.5f, 0.75f, eyeCol);
        Render2D.drawCircle(context, 0.8f, -2.5f, 0.75f, eyeCol);

        context.getMatrices().popMatrix();
    }

    public static void renderPumpkins(DrawContext context, float gx, float gy, float w, float h, float mouseX, float mouseY, float alpha) {
        if (!ClientSettings.isHalloweenPumpkins() || alpha <= 0.01f) return;

        long now = System.currentTimeMillis();
        float timeSec = (now % 10000000L) / 1000.0f;

        float p1X = gx + w - 46.0f;
        float p1Y = gy - 13.0f;
        renderJackOLantern(context, p1X, p1Y, 34.0f, 28.0f, -4.0f, mouseX, mouseY, alpha, timeSec, 0.0f);

        float p2X = gx - 8.0f;
        float p2Y = gy + h - 25.0f;
        renderJackOLantern(context, p2X, p2Y, 28.0f, 24.0f, 8.0f, mouseX, mouseY, alpha, timeSec, 2.5f);
    }

    public static void renderMiniJackOLantern(DrawContext context, float cx, float cy, float width, float height, float tiltDeg, float alpha) {
        if (alpha <= 0.01f) return;
        long now = System.currentTimeMillis();
        float timeSec = (now % 10000000L) / 1000.0f;
        renderJackOLantern(context, cx, cy, width, height, tiltDeg, -9999.0f, -9999.0f, alpha, timeSec, 1.2f);
    }

    private static void renderJackOLantern(DrawContext context, float cx, float cy, float width, float height,
                                          float tiltDeg, float mouseX, float mouseY, float alpha, float timeSec, float seed) {
        boolean hovered = (mouseX > -9000.0f) && Math.abs(mouseX - cx) < width * 0.7f && Math.abs(mouseY - cy) < height * 0.7f;

        float flicker = 0.82f + 0.18f * (float) Math.sin(timeSec * 7.1f + seed) + 0.09f * (float) Math.sin(timeSec * 13.7f + seed * 1.5f);
        if (hovered) flicker = Math.min(1.3f, flicker * 1.25f);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(cx, cy);
        float tiltRad = (float) Math.toRadians(tiltDeg);
        if (tiltRad != 0.0f) {
            context.getMatrices().rotate(tiltRad);
        }

        if (hovered) {
            float bounce = (float) Math.sin(timeSec * 8.0f) * 0.03f;
            context.getMatrices().scale(1.04f + bounce, 1.04f - bounce);
        }

        float shadowSpread = Math.max(2.5f, width * 0.28f);
        int bloomCol = ColorUtil.rgba(255, 120, 0, (int) (48 * flicker * alpha));
        Render2D.drawShadow(context, -width * 0.5f, -height * 0.4f, width, height, height * 0.45f, shadowSpread, bloomCol);

        float stemThick = Math.max(1.1f, width * 0.08f);
        int stemBaseCol = ColorUtil.rgba(46, 92, 32, (int) (240 * alpha));
        int stemHighlight = ColorUtil.rgba(76, 140, 54, (int) (240 * alpha));
        Render2D.drawLine(context, 0.0f, -height * 0.45f, -width * 0.075f, -height * 0.72f, stemBaseCol, stemThick);
        Render2D.drawLine(context, width * 0.015f, -height * 0.45f, -width * 0.06f, -height * 0.70f, stemHighlight, stemThick * 0.45f);

        if (width >= 20.0f) {
            int vineCol = ColorUtil.rgba(102, 187, 106, (int) (220 * alpha));
            Render2D.drawLine(context, 0.0f, -height * 0.48f, 3.5f, -height * 0.58f, vineCol, 1.1f);
            Render2D.drawLine(context, 3.5f, -height * 0.58f, 5.0f, -height * 0.50f, vineCol, 1.1f);
        }

        int darkCrease = ColorUtil.rgba(165, 42, 0, (int) (245 * alpha));
        int outerLobeCol = ColorUtil.rgba(216, 67, 21, (int) (245 * alpha));
        int midLobeCol = ColorUtil.rgba(245, 124, 0, (int) (245 * alpha));
        int frontLobeCol = ColorUtil.rgba(255, 152, 0, (int) (245 * alpha));
        int highlightCol = ColorUtil.rgba(255, 183, 77, (int) (140 * alpha));

        Render2D.drawRoundedRect(context, -width * 0.5f, -height * 0.5f, width, height, height * 0.46f, darkCrease);

        float outerW = width * 0.42f;
        float outerH = height * 0.88f;
        Render2D.drawRoundedRect(context, -width * 0.5f, -outerH * 0.5f, outerW, outerH, outerH * 0.46f, outerLobeCol);
        Render2D.drawRoundedRect(context, width * 0.5f - outerW, -outerH * 0.5f, outerW, outerH, outerH * 0.46f, outerLobeCol);

        float midW = width * 0.46f;
        float midH = height * 0.94f;
        Render2D.drawRoundedRect(context, -width * 0.35f, -midH * 0.5f, midW, midH, midH * 0.46f, midLobeCol);
        Render2D.drawRoundedRect(context, width * 0.35f - midW, -midH * 0.5f, midW, midH, midH * 0.46f, midLobeCol);

        float frontW = width * 0.48f;
        float frontH = height * 0.98f;
        Render2D.drawRoundedRect(context, -frontW * 0.5f, -frontH * 0.5f, frontW, frontH, frontH * 0.46f, frontLobeCol);
        Render2D.drawRoundedRect(context, -frontW * 0.32f, -frontH * 0.42f, frontW * 0.64f, frontH * 0.28f, frontH * 0.14f, highlightCol);

        int flameYellow = ColorUtil.rgba(255, 238, 120, (int) (255 * alpha));
        int flameOrange = ColorUtil.rgba(255, 110, 0, (int) (245 * alpha));
        int carveSocket = ColorUtil.rgba(90, 20, 0, (int) (230 * alpha));

        float eyeOuterR = Math.max(0.9f, width * 0.095f);
        float eyeMidR = Math.max(0.6f, width * 0.07f);
        float eyeCoreR = Math.max(0.4f, width * 0.04f);

        float leX = -width * 0.20f;
        float leY = -height * 0.10f;
        Render2D.drawCircle(context, leX, leY, eyeOuterR, carveSocket);
        Render2D.drawCircle(context, leX + 0.2f, leY + 0.2f, eyeMidR, flameOrange);
        Render2D.drawCircle(context, leX + 0.3f, leY + 0.3f, eyeCoreR, flameYellow);

        float reX = width * 0.20f;
        float reY = -height * 0.10f;
        Render2D.drawCircle(context, reX, reY, eyeOuterR, carveSocket);
        Render2D.drawCircle(context, reX - 0.2f, reY + 0.2f, eyeMidR, flameOrange);
        Render2D.drawCircle(context, reX - 0.3f, reY + 0.3f, eyeCoreR, flameYellow);

        float nX = 0.0f;
        float nY = height * 0.04f;
        float noseR = Math.max(0.6f, width * 0.055f);
        Render2D.drawCircle(context, nX, nY, noseR, carveSocket);
        Render2D.drawCircle(context, nX, nY + 0.2f, noseR * 0.65f, flameYellow);

        float mw = width * 0.62f;
        float mh = height * 0.30f;
        float my = height * 0.22f;

        Render2D.drawRoundedRect(context, -mw * 0.5f, my, mw, mh, mh * 0.45f, carveSocket);

        float flameW = mw * 0.88f;
        float flameH = mh * 0.72f;
        Render2D.drawRoundedRect(context, -flameW * 0.5f, my + mh * 0.14f, flameW, flameH, flameH * 0.45f, flameOrange);
        Render2D.drawRoundedRect(context, -flameW * 0.38f, my + mh * 0.25f, flameW * 0.76f, flameH * 0.50f, flameH * 0.25f, flameYellow);

        float toothW = Math.max(1.0f, width * 0.08f);
        Render2D.drawRect(context, -mw * 0.26f, my - 0.5f, toothW, mh * 0.48f, frontLobeCol);
        Render2D.drawRect(context, mw * 0.10f, my + mh * 0.52f, toothW, mh * 0.50f, frontLobeCol);

        context.getMatrices().popMatrix();
    }

    public static void renderWitchHat(DrawContext context, float cx, float cy, float size, float alpha) {
        if (!ClientSettings.isHalloweenStyle() || alpha <= 0.01f) return;
        renderWitchHatDirect(context, cx, cy, size, alpha);
    }

    public static void renderWitchHatDirect(DrawContext context, float cx, float cy, float size, float alpha) {
        if (alpha <= 0.01f) return;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(cx, cy);
        context.getMatrices().rotate((float) Math.toRadians(13.0));

        int hatBody = ColorUtil.rgba(30, 22, 42, (int) (245 * alpha));
        int hatShade = ColorUtil.rgba(20, 14, 28, (int) (245 * alpha));
        int ribbonCol = ColorUtil.rgba(255, 117, 24, (int) (250 * alpha));
        int buckleCol = ColorUtil.rgba(255, 213, 79, (int) (250 * alpha));
        int buckleHole = ColorUtil.rgba(30, 22, 42, (int) (250 * alpha));

        float brimW = size * 1.35f;
        float brimH = size * 0.32f;
        Render2D.drawRoundedRect(context, -brimW * 0.5f, -brimH * 0.5f, brimW, brimH, brimH * 0.5f, hatShade);
        Render2D.drawRoundedRect(context, -brimW * 0.46f, -brimH * 0.45f, brimW * 0.92f, brimH * 0.75f, brimH * 0.37f, hatBody);

        float coneBaseW = size * 0.72f;
        float coneMidW = size * 0.46f;
        float coneTopW = size * 0.22f;

        Render2D.drawRoundedRect(context, -coneBaseW * 0.5f, -size * 0.42f, coneBaseW, size * 0.40f, 3.0f, hatBody);
        Render2D.drawRoundedRect(context, -coneMidW * 0.5f + 1.0f, -size * 0.78f, coneMidW, size * 0.42f, 2.5f, hatBody);
        Render2D.drawRoundedRect(context, -coneTopW * 0.5f + 3.2f, -size * 1.05f, coneTopW, size * 0.35f, 2.0f, hatShade);

        float ribbonH = size * 0.16f;
        Render2D.drawRoundedRect(context, -coneBaseW * 0.48f, -size * 0.22f, coneBaseW * 0.96f, ribbonH, 1.5f, ribbonCol);

        float buckleS = size * 0.22f;
        Render2D.drawRoundedRect(context, -buckleS * 0.5f, -size * 0.25f, buckleS, buckleS, 2.0f, buckleCol);
        Render2D.drawRect(context, -buckleS * 0.22f, -size * 0.22f, buckleS * 0.44f, buckleS * 0.44f, buckleHole);

        context.getMatrices().popMatrix();
    }
}

package egg.client.ui.halloween;

import egg.client.module.misc.ClientSettings;
import egg.client.render.Render2D;
import egg.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

public final class HalloweenGuiRenderer {
    private HalloweenGuiRenderer() {}

    private static float spiderRetractAnim = 0.0f;
    private static long lastFrameTime = System.currentTimeMillis();

    /**
     * Atmospheric rising embers and particles inside the GUI window.
     */
    public static void renderEmbers(DrawContext context, float gx, float gy, float w, float h, float alpha) {
        if (!ClientSettings.isHalloweenEmbers() || alpha <= 0.01f) return;

        long now = System.currentTimeMillis();
        float timeSec = (now % 10000000L) / 1000.0f;

        // 18 deterministic floating embers
        for (int i = 0; i < 18; i++) {
            float seedX = (float) ((i * 37.19 + 13.7) % 1.0);
            float speed = 10.0f + (i % 5) * 3.5f;
            float lifeProgress = ((timeSec * speed + i * 43.1f) % h) / h; // 0 to 1 as it rises

            float px = gx + seedX * (w - 20.0f) + 10.0f + (float) Math.sin(timeSec * 1.8f + i * 1.3f) * 8.0f;
            float py = gy + h - lifeProgress * h;

            // Fade in at bottom, peak in middle, fade out at top
            float emberFade = (float) Math.sin(lifeProgress * Math.PI);
            float pulse = 0.75f + 0.25f * (float) Math.sin(timeSec * 4.0f + i * 2.0f);
            int emberAlpha = (int) (140 * emberFade * pulse * alpha);
            if (emberAlpha <= 5) continue;

            int color;
            if (i % 3 == 0) {
                // Spooky purple flame ember
                color = ColorUtil.rgba(186, 104, 200, emberAlpha);
            } else if (i % 3 == 1) {
                // Hot jack-o'-lantern orange ember
                color = ColorUtil.rgba(255, 140, 20, emberAlpha);
            } else {
                // Golden spark ember
                color = ColorUtil.rgba(255, 215, 64, emberAlpha);
            }

            float radius = (i % 2 == 0) ? 1.6f : 1.2f;
            Render2D.drawCircle(context, px, py, radius, color);
        }
    }

    /**
     * Intricate organic cobwebs draping over the corners of the ClickGUI window.
     */
    public static void renderWebs(DrawContext context, float gx, float gy, float w, float h, float alpha) {
        if (!ClientSettings.isHalloweenWebs() || alpha <= 0.01f) return;

        int silkCol = ColorUtil.rgba(235, 230, 255, (int) (115 * alpha));
        int arcCol = ColorUtil.rgba(215, 210, 245, (int) (85 * alpha));
        int dewCol = ColorUtil.rgba(255, 255, 255, (int) (180 * alpha));

        // 1. TOP-LEFT CORNER WEB (spreading from (gx, gy))
        renderCornerWeb(context, gx, gy, 72.0f, 0.0f, (float) (Math.PI / 2.0), 6, 5, 0.82f, silkCol, arcCol, dewCol);

        // 2. TOP-RIGHT CORNER WEB (spreading from (gx + w, gy))
        renderCornerWeb(context, gx + w, gy, 62.0f, (float) (Math.PI / 2.0), (float) Math.PI, 5, 4, 0.82f, silkCol, arcCol, dewCol);

        // 3. BOTTOM-RIGHT CORNER WEB (spreading from (gx + w, gy + h))
        renderCornerWeb(context, gx + w, gy + h, 48.0f, (float) Math.PI, (float) (3.0 * Math.PI / 2.0), 5, 3, 0.80f, silkCol, arcCol, dewCol);
    }

    private static void renderCornerWeb(DrawContext context, float cx, float cy, float maxRadius,
                                        float startAngle, float endAngle, int rayCount, int arcCount,
                                        float sagFactor, int silkCol, int arcCol, int dewCol) {
        float angleStep = (endAngle - startAngle) / (rayCount - 1);

        // Radial structural silk rays
        float[] rayCos = new float[rayCount];
        float[] raySin = new float[rayCount];
        for (int i = 0; i < rayCount; i++) {
            float ang = startAngle + i * angleStep;
            rayCos[i] = (float) Math.cos(ang);
            raySin[i] = (float) Math.sin(ang);

            float rx = cx + rayCos[i] * maxRadius;
            float ry = cy + raySin[i] * maxRadius;
            Render2D.drawLine(context, cx, cy, rx, ry, silkCol, 1.0f);
        }

        // Concentric sagging cross-threads (catenary arcs between radial rays)
        for (int ring = 1; ring <= arcCount; ring++) {
            float r = maxRadius * (ring / (float) arcCount);

            for (int i = 0; i < rayCount - 1; i++) {
                float x1 = cx + rayCos[i] * r;
                float y1 = cy + raySin[i] * r;

                float x2 = cx + rayCos[i + 1] * r;
                float y2 = cy + raySin[i + 1] * r;

                // Midpoint sags inward toward corner center (cx, cy)
                float midAngle = startAngle + (i + 0.5f) * angleStep;
                float midR = r * sagFactor;
                float mx = cx + (float) Math.cos(midAngle) * midR;
                float my = cy + (float) Math.sin(midAngle) * midR;

                Render2D.drawLine(context, x1, y1, mx, my, arcCol, 1.0f);
                Render2D.drawLine(context, mx, my, x2, y2, arcCol, 1.0f);

                // Dewdrops at select intersections
                if ((ring + i) % 2 == 0) {
                    Render2D.drawCircle(context, x1, y1, 1.2f, dewCol);
                }
            }
        }
    }

    /**
     * Interactive dangling spider hanging on a silk thread from the top-left web.
     */
    public static void renderSpider(DrawContext context, float gx, float gy, float mouseX, float mouseY, float alpha) {
        if (!ClientSettings.isHalloweenWebs() || alpha <= 0.01f) return;

        long now = System.currentTimeMillis();
        float delta = Math.min(0.1f, (now - lastFrameTime) / 1000.0f);
        lastFrameTime = now;

        float timeSec = (now % 10000000L) / 1000.0f;

        float threadAnchorX = gx + 46.0f;
        float threadAnchorY = gy + 4.0f;
        float baseLen = 42.0f;

        // Proximity detection to mouse
        float defaultSpiderX = threadAnchorX;
        float defaultSpiderY = threadAnchorY + baseLen;
        float distToMouse = (float) Math.hypot(mouseX - defaultSpiderX, mouseY - defaultSpiderY);

        float targetRetract = (distToMouse < 45.0f) ? MathHelper.clamp((45.0f - distToMouse) / 45.0f, 0.0f, 1.0f) * 20.0f : 0.0f;
        spiderRetractAnim += (targetRetract - spiderRetractAnim) * Math.min(1.0f, delta * 8.0f);

        float currentLen = Math.max(16.0f, baseLen - spiderRetractAnim);

        // Gentle pendulum sway in the breeze
        float swingAngle = (float) Math.sin(timeSec * 2.2f) * 0.12f;
        float spiderX = threadAnchorX + (float) Math.sin(swingAngle) * currentLen;
        float spiderY = threadAnchorY + (float) Math.cos(swingAngle) * currentLen;

        // Silk suspension thread
        int threadCol = ColorUtil.rgba(240, 235, 255, (int) (125 * alpha));
        Render2D.drawLine(context, threadAnchorX, threadAnchorY, spiderX, spiderY, threadCol, 1.0f);

        // Draw spider body rotated along swing angle
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(spiderX, spiderY);
        context.getMatrices().rotate(swingAngle);

        int spiderBodyCol = ColorUtil.rgba(28, 24, 34, (int) (240 * alpha));
        int spiderHighlight = ColorUtil.rgba(52, 44, 64, (int) (240 * alpha));
        int legCol = ColorUtil.rgba(36, 30, 44, (int) (230 * alpha));
        int eyeCol = ColorUtil.rgba(255, 64, 32, (int) (245 * alpha));

        // 8 articulated spider legs
        float legTwitch = (float) Math.sin(timeSec * 5.0f) * 1.2f;
        for (int leg = 0; leg < 4; leg++) {
            float yOffset = -2.0f + leg * 1.5f;

            // Left leg (bends outward then down)
            float lkX = -5.0f - leg * 0.5f;
            float lkY = yOffset - 3.5f + legTwitch * (leg % 2 == 0 ? 1 : -1);
            float ltX = -8.0f - leg * 0.6f;
            float ltY = yOffset + 2.5f;
            Render2D.drawLine(context, 0.0f, yOffset, lkX, lkY, legCol, 1.0f);
            Render2D.drawLine(context, lkX, lkY, ltX, ltY, legCol, 1.0f);

            // Right leg (bends outward then down)
            float rkX = 5.0f + leg * 0.5f;
            float rkY = yOffset - 3.5f - legTwitch * (leg % 2 == 0 ? 1 : -1);
            float rtX = 8.0f + leg * 0.6f;
            float rtY = yOffset + 2.5f;
            Render2D.drawLine(context, 0.0f, yOffset, rkX, rkY, legCol, 1.0f);
            Render2D.drawLine(context, rkX, rkY, rtX, rtY, legCol, 1.0f);
        }

        // Abdomen (oval back)
        Render2D.drawRoundedRect(context, -3.2f, -1.0f, 6.4f, 8.0f, 3.2f, spiderBodyCol);
        Render2D.drawRoundedRect(context, -1.8f, 0.5f, 3.6f, 5.0f, 1.8f, spiderHighlight);

        // Cephalothorax (head)
        Render2D.drawCircle(context, 0.0f, -3.0f, 2.6f, spiderBodyCol);

        // Glowing red/orange twin eyes
        Render2D.drawCircle(context, -1.0f, -3.2f, 0.85f, eyeCol);
        Render2D.drawCircle(context, 1.0f, -3.2f, 0.85f, eyeCol);

        context.getMatrices().popMatrix();
    }

    /**
     * Glowing Jack-o'-Lantern pumpkins perched on the edges of the window.
     */
    public static void renderPumpkins(DrawContext context, float gx, float gy, float w, float h, float mouseX, float mouseY, float alpha) {
        if (!ClientSettings.isHalloweenPumpkins() || alpha <= 0.01f) return;

        long now = System.currentTimeMillis();
        float timeSec = (now % 10000000L) / 1000.0f;

        // PUMPKIN 1: Perched on top-right border near search/title
        float p1X = gx + w - 46.0f;
        float p1Y = gy - 13.0f;
        renderJackOLantern(context, p1X, p1Y, 34.0f, 28.0f, -4.0f, mouseX, mouseY, alpha, timeSec, 0.0f);

        // PUMPKIN 2: Resting snugly on the bottom-left sidebar corner
        float p2X = gx - 8.0f;
        float p2Y = gy + h - 25.0f;
        renderJackOLantern(context, p2X, p2Y, 28.0f, 24.0f, 8.0f, mouseX, mouseY, alpha, timeSec, 2.5f);
    }

    private static void renderJackOLantern(DrawContext context, float cx, float cy, float width, float height,
                                          float tiltDeg, float mouseX, float mouseY, float alpha, float timeSec, float seed) {
        boolean hovered = Math.abs(mouseX - cx) < width * 0.7f && Math.abs(mouseY - cy) < height * 0.7f;

        // Candlelight flicker physics
        float flicker = 0.82f + 0.18f * (float) Math.sin(timeSec * 7.1f + seed) + 0.09f * (float) Math.sin(timeSec * 13.7f + seed * 1.5f);
        if (hovered) flicker = Math.min(1.3f, flicker * 1.25f);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(cx, cy);
        float tiltRad = (float) Math.toRadians(tiltDeg);
        if (tiltRad != 0.0f) {
            context.getMatrices().rotate(tiltRad);
        }

        // Slight hover bounce
        if (hovered) {
            float bounce = (float) Math.sin(timeSec * 8.0f) * 0.03f;
            context.getMatrices().scale(1.04f + bounce, 1.04f - bounce);
        }

        // Warm candlelight bloom beneath the pumpkin
        int bloomCol = ColorUtil.rgba(255, 120, 0, (int) (48 * flicker * alpha));
        Render2D.drawShadow(context, -width * 0.5f, -height * 0.4f, width, height, height * 0.45f, 10.0f, bloomCol);

        // Stem (gnarled green stalk on top)
        int stemBaseCol = ColorUtil.rgba(46, 92, 32, (int) (240 * alpha));
        int stemHighlight = ColorUtil.rgba(76, 140, 54, (int) (240 * alpha));
        Render2D.drawLine(context, 0.0f, -height * 0.45f, -2.5f, -height * 0.72f, stemBaseCol, 2.8f);
        Render2D.drawLine(context, 0.5f, -height * 0.45f, -2.0f, -height * 0.70f, stemHighlight, 1.2f);

        // Curled green vine tendril
        int vineCol = ColorUtil.rgba(102, 187, 106, (int) (220 * alpha));
        Render2D.drawLine(context, 0.0f, -height * 0.48f, 3.5f, -height * 0.58f, vineCol, 1.1f);
        Render2D.drawLine(context, 3.5f, -height * 0.58f, 5.0f, -height * 0.50f, vineCol, 1.1f);

        // Pumpkin 3D multi-lobed body
        int darkCrease = ColorUtil.rgba(165, 42, 0, (int) (245 * alpha));
        int outerLobeCol = ColorUtil.rgba(216, 67, 21, (int) (245 * alpha));
        int midLobeCol = ColorUtil.rgba(245, 124, 0, (int) (245 * alpha));
        int frontLobeCol = ColorUtil.rgba(255, 152, 0, (int) (245 * alpha));
        int highlightCol = ColorUtil.rgba(255, 183, 77, (int) (140 * alpha));

        // Dark base silhouette
        Render2D.drawRoundedRect(context, -width * 0.5f, -height * 0.5f, width, height, height * 0.46f, darkCrease);

        // Outer lateral lobes
        float outerW = width * 0.42f;
        float outerH = height * 0.88f;
        Render2D.drawRoundedRect(context, -width * 0.5f, -outerH * 0.5f, outerW, outerH, outerH * 0.46f, outerLobeCol);
        Render2D.drawRoundedRect(context, width * 0.5f - outerW, -outerH * 0.5f, outerW, outerH, outerH * 0.46f, outerLobeCol);

        // Mid lobes
        float midW = width * 0.46f;
        float midH = height * 0.94f;
        Render2D.drawRoundedRect(context, -width * 0.35f, -midH * 0.5f, midW, midH, midH * 0.46f, midLobeCol);
        Render2D.drawRoundedRect(context, width * 0.35f - midW, -midH * 0.5f, midW, midH, midH * 0.46f, midLobeCol);

        // Front central lobe
        float frontW = width * 0.48f;
        float frontH = height * 0.98f;
        Render2D.drawRoundedRect(context, -frontW * 0.5f, -frontH * 0.5f, frontW, frontH, frontH * 0.46f, frontLobeCol);

        // Top specular sheen
        Render2D.drawRoundedRect(context, -frontW * 0.32f, -frontH * 0.42f, frontW * 0.64f, frontH * 0.28f, frontH * 0.14f, highlightCol);

        // --- CARVED JACK-O'-LANTERN FACE ---
        int flameYellow = ColorUtil.rgba(255, 238, 120, (int) (255 * alpha));
        int flameOrange = ColorUtil.rgba(255, 110, 0, (int) (245 * alpha));
        int carveSocket = ColorUtil.rgba(90, 20, 0, (int) (230 * alpha));

        // 1. LEFT EYE (carved triangle)
        float leX = -width * 0.20f;
        float leY = -height * 0.10f;
        Render2D.drawCircle(context, leX, leY, 3.2f, carveSocket);
        Render2D.drawCircle(context, leX + 0.2f, leY + 0.2f, 2.4f, flameOrange);
        Render2D.drawCircle(context, leX + 0.3f, leY + 0.3f, 1.4f, flameYellow);

        // 2. RIGHT EYE (carved triangle)
        float reX = width * 0.20f;
        float reY = -height * 0.10f;
        Render2D.drawCircle(context, reX, reY, 3.2f, carveSocket);
        Render2D.drawCircle(context, reX - 0.2f, reY + 0.2f, 2.4f, flameOrange);
        Render2D.drawCircle(context, reX - 0.3f, reY + 0.3f, 1.4f, flameYellow);

        // 3. NOSE (small carved triangle)
        float nX = 0.0f;
        float nY = height * 0.04f;
        Render2D.drawCircle(context, nX, nY, 1.8f, carveSocket);
        Render2D.drawCircle(context, nX, nY + 0.2f, 1.1f, flameYellow);

        // 4. JAGGED GRINNING MOUTH
        float mw = width * 0.62f;
        float mh = height * 0.30f;
        float my = height * 0.22f;

        // Dark carved mouth cavity
        Render2D.drawRoundedRect(context, -mw * 0.5f, my, mw, mh, mh * 0.45f, carveSocket);

        // Glowing incandescent flame inside mouth
        float flameW = mw * 0.88f;
        float flameH = mh * 0.72f;
        Render2D.drawRoundedRect(context, -flameW * 0.5f, my + mh * 0.14f, flameW, flameH, flameH * 0.45f, flameOrange);
        Render2D.drawRoundedRect(context, -flameW * 0.38f, my + mh * 0.25f, flameW * 0.76f, flameH * 0.50f, flameH * 0.25f, flameYellow);

        // Carved cutout teeth (pumpkin flesh protrusions into mouth cavity)
        // Upper tooth poking down from left
        Render2D.drawRect(context, -mw * 0.26f, my - 0.5f, 2.8f, mh * 0.48f, frontLobeCol);
        // Lower tooth poking up from right
        Render2D.drawRect(context, mw * 0.10f, my + mh * 0.52f, 2.8f, mh * 0.50f, frontLobeCol);

        context.getMatrices().popMatrix();
    }

    /**
     * Adorable crooked witch hat perched on top of the Egg Client Logo.
     */
    public static void renderWitchHat(DrawContext context, float cx, float cy, float size, float alpha) {
        if (!ClientSettings.isHalloweenStyle() || alpha <= 0.01f) return;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(cx, cy);
        context.getMatrices().rotate((float) Math.toRadians(-12.0));

        int hatBody = ColorUtil.rgba(30, 22, 42, (int) (245 * alpha));
        int hatShade = ColorUtil.rgba(20, 14, 28, (int) (245 * alpha));
        int ribbonCol = ColorUtil.rgba(255, 117, 24, (int) (250 * alpha));
        int buckleCol = ColorUtil.rgba(255, 213, 79, (int) (250 * alpha));
        int buckleHole = ColorUtil.rgba(30, 22, 42, (int) (250 * alpha));

        // Broad floppy curved brim
        float brimW = size * 1.35f;
        float brimH = size * 0.32f;
        Render2D.drawRoundedRect(context, -brimW * 0.5f, -brimH * 0.5f, brimW, brimH, brimH * 0.5f, hatShade);
        Render2D.drawRoundedRect(context, -brimW * 0.46f, -brimH * 0.45f, brimW * 0.92f, brimH * 0.75f, brimH * 0.37f, hatBody);

        // Hat cone (tapering upward with crooked tip)
        float coneBaseW = size * 0.72f;
        float coneMidW = size * 0.46f;
        float coneTopW = size * 0.22f;

        // Base segment
        Render2D.drawRoundedRect(context, -coneBaseW * 0.5f, -size * 0.42f, coneBaseW, size * 0.40f, 3.0f, hatBody);
        // Mid segment
        Render2D.drawRoundedRect(context, -coneMidW * 0.5f - 1.0f, -size * 0.78f, coneMidW, size * 0.42f, 2.5f, hatBody);
        // Top crooked tip bending back
        Render2D.drawRoundedRect(context, -coneTopW * 0.5f - 3.2f, -size * 1.05f, coneTopW, size * 0.35f, 2.0f, hatShade);

        // Vibrant pumpkin-orange belt ribbon around base
        float ribbonH = size * 0.16f;
        Render2D.drawRoundedRect(context, -coneBaseW * 0.48f, -size * 0.22f, coneBaseW * 0.96f, ribbonH, 1.5f, ribbonCol);

        // Shiny gold buckle in the center
        float buckleS = size * 0.22f;
        Render2D.drawRoundedRect(context, -buckleS * 0.5f, -size * 0.25f, buckleS, buckleS, 2.0f, buckleCol);
        Render2D.drawRect(context, -buckleS * 0.22f, -size * 0.22f, buckleS * 0.44f, buckleS * 0.44f, buckleHole);

        context.getMatrices().popMatrix();
    }
}
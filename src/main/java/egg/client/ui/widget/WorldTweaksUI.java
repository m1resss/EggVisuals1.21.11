package egg.client.ui.widget;

import egg.client.module.render.WorldTweaks;
import egg.client.render.DynamicGuiTextures;
import egg.client.render.Render2D;
import egg.client.render.font.ClientFont;
import egg.client.util.ColorUtil;
import egg.client.util.GuiSoundManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class WorldTweaksUI {
    private static final Identifier BLOOM_TEXTURE = Identifier.of("delta", "pictures/bloom.png");

    public enum DraggingCard {
        NONE,
        TIME,
        SEASON
    }

    private static DraggingCard activeDrag = DraggingCard.NONE;
    private static float activeCardX = 0.0f;
    private static float activeCardW = 200.0f;

    public static float getTotalHeight() {
        return 86.0f + 8.0f + 86.0f + 8.0f + 26.0f; // Time Card + Season Card + Weather Bar
    }

    public static void render(DrawContext context, WorldTweaks wt, float x, float y, float width, float alpha, float mouseX, float mouseY) {
        float cardH = 86.0f;

        // 1. Time of Day Arc Card
        renderTimeArcCard(context, wt, x, y, width, cardH, alpha, mouseX, mouseY);

        // 2. Season Arc Card
        float seasonY = y + cardH + 8.0f;
        renderSeasonArcCard(context, wt, x, seasonY, width, cardH, alpha, mouseX, mouseY);

        // 3. Weather Pills Bar
        float weatherY = seasonY + cardH + 8.0f;
        renderWeatherBar(context, wt, x, weatherY, width, alpha, mouseX, mouseY);
    }

    private static void renderTimeArcCard(DrawContext context, WorldTweaks wt, float x, float y, float w, float h, float alpha, float mouseX, float mouseY) {
        double time = wt.getTimeOfDay();
        float t = (float) ((time % 24000L) / 24000.0);

        int hours = (int) (((time + 6000L) % 24000L) / 1000L);
        int minutes = (int) ((((time + 6000L) % 24000L) % 1000L) * 60L / 1000L);
        String timeStr = String.format("%02d:%02d", hours, minutes);

        String title, subtitle;
        int topColor, bottomColor, orbColor, glowColor;

        if (t >= 0.22f && t < 0.48f) {
            // Day
            title = "Day";
            subtitle = "Mostly clear";
            topColor = 0xFF2A84EE;
            bottomColor = 0xFF58AEF6;
            orbColor = 0xFFFFD84D;
            glowColor = 0xAAFFC830;
        } else if (t >= 0.48f && t < 0.65f) {
            // Evening / Sunset
            title = "Evening";
            subtitle = "Golden hour";
            topColor = 0xFF584564;
            bottomColor = 0xFFDC7F43;
            orbColor = 0xFFFFD2A0;
            glowColor = 0xAAFF8A3A;
        } else if (t >= 0.65f && t < 0.92f) {
            // Night
            title = "Night";
            subtitle = "Clear sky";
            topColor = 0xFF0B0E17;
            bottomColor = 0xFF141A28;
            orbColor = 0xFFE2E8F0;
            glowColor = 0x888AA8D4;
        } else {
            // Morning / Dawn
            title = "Morning";
            subtitle = "Dawn rose";
            topColor = 0xFF4A3458;
            bottomColor = 0xFFD87870;
            orbColor = 0xFFFFCBA4;
            glowColor = 0xAAFF8878;
        }

        // Draw Card Body
        Render2D.drawRoundedGradientRect(context, x, y, w, h, 10.0f,
                ColorUtil.withAlphaFloat(topColor, alpha),
                ColorUtil.withAlphaFloat(bottomColor, alpha));
        Render2D.drawRoundedOutline(context, x, y, w, h, 10.0f, 1.0f,
                ColorUtil.rgba(255, 255, 255, (int) (55 * alpha)));

        // Header info
        ClientFont.draw(context, title, x + 14.0f, y + 10.0f, ClientFont.SF_BOLD, ColorUtil.withAlphaFloat(0xFFFFFFFF, alpha));
        ClientFont.draw(context, subtitle, x + 14.0f, y + 22.0f, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(0xD0FFFFFF, alpha * 0.85f));
        ClientFont.draw(context, timeStr, x + w - 14.0f - ClientFont.getWidth(timeStr, ClientFont.SF_BOLD), y + 10.0f, ClientFont.SF_BOLD, ColorUtil.withAlphaFloat(0xFFFFFFFF, alpha));

        // Draw Vector Smooth Arc
        float arcAreaY = y + 24.0f;
        float arcAreaH = h - 24.0f;
        Identifier arcTex = DynamicGuiTextures.getCardArc(Math.round(w), Math.round(arcAreaH));
        Render2D.drawTexture(context, arcTex, x, arcAreaY, w, arcAreaH, ColorUtil.rgba(255, 255, 255, (int) (220 * alpha)));

        // Mathematical arc position for Knob
        float arcTopY = arcAreaY + 20.0f;
        float d = (w - 36.0f) / 2.0f;
        float s = arcAreaH - 20.0f;
        float R = (d * d + s * s) / (2.0f * s);
        float cy = arcTopY + R;
        float cx = x + w / 2.0f;

        float handleX = (cx - d) + t * (2.0f * d);
        float dx = handleX - cx;
        float handleY = cy - (float) Math.sqrt(Math.max(0.0f, R * R - dx * dx));

        // Knob Radial Glow Halo
        float haloSize = 36.0f;
        Render2D.drawTexture(context, BLOOM_TEXTURE, handleX - haloSize * 0.5f, handleY - haloSize * 0.5f, haloSize, haloSize,
                ColorUtil.withAlphaFloat(glowColor, alpha * 0.9f));

        // Knob Body
        float orbRadius = 7.0f;
        Render2D.drawCircle(context, handleX, handleY, orbRadius, ColorUtil.withAlphaFloat(orbColor, alpha));
        Render2D.drawCircle(context, handleX, handleY, orbRadius * 0.5f, ColorUtil.withAlphaFloat(0xFFFFFFFF, alpha * 0.95f));
        Render2D.drawCircleOutline(context, handleX, handleY, orbRadius, 1.2f, ColorUtil.rgba(255, 255, 255, (int) (210 * alpha)));
    }

    private static void renderSeasonArcCard(DrawContext context, WorldTweaks wt, float x, float y, float w, float h, float alpha, float mouseX, float mouseY) {
        String curSeason = wt.getSeasonSetting().get();
        float t;
        int topColor, bottomColor, knobColor, glowColor;
        String seasonName, seasonDesc;

        if ("Зима".equals(curSeason)) {
            t = 0.08f;
            seasonName = "Зима";
            seasonDesc = "Морозный снег и снежинки";
            topColor = 0xFF1B3852;
            bottomColor = 0xFF35698C;
            knobColor = 0xFFEAF5FF;
            glowColor = 0xAA9AD5FF;
        } else if ("Весна".equals(curSeason)) {
            t = 0.35f;
            seasonName = "Весна";
            seasonDesc = "Цветение сакуры и лепестки";
            topColor = 0xFF58243C;
            bottomColor = 0xFF9E466E;
            knobColor = 0xFFFFD4E2;
            glowColor = 0xAAFF80A6;
        } else if ("Лето".equals(curSeason)) {
            t = 0.65f;
            seasonName = "Лето";
            seasonDesc = "Яркое солнце и корона";
            topColor = 0xFF704410;
            bottomColor = 0xFFC68218;
            knobColor = 0xFFFFE55C;
            glowColor = 0xAAFFB820;
        } else if ("Осень".equals(curSeason)) {
            t = 0.92f;
            seasonName = "Осень";
            seasonDesc = "Золотой лес и листопад";
            topColor = 0xFF5C2B14;
            bottomColor = 0xFFA8501E;
            knobColor = 0xFFFF8A38;
            glowColor = 0xAAFF5A1C;
        } else {
            t = 0.5f;
            seasonName = "Выкл";
            seasonDesc = "Ванильный сезон";
            topColor = 0xFF1E222E;
            bottomColor = 0xFF2B3042;
            knobColor = 0xFFCBD2E4;
            glowColor = 0x66A0ACCC;
        }

        // Draw Card Body
        Render2D.drawRoundedGradientRect(context, x, y, w, h, 10.0f,
                ColorUtil.withAlphaFloat(topColor, alpha),
                ColorUtil.withAlphaFloat(bottomColor, alpha));
        Render2D.drawRoundedOutline(context, x, y, w, h, 10.0f, 1.0f,
                ColorUtil.rgba(255, 255, 255, (int) (55 * alpha)));

        // Header info
        ClientFont.draw(context, seasonName, x + 14.0f, y + 10.0f, ClientFont.SF_BOLD, ColorUtil.withAlphaFloat(0xFFFFFFFF, alpha));
        ClientFont.draw(context, seasonDesc, x + 14.0f, y + 22.0f, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(0xD0FFFFFF, alpha * 0.85f));

        // Draw Vector Smooth Arc
        float arcAreaY = y + 24.0f;
        float arcAreaH = h - 24.0f;
        Identifier arcTex = DynamicGuiTextures.getCardArc(Math.round(w), Math.round(arcAreaH));
        Render2D.drawTexture(context, arcTex, x, arcAreaY, w, arcAreaH, ColorUtil.rgba(255, 255, 255, (int) (220 * alpha)));

        // Arc Geometry
        float arcTopY = arcAreaY + 20.0f;
        float d = (w - 36.0f) / 2.0f;
        float s = arcAreaH - 20.0f;
        float R = (d * d + s * s) / (2.0f * s);
        float cy = arcTopY + R;
        float cx = x + w / 2.0f;

        // Station Markers for the 4 seasons
        float[] stations = new float[]{0.08f, 0.35f, 0.65f, 0.92f};
        for (int i = 0; i < 4; i++) {
            float st = stations[i];
            float sx = (cx - d) + st * (2.0f * d);
            float sdy = sx - cx;
            float sy = cy - (float) Math.sqrt(Math.max(0.0f, R * R - sdy * sdy));
            Render2D.drawCircle(context, sx, sy, 3.2f, ColorUtil.rgba(255, 255, 255, (int) (140 * alpha)));
            Render2D.drawCircle(context, sx, sy, 1.6f, ColorUtil.rgba(255, 255, 255, (int) (230 * alpha)));
        }

        // Orb Handle position
        float handleX = (cx - d) + t * (2.0f * d);
        float dx = handleX - cx;
        float handleY = cy - (float) Math.sqrt(Math.max(0.0f, R * R - dx * dx));

        // Glow Halo
        float haloSize = 36.0f;
        Render2D.drawTexture(context, BLOOM_TEXTURE, handleX - haloSize * 0.5f, handleY - haloSize * 0.5f, haloSize, haloSize,
                ColorUtil.withAlphaFloat(glowColor, alpha * 0.9f));

        // Knob Body
        float orbRadius = 7.0f;
        Render2D.drawCircle(context, handleX, handleY, orbRadius, ColorUtil.withAlphaFloat(knobColor, alpha));
        Render2D.drawCircle(context, handleX, handleY, orbRadius * 0.5f, ColorUtil.withAlphaFloat(0xFFFFFFFF, alpha * 0.95f));
        Render2D.drawCircleOutline(context, handleX, handleY, orbRadius, 1.2f, ColorUtil.rgba(255, 255, 255, (int) (210 * alpha)));
    }

    private static void renderWeatherBar(DrawContext context, WorldTweaks wt, float x, float y, float w, float alpha, float mouseX, float mouseY) {
        String[] options = new String[]{"Ясно", "Дождь", "Гроза"};
        String current = wt.getWeatherSetting().get();

        float pillW = (w - 12.0f) / 3.0f;
        float pillH = 26.0f;

        for (int i = 0; i < 3; i++) {
            String opt = options[i];
            boolean selected = opt.equals(current);
            float px = x + i * (pillW + 6.0f);

            int bgCol;
            int textCol;
            if (selected) {
                bgCol = 0xFF2563EB; // Electric blue pill
                textCol = 0xFFFFFFFF;
            } else {
                boolean hovered = mouseX >= px && mouseX <= px + pillW && mouseY >= y && mouseY <= y + pillH;
                bgCol = hovered ? 0xFF2A3142 : 0xFF1E2330;
                textCol = hovered ? 0xFFE2E8F0 : 0xFF94A3B8;
            }

            Render2D.drawRoundedRect(context, px, y, pillW, pillH, 6.0f, ColorUtil.withAlphaFloat(bgCol, alpha));
            if (!selected) {
                Render2D.drawRoundedOutline(context, px, y, pillW, pillH, 6.0f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (25 * alpha)));
            }

            float tw = ClientFont.getWidth(opt, ClientFont.SF_BOLD);
            float th = ClientFont.FONT_HEIGHT;
            ClientFont.draw(context, opt, px + (pillW - tw) * 0.5f, y + (pillH - th) * 0.5f + 1.0f,
                    ClientFont.SF_BOLD, ColorUtil.withAlphaFloat(textCol, alpha));
        }
    }

    public static boolean mouseClicked(WorldTweaks wt, float x, float y, float cardW, float mouseX, float mouseY, int button) {
        if (button != 0) return false;

        float cardH = 86.0f;

        // 1. Time Card Drag/Click Check
        if (mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + cardH) {
            activeDrag = DraggingCard.TIME;
            activeCardX = x;
            activeCardW = cardW;
            updateTimeFromMouse(wt, x, cardW, mouseX);
            return true;
        }

        // 2. Season Card Drag/Click Check
        float seasonY = y + cardH + 8.0f;
        if (mouseX >= x && mouseX <= x + cardW && mouseY >= seasonY && mouseY <= seasonY + cardH) {
            activeDrag = DraggingCard.SEASON;
            activeCardX = x;
            activeCardW = cardW;
            updateSeasonFromMouse(wt, x, cardW, mouseX);
            return true;
        }

        // 3. Weather Bar Check
        float weatherY = seasonY + cardH + 8.0f;
        if (mouseX >= x && mouseX <= x + cardW && mouseY >= weatherY && mouseY <= weatherY + 26.0f) {
            String[] options = new String[]{"Ясно", "Дождь", "Гроза"};
            float pillW = (cardW - 12.0f) / 3.0f;
            for (int i = 0; i < 3; i++) {
                float px = x + i * (pillW + 6.0f);
                if (mouseX >= px && mouseX <= px + pillW) {
                    wt.getWeatherSetting().set(options[i]);
                    wt.syncAudioState();
                    egg.client.config.ConfigManager.save();
                    GuiSoundManager.playSlider();
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean mouseDragged(WorldTweaks wt, float mouseX, float mouseY) {
        if (activeDrag == DraggingCard.TIME) {
            updateTimeFromMouse(wt, activeCardX, activeCardW, mouseX);
            return true;
        } else if (activeDrag == DraggingCard.SEASON) {
            updateSeasonFromMouse(wt, activeCardX, activeCardW, mouseX);
            return true;
        }
        return false;
    }

    public static void mouseReleased() {
        if (activeDrag != DraggingCard.NONE) {
            activeDrag = DraggingCard.NONE;
            egg.client.config.ConfigManager.save();
        }
    }

    private static void updateTimeFromMouse(WorldTweaks wt, float cardX, float cardW, float mouseX) {
        float arcX1 = cardX + 18.0f;
        float arcX2 = cardX + cardW - 18.0f;
        float t = MathHelper.clamp((mouseX - arcX1) / (arcX2 - arcX1), 0.0f, 1.0f);
        double oldTime = wt.getTimeOfDay();
        wt.setTimeOfDay(t * 24000.0);
        if (Math.abs(wt.getTimeOfDay() - oldTime) > 60.0) {
            GuiSoundManager.playSlider();
        }
    }

    private static void updateSeasonFromMouse(WorldTweaks wt, float cardX, float cardW, float mouseX) {
        float arcX1 = cardX + 18.0f;
        float arcX2 = cardX + cardW - 18.0f;
        float t = MathHelper.clamp((mouseX - arcX1) / (arcX2 - arcX1), 0.0f, 1.0f);

        String chosen;
        if (t < 0.22f) {
            chosen = "Зима";
        } else if (t < 0.50f) {
            chosen = "Весна";
        } else if (t < 0.78f) {
            chosen = "Лето";
        } else {
            chosen = "Осень";
        }
        if (!chosen.equals(wt.getSeasonSetting().get())) {
            wt.getSeasonSetting().set(chosen);
            wt.refreshWorldChunks();
            GuiSoundManager.playSlider();
        }
    }
}

package delta.ui.widget;

import platform.inject.accessors.BossBarHudAccessor;
import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.render.EasingList;
import delta.render.Fonts;
import delta.render.ColorUtil;
import delta.util.MathUtil;
import delta.util.ServerUtil;

import delta.config.ThemeInfo;
import delta.core.Interface;
import delta.event.DrawEvent;
import delta.ui.element.DragInfo;
import delta.ui.widget.Widget;

import delta.setting.BooleanSetting;
import delta.setting.ModeSetting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.util.Identifier;
import org.joml.Vector4f;

public class WatermarkWidget extends Widget implements Interface {
    private static final Identifier EGG_LOGO = Identifier.of("delta", "textures/egg_logo.png");
    private static final Identifier FUNTIME_ICON = Identifier.of("delta", "textures/servers/funtime.png");
    private static final Identifier REALLYWORLD_ICON = Identifier.of("delta", "textures/servers/reallyworld.png");
    private static final Identifier SPACETIME_ICON = Identifier.of("delta", "textures/servers/spacetime.png");

    private float f;
    private float animatedPing = 0.0f;
    private float animatedPosX = 0.0f;
    private float animatedPosY = 0.0f;
    private float animatedPosZ = 0.0f;
    private final BooleanSetting g; // Боковое отображение
    private final BooleanSetting h; // Разделять элементы (legacy compat)
    private final ModeSetting brand; // Бренд
    private final BooleanSetting showLogo; // Иконка
    private final BooleanSetting l; // Логин в клиенте
    private final BooleanSetting showServer; // Сервер
    private final BooleanSetting i; // Частота кадров
    private final BooleanSetting n; // Задержка сервера (TPS)
    private final BooleanSetting j; // Задержка игрока (Ping)
    private final BooleanSetting k; // Текущее время
    private final BooleanSetting m; // Координаты
    private final BooleanSetting o; // Скорость игрока
    private boolean lastSideMode = false;
    private boolean positionInitialized = false;
    private float savedUserY = -1.0f;
    private boolean userMoved = false;

    public WatermarkWidget() {
        super(new DragInfo("Инфо-панель", 0.0f, 0.0f, 0.0f, 0.0f));
        this.g = new BooleanSetting("Боковое отображение", false);
        this.h = new BooleanSetting("Разделять элементы", false);
        this.brand = new ModeSetting("Бренд", "Egg", "Egg", "egg client", "delta", "rockstar", "none");
        this.showLogo = new BooleanSetting("Иконка", true);
        this.l = new BooleanSetting("Логин в клиенте", true);
        this.showServer = new BooleanSetting("Сервер", true);
        this.i = new BooleanSetting("Частота кадров", true);
        this.n = new BooleanSetting("Задержка сервера", true);
        this.j = new BooleanSetting("Задержка игрока", true);
        this.k = new BooleanSetting("Текущее время", true);
        this.m = new BooleanSetting("Координаты", false);
        this.o = new BooleanSetting("Скорость игрока", false);
        j().a(this);
        a(this.g, this.showLogo, this.l, this.showServer, this.i, this.n, this.j, this.k, this.m, this.o);
    }

    private Identifier getServerIcon(String address) {
        if (address == null) return null;
        String lower = address.toLowerCase(Locale.ROOT);
        if (lower.contains("funtime.su") || lower.contains("funtime.sh") || lower.contains("funtime")) {
            return FUNTIME_ICON;
        }
        if (lower.contains("reallyworld.ru") || lower.contains("reallyworld.me") || lower.contains("reallyworld")) {
            return REALLYWORLD_ICON;
        }
        if (lower.contains("space-times.ru") || lower.contains("space-times.fun") || lower.contains("space-times") || lower.contains("spacetime")) {
            return SPACETIME_ICON;
        }
        return null;
    }

    private float calculateItemWidth(WatermarkItem item, float fontSize) {
        float w = 0.0f;
        if (item.customTexture != null) {
            w += 10.0f + 3.0f;
        } else if (item.iconGlyph != null) {
            w += Fonts.a.a(item.iconGlyph, 7.5f) + 3.0f;
        }
        w += Fonts.e.a(item.text, fontSize);
        return w;
    }

    private float renderItem(DrawEvent event, WatermarkItem item, float cursor, float rowY, float pillHeight, float textY, float fontSize, int textColor, float anim) {
        if (item.customTexture != null) {
            float texSize = 10.0f;
            float texY = rowY + ((pillHeight - texSize) / 2.0f) + 0.65f;
            event.d().a(event.h(), item.customTexture, cursor, texY, texSize, texSize, 2.5f, ColorUtil.a(255, 255, 255, (int) (255 * anim)));
            cursor += texSize + 3.0f;
        } else if (item.iconGlyph != null) {
            float iconH = Fonts.a.a(7.5f);
            float iconY = rowY + ((pillHeight - iconH) / 2.0f) + 0.95f;
            Fonts.a.a(event.h(), item.iconGlyph, cursor, iconY, 7.5f, item.iconColor);
            cursor += Fonts.a.a(item.iconGlyph, 7.5f) + 3.0f;
        }
        Fonts.e.a(event.h(), item.text, cursor, textY, fontSize, textColor);
        cursor += Fonts.e.a(item.text, fontSize);
        return cursor;
    }

    @Override
    public void a(DrawEvent event) {
        float bossOffset;
        d().a(true);
        d().a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());
        this.f = MathUtil.c(this.f, aM_.getCurrentFps(), 0.1f);
        float anim = Math.max(0.1f, a());

        float pillHeight = 18.0f;
        float radius = getRounding();
        float blurVal = getBlur();
        float opacityVal = getOpacity();
        float fontSize = 8.5f;
        float brandFontSize = 9.6f;
        float dotSize = 7.0f;
        float logoSize = 13.0f;
        float padH = 6.0f;
        float logoPadH = 4.0f;
        float logoGap = 4.0f;
        float gap = 5.0f;
        float dotGap = 5.0f;

        boolean hasLogo = this.showLogo.c().booleanValue();
        String brandSettingVal = this.brand.c();
        String brandStr = brandSettingVal;
        if (brandStr.equalsIgnoreCase("egg client") || brandStr.equalsIgnoreCase("Egg")) {
            brandStr = "Egg";
        }
        boolean hasBrand = !brandStr.equalsIgnoreCase("none");

        // Primary theme accent for icons
        int themePrimary = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
        int iconPrimary = ColorUtil.a(themePrimary, (int) (240 * anim));

        // Collect dynamic stats with icons safely
        List<WatermarkItem> stats = new ArrayList<>();
        if (this.l.c().booleanValue()) {
            String username = (aM_.player != null && aM_.player.getName() != null) 
                    ? aM_.player.getName().getString() 
                    : (aM_.getSession() != null ? aM_.getSession().getUsername() : "User");
            stats.add(new WatermarkItem(username, "L", null, iconPrimary));
        }
        if (this.showServer.c().booleanValue()) {
            String serverName = "одиночная";
            String rawAddr = "";
            try {
                if (aM_.player != null && aM_.player.networkHandler != null && aM_.player.networkHandler.getServerInfo() != null) {
                    String addr = aM_.player.networkHandler.getServerInfo().address;
                    if (addr != null && !addr.trim().isEmpty()) {
                        serverName = addr.trim();
                        rawAddr = addr.trim();
                    }
                }
            } catch (Exception ignored) {
            }
            Identifier serverTex = getServerIcon(rawAddr);
            stats.add(new WatermarkItem(serverName, serverTex == null ? "w" : null, serverTex, iconPrimary));
        }
        if (this.i.c().booleanValue()) {
            stats.add(new WatermarkItem(Math.round(this.f) + " fps", "i", null, iconPrimary));
        }
        if (this.n.c().booleanValue()) {
            String tpsStr = "20.0 TPS";
            try {
                if (Delta.h() != null && Delta.h().d() != null && Delta.h().d().v() != null && Delta.h().d().v().j() != null) {
                    tpsStr = String.format(Locale.ROOT, "%.1f TPS", Float.valueOf(Delta.h().d().v().j().a()));
                }
            } catch (Exception ignored) {
            }
            stats.add(new WatermarkItem(tpsStr, "d", null, iconPrimary));
        }
        if (this.j.c().booleanValue()) {
            float targetPing = 0.0f;
            try {
                targetPing = (float) ServerUtil.d();
            } catch (Exception ignored) {
            }
            this.animatedPing = MathUtil.c(this.animatedPing, targetPing, 10.0f);
            stats.add(new WatermarkItem(Math.round(this.animatedPing) + " ms", "q", null, iconPrimary));
        }
        if (this.m.c().booleanValue() && aM_.player != null) {
            this.animatedPosX = MathUtil.c(this.animatedPosX, (float) aM_.player.getX(), 12.0f);
            this.animatedPosY = MathUtil.c(this.animatedPosY, (float) aM_.player.getY(), 12.0f);
            this.animatedPosZ = MathUtil.c(this.animatedPosZ, (float) aM_.player.getZ(), 12.0f);
            stats.add(new WatermarkItem(Math.round(this.animatedPosX) + " " + Math.round(this.animatedPosY) + " " + Math.round(this.animatedPosZ), "F", null, iconPrimary));
        }
        if (this.o.c().booleanValue() && aM_.player != null) {
            String speedStr = "0.00 bps";
            try {
                speedStr = String.format(Locale.ROOT, "%.2f bps", Double.valueOf(ServerUtil.c()));
            } catch (Exception ignored) {
            }
            stats.add(new WatermarkItem(speedStr, "I", null, iconPrimary));
        }
        if (this.k.c().booleanValue()) {
            stats.add(new WatermarkItem(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), "c", null, iconPrimary));
        }

        if (!hasBrand && !hasLogo && stats.isEmpty()) {
            super.a(event);
            return;
        }

        // Build rows (maximum 2 rows total: row 1 takes first 3 elements, row 2 takes all remaining elements)
        List<WatermarkRow> rows = new ArrayList<>();
        WatermarkRow row1 = new WatermarkRow();
        row1.hasLogo = hasLogo;
        row1.hasBrand = hasBrand;
        row1.brandStr = brandStr;

        int statIdx = 0;
        int row1Capacity = hasBrand ? 2 : 3;
        while (statIdx < stats.size() && row1.stats.size() < row1Capacity) {
            row1.stats.add(stats.get(statIdx++));
        }
        rows.add(row1);

        // Maximum 2 rows: all remaining stats go onto row 2
        if (statIdx < stats.size()) {
            WatermarkRow row2 = new WatermarkRow();
            row2.hasLogo = false;
            row2.hasBrand = false;
            row2.brandStr = "";
            while (statIdx < stats.size()) {
                row2.stats.add(stats.get(statIdx++));
            }
            rows.add(row2);
        }

        // Calculate width for each row
        float logoEndPad = hasLogo ? (logoPadH + logoSize) : 0.0f;
        for (WatermarkRow row : rows) {
            float rowW = 0.0f;
            if (row.hasLogo) {
                rowW += logoEndPad;
                if (row.hasBrand) {
                    rowW += dotGap + Fonts.e.a("•", dotSize) + dotGap;
                } else if (!row.stats.isEmpty()) {
                    rowW += gap;
                }
            } else {
                rowW += padH;
            }

            if (row.hasBrand) {
                rowW += Fonts.e.a(row.brandStr, brandFontSize);
            }

            boolean isFirstItemWithoutBrandOrLogo = !row.hasBrand && !row.hasLogo;
            for (int i = 0; i < row.stats.size(); i++) {
                WatermarkItem item = row.stats.get(i);
                float itemW = calculateItemWidth(item, fontSize);
                if (i == 0 && isFirstItemWithoutBrandOrLogo) {
                    rowW += itemW;
                } else {
                    rowW += dotGap + Fonts.e.a("•", dotSize) + dotGap + itemW;
                }
            }
            rowW += padH;
            row.width = rowW;
        }

        if (this.g.c().booleanValue() || ((platform.inject.accessors.BossBarHudAccessor) aM_.inGameHud.getBossBarHud()).getBossBars().isEmpty()) {
            bossOffset = 0.0f;
        } else {
            int size = ((platform.inject.accessors.BossBarHudAccessor) aM_.inGameHud.getBossBarHud()).getBossBars().size() - 1;
            Objects.requireNonNull(aM_.textRenderer);
            bossOffset = (((12 + (size * (10 + 9))) + 5) * aM_.getWindow().calculateScaleFactor(((Integer) aM_.options.getGuiScale().getValue()).intValue(), aM_.forcesUnicodeFont())) / aM_.getWindow().calculateScaleFactor(2, aM_.forcesUnicodeFont());
        }

        // Dynamic Island collision detection & smooth adaptation
        float islandBottom = 0.0f;
        float islandLeft = 0.0f;
        float islandRight = 0.0f;
        boolean islandActive = false;
        delta.module.render.Interface_2 iface = delta.module.render.Interface_2.getInstance();
        if (iface != null && iface.getElementsSetting() != null) {
            for (Widget w : iface.q()) {
                if (w instanceof MediaPlayerWidget) {
                    BooleanSetting mpEnabled = iface.getElementsSetting().a(w.j().j());
                    if (mpEnabled != null && mpEnabled.c().booleanValue() && w.a() > 0.01f) {
                        islandLeft = w.j().a();
                        islandRight = w.j().a() + w.j().f();
                        islandBottom = w.j().b() + w.j().g() + 5.0f; // w.j().g() is height
                        islandActive = true;
                    }
                    break;
                }
            }
        }

        float rowGap = 3.5f;
        int screenWidth = aM_.getWindow().getScaledWidth();
        float maxRowWidth = 0.0f;
        for (WatermarkRow r : rows) {
            maxRowWidth = Math.max(maxRowWidth, r.width);
        }
        float totalWidgetWidth = Math.max(1.0f, maxRowWidth);
        float totalWidgetHeight = rows.size() * pillHeight + (rows.size() - 1) * rowGap;
        float defaultBaseY = 5.0f + bossOffset;

        boolean sideMode = this.g.c().booleanValue();
        if (!this.positionInitialized) {
            this.positionInitialized = true;
            this.lastSideMode = sideMode;
            if (j().a() == 0.0f && j().b() == 0.0f) {
                if (sideMode) {
                    j().a(5.0f);
                    j().b(5.0f + bossOffset);
                } else {
                    j().a((screenWidth - totalWidgetWidth) / 2.0f);
                    j().b(5.0f + bossOffset);
                }
            } else {
                this.userMoved = true;
            }
            this.savedUserY = j().b();
        } else if (sideMode != this.lastSideMode) {
            this.lastSideMode = sideMode;
            this.userMoved = false;
            if (sideMode) {
                j().a(5.0f);
                j().b(5.0f + bossOffset);
            } else {
                j().a((screenWidth - totalWidgetWidth) / 2.0f);
                j().b(5.0f + bossOffset);
            }
            this.savedUserY = j().b();
        }

        // Check if user is actively dragging this widget in ChatScreen
        DragInfo activeDrag = null;
        try {
            if (Delta.h() != null && Delta.h().d() != null && Delta.h().d().s() != null) {
                activeDrag = Delta.h().d().s().g();
            }
        } catch (Throwable ignored) {}
        boolean isDraggingThis = (activeDrag == j());

        if (isDraggingThis) {
            this.userMoved = true;
            this.savedUserY = j().b();
        } else {
            if (!this.userMoved) {
                this.savedUserY = defaultBaseY;
                if (!sideMode) {
                    j().a((screenWidth - totalWidgetWidth) / 2.0f);
                }
            }
            boolean horizOverlap = (j().a() + totalWidgetWidth > islandLeft + 2.0f) && (j().a() < islandRight - 2.0f);
            float targetY = this.savedUserY;
            if (islandActive && horizOverlap && this.savedUserY < islandBottom) {
                targetY = islandBottom;
            }
            if (Math.abs(j().b() - targetY) > 0.1f) {
                j().b(MathUtil.c(j().b(), targetY, 0.25f));
            } else {
                j().b(targetY);
            }
        }

        j().c(totalWidgetWidth);
        j().d(totalWidgetHeight);

        float baseX = j().a();
        float baseY = j().b();

        // Styling colors
        int baseWatermarkBg = ColorUtil.a(24, 26, 36, 215);
        if (iface != null && iface.getWatermarkBgColor() != null) {
            baseWatermarkBg = iface.getWatermarkBgColor().c().intValue();
        }
        float wmAlpha = (((baseWatermarkBg >> 24) & 0xFF) / 255.0f);
        if (wmAlpha <= 0.0f) wmAlpha = 0.85f;
        int bgColor = ColorUtil.a(baseWatermarkBg, (int) (opacityVal * wmAlpha * 255.0f * anim));
        int borderColor = ColorUtil.a(255, 255, 255, (int) (0.12f * 255.0f * anim));
        int dotColor = ColorUtil.a(145, 148, 160, (int) (0.85f * 255.0f * anim));
        int textColor = ColorUtil.a(235, 238, 248, (int) (0.95f * 255.0f * anim));
        int whiteColor = ColorUtil.a(255, 255, 255, (int) (1.0f * 255.0f * anim));

        // Render each row as its own neat pill capsule
        for (int rIdx = 0; rIdx < rows.size(); rIdx++) {
            WatermarkRow row = rows.get(rIdx);
            float rowX;
            if (baseX + totalWidgetWidth / 2.0f < screenWidth * 0.35f) {
                rowX = baseX;
            } else if (baseX + totalWidgetWidth / 2.0f > screenWidth * 0.65f) {
                rowX = baseX + (totalWidgetWidth - row.width);
            } else {
                rowX = baseX + (totalWidgetWidth - row.width) / 2.0f;
            }
            float rowY = baseY + rIdx * (pillHeight + rowGap);

            // 1. Render sleek frosted card background & subtle outline
            if (blurVal > 0.0f && !event.d().e().e().isEmpty()) {
                event.d().b(event.h(), rowX, rowY, row.width, pillHeight, radius, bgColor, anim);
            } else {
                event.d().a(event.h(), rowX, rowY, row.width, pillHeight, radius, bgColor);
            }
            Widget.drawHudThemeGradient(event.d(), event.h(), rowX, rowY, row.width, pillHeight, radius, anim);

            // 2. Outer card contour border
            event.d().a(event.h(), rowX, rowY, row.width, pillHeight, radius, 1.0f, borderColor);

            // 3. Render content
            float cursor = rowX;
            float textY = rowY + ((pillHeight - Fonts.e.a(fontSize)) / 2.0f) - 0.5f;
            float brandTextY = rowY + ((pillHeight - Fonts.e.a(brandFontSize)) / 2.0f) - 0.5f;
            float dotY = rowY + ((pillHeight - Fonts.e.a(dotSize)) / 2.0f) - 0.5f;
            float logoY = rowY + ((pillHeight - logoSize) / 2.0f);

            if (row.hasLogo) {
                event.d().a(event.h(), EGG_LOGO, rowX + logoPadH, logoY, logoSize, logoSize, 2.0f, ColorUtil.a(255, 255, 255, (int) (255 * anim)));
                if (row.hasBrand) {
                    cursor = rowX + logoEndPad + dotGap;
                    Fonts.e.a(event.h(), "•", cursor, dotY, dotSize, dotColor);
                    cursor += Fonts.e.a("•", dotSize) + dotGap;
                } else if (!row.stats.isEmpty()) {
                    cursor = rowX + logoEndPad + gap;
                } else {
                    cursor = rowX + logoEndPad;
                }
            } else {
                cursor = rowX + padH;
            }

            if (row.hasBrand) {
                if (brandStr.equalsIgnoreCase("Egg") || brandStr.equalsIgnoreCase("egg client")) {
                    delta.module.render.Interface_2 inter = delta.module.render.Interface_2.getInstance();
                    int tc1 = inter != null ? inter.getTitleColor1().c().intValue() : 0xFF8B5CF6;
                    int tc2 = inter != null ? inter.getTitleColor2().c().intValue() : 0xFF00CEC9;
                    int c1Anim = ColorUtil.a(tc1, anim);
                    int c2Anim = ColorUtil.a(tc2, anim);
                    float time = (float) (System.currentTimeMillis() % 4000L) / 4000.0f;
                    float cur = cursor;
                    for (int i = 0; i < brandStr.length(); i++) {
                        String s = String.valueOf(brandStr.charAt(i));
                        float progress = (float) i / Math.max(1, brandStr.length() - 1);
                        float wave = 0.5f + 0.5f * (float) Math.sin((progress * 2.2f) - (time * Math.PI * 2.0));
                        int color = ColorUtil.a(c1Anim, c2Anim, wave);
                        Fonts.e.a(event.h(), s, cur, brandTextY, brandFontSize, color);
                        cur += Fonts.e.a(s, brandFontSize);
                    }
                    cursor += Fonts.e.a(brandStr, brandFontSize);
                } else {
                    Fonts.e.a(event.h(), brandStr, cursor, brandTextY, brandFontSize, whiteColor);
                    cursor += Fonts.e.a(brandStr, brandFontSize);
                }
            }

            boolean isFirstItemWithoutBrandOrLogo = !row.hasBrand && !row.hasLogo;
            for (int i = 0; i < row.stats.size(); i++) {
                WatermarkItem item = row.stats.get(i);
                if (i == 0 && isFirstItemWithoutBrandOrLogo) {
                    cursor = renderItem(event, item, cursor, rowY, pillHeight, textY, fontSize, textColor, anim);
                } else {
                    cursor += dotGap;
                    Fonts.e.a(event.h(), "•", cursor, dotY, dotSize, dotColor);
                    cursor += Fonts.e.a("•", dotSize) + dotGap;
                    cursor = renderItem(event, item, cursor, rowY, pillHeight, textY, fontSize, textColor, anim);
                }
            }
        }

        super.a(event);
    }

    private static class WatermarkItem {
        final String text;
        final String iconGlyph;
        final Identifier customTexture;
        final int iconColor;

        WatermarkItem(String text, String iconGlyph, Identifier customTexture, int iconColor) {
            this.text = text;
            this.iconGlyph = iconGlyph;
            this.customTexture = customTexture;
            this.iconColor = iconColor;
        }
    }

    private static class WatermarkRow {
        boolean hasLogo;
        boolean hasBrand;
        String brandStr;
        final List<WatermarkItem> stats = new ArrayList<>();
        float width;
    }
}

package delta.ui.widget;

import static delta.core.Interface.aM_;

import delta.config.ThemeInfo;
import delta.core.Category;
import delta.core.Delta;
import delta.core.GlobalEvent;
import delta.core.Interface;
import delta.core.Module;
import delta.event.DrawEvent;
import delta.module.render.ArrayListModule;
import delta.render.ColorUtil;
import delta.render.EasingList;
import delta.render.Fonts;
import delta.setting.BooleanSetting;
import delta.setting.ColorSetting;
import delta.setting.ModeSetting;
import delta.setting.MultiModeSetting;
import delta.setting.SliderSetting;
import delta.ui.element.DragInfo;
import delta.util.MathUtil;
import net.minecraft.client.gui.screen.ChatScreen;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ArrayListWidget extends Widget implements Interface {
    private final Map<Module, Float> rowProgress = new IdentityHashMap<>();
    private final Map<Module, Float> rowAnimatedY = new IdentityHashMap<>();
    private float currentWidth = 100.0f;
    private float currentHeight = 20.0f;
    private boolean initializedPosition = false;

    public ArrayListWidget() {
        super(new DragInfo("Список модулей", 0.0f, 0.0f, 100.0f, 20.0f));
        j().a(this);
        ArrayListModule mod = ArrayListModule.getInstance();
        if (mod != null) {
            a(mod.getColorMode(), mod.getShowSideBar(), mod.getShowBackground());
        }
    }

    public ModeSetting getColorMode() {
        ArrayListModule mod = ArrayListModule.getInstance();
        return mod != null ? mod.getColorMode() : null;
    }

    public ColorSetting getColor1() {
        ArrayListModule mod = ArrayListModule.getInstance();
        return mod != null ? mod.getColor1() : null;
    }

    public ColorSetting getColor2() {
        ArrayListModule mod = ArrayListModule.getInstance();
        return mod != null ? mod.getColor2() : null;
    }

    public SliderSetting getWaveSpeed() {
        ArrayListModule mod = ArrayListModule.getInstance();
        return mod != null ? mod.getWaveSpeed() : null;
    }

    public SliderSetting getWaveFreq() {
        ArrayListModule mod = ArrayListModule.getInstance();
        return mod != null ? mod.getWaveFreq() : null;
    }

    public BooleanSetting getShowSideBar() {
        ArrayListModule mod = ArrayListModule.getInstance();
        return mod != null ? mod.getShowSideBar() : null;
    }

    public SliderSetting getSideBarWidth() {
        ArrayListModule mod = ArrayListModule.getInstance();
        return mod != null ? mod.getSideBarWidth() : null;
    }

    public BooleanSetting getShowBackground() {
        ArrayListModule mod = ArrayListModule.getInstance();
        return mod != null ? mod.getShowBackground() : null;
    }

    public SliderSetting getRowRounding() {
        ArrayListModule mod = ArrayListModule.getInstance();
        return mod != null ? mod.getRowRounding() : null;
    }

    public SliderSetting getRowSpacing() {
        ArrayListModule mod = ArrayListModule.getInstance();
        return mod != null ? mod.getRowSpacing() : null;
    }

    public SliderSetting getFontSize() {
        ArrayListModule mod = ArrayListModule.getInstance();
        return mod != null ? mod.getFontSize() : null;
    }

    private boolean isExcluded(Module module) {
        if (module == null) {
            return true;
        }
        String name = module.j();
        if (name.equalsIgnoreCase("Interface")
                || name.equalsIgnoreCase("ClickAction")
                || name.equalsIgnoreCase("ArrayList")
                || name.equalsIgnoreCase("Список модулей")) {
            return true;
        }

        ArrayListModule mod = ArrayListModule.getInstance();
        if (mod != null && mod.getCategoryFilter() != null) {
            Category cat = module.l();
            if (cat != null) {
                MultiModeSetting filter = mod.getCategoryFilter();
                BooleanSetting combat = filter.a("Combat");
                BooleanSetting movement = filter.a("Movement");
                BooleanSetting visual = filter.a("Visual");
                BooleanSetting player = filter.a("Player");
                BooleanSetting misc = filter.a("Misc");

                if (cat == Category.Combat && combat != null && !combat.c().booleanValue()) return true;
                if (cat == Category.Movement && movement != null && !movement.c().booleanValue()) return true;
                if (cat == Category.Render && visual != null && !visual.c().booleanValue()) return true;
                if (cat == Category.Player && player != null && !player.c().booleanValue()) return true;
                if (cat == Category.Misc && misc != null && !misc.c().booleanValue()) return true;
            }
        }
        return false;
    }

    @Override
    public void a(DrawEvent event) {
        ArrayListModule mod = ArrayListModule.getInstance();
        if (mod != null && !mod.m()) {
            super.a(event);
            return;
        }

        if (!this.initializedPosition && j().c() == 0.0f && j().d() == 0.0f) {
            float screenW = aM_.getWindow().getScaledWidth();
            j().a(Math.max(10.0f, screenW - 120.0f));
            j().b(5.0f);
            this.initializedPosition = true;
        }

        d().a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());
        float anim = a();
        if (anim <= 0.0f) {
            super.a(event);
            return;
        }

        // Update module animations
        for (Module module : Delta.h().d().t().e()) {
            boolean targetOn = module.m() && !isExcluded(module);
            float current = this.rowProgress.getOrDefault(module, 0.0f);
            float updated = MathUtil.c(current, targetOn ? 1.0f : 0.0f, 16.0f);
            if (updated > 0.0001f || targetOn) {
                this.rowProgress.put(module, updated);
            } else {
                this.rowProgress.remove(module);
            }
        }

        ModeSetting colorMode = mod != null ? mod.getColorMode() : null;
        ColorSetting color1 = mod != null ? mod.getColor1() : null;
        ColorSetting color2 = mod != null ? mod.getColor2() : null;
        SliderSetting waveSpeed = mod != null ? mod.getWaveSpeed() : null;
        SliderSetting waveFreq = mod != null ? mod.getWaveFreq() : null;
        BooleanSetting showSideBar = mod != null ? mod.getShowSideBar() : null;
        SliderSetting sideBarWidth = mod != null ? mod.getSideBarWidth() : null;
        BooleanSetting showBackground = mod != null ? mod.getShowBackground() : null;
        SliderSetting rowRounding = mod != null ? mod.getRowRounding() : null;
        SliderSetting rowSpacing = mod != null ? mod.getRowSpacing() : null;
        SliderSetting fontSize = mod != null ? mod.getFontSize() : null;

        float fSize = fontSize != null ? fontSize.h().floatValue() : 7.5f;
        List<Module> visible = new ArrayList<>();
        for (Map.Entry<Module, Float> entry : this.rowProgress.entrySet()) {
            if (entry.getValue() > 0.005f) {
                visible.add(entry.getKey());
            }
        }

        // Sort descending by text width
        visible.sort((m1, m2) -> Float.compare(Fonts.e.a(m2.j(), fSize), Fonts.e.a(m1.j(), fSize)));

        float widgetX = j().a();
        float widgetY = j().b();
        float screenW = aM_.getWindow().getScaledWidth();
        boolean rightAligned = (widgetX + this.currentWidth * 0.5f) >= screenW * 0.5f;

        float maxRowWidth = 40.0f;
        float totalHeight = 0.0f;
        float rowH = 13.0f;
        float spacing = rowSpacing != null ? rowSpacing.h().floatValue() : 2.0f;
        float barW = sideBarWidth != null ? sideBarWidth.h().floatValue() : 2.0f;
        boolean hasBar = showSideBar == null || showSideBar.c().booleanValue();

        for (Module m : visible) {
            float textW = Fonts.e.a(m.j(), fSize);
            float rWidth = textW + 12.0f + (hasBar ? barW + 3.0f : 0.0f);
            if (rWidth > maxRowWidth) {
                maxRowWidth = rWidth;
            }
            totalHeight += (rowH + spacing) * this.rowProgress.get(m);
        }

        this.currentWidth = Math.max(50.0f, maxRowWidth);
        this.currentHeight = Math.max(15.0f, totalHeight);
        j().c(this.currentWidth);
        j().d(this.currentHeight);

        float timeSec = (float) (System.currentTimeMillis() % 1000000L) / 1000.0f;
        float speed = waveSpeed != null ? waveSpeed.h().floatValue() : 2.5f;
        float freq = waveFreq != null ? waveFreq.h().floatValue() : 0.04f;
        float phase = timeSec * speed * 2.0f;
        float blurVal = getBlur();
        float opacityVal = getOpacity();
        float radius = rowRounding != null ? rowRounding.h().floatValue() : 4.0f;
        boolean hasBg = showBackground == null || showBackground.c().booleanValue();

        float currentY = widgetY;
        int rowIndex = 0;
        for (Module module : visible) {
            float p = this.rowProgress.get(module);
            float rowAnim = anim * p;
            if (rowAnim <= 0.005f) {
                continue;
            }

            float textW = Fonts.e.a(module.j(), fSize);
            float rowW = textW + 12.0f + (hasBar ? barW + 3.0f : 0.0f);

            float slide = (1.0f - p) * 20.0f;
            float targetRowX = rightAligned ? (widgetX + this.currentWidth - rowW) : widgetX;
            float drawX = rightAligned ? (targetRowX + slide) : (targetRowX - slide);

            float targetY = currentY;
            float animY = this.rowAnimatedY.getOrDefault(module, targetY);
            animY = MathUtil.c(animY, targetY, 15.0f);
            this.rowAnimatedY.put(module, animY);
            float drawY = animY;

            // Gradient wave calculation across rows
            float waveVal = 0.5f + 0.5f * (float) Math.sin((drawY * freq) + (rowIndex * 0.28f) - phase);
            waveVal = Math.max(0.0f, Math.min(1.0f, waveVal));

            int waveColor;
            if (colorMode != null && colorMode.l("Радуга")) {
                float hue = ((drawY * freq * 0.4f) + (rowIndex * 0.08f) - (timeSec * speed * 0.15f)) % 1.0f;
                if (hue < 0.0f) {
                    hue += 1.0f;
                }
                waveColor = Color.HSBtoRGB(hue, 0.85f, 1.0f) | 0xFF000000;
            } else if (colorMode != null && colorMode.l("Градиент")) {
                int c1 = color1 != null ? (color1.c().intValue() | 0xFF000000) : 0xFF8B5CF6;
                int c2 = color2 != null ? (color2.c().intValue() | 0xFF000000) : 0xFF00CEC9;
                waveColor = ColorUtil.a(c1, c2, waveVal);
            } else { // "Тема"
                int primary = Delta.h().d().o().a(ThemeInfo.PRIMARY).a() | 0xFF000000;
                int secondary = ColorUtil.a(primary, 0xFFFFFFFF, 0.35f) | 0xFF000000;
                waveColor = ColorUtil.a(primary, secondary, waveVal);
            }

            // 1. Frosted glass plate with blur and transparency (matching HUD style)
            if (hasBg) {
                int hudBg = getHudBackgroundColor();
                float baseA = ((hudBg >> 24) & 0xFF) / 255.0f;
                if (baseA <= 0.0f) baseA = 0.85f;
                int cardBg = ColorUtil.a(hudBg, (int) (opacityVal * baseA * 235.0f * rowAnim));
                int cardBorder = ColorUtil.a(255, 255, 255, (int) (0.12f * 255.0f * rowAnim));

                if (blurVal > 0.0f && !event.d().e().e().isEmpty()) {
                    event.d().b(event.h(), drawX, drawY, rowW, rowH, radius, cardBg, rowAnim);
                } else {
                    event.d().a(event.h(), drawX, drawY, rowW, rowH, radius, cardBg);
                }
                Widget.drawHudThemeGradient(event.d(), event.h(), drawX, drawY, rowW, rowH, radius, rowAnim);

                // Subtle inner wave glow
                event.d().a(event.h(), drawX, drawY, rowW, rowH, radius, ColorUtil.a(waveColor, 0.07f * rowAnim));

                // Outer border contour
                event.d().a(event.h(), drawX, drawY, rowW, rowH, radius, 1.0f, cardBorder);
            }

            // 2. Module Name text
            float textX = rightAligned ? (drawX + 6.0f) : (drawX + (hasBar ? barW + 5.0f : 6.0f));
            float textY = drawY + ((rowH - Fonts.e.a(fSize)) / 2.0f) - 0.5f;
            Fonts.e.a(event.h(), module.j(), textX, textY, fSize, ColorUtil.a(waveColor, rowAnim));

            // 3. Side accent bar
            if (hasBar) {
                float barX = rightAligned ? (drawX + rowW - barW - 1.5f) : (drawX + 1.5f);
                float barY = drawY + 2.0f;
                float barH = rowH - 4.0f;
                event.d().a(event.h(), barX, barY, barW, barH, 1.0f, ColorUtil.a(waveColor, rowAnim));
            }

            currentY += (rowH + spacing) * p;
            rowIndex++;
        }

        super.a(event);
    }

    @Override
    public void a(GlobalEvent event) {
        boolean visible = (aM_.currentScreen instanceof ChatScreen);
        for (Map.Entry<Module, Float> entry : this.rowProgress.entrySet()) {
            if (entry.getValue() > 0.01f) {
                visible = true;
                break;
            }
        }
        d().a(visible);
        super.a(event);
    }
}

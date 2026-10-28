package delta.ui.widget;

import delta.util.KeyUtil;
import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.core.Module;
import delta.render.EasingList;
import delta.render.Fonts;
import delta.render.ColorUtil;
import delta.util.MathUtil;

import delta.config.ThemeInfo;
import delta.core.GlobalEvent;
import delta.core.Interface;
import delta.event.DrawEvent;
import delta.ui.element.DragInfo;
import delta.ui.widget.Widget;

import net.minecraft.client.gui.screen.ChatScreen;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.List;

public class HotkeysWidget extends Widget implements Interface {
    public HotkeysWidget() {
        super(new DragInfo("Клавиши", 0.0f, 0.0f, 0.0f, 0.0f));
        j().a(this);
    }

    @Override
    public void a(DrawEvent event) {
        d().a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());
        float anim = a();
        if (anim <= 0.0f) {
            super.a(event);
            return;
        }

        float x = j().a();
        float y = j().b();
        float headerHeight = 17.5f;
        float rowHeight = 15.0f;
        float radius = getRounding();

        // Collect active bound modules
        List<Module> activeModules = new ArrayList<>();
        for (Module module : Delta.h().d().t().e()) {
            module.f().a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());
            if (module.p() != -1 && module.f().c() > 0.0f) {
                activeModules.add(module);
            }
        }

        // Calculate card dimensions
        float titleW = Fonts.e.a("Бинды", 8.5f);
        float iconW = Fonts.a.a("Q", 8.5f);
        float minRequiredWidth = 8.0f + titleW + 20.0f + iconW + 8.0f;

        float maxRowWidth = minRequiredWidth;
        for (Module m : activeModules) {
            String keyStr = KeyUtil.b(m.p());
            float w = 8.0f + Fonts.e.a(m.j(), 7.5f) + 16.0f + Fonts.e.a(keyStr, 7.5f) + 8.0f;
            if (w > maxRowWidth) {
                maxRowWidth = w;
            }
        }

        float targetWidth = Math.max(120.0f, maxRowWidth);
        float cardWidth = MathUtil.c(j().f() > 0.0f ? j().f() : targetWidth, targetWidth, 0.35f);
        j().c(cardWidth);

        boolean hasRows = !activeModules.isEmpty();
        float totalHeight = headerHeight + (hasRows ? 1.0f + (activeModules.size() * rowHeight) + 3.0f : 0.0f);
        j().d(totalHeight);

        float blurVal = getBlur();
        float opacityVal = getOpacity();

        // Rockstar card styling
        int primaryColor = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
        int accentColor = ColorUtil.a(primaryColor, anim);
        int hudBg = getHudBackgroundColor();
        float baseA = ((hudBg >> 24) & 0xFF) / 255.0f;
        if (baseA <= 0.0f) baseA = 0.85f;
        int cardBg = ColorUtil.a(hudBg, (int) (opacityVal * baseA * 255.0f * anim));
        int cardBorder = ColorUtil.a(255, 255, 255, (int) (0.12f * 255.0f * anim));
        int dividerColor = ColorUtil.a(255, 255, 255, (int) (0.10f * 255.0f * anim));
        int titleColor = ColorUtil.a(255, 255, 255, (int) (1.0f * 255.0f * anim));

        // 1. Frosted base card
        if (blurVal > 0.0f && !event.d().e().e().isEmpty()) {
            event.d().b(event.h(), x, y, cardWidth, totalHeight, radius, cardBg, anim);
        } else {
            event.d().a(event.h(), x, y, cardWidth, totalHeight, radius, cardBg);
        }

        // HUD theme gradient
        Widget.drawHudThemeGradient(event.d(), event.h(), x, y, cardWidth, totalHeight, radius, anim);

        // 2. Outer card contour border
        event.d().a(event.h(), x, y, cardWidth, totalHeight, radius, 1.0f, cardBorder);

        // Header: "Бинды" on left, link icon on right
        float titleY = y + ((headerHeight - Fonts.e.a(8.5f)) / 2.0f) - 0.5f;
        float iconY = y + ((headerHeight - Fonts.a.a(8.5f)) / 2.0f) - 0.2f;
        Fonts.e.a(event.h(), "Бинды", x + 8.0f, titleY, 8.5f, titleColor);
        Fonts.a.a(event.h(), "Q", x + cardWidth - 8.0f - iconW, iconY, 8.5f, accentColor);

        // Body rows: Active binds
        if (hasRows) {
            float rowY = y + headerHeight + 2.5f;
            for (Module module : activeModules) {
                float moduleAnim = module.f().c() * anim;
                if (moduleAnim <= 0.0f) {
                    continue;
                }

                int rowTextColor = ColorUtil.a(235, 238, 248, (int) (0.92f * 255.0f * moduleAnim));
                int rowKeyColor = ColorUtil.a(primaryColor, moduleAnim);

                String keyStr = KeyUtil.b(module.p());
                float keyW = Fonts.e.a(keyStr, 7.5f);
                float textY = rowY + ((rowHeight - Fonts.e.a(7.5f)) / 2.0f) - 0.5f;

                // Module name on left
                Fonts.e.a(event.h(), module.j(), x + 8.0f, textY, 7.5f, rowTextColor);
                // Key bind on right in accent color
                Fonts.e.a(event.h(), keyStr, x + cardWidth - 8.0f - keyW, textY, 7.5f, rowKeyColor);

                rowY += rowHeight;
            }
        }

        super.a(event);
    }

    @Override
    public void a(GlobalEvent event) {
        boolean visible = aM_.currentScreen instanceof ChatScreen;
        for (Module module : Delta.h().d().t().e()) {
            if (module.p() != -1 && module.f().c() > 0.0f) {
                visible = true;
                break;
            }
        }
        d().a(visible);
        super.a(event);
    }
}

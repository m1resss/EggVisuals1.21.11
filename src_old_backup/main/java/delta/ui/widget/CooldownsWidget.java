package delta.ui.widget;

import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.core.GlobalEvent;
import delta.core.Interface;
import delta.event.DrawEvent;
import delta.mixin.IItemCooldownManager;
import delta.render.AnimationUtil;
import delta.render.ColorUtil;
import delta.render.EasingList;
import delta.render.Fonts;
import delta.util.InventoryUtil;
import delta.util.MathUtil;
import delta.config.ThemeInfo;
import delta.ui.element.DragInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.registry.Registries;
import org.joml.Vector4f;
import platform.inject.accessors.ItemCooldownEntryAccessor;
import platform.inject.accessors.ItemCooldownManagerAccessor;

public class CooldownsWidget extends Widget implements Interface {
    public CooldownsWidget() {
        super(new DragInfo("Задержки", 0.0f, 0.0f, 0.0f, 0.0f));
        j().a(this);
    }

    private static class CooldownItem {
        final Item item;
        final float remainingSeconds;
        final float anim;

        CooldownItem(Item item, float remainingSeconds, float anim) {
            this.item = item;
            this.remainingSeconds = remainingSeconds;
            this.anim = anim;
        }
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

        ItemCooldownManagerAccessor accessor = (ItemCooldownManagerAccessor) (Object) aM_.player.getItemCooldownManager();
        List<CooldownItem> activeCooldowns = new ArrayList<>();

        for (Map.Entry<Identifier, Object> entry : accessor.getEntries().entrySet()) {
            IItemCooldownManager cooldown = (IItemCooldownManager) entry.getValue();
            ItemCooldownEntryAccessor end = (ItemCooldownEntryAccessor) entry.getValue();
            AnimationUtil animationUtil = cooldown.getAnimation();
            animationUtil.a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());

            float itemAnim = animationUtil.c() * anim;
            if (itemAnim > 0.0f) {
                Item item = Registries.ITEM.get(entry.getKey());
                int remainingTicks = Math.max(end.getEndTick() - accessor.getTick(), 0);
                activeCooldowns.add(new CooldownItem(item, remainingTicks / 20.0f, itemAnim));
            }
        }

        float titleW = Fonts.e.a("Задержки", 8.5f);
        float iconW = Fonts.a.a("d", 8.5f);
        float minRequiredWidth = 8.0f + titleW + 20.0f + iconW + 8.0f;

        float maxRowWidth = minRequiredWidth;
        for (CooldownItem ci : activeCooldowns) {
            String timeStr = String.format(Locale.ROOT, "%.1fс", Float.valueOf(ci.remainingSeconds));
            float w = 8.0f + 14.0f + 4.0f + Fonts.e.a(ci.item.getName().getString(), 7.5f) + 16.0f + Fonts.e.a(timeStr, 7.5f) + 8.0f;
            if (w > maxRowWidth) {
                maxRowWidth = w;
            }
        }

        float targetWidth = Math.max(125.0f, maxRowWidth);
        float cardWidth = MathUtil.c(j().f() > 0.0f ? j().f() : targetWidth, targetWidth, 0.35f);
        j().c(cardWidth);

        boolean hasRows = !activeCooldowns.isEmpty();
        float totalHeight = headerHeight + (hasRows ? 1.0f + (activeCooldowns.size() * rowHeight) + 3.0f : 0.0f);
        j().d(totalHeight);

        float blurVal = getBlur();
        float opacityVal = getOpacity();

        // Styling colors
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

        // Header: "Задержки" on left, clock/timer icon "d" on right
        float titleY = y + ((headerHeight - Fonts.e.a(8.5f)) / 2.0f) - 0.5f;
        float iconY = y + ((headerHeight - Fonts.a.a(8.5f)) / 2.0f) - 0.2f;
        Fonts.e.a(event.h(), "Задержки", x + 8.0f, titleY, 8.5f, titleColor);
        Fonts.a.a(event.h(), "d", x + cardWidth - 8.0f - iconW, iconY, 8.5f, accentColor);

        // Body rows: Active cooldowns
        if (hasRows) {
            float rowY = y + headerHeight + 2.5f;
            for (CooldownItem ci : activeCooldowns) {
                if (ci.anim <= 0.0f) {
                    continue;
                }

                String name = ci.item.getName().getString();
                String timeStr = String.format(Locale.ROOT, "%.1fс", Float.valueOf(ci.remainingSeconds));
                float timeW = Fonts.e.a(timeStr, 7.5f);
                float textY = rowY + ((rowHeight - Fonts.e.a(7.5f)) / 2.0f) - 0.5f;

                int nameColor = ColorUtil.a(235, 238, 248, (int) (0.92f * 255.0f * ci.anim));
                int timeColor = ColorUtil.a(primaryColor, ci.anim);

                // Render item icon
                event.e().a(event.i(), ci.item.getDefaultStack(), x + 8.0f, rowY + 1.0f, 0, ci.anim, 0.45f, false);

                // Item name
                Fonts.e.a(event.h(), name, x + 8.0f + 14.0f, textY, 7.5f, nameColor);

                // Remaining time
                Fonts.e.a(event.h(), timeStr, x + cardWidth - 8.0f - timeW, textY, 7.5f, timeColor);

                rowY += rowHeight;
            }
        }

        super.a(event);
    }

    @Override
    public void a(GlobalEvent event) {
        boolean visible = aM_.currentScreen instanceof ChatScreen;
        ItemCooldownManagerAccessor accessor = (ItemCooldownManagerAccessor) (Object) aM_.player.getItemCooldownManager();
        int tick = accessor.getTick();
        for (Map.Entry<Identifier, Object> entry : accessor.getEntries().entrySet()) {
            IItemCooldownManager cooldown = (IItemCooldownManager) entry.getValue();
            ItemCooldownEntryAccessor end = (ItemCooldownEntryAccessor) entry.getValue();
            boolean has = InventoryUtil.b(Registries.ITEM.get(entry.getKey())) != -1;
            cooldown.getAnimation().a(has && end.getEndTick() - 5 > tick);
            if (has && cooldown.getAnimation().c() > 0.0d) {
                visible = true;
            }
        }
        d().a(visible);
        super.a(event);
    }
}

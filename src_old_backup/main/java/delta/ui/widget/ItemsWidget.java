package delta.ui.widget;

import delta.util.KeyUtil;
import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.core.InterfaceC0020Opcode;
import delta.render.EasingList;
import delta.render.Fonts;
import delta.render.ColorUtil;
import delta.util.InventoryUtil;
import delta.util.MathUtil;

import delta.core.GlobalEvent;
import delta.core.Interface;
import delta.event.DrawEvent;
import delta.module.misc.ServerAssistant;
import delta.ui.element.DragInfo;
import delta.ui.widget.Widget;

import java.util.List;
import java.util.Locale;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.registry.Registries;
import platform.inject.accessors.ItemCooldownManagerAccessor;

public class ItemsWidget extends Widget implements Interface {
    public ItemsWidget() {
        super(new DragInfo("Предметы", 0.0f, 0.0f, 0.0f, 0.0f));
        j().a(this);
    }

    @Override
    public void a(DrawEvent event) {
        d().a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());
        ServerAssistant assistant = Delta.h().d().t().aj();
        List<ServerAssistant.b> providers = assistant.q();
        boolean active = false;
        for (ServerAssistant.b provider : providers) {
            provider.a().a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());
            active |= provider.a().c() > 0.0f;
        }
        boolean example = !active && a() > 0.0f;
        float pillH = 17.0f;
        float y = j().b();
        float x = j().a();
        float contentX = x;
        float blurVal = getBlur();
        float opacityVal = getOpacity();
        float radius = Math.min(pillH * 0.5f, getRounding());
        int hudBg = getHudBackgroundColor();
        float baseA = ((hudBg >> 24) & 0xFF) / 255.0f;
        if (baseA <= 0.0f) baseA = 0.85f;

        ItemCooldownManagerAccessor accessor = (ItemCooldownManagerAccessor) (ItemCooldownManagerAccessor) aM_.player.getItemCooldownManager();
        int i = 0;
        while (i < providers.size()) {
            ServerAssistant.b provider2 = providers.get(i);
            float animation = example ? i < 3 ? a() : 0.0f : provider2.a().c() * a();
            if (animation > 0.0f) {
                Identifier itemId = Registries.ITEM.getId(provider2.c());
                Object entry = accessor.getEntries().get(itemId);
                int remaining = entry != null ? Math.max(((platform.inject.accessors.ItemCooldownEntryAccessor) entry).getEndTick() - accessor.getTick(), 0) : 0;
                String label;
                if (remaining > 0) {
                    label = String.format(Locale.US, "%.1f", Float.valueOf(remaining / 20.0f));
                } else {
                    label = KeyUtil.b(provider2.b().c().intValue());
                    if (example && (label == null || label.equals("N/A"))) {
                        label = i == 0 ? "X" : (i == 1 ? "M2" : "F");
                    }
                }
                float badgeW = Math.max(13.0f, Fonts.e.a(label, 6.5f) + 6.0f);
                float badgeH = 11.0f;
                float badgeX = contentX + 16.5f;
                float badgeY = y + ((pillH - badgeH) / 2.0f);
                float pillW = 16.5f + badgeW + 3.5f;

                // Pill background bound to HUD settings (Blur / Liquid Glass / Color / Opacity / Rounding)
                int pillBg = ColorUtil.a(hudBg, (int) (opacityVal * baseA * 255.0f * animation));
                int pillBorder = ColorUtil.a(255, 255, 255, (int) (0.15f * 255.0f * animation));

                if (blurVal > 0.0f && !event.d().e().e().isEmpty()) {
                    event.d().b(event.h(), contentX, y, pillW, pillH, radius, pillBg, animation);
                } else {
                    event.d().a(event.h(), contentX, y, pillW, pillH, radius, pillBg);
                }
                Widget.drawHudThemeGradient(event.d(), event.h(), contentX, y, pillW, pillH, radius, animation);
                event.d().a(event.h(), contentX, y, pillW, pillH, radius, 0.75f, pillBorder);

                // Render item icon centered on the left
                Delta.h().d().j().a(event.i(), provider2.c().getDefaultStack(), contentX + 2.5f, y + ((pillH - 16.0f * 0.72f) / 2.0f) + 1.0f, InterfaceC0020Opcode.aN, animation, 0.72f, false);

                // Sleek key badge
                int badgeBg = remaining > 0 ? ColorUtil.a(230, 60, 60, (int) (65.0f * animation)) : ColorUtil.a(255, 255, 255, (int) (22.0f * animation));
                int badgeBorder = remaining > 0 ? ColorUtil.a(255, 90, 90, (int) (110.0f * animation)) : ColorUtil.a(255, 255, 255, (int) (45.0f * animation));
                event.d().a(event.h(), badgeX, badgeY, badgeW, badgeH, 3.0f, badgeBg);
                event.d().a(event.h(), badgeX, badgeY, badgeW, badgeH, 3.0f, 0.75f, badgeBorder);

                int textColor = remaining > 0 ? ColorUtil.a(255, 130, 130, (int) (255.0f * animation)) : ColorUtil.a(255, 255, 255, (int) (255.0f * animation));
                Fonts.e.b(event.h(), label, badgeX + (badgeW / 2.0f), badgeY + 1.5f, 6.5f, textColor);

                contentX += (pillW + 3.5f) * animation;
            }
            i++;
        }
        j().c(MathUtil.c(j().f(), Math.max(0.0f, (contentX - x) - 3.5f), 0.5f));
        j().d((active || example) ? pillH : 0.0f);
        super.a(event);
    }

    @Override
    public void a(GlobalEvent event) {
        ServerAssistant assistant = Delta.h().d().t().aj();
        boolean visible = aM_.currentScreen instanceof ChatScreen;
        for (ServerAssistant.b provider : assistant.q()) {
            provider.a().a(assistant.m() && provider.b().c().intValue() != -1 && provider.b().e().get().booleanValue() && InventoryUtil.b(provider.c()) != -1);
            if (provider.a().c() > 0.0f) {
                visible = true;
            }
        }
        d().a(visible);
        super.a(event);
    }
}

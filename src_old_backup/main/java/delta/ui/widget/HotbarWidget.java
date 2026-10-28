package delta.ui.widget;

import static delta.core.Interface.aM_;

import delta.config.ThemeInfo;
import delta.core.Delta;
import delta.core.GlobalEvent;
import delta.core.Interface;
import delta.event.DrawEvent;
import delta.render.ColorUtil;
import delta.render.EasingList;
import delta.render.Fonts;
import delta.setting.BooleanSetting;
import delta.ui.element.DragInfo;
import net.minecraft.item.ItemStack;

public class HotbarWidget extends Widget implements Interface {
    private final BooleanSetting f;
    private final BooleanSetting g;
    private final BooleanSetting h;
    private final BooleanSetting i;
    private float j;

    public HotbarWidget() {
        super(new DragInfo("Хотбар", 0.0f, 0.0f, 216.0f, 24.0f));
        this.f = new BooleanSetting("Показывать вторую руку", true);
        this.g = new BooleanSetting("Показывать номера слотов", true);
        this.h = new BooleanSetting("Показывать опыт", true);
        this.i = new BooleanSetting("Показывать количество", true);
        j().a(this);
        j().a(2);
        a(this.f, this.g, this.h, this.i);
    }

    @Override
    public void a(DrawEvent event) {
        d().a(0.0f, 1.0f, 0.25f, EasingList.g, event.g());
        if (aM_.player == null || aM_.options.hudHidden || aM_.player.isSpectator()) {
            super.a(event);
            return;
        }
        float width = 216.0f;
        float height = 24.0f;
        float x = (aM_.getWindow().getScaledWidth() - width) / 2.0f;
        float y = aM_.getWindow().getScaledHeight() - 28.0f;
        j().a(x);
        j().b(y);
        j().c(width);
        j().d(height);
        a(event, x, y, width, height, true, a());
        this.j += (((float) aM_.player.getInventory().getSelectedSlot()) - this.j) * 0.70f;
        for (int slot = 0; slot < 9; slot++) {
            float slotX = x + (slot * 24.0f);
            boolean selected = Math.round(this.j) == slot;
            int background = selected ? ColorUtil.a(Delta.h().d().o().a(ThemeInfo.PRIMARY).a(), (int) (170.0f * a())) : ColorUtil.a(255, 255, 255, (int) (14.0f * a()));
            event.d().a(event.h(), slotX + 0.5f, y + 0.5f, 23.0f, 23.0f, slot == 0 || slot == 8 ? 3.5f : 0.0f, background);
            ItemStack stack = aM_.player.getInventory().getMainStacks().get(slot);
            event.e().a(event.i(), stack, slotX + 4.0f, y + 4.0f, 40, a(), 0.75f, this.i.c().booleanValue());
            if (this.g.c().booleanValue()) {
                int slotColor = ColorUtil.a(255, 255, 255, (int) ((selected ? 255.0f : 175.0f) * a()));
                Fonts.e.a(event.h(), String.valueOf(slot + 1), slotX + 2.5f, y + 2.0f, 6.5f, slotColor);
            }
            delta.module.misc.ServerAssistant sa = Delta.h().d().t().aj();
            if (sa != null && sa.m()) {
                String bind = sa.getBindNameForItem(stack);
                if (bind != null && !bind.equals("N/A")) {
                    float bindW = Fonts.e.a(bind, 6.0f);
                    float bw = bindW + 4.0f;
                    float bh = 8.0f;
                    float bx = slotX + 23.0f - bw - 1.0f;
                    float by = y + 1.0f;
                    event.d().a(event.h(), bx, by, bw, bh, 2.0f, ColorUtil.a(16, 18, 26, (int) (220.0f * a())));
                    event.d().a(event.h(), bx, by, bw, bh, 2.0f, 0.75f, ColorUtil.a(255, 255, 255, (int) (45.0f * a())));
                    Fonts.e.b(event.h(), bind, bx + (bw / 2.0f), by + 1.0f, 6.0f, ColorUtil.a(255, 255, 255, (int) (255.0f * a())));
                }
            }
        }
        if (this.f.c().booleanValue() && !aM_.player.getOffHandStack().isEmpty()) {
            float offhandX = x - 36.0f;
            a(event, offhandX, y, 24.0f, 24.0f, true, a());
            event.e().a(event.i(), aM_.player.getOffHandStack(), offhandX + 4.0f, y + 4.0f, 40, a(), 0.75f, this.i.c().booleanValue());
        }
        if (this.h.c().booleanValue() && aM_.player.experienceLevel > 0) {
            String level = String.valueOf(aM_.player.experienceLevel);
            Fonts.e.b(event.h(), level, x + (width / 2.0f), y - 9.0f, 7.0f, ColorUtil.a(100, 230, 120, (int) (255.0f * a())));
        }
        super.a(event);
    }

    @Override
    public void a(GlobalEvent event) {
        d().a(aM_.player != null && !aM_.player.isSpectator());
        super.a(event);
    }
}

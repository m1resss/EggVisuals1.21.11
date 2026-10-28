package delta.ui.widget;

import delta.staff.StaffConstructor;
import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.render.EasingList;
import delta.render.Fonts;
import delta.render.ColorUtil;
import delta.util.MathUtil;

import delta.config.ThemeInfo;
import delta.core.GlobalEvent;
import delta.core.Interface;
import delta.event.DrawEvent;
import delta.render.AnimationUtil;
import delta.ui.element.DragInfo;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.List;

public class StaffWidget extends Widget implements Interface {
    public StaffWidget() {
        super(new DragInfo("Стафф", 0.0f, 0.0f, 0.0f, 0.0f));
        j().a(this);
    }

    private static class StaffRow {
        final StaffConstructor staff;
        final float anim;
        final boolean near;

        StaffRow(StaffConstructor staff, float anim, boolean near) {
            this.staff = staff;
            this.anim = anim;
            this.near = near;
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

        List<StaffRow> activeStaff = new ArrayList<>();
        for (StaffConstructor staff : Delta.h().d().f().a()) {
            AnimationUtil animationUtil = staff.b();
            animationUtil.a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());
            float staffAnim = animationUtil.c() * anim;
            if (staffAnim > 0.0f) {
                activeStaff.add(new StaffRow(staff, staffAnim, isPlayerNear(staff.a())));
            }
        }

        float titleW = Fonts.e.a("Стафф", 8.5f);
        float iconW = Fonts.a.a("y", 8.5f);
        float minRequiredWidth = 8.0f + titleW + 20.0f + iconW + 8.0f;

        float maxRowWidth = minRequiredWidth;
        for (StaffRow sr : activeStaff) {
            String status = sr.near ? "Near" : "Online";
            float w = 8.0f + 14.0f + 4.0f + Fonts.e.a(sr.staff.a(), 7.5f) + 16.0f + Fonts.e.a(status, 7.5f) + 8.0f;
            if (w > maxRowWidth) {
                maxRowWidth = w;
            }
        }

        float targetWidth = Math.max(125.0f, maxRowWidth);
        float cardWidth = MathUtil.c(j().f() > 0.0f ? j().f() : targetWidth, targetWidth, 0.35f);
        j().c(cardWidth);

        boolean hasRows = !activeStaff.isEmpty();
        float totalHeight = headerHeight + (hasRows ? 1.0f + (activeStaff.size() * rowHeight) + 3.0f : 0.0f);
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

        // Header: "Стафф" on left, shield icon "y" on right
        float titleY = y + ((headerHeight - Fonts.e.a(8.5f)) / 2.0f) - 0.5f;
        float iconY = y + ((headerHeight - Fonts.a.a(8.5f)) / 2.0f) - 0.2f;
        Fonts.e.a(event.h(), "Стафф", x + 8.0f, titleY, 8.5f, titleColor);
        Fonts.a.a(event.h(), "y", x + cardWidth - 8.0f - iconW, iconY, 8.5f, accentColor);

        // Body rows: Active staff members
        if (hasRows) {
            float rowY = y + headerHeight + 2.5f;
            for (StaffRow sr : activeStaff) {
                if (sr.anim <= 0.0f) {
                    continue;
                }

                String status = sr.near ? "Near" : "Online";
                float statusW = Fonts.e.a(status, 7.5f);
                float textY = rowY + ((rowHeight - Fonts.e.a(7.5f)) / 2.0f) - 0.5f;

                int rowTextColor = ColorUtil.a(235, 238, 248, (int) (0.92f * 255.0f * sr.anim));
                int statusColor = ColorUtil.a(sr.near ? -1529792 : -9711765, sr.anim);

                PlayerListEntry entry = (aM_.getNetworkHandler() == null) ? null : aM_.getNetworkHandler().getPlayerList().stream().filter(e -> {
                    return e.getProfile().name().equalsIgnoreCase(sr.staff.a());
                }).findFirst().orElse(null);

                if (entry != null) {
                    event.d().a(event.h(), x + 8.0f, rowY + 3.0f, 8.0f, 8.0f, 2.0f, ColorUtil.a(-1, sr.anim), 0.125f, 0.125f, 0.125f, 0.125f, delta.render.RenderStateHelper.getTextureId(entry.getSkinTextures().body().id()));
                } else {
                    Fonts.a.a(event.h(), "y", x + 8.0f, rowY + ((rowHeight - Fonts.a.a(8.0f)) / 2.0f), 8.0f, ColorUtil.a(primaryColor, sr.anim));
                }

                // Staff name
                Fonts.e.a(event.h(), sr.staff.a(), x + 8.0f + 14.0f, textY, 7.5f, rowTextColor);

                // Status
                Fonts.e.a(event.h(), status, x + cardWidth - 8.0f - statusW, textY, 7.5f, statusColor);

                rowY += rowHeight;
            }
        }

        super.a(event);
    }

    @Override
    public void a(GlobalEvent event) {
        boolean visible = aM_.currentScreen instanceof ChatScreen;
        for (StaffConstructor staff : Delta.h().d().f().a()) {
            staff.b().a((aM_.getNetworkHandler() != null && aM_.getNetworkHandler().getPlayerList().stream().anyMatch(e -> {
                return e.getProfile().name().equalsIgnoreCase(staff.a());
            })) || isPlayerNear(staff.a()));
            if (staff.b().c() > 0.0f) {
                visible = true;
            }
        }
        d().a(visible);
        super.a(event);
    }

    private boolean isPlayerNear(String name) {
        return aM_.world != null && aM_.world.getPlayers().stream().anyMatch(playerEntity -> {
            return playerEntity.getName().getString().equalsIgnoreCase(name);
        });
    }
}

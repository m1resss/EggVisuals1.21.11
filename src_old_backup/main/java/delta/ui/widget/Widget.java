package delta.ui.widget;



import static delta.core.Interface.aM_;

import delta.core.Delta;

import delta.core.Interface;

import delta.core.InterfaceC0020Opcode;

import delta.render.EasingList;

import delta.render.Fonts;

import delta.render.ColorUtil;



import delta.config.ThemeInfo;

import delta.config.ThemeProcessor;

import delta.core.GlobalEvent;

import delta.event.BackendEvent;

import delta.event.DrawEvent;

import delta.event.PacketEvent;

import delta.ui.element.Element_2;



import delta.render.AnimationUtil;

import delta.ui.element.DragInfo;

import delta.setting.Setting;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

import lombok.Generated;

import net.minecraft.item.ItemStack;

import net.minecraft.text.Text;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.math.MatrixStack;



public class Widget {

    private final List<Setting<?>> f = new ObjectArrayList();

    private final List<Element_2<?>> g = new ObjectArrayList();

    protected final AnimationUtil a = new AnimationUtil();

    protected final AnimationUtil b = new AnimationUtil();

    protected final AnimationUtil c = new AnimationUtil();

    private boolean h = false;

    protected float d = 15.0f;

    protected float e = 8.5f;

    private final DragInfo i;



    @Generated

    public List<Setting<?>> b() {

        return this.f;

    }



    @Generated

    public List<Element_2<?>> c() {

        return this.g;

    }



    @Generated

    public AnimationUtil d() {

        return this.a;

    }



    @Generated

    public AnimationUtil e() {

        return this.b;

    }



    @Generated

    public AnimationUtil f() {

        return this.c;

    }



    @Generated

    public void a(boolean status) {

        this.h = status;

    }



    @Generated

    public boolean g() {

        return this.h;

    }



    @Generated

    public float h() {

        return this.d;

    }



    @Generated

    public float i() {

        return this.e;

    }



    @Generated

    public DragInfo j() {

        return this.i;

    }



    public Widget(DragInfo dragInfo) {

        this.i = dragInfo;

        dragInfo.a(this);

    }



    protected final void a(Setting<?>... settings) {

        for (Setting<?> setting : settings) {

            this.f.add(setting);

            this.g.add(setting.d());

        }

    }



    public void a(DrawEvent event) {

        e().a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());

        this.c.a(this.h && (Interface.aM_.currentScreen instanceof ChatScreen));

        this.c.a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());

        if (this.c.c() > 0.0f) {

            b(event);

        }

    }



    public void a(GlobalEvent event) {

        e().a(this.i == Delta.h().d().s().g());

    }



    public void a(PacketEvent event) {

    }



    public void a(BackendEvent event) {

    }



    protected void b(DrawEvent event) {
        List<Element_2<?>> visible = this.g.stream().filter(e -> {
            return e.e().e().get().booleanValue();
        }).toList();
        if (!visible.isEmpty()) {
            float rowHeight = 15.0f;
            float padH = 8.0f;
            float padV = 5.0f;
            float rowGap = 2.0f;

            float maxTextW = 0.0f;
            for (Element_2<?> el : visible) {
                float tw = Fonts.e.a(el.e().i(), 7.5f);
                if (el instanceof delta.ui.element.ModeElement me) {
                    tw += Fonts.e.a(me.e().c(), 7.0f) + 16.0f;
                }
                if (tw > maxTextW) {
                    maxTextW = tw;
                }
            }
            float panelWidth = Math.max(130.0f, padH * 2.0f + maxTextW + 36.0f);
            float totalHeight = padV * 2.0f + (rowHeight * visible.size()) + (visible.size() - 1) * rowGap;
            float anim = this.c.c() * a();

            float baseX = (this.i.b() - totalHeight) - 4.0f >= 0.0f ? (this.i.a() + (this.i.f() / 2.0f)) - (panelWidth / 2.0f) : this.i.a() + this.i.f() + 4.0f;
            float baseY = (this.i.b() - totalHeight) - 4.0f >= 0.0f ? (this.i.b() - totalHeight) - 4.0f : this.i.b();
            float baseX2 = Math.min(Math.max(baseX, 4.0f), (Interface.aM_.getWindow().getScaledWidth() - panelWidth) - 4.0f);
            float baseY2 = Math.min(Math.max(baseY, 4.0f), Interface.aM_.getWindow().getScaledHeight() - totalHeight - 4.0f);

            float radius = 7.0f;
            int cardBg = ColorUtil.a(16, 18, 24, (int) (235 * anim));
            int cardBorder = ColorUtil.a(255, 255, 255, (int) (22 * anim));

            if (!event.d().e().e().isEmpty()) {
                event.d().b(event.h(), baseX2, baseY2, panelWidth, totalHeight, radius, cardBg, anim);
            } else {
                event.d().a(event.h(), baseX2, baseY2, panelWidth, totalHeight, radius, cardBg);
            }
            drawHudThemeGradient(event.d(), event.h(), baseX2, baseY2, panelWidth, totalHeight, radius, anim);
            event.d().a(event.h(), baseX2, baseY2, panelWidth, totalHeight, radius, 1.0f, cardBorder);

            float y = baseY2 + padV;
            for (Element_2<?> element : visible) {
                element.d().set(baseX2, y, panelWidth, rowHeight);
                element.a(event, baseX2 + padH, y, panelWidth - (padH * 2.0f), anim);
                y += rowHeight + rowGap;
                if (element != visible.getLast()) {
                    event.d().a(event.h(), baseX2 + padH * 0.5f, y - (rowGap * 0.5f) - 0.25f, panelWidth - padH, 0.5f, 0.0f, ColorUtil.a(255, 255, 255, (int) (12 * anim)));
                }
            }
        }
    }



    protected void a(DrawEvent event, String icon, Object title, float width, float animation) {

        a(event, icon, title, width, animation, Delta.h().d().o().a(ThemeInfo.PRIMARY).a());

    }



    protected void a(DrawEvent event, String icon, Object title, float width, float animation, int iconColor) {

        a(event, this.i.a(), this.i.b(), icon, title, width, animation, iconColor);

    }



    protected void a(DrawEvent event, float x, float y, String icon, Object title, float width, float animation, int iconColor) {

        a(event, x, y, icon, null, title, width, animation, iconColor);

    }



    protected void a(DrawEvent event, float x, float y, ItemStack icon, Object title, float width, float animation, int iconColor) {

        a(event, x, y, null, icon, title, width, animation, iconColor);

    }



    private void a(DrawEvent event, float x, float y, String icon, ItemStack stack, Object title, float width, float animation, int iconColor) {

        if (animation > 0.0f) {

            float iconSize = this.e + 1.0f;

            a(event, x, y, width, this.d, true, animation);

            if (stack != null) {

                Delta.h().d().j().a(event.i(), stack, x + 3.0f, (y + ((this.d - 8.0f) / 2.0f)) - 0.25f, 0, animation, 0.5f, false);

            } else {

                Fonts.a.a(event.h(), icon, x + 3.0f, y + ((this.d - Fonts.a.a(iconSize)) / 2.0f), iconSize, ColorUtil.a(iconColor, animation));

            }

            a(event, x + 13.5f, y, this.d, animation);

            if (title instanceof Text) {

                Text text = (Text) title;

                Fonts.e.a(event.h(), text, x + 17.5f, (y + ((this.d - Fonts.e.a(this.e)) / 2.0f)) - 0.5f, this.e, animation);

            } else {

                Fonts.e.a(event.h(), String.valueOf(title), x + 17.5f, (y + ((this.d - Fonts.e.a(this.e)) / 2.0f)) - 0.5f, this.e, ColorUtil.a(-1, animation));

            }

        }

    }



    public int getHudBackgroundColor() {

        try {

            delta.module.render.Interface_2 iface = Delta.h().d().t().bd();

            if (iface != null && iface.getHudBgColor() != null) {

                return iface.getHudBgColor().c().intValue();

            }

        } catch (Exception ignored) {}

        return ColorUtil.a(24, 26, 36, 215);

    }



    protected void a(DrawEvent event, float x, float y, float width, float height, boolean glow, float animation) {

        if (animation > 0.0f) {

            int hudBg = getHudBackgroundColor();

            float baseA = ((hudBg >> 24) & 0xFF) / 255.0f;

            if (baseA <= 0.0f) baseA = 0.85f;

            float alpha = baseA * getOpacity() * animation;

            int background = hudBg;

            float r = getRounding();

            if (glow) {

                event.d().a(event.h(), x, y, width, height, r + (1.0f * this.b.c()), ColorUtil.a(background, alpha), animation, ColorUtil.a(background, alpha), r + 3.0f + (2.0f * this.b.c()));

            } else {

                event.d().b(event.h(), x, y, width, height, r, ColorUtil.a(background, alpha), animation);

            }

            drawHudThemeGradient(event.d(), event.h(), x, y, width, height, r, animation);

        }

    }

    public static void drawHudThemeGradient(delta.render.Draw2DProcessor draw, MatrixStack matrices, float x, float y, float width, float height, float radius, float animation) {
        if (animation <= 0.01f) return;
        delta.module.render.Interface_2 iface = delta.module.render.Interface_2.getInstance();
        if (iface == null || !iface.isHudThemeGradient()) return;

        float strength = iface.getHudGradientStrength();
        if (strength <= 0.001f) return;

        int themeCol = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
        int r = (themeCol >> 16) & 0xFF;
        int g = (themeCol >> 8) & 0xFF;
        int b = themeCol & 0xFF;

        // Scaled by HUD gradient strength setting
        int colTL = ColorUtil.a(r, g, b, (int) (0.10f * strength * 255.0f * animation));
        int colBL = ColorUtil.a(r, g, b, (int) (0.16f * strength * 255.0f * animation));
        int colTR = ColorUtil.a(r, g, b, (int) (0.28f * strength * 255.0f * animation));
        int colBR = ColorUtil.a(r, g, b, (int) (0.42f * strength * 255.0f * animation));

        draw.a(matrices, x, y, width, height, radius, colTL, colTR, colBL, colBR);
        int borderCol = ColorUtil.a(r, g, b, (int) (0.22f * strength * 255.0f * animation));
        draw.a(matrices, x, y, width, height, radius, 0.75f, borderCol);
    }



    protected void a(DrawEvent event, float x, float y, float height, float animation) {

        float separatorHeight = height / 2.0f;

        event.d().a(event.h(), x, y + ((height - separatorHeight) / 2.0f), 0.75f, separatorHeight, 0.0f, ColorUtil.a(ColorUtil.a(InterfaceC0020Opcode.aN, InterfaceC0020Opcode.aN, InterfaceC0020Opcode.aN, 255), 0.5f * animation));

    }



    public float a() {

        return this.a.c() * (1.0f - (0.1f * this.b.c()));

    }



    public float getRounding() {

        try {

            return Delta.h().d().t().bd().getRounding();

        } catch (Exception e) {

            return 6.5f;

        }

    }



    public float getBlur() {

        try {

            return Delta.h().d().t().bd().getBlur();

        } catch (Exception e) {

            return 45.0f;

        }

    }



    public float getOpacity() {

        try {

            return Delta.h().d().t().bd().getOpacity();

        } catch (Exception e) {

            return 0.85f;

        }

    }

}


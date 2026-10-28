package delta.render;

import delta.core.Interface;

import static delta.core.Interface.aM_;
import delta.core.Module;
import delta.render.EasingList;
import delta.util.MathUtil;

import delta.core.Category;
import delta.core.EventTarget;
import delta.core.ModuleRegister;
import delta.event.DrawEvent;
import delta.event.TickEvent;
import delta.render.AnimationUtil;
import delta.setting.BooleanSetting;

import delta.setting.MultiModeSetting;
import lombok.Generated;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.Perspective;

@ModuleRegister(a = "Animations", b = "Анимирует выбранные элементы игры", c = Category.Render)
public class Animations extends Module {
    private final MultiModeSetting b = new MultiModeSetting("Выберите что анимировать", new BooleanSetting("TAB", true), new BooleanSetting("Открытие инвентаря", true), new BooleanSetting("Смена перспективы", true), new BooleanSetting("Поднятие хотбара", true), new BooleanSetting("Слот хотбара", true), new BooleanSetting("Появление сообщений", true), new BooleanSetting("Предметы", true));
    private final AnimationUtil c = new AnimationUtil();
    private final AnimationUtil d = new AnimationUtil();
    private final AnimationUtil e = new AnimationUtil();
    private final AnimationUtil f = new AnimationUtil();
    private float g = -1.0f;

    @Generated
    public MultiModeSetting q() {
        return this.b;
    }

    @Generated
    public AnimationUtil r() {
        return this.c;
    }

    @Generated
    public AnimationUtil s() {
        return this.d;
    }

    @Generated
    public AnimationUtil t() {
        return this.e;
    }

    @Generated
    public AnimationUtil u() {
        return this.f;
    }

    @Generated
    public float v() {
        return this.g;
    }

    public Animations() {
        a(this.b);
    }

    @Override
    public void c() {
        super.c();
        this.g = -1.0f;
    }

    @EventTarget
    public void a(DrawEvent event) {
        this.c.a(0.0f, 1.0f, 0.5f, EasingList.g, event.g());
        this.d.a(0.0f, 1.0f, 0.45f, EasingList.g, event.g());
        this.e.a(0.0f, 1.0f, 0.4f, EasingList.g, event.g());
        this.f.a(0.0f, 1.0f, 0.35f, EasingList.g, event.g());
        this.g = this.g < 0.0f ? aM_.player.getInventory().getSelectedSlot() : MathUtil.c(this.g, aM_.player.getInventory().getSelectedSlot(), 1.25f);
    }

    @EventTarget
    public void a(TickEvent event) {
        this.e.a(aM_.currentScreen instanceof InventoryScreen);
        this.f.a(aM_.options.getPerspective() != Perspective.FIRST_PERSON);
        this.d.a(aM_.currentScreen instanceof ChatScreen);
    }
}

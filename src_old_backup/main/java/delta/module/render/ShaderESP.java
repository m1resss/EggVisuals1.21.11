package delta.module.render;

import delta.config.ThemeInfo;
import delta.core.Category;
import delta.core.Delta;
import delta.core.Interface;
import static delta.core.Interface.aM_;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.render.ColorUtil;
import delta.setting.BooleanSetting;
import delta.setting.ColorSetting;
import delta.setting.ModeSetting;
import delta.setting.MultiModeSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

@ModuleRegister(a = "Shader ESP", b = "Накладывает шейдерную обводку на сущностей", c = Category.Render)
public class ShaderESP extends Module implements Interface {
    private final MultiModeSetting targets = new MultiModeSetting("Сущности",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Мобы", false));
    private final ModeSetting colorMode = new ModeSetting("Цвет", "Тема", "Тема", "Кастомный");
    private final ColorSetting customColor = new ColorSetting("Кастомный цвет", Integer.valueOf(ColorUtil.a(255, 60, 60, 255)));

    public ShaderESP() {
        a(this.targets, this.colorMode, this.customColor);
    }

    public static boolean shouldOutline(Entity entity) {
        if (entity == null || aM_.player == null || aM_.world == null) {
            return false;
        }
        ShaderESP shaderESP = Delta.h().d().t().ad();
        if (shaderESP == null || !shaderESP.m()) {
            return false;
        }
        if (entity == aM_.player || entity.isRemoved()) {
            return false;
        }
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        // Только на видимых противниках (не через стены)
        if (!aM_.player.canSee(living)) {
            return false;
        }
        // Не подсвечивать друзей
        if ((entity instanceof PlayerEntity) && Delta.h().d().e().d(entity.getName().getString())) {
            return false;
        }
        BooleanSetting players = shaderESP.targets.a("Игроки");
        BooleanSetting mobs = shaderESP.targets.a("Мобы");
        if (entity instanceof PlayerEntity) {
            return players != null && players.c().booleanValue();
        }
        return mobs != null && mobs.c().booleanValue();
    }

    public static int getColor() {
        ShaderESP shaderESP = Delta.h().d().t().ad();
        if (shaderESP == null) {
            return 0xFFFFFFFF;
        }
        if (shaderESP.colorMode.e().equals("Кастомный")) {
            return shaderESP.customColor.c().intValue();
        }
        try {
            return Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
        } catch (Exception e) {
            return 0xFFFFFFFF;
        }
    }
}

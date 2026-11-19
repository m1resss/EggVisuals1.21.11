package egg.client.module.misc;

import egg.client.EggClient;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BindSetting;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.ui.theme.ClientTheme;
import egg.client.util.ColorUtil;
import org.lwjgl.glfw.GLFW;

public class ClientSettings extends Module {
    private final BindSetting openKey = new BindSetting("Клавиша открытия", GLFW.GLFW_KEY_RIGHT_SHIFT, null, "Клавиша для открытия и закрытия меню ClickGUI");
    private final ColorSetting accentColor = new ColorSetting("Цвет интерфейса", ColorUtil.rgb(111, 95, 176), "Пользовательский цвет акцентов интерфейса");
    private final BooleanSetting cursorGlow = new BooleanSetting("Подсветка курсора", true, "Мягкий рассеянный луч освещения, следующий за курсором в меню");
    private final BooleanSetting dotGrid = new BooleanSetting("Точечная сетка", true, "Едва заметная фактурная точечная сетка блокнота на фоне меню");

    private final ModeSetting openAnimMode = new ModeSetting("Анимация открытия", "Lens", "Стиль анимации открытия/закрытия ClickGUI", "Lens", "Волна", "Простая", "Выкл");
    private final ModeSetting lensCenterMode = new ModeSetting("Центр линзы", "Центр окна", "Точка раскрытия жидкой линзы", "Центр окна", "Курсор");
    private final BooleanSetting smoothAnimations = new BooleanSetting("Smooth Animations", true, "Плавные анимации интерфейса");
    private final SliderSetting animSpeed = new SliderSetting("Anim Speed", 1.0, 0.5, 2.5, 0.1, "Множитель скорости анимаций");
    private final SliderSetting openSoundVolume = new SliderSetting("Громкость звука меню", 1.0, 0.0, 1.0, 0.05, "Громкость звука открытия и закрытия меню ClickGUI (0 = без звука)");
    private final BooleanSetting guiBlur = new BooleanSetting("Background Blur", true, "Размытие заднего плана ванильным шейдером");
    private final BooleanSetting guiShadow = new BooleanSetting("Window Shadow", true, "Мягкая тень вокруг окна GUI");
    private final BooleanSetting scrollMotionBlur = new BooleanSetting("Scroll Motion Blur", true, "Кинематографичный моушен блюр при скролле списка модулей");

    public ClientSettings() {
        super("ClientSettings", "Настройки интерфейса и визуальных эффектов", Category.SETTINGS);
        addSettings(
                openKey, accentColor, cursorGlow, dotGrid,
                openAnimMode, lensCenterMode, smoothAnimations, animSpeed, openSoundVolume, guiBlur, guiShadow, scrollMotionBlur
        );
    }

    public ColorSetting getAccentColorSetting() {
        return accentColor;
    }

    public static int getOpenKey() {
        if (EggClient.getInstance() == null || EggClient.getInstance().getModuleManager() == null) {
            return GLFW.GLFW_KEY_RIGHT_SHIFT;
        }
        ClientSettings s = EggClient.getInstance().getModuleManager().getModule(ClientSettings.class);
        return (s != null) ? s.openKey.getKey() : GLFW.GLFW_KEY_RIGHT_SHIFT;
    }

    public static int getCustomAccent() {
        if (EggClient.getInstance() == null || EggClient.getInstance().getModuleManager() == null) {
            return ColorUtil.rgb(111, 95, 176);
        }
        ClientSettings s = EggClient.getInstance().getModuleManager().getModule(ClientSettings.class);
        if (s == null) return ColorUtil.rgb(111, 95, 176);
        return s.accentColor.get();
    }

    public static ClientTheme getActiveTheme() {
        return ClientTheme.GRAPHITE;
    }

    public static boolean isCursorGlow() {
        if (EggClient.getInstance() == null || EggClient.getInstance().getModuleManager() == null) return true;
        ClientSettings s = EggClient.getInstance().getModuleManager().getModule(ClientSettings.class);
        return s == null || s.cursorGlow.get();
    }

    public static boolean isDotGrid() {
        if (EggClient.getInstance() == null || EggClient.getInstance().getModuleManager() == null) return true;
        ClientSettings s = EggClient.getInstance().getModuleManager().getModule(ClientSettings.class);
        return s == null || s.dotGrid.get();
    }

    public static String getOpenAnimationMode() {
        if (EggClient.getInstance() == null || EggClient.getInstance().getModuleManager() == null) return "Lens";
        ClientSettings s = EggClient.getInstance().getModuleManager().getModule(ClientSettings.class);
        return s == null ? "Lens" : s.openAnimMode.get();
    }

    public static String getLensCenterMode() {
        if (EggClient.getInstance() == null || EggClient.getInstance().getModuleManager() == null) return "Центр окна";
        ClientSettings s = EggClient.getInstance().getModuleManager().getModule(ClientSettings.class);
        return s == null ? "Центр окна" : s.lensCenterMode.get();
    }

    public static boolean animationsEnabled() {
        if (EggClient.getInstance() == null || EggClient.getInstance().getModuleManager() == null) return true;
        ClientSettings s = EggClient.getInstance().getModuleManager().getModule(ClientSettings.class);
        return s == null || s.smoothAnimations.get();
    }

    public static float getSpeedMultiplier() {
        if (EggClient.getInstance() == null || EggClient.getInstance().getModuleManager() == null) return 1.0f;
        ClientSettings s = EggClient.getInstance().getModuleManager().getModule(ClientSettings.class);
        return s == null ? 1.0f : (float) s.animSpeed.get();
    }

    public static float getOpenSoundVolume() {
        if (EggClient.getInstance() == null || EggClient.getInstance().getModuleManager() == null) return 1.0f;
        ClientSettings s = EggClient.getInstance().getModuleManager().getModule(ClientSettings.class);
        return s == null ? 1.0f : (float) s.openSoundVolume.get();
    }

    public static boolean isBlurEnabled() {
        if (EggClient.getInstance() == null || EggClient.getInstance().getModuleManager() == null) return true;
        ClientSettings s = EggClient.getInstance().getModuleManager().getModule(ClientSettings.class);
        return s == null || s.guiBlur.get();
    }

    public static boolean isShadowEnabled() {
        if (EggClient.getInstance() == null || EggClient.getInstance().getModuleManager() == null) return true;
        ClientSettings s = EggClient.getInstance().getModuleManager().getModule(ClientSettings.class);
        return s == null || s.guiShadow.get();
    }

    public static boolean isScrollMotionBlurEnabled() {
        if (EggClient.getInstance() == null || EggClient.getInstance().getModuleManager() == null) return true;
        ClientSettings s = EggClient.getInstance().getModuleManager().getModule(ClientSettings.class);
        return s == null || s.scrollMotionBlur.get();
    }
}

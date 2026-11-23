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
    private final SliderSetting guiScale = new SliderSetting("Масштаб GUI", 1.0, 0.6, 1.4, 0.05, "Пропорциональное масштабирование всех элементов ClickGUI");

    private final ModeSetting openAnimMode = new ModeSetting("Анимация открытия", "Lens", "Стиль анимации открытия/закрытия ClickGUI", "Lens", "Волна", "Простая", "Выкл");
    private final ModeSetting lensCenterMode = new ModeSetting("Центр линзы", "Центр окна", "Точка раскрытия жидкой линзы", "Центр окна", "Курсор");
    private final BooleanSetting smoothAnimations = new BooleanSetting("Smooth Animations", true, "Плавные анимации интерфейса");
    private final SliderSetting animSpeed = new SliderSetting("Anim Speed", 1.0, 0.5, 2.5, 0.1, "Множитель скорости анимаций");
    private final SliderSetting openSoundVolume = new SliderSetting("Громкость звука меню", 1.0, 0.0, 1.0, 0.05, "Громкость звука открытия и закрытия меню ClickGUI (0 = без звука)");
    private final BooleanSetting guiBlur = new BooleanSetting("Background Blur", true, "Матовое размытие мира под окном и карточками");
    private final SliderSetting blurStrength = new SliderSetting("Сила блюра", 6.0, 1.0, 10.0, 1.0, "Интенсивность размытия под меню");
    private final SliderSetting guiBgOpacity = new SliderSetting("Прозрачность меню", 0.85, 0.30, 1.0, 0.05, "Плотность затемнения матового стекла");
    private final SliderSetting screenDimOpacity = new SliderSetting("Затемнение фона", 0.35, 0.05, 0.80, 0.05, "Степень затемнения экрана за меню в стиле Neverlose CS2");
    private final BooleanSetting guiShadow = new BooleanSetting("Window Shadow", true, "Мягкая тень вокруг окна GUI");
    private final BooleanSetting scrollMotionBlur = new BooleanSetting("Scroll Motion Blur", true, "Кинематографичный моушен блюр при скролле списка модулей");

    public ClientSettings() {
        super("ClientSettings", "Настройки интерфейса и визуальных эффектов", Category.SETTINGS);
        addSettings(
                openKey, accentColor, guiScale,
                openAnimMode, lensCenterMode, smoothAnimations, animSpeed, openSoundVolume,
                guiBlur, blurStrength, guiBgOpacity, screenDimOpacity, guiShadow, scrollMotionBlur
        );
    }

    public ColorSetting getAccentColorSetting() {
        return accentColor;
    }

    public SliderSetting getGuiScaleSetting() {
        return guiScale;
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

    public static float getGuiScale() {
        if (EggClient.getInstance() == null || EggClient.getInstance().getModuleManager() == null) return 1.0f;
        ClientSettings s = EggClient.getInstance().getModuleManager().getModule(ClientSettings.class);
        return s == null ? 1.0f : (float) s.guiScale.get();
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

    public static int getBlurStrength() {
        if (EggClient.getInstance() == null || EggClient.getInstance().getModuleManager() == null) return 6;
        ClientSettings s = EggClient.getInstance().getModuleManager().getModule(ClientSettings.class);
        return s == null ? 6 : s.blurStrength.getValueInt();
    }

    public static float getGuiBgOpacity() {
        if (EggClient.getInstance() == null || EggClient.getInstance().getModuleManager() == null) return 0.85f;
        ClientSettings s = EggClient.getInstance().getModuleManager().getModule(ClientSettings.class);
        return s == null ? 0.85f : (float) s.guiBgOpacity.get();
    }

    public static float getScreenDimOpacity() {
        if (EggClient.getInstance() == null || EggClient.getInstance().getModuleManager() == null) return 0.35f;
        ClientSettings s = EggClient.getInstance().getModuleManager().getModule(ClientSettings.class);
        return s == null ? 0.35f : (float) s.screenDimOpacity.get();
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

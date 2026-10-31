package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.SliderSetting;

public class Animations extends Module {
    private static Animations instance;

    private final BooleanSetting tab = new BooleanSetting("Таб", true, "Красивая анимация масштабирования и открытия списка игроков (Tab)");
    private final BooleanSetting chat = new BooleanSetting("Чат", true, "Плавный подъем сообщений чата снизу вверх");
    private final BooleanSetting chatOpen = new BooleanSetting("Открытие чата", true, "Плавный выезд строки ввода чата при открытии");
    private final BooleanSetting containers = new BooleanSetting("Контейнеры", true, "Плавный зум и анимация открытия инвентарей и меню");
    private final BooleanSetting perspective = new BooleanSetting("Перспектива", true, "Плавный переход камеры при переключении F5");
    private final SliderSetting animSpeed = new SliderSetting("Скорость", 1.0, 0.5, 2.5, 0.1, "Множитель скорости анимаций");

    public Animations() {
        super("Animations", "Плавные анимации чата, таба, контейнеров и камеры", Category.ESP);
        instance = this;
        addSettings(tab, chat, chatOpen, containers, perspective, animSpeed);
    }

    public static Animations getInstance() {
        return instance;
    }

    public boolean isTabEnabled() {
        return isEnabled() && tab.get();
    }

    public boolean isChatEnabled() {
        return isEnabled() && chat.get();
    }

    public boolean isChatOpenEnabled() {
        return isEnabled() && chatOpen.get();
    }

    public boolean isContainersEnabled() {
        return isEnabled() && containers.get();
    }

    public boolean isPerspectiveEnabled() {
        return isEnabled() && perspective.get();
    }

    public float getSpeed() {
        return (float) animSpeed.get();
    }
}

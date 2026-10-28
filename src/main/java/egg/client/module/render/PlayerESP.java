package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;

public class PlayerESP extends Module {
    private final BooleanSetting box = new BooleanSetting("2D Box", true, "Отрисовка прямоугольного бокса вокруг игрока");
    private final BooleanSetting healthBar = new BooleanSetting("HealthBar", true, "Полоса здоровья слева от бокса");
    private final BooleanSetting armorBar = new BooleanSetting("ArmorBar", true, "Индикатор прочности экипировки");

    public PlayerESP() {
        super("PlayerESP", "Подсветка игроков через стены и в поле зрения", Category.ESP);
        addSettings(box, healthBar, armorBar);
    }
}

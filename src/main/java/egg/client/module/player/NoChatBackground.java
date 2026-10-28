package egg.client.module.player;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;

public class NoChatBackground extends Module {
    public NoChatBackground() {
        super("NoChatBackground", "Отключение темного фона сообщений в чате", Category.PLAYER);
    }
}

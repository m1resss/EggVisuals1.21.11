package egg.client.module.player;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;

public class NoChatBackground extends Module {
    public NoChatBackground() {
        super("NoChatBackground", "Убирает темный фон под текстом сообщений в чате", Category.PLAYER);
    }
}

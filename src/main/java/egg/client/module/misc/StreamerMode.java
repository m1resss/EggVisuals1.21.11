package egg.client.module.misc;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;

public class StreamerMode extends Module {
    private final BooleanSetting hideName = new BooleanSetting("Hide Name", true, "Скрывать свой ник в чате и на экране");
    private final BooleanSetting hideAnarchy = new BooleanSetting("Hide Anarchy #", true, "Скрывать номер анархии");

    public StreamerMode() {
        super("StreamerMode", "Скрывает личные данные и номер сервера при стриме/записи", Category.MISC);
        addSettings(hideName, hideAnarchy);
    }

    public String filterText(String text) {
        if (!isEnabled() || text == null) return text;
        String result = text;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (hideName.get() && mc.getSession() != null && mc.getSession().getUsername() != null) {
            result = result.replaceAll("(?i)" + mc.getSession().getUsername(), "Protected");
        }
        if (hideAnarchy.get()) {
            result = result.replaceAll("(?i)Анархия-\\d+", "Анархия-???");
            result = result.replaceAll("(?i)Anarchy-\\d+", "Anarchy-???");
        }
        return result;
    }
}

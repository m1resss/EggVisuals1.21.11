package egg.client.module.player;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;

public class AutoAccept extends Module {
    private final BooleanSetting teleport = new BooleanSetting("Teleport", true, "Автоматически принимать запросы телепортации");
    private final BooleanSetting clan = new BooleanSetting("Clan", true, "Автоматически принимать приглашения в клан");

    public AutoAccept() {
        super("AutoAccept", "Автоматически принимает запросы телепортации и приглашения в клан", Category.PLAYER);
        addSettings(teleport, clan);
    }

    public void onChatMessage(String message) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.networkHandler == null) return;

        String msg = message.toLowerCase();
        if (teleport.get()) {
            if (msg.contains("телепортироваться") || msg.contains("teleport") || msg.contains("/tpaccept") || msg.contains("запрос на телепортацию")) {
                mc.player.networkHandler.sendChatCommand("tpaccept");
            }
        }
        if (clan.get()) {
            if (msg.contains("приглашает вас в клан") || msg.contains("invites you to the clan")) {
                String[] parts = message.split("\\s+");
                for (String p : parts) {
                    if (p.length() > 2 && !p.equalsIgnoreCase("клан") && !p.equalsIgnoreCase("clan")) {
                        mc.player.networkHandler.sendChatCommand("clan accept " + p);
                        break;
                    }
                }
            }
        }
    }
}

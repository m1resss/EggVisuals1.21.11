package egg.client.module.player;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.SliderSetting;

public class ItemScroller extends Module {
    private final SliderSetting delay = new SliderSetting("Delay (ms)", 40.0, 0.0, 100.0, 5.0, "Задержка между слотами при перемещении");
    private long lastMoveTime = 0L;

    public ItemScroller() {
        super("ItemScroller", "Быстрое перемещение предметов в сундуках зажатием Shift и ЛКМ", Category.PLAYER);
        addSettings(delay);
    }

    public boolean canMove() {
        long now = System.currentTimeMillis();
        if (now - lastMoveTime >= (long) delay.get()) {
            lastMoveTime = now;
            return true;
        }
        return false;
    }
}

package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;

public class ItemPhysic extends Module {
    private final BooleanSetting smallItems = new BooleanSetting("Small Items", false, "Уменьшить размер лежащих предметов");

    public ItemPhysic() {
        super("ItemPhysic", "Реалистичная физика лежащих на земле предметов", Category.ESP);
        addSettings(smallItems);
    }

    public boolean isSmall() {
        return smallItems.get();
    }
}

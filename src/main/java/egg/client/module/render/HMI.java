package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;

public class HMI extends Module {
    private static HMI instance;

    public HMI() {
        super("HMI", "Анимации удержания и использования предметов от первого лица", Category.ESP);
        instance = this;
    }

    public static HMI getInstance() {
        return instance;
    }
}

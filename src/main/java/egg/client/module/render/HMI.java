package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.misc.Fun;

public class HMI extends Module {
    private static HMI instance;

    public HMI() {
        super("HMI", "Анимации удержания и использования предметов от первого лица", Category.ESP);
        instance = this;
    }

    public static boolean usesAnimations() {
        return instance != null && instance.isEnabled() && !Fun.usesLocomotion();
    }
}

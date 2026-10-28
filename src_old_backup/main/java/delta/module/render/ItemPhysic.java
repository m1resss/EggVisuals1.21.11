package delta.module.render;

import delta.core.Module;

import delta.core.Category;
import delta.core.ModuleRegister;

import delta.setting.BooleanSetting;
import lombok.Generated;

@ModuleRegister(a = "Item Physic", b = "Добавляет физику предметам, лежащим на земле", c = Category.Render)
public class ItemPhysic extends Module {
    private final BooleanSetting b = new BooleanSetting("Уменьшить размер предметов", false);

    @Generated
    public BooleanSetting q() {
        return this.b;
    }

    public ItemPhysic() {
        a(this.b);
    }
}

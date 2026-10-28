package delta.module.combat;

import static delta.core.Interface.aM_;
import delta.core.Category;
import delta.core.Interface;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.setting.BindSetting;
import java.util.List;
import lombok.Generated;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

@ModuleRegister(a = "Item Helper", b = "Выбирает слот нужного предмета в хотбаре по нажатию клавиши", c = Category.Combat)
public class ItemHelper extends Module implements Interface {
    public ItemHelper() {
        List.of(
            new a(this, "Зачарованное яблоко", Items.ENCHANTED_GOLDEN_APPLE),
            new a(this, "Золотое яблоко", Items.GOLDEN_APPLE),
            new a(this, "Плод хоруса", Items.CHORUS_FRUIT),
            new a(this, "Арбалет", Items.CROSSBOW)
        ).forEach(item -> {
            a(item.b());
        });
    }

    final class a {
        private final Item a;
        private final BindSetting b;
        private int prevSlot = -1;

        @Generated
        public Item a() {
            return this.a;
        }

        @Generated
        public BindSetting b() {
            return this.b;
        }

        @Generated
        public int c() {
            return this.prevSlot;
        }

        a(ItemHelper itemHelper, String name, Item item) {
            this.a = item;
            this.b = new BindSetting(name, -1, 1).a(this::onPress).b(this::onRelease);
        }

        private int findHotbarSlot() {
            if (aM_.player == null) {
                return -1;
            }
            for (int i = 0; i < 9; i++) {
                ItemStack stack = aM_.player.getInventory().getStack(i);
                if (stack != null && !stack.isEmpty() && stack.isOf(this.a)) {
                    return i;
                }
            }
            return -1;
        }

        private void onPress() {
            if (!m() || aM_.player == null) {
                return;
            }
            if (this.prevSlot != -1) {
                aM_.player.getInventory().setSelectedSlot(this.prevSlot);
                this.prevSlot = -1;
                return;
            }
            int slot = findHotbarSlot();
            if (slot != -1 && slot != aM_.player.getInventory().getSelectedSlot()) {
                this.prevSlot = aM_.player.getInventory().getSelectedSlot();
                aM_.player.getInventory().setSelectedSlot(slot);
            }
        }

        private void onRelease() {
            if (!m() || aM_.player == null) {
                return;
            }
            if (this.prevSlot != -1) {
                aM_.player.getInventory().setSelectedSlot(this.prevSlot);
                this.prevSlot = -1;
            }
        }
    }
}

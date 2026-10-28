package delta.module.player;

import static delta.core.Interface.aM_;
import delta.core.Category;
import delta.core.EventTarget;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.KeyEvent;
import delta.event.TickEvent;
import delta.setting.BindSetting;
import delta.setting.BooleanSetting;
import delta.setting.MultiModeSetting;
import delta.setting.SliderSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Rarity;

@ModuleRegister(a = "Smart Trash Drop", b = "Безопасный и быстрый сброс мусорных блоков с защитой от античита", c = Category.Player)
public class SmartTrashDrop extends Module {
    private final BindSetting dropKey = new BindSetting("Клавиша сброса", -1);
    private final BooleanSetting autoDropWhenFull = new BooleanSetting("Сброс при заполнении", false);
    private final SliderSetting dropDelay = new SliderSetting("Задержка (тики)", 4.0f, 4.0f, 10.0f, 1.0f);
    private final BooleanSetting hotbarProtection = new BooleanSetting("Защита хотбара", true);

    private final MultiModeSetting trashFilter = new MultiModeSetting(
        "Фильтр мусора",
        new BooleanSetting("Булыжник и сланцы", true),
        new BooleanSetting("Земля и грязь", true),
        new BooleanSetting("Диорит / Гранит / Андезит", true),
        new BooleanSetting("Туф и базальт", true),
        new BooleanSetting("Незерак", true),
        new BooleanSetting("Гравий и песок", true),
        new BooleanSetting("Гнилая плоть", true)
    );

    private boolean dropActive = false;
    private int tickCounter = 0;

    public SmartTrashDrop() {
        a(this.dropKey, this.autoDropWhenFull, this.dropDelay, this.hotbarProtection, this.trashFilter);
    }

    @Override
    public void c() {
        super.c();
        this.dropActive = false;
        this.tickCounter = 0;
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (event.d() == 1 && this.dropKey.c() != null && this.dropKey.c() != -1 && this.dropKey.c() == event.b()) {
            this.dropActive = !this.dropActive;
            this.tickCounter = 0;
        }
    }

    private boolean isSafeTrash(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.contains(DataComponentTypes.CUSTOM_NAME)) {
            return false;
        }
        if (stack.hasGlint() || stack.contains(DataComponentTypes.ENCHANTMENTS) || stack.contains(DataComponentTypes.STORED_ENCHANTMENTS)) {
            return false;
        }
        if (stack.contains(DataComponentTypes.CONTAINER)) {
            return false;
        }
        if (stack.getRarity() != Rarity.COMMON) {
            return false;
        }

        Item item = stack.getItem();

        if (this.trashFilter.a("Булыжник и сланцы").c().booleanValue()) {
            if (item == Items.COBBLESTONE || item == Items.STONE || item == Items.COBBLED_DEEPSLATE
                || item == Items.DEEPSLATE || item == Items.INFESTED_COBBLESTONE || item == Items.INFESTED_STONE) {
                return true;
            }
        }

        if (this.trashFilter.a("Земля и грязь").c().booleanValue()) {
            if (item == Items.DIRT || item == Items.COARSE_DIRT || item == Items.ROOTED_DIRT || item == Items.MUD) {
                return true;
            }
        }

        if (this.trashFilter.a("Диорит / Гранит / Андезит").c().booleanValue()) {
            if (item == Items.DIORITE || item == Items.GRANITE || item == Items.ANDESITE) {
                return true;
            }
        }

        if (this.trashFilter.a("Туф и базальт").c().booleanValue()) {
            if (item == Items.TUFF || item == Items.BASALT || item == Items.BLACKSTONE || item == Items.POLISHED_BASALT) {
                return true;
            }
        }

        if (this.trashFilter.a("Незерак").c().booleanValue()) {
            if (item == Items.NETHERRACK) {
                return true;
            }
        }

        if (this.trashFilter.a("Гравий и песок").c().booleanValue()) {
            if (item == Items.GRAVEL || item == Items.SAND || item == Items.RED_SAND || item == Items.FLINT) {
                return true;
            }
        }

        if (this.trashFilter.a("Гнилая плоть").c().booleanValue()) {
            if (item == Items.ROTTEN_FLESH || item == Items.POISONOUS_POTATO || item == Items.GLASS_BOTTLE) {
                return true;
            }
        }

        return false;
    }

    private boolean isInventoryNearlyFull() {
        if (aM_.player == null) {
            return false;
        }
        int emptySlots = 0;
        for (int i = 9; i < 36; i++) {
            if (aM_.player.getInventory().getStack(i).isEmpty()) {
                emptySlots++;
            }
        }
        return emptySlots <= 1;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (aM_.player == null || aM_.world == null || !aM_.player.isAlive()) {
            this.dropActive = false;
            return;
        }

        if (this.autoDropWhenFull.c().booleanValue() && isInventoryNearlyFull()) {
            this.dropActive = true;
        }

        if (!this.dropActive) {
            return;
        }

        this.tickCounter++;
        int requiredDelay = this.dropDelay.c().intValue();
        if (this.tickCounter < requiredDelay) {
            return;
        }
        this.tickCounter = 0;

        ScreenHandler handler = aM_.player.currentScreenHandler;
        if (handler == null) {
            handler = aM_.player.playerScreenHandler;
        }

        Slot trashSlot = null;
        for (Slot slot : handler.slots) {
            if (slot.inventory == aM_.player.getInventory()) {
                int invIndex = slot.getIndex();
                if (this.hotbarProtection.c().booleanValue() && invIndex < 9) {
                    continue;
                }
                if (isSafeTrash(slot.getStack())) {
                    trashSlot = slot;
                    break;
                }
            }
        }

        if (trashSlot != null) {
            aM_.interactionManager.clickSlot(handler.syncId, trashSlot.id, 1, SlotActionType.THROW, aM_.player);
        } else {
            this.dropActive = false;
        }
    }
}

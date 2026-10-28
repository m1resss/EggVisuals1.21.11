package egg.client.inventory;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class InventorySlotRule {
    private final int slotIndex; // 0..40 (0-8 hotbar, 9-35 main, 36 boots, 37 legs, 38 chest, 39 helm, 40 offhand)
    private String itemId;       // e.g. "minecraft:netherite_sword"
    private String customName;   // e.g. "Снежок заморозка", or null
    private int count = 1;
    private transient ItemStack cachedIcon;

    public InventorySlotRule(int slotIndex, String itemId, String customName, int count) {
        this.slotIndex = slotIndex;
        this.itemId = (itemId != null) ? itemId : "";
        this.customName = (customName != null && !customName.trim().isEmpty()) ? customName.trim() : null;
        this.count = Math.max(1, count);
    }

    public static InventorySlotRule fromStack(int slotIndex, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new InventorySlotRule(slotIndex, "", null, 0);
        }
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        String cName = null;
        if (stack.contains(DataComponentTypes.CUSTOM_NAME)) {
            cName = stack.getName().getString();
        }
        return new InventorySlotRule(slotIndex, id, cName, stack.getCount());
    }

    public boolean isEmpty() {
        return itemId == null || itemId.isEmpty() || itemId.equals("minecraft:air");
    }

    public boolean matches(ItemStack stack) {
        if (isEmpty()) return false;
        if (stack == null || stack.isEmpty()) return false;

        Item ruleItem = getItem();
        Item currentItem = stack.getItem();

        // Обычные и зачарованные яблоки - принципиально разные предметы, никогда не смешивать
        if ((ruleItem == Items.GOLDEN_APPLE && currentItem == Items.ENCHANTED_GOLDEN_APPLE) ||
            (ruleItem == Items.ENCHANTED_GOLDEN_APPLE && currentItem == Items.GOLDEN_APPLE)) {
            return false;
        }

        // 1. Приоритетное совпадение по кастомному имени (для предметов HolyWorld / FunTime)
        if (customName != null && !customName.isEmpty()) {
            String stackName = stack.getName().getString().toLowerCase();
            if (stackName.contains(customName.toLowerCase())) {
                return true;
            }
        }

        // 2. Точное совпадение по ID предмета
        String sId = Registries.ITEM.getId(currentItem).toString();
        if (sId.equalsIgnoreCase(itemId)) {
            return true;
        }

        // 3. Совместимое совпадение по категории (любой меч, любая кирка, броня)
        return isCompatibleFallback(stack);
    }

    private boolean isCompatibleFallback(ItemStack stack) {
        if (isEmpty()) return false;
        Item ruleItem = getItem();
        Item currentItem = stack.getItem();

        // Мечи
        if (isSword(ruleItem) && isSword(currentItem)) return true;
        // Кирки
        if (isPickaxe(ruleItem) && isPickaxe(currentItem)) return true;
        // Топоры
        if (isAxe(ruleItem) && isAxe(currentItem)) return true;
        // Лопаты
        if (isShovel(ruleItem) && isShovel(currentItem)) return true;
        // Лук / арбалет
        if ((ruleItem == Items.BOW || ruleItem == Items.CROSSBOW) &&
            (currentItem == Items.BOW || currentItem == Items.CROSSBOW)) return true;
        // Тотемы бессмертия
        if (ruleItem == Items.TOTEM_OF_UNDYING && currentItem == Items.TOTEM_OF_UNDYING) return true;
        // Броня
        if (isHelmet(ruleItem) && isHelmet(currentItem)) return true;
        if (isChestplate(ruleItem) && isChestplate(currentItem)) return true;
        if (isLeggings(ruleItem) && isLeggings(currentItem)) return true;
        if (isBoots(ruleItem) && isBoots(currentItem)) return true;

        return false;
    }

    private static boolean isSword(Item item) {
        return item == Items.NETHERITE_SWORD || item == Items.DIAMOND_SWORD || item == Items.IRON_SWORD ||
               item == Items.GOLDEN_SWORD || item == Items.STONE_SWORD || item == Items.WOODEN_SWORD;
    }

    private static boolean isPickaxe(Item item) {
        return item == Items.NETHERITE_PICKAXE || item == Items.DIAMOND_PICKAXE || item == Items.IRON_PICKAXE ||
               item == Items.GOLDEN_PICKAXE || item == Items.STONE_PICKAXE || item == Items.WOODEN_PICKAXE;
    }

    private static boolean isAxe(Item item) {
        return item == Items.NETHERITE_AXE || item == Items.DIAMOND_AXE || item == Items.IRON_AXE ||
               item == Items.GOLDEN_AXE || item == Items.STONE_AXE || item == Items.WOODEN_AXE;
    }

    private static boolean isShovel(Item item) {
        return item == Items.NETHERITE_SHOVEL || item == Items.DIAMOND_SHOVEL || item == Items.IRON_SHOVEL ||
               item == Items.GOLDEN_SHOVEL || item == Items.STONE_SHOVEL || item == Items.WOODEN_SHOVEL;
    }

    private static boolean isHelmet(Item item) {
        return item == Items.NETHERITE_HELMET || item == Items.DIAMOND_HELMET || item == Items.IRON_HELMET ||
               item == Items.GOLDEN_HELMET || item == Items.CHAINMAIL_HELMET || item == Items.LEATHER_HELMET ||
               item == Items.TURTLE_HELMET;
    }

    private static boolean isChestplate(Item item) {
        return item == Items.NETHERITE_CHESTPLATE || item == Items.DIAMOND_CHESTPLATE || item == Items.IRON_CHESTPLATE ||
               item == Items.GOLDEN_CHESTPLATE || item == Items.CHAINMAIL_CHESTPLATE || item == Items.LEATHER_CHESTPLATE ||
               item == Items.ELYTRA;
    }

    private static boolean isLeggings(Item item) {
        return item == Items.NETHERITE_LEGGINGS || item == Items.DIAMOND_LEGGINGS || item == Items.IRON_LEGGINGS ||
               item == Items.GOLDEN_LEGGINGS || item == Items.CHAINMAIL_LEGGINGS || item == Items.LEATHER_LEGGINGS;
    }

    private static boolean isBoots(Item item) {
        return item == Items.NETHERITE_BOOTS || item == Items.DIAMOND_BOOTS || item == Items.IRON_BOOTS ||
               item == Items.GOLDEN_BOOTS || item == Items.CHAINMAIL_BOOTS || item == Items.LEATHER_BOOTS;
    }

    public Item getItem() {
        if (isEmpty()) return Items.AIR;
        try {
            return Registries.ITEM.get(Identifier.of(itemId));
        } catch (Exception e) {
            return Items.AIR;
        }
    }

    public ItemStack getIconStack() {
        if (cachedIcon != null && !cachedIcon.isEmpty()) return cachedIcon;
        Item item = getItem();
        if (item == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        if (customName != null && !customName.isEmpty()) {
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(customName));
        }
        cachedIcon = stack;
        return stack;
    }

    public ItemStack getDisplayStack() {
        return getIconStack();
    }

    public int getSlotIndex() { return slotIndex; }
    public String getItemId() { return itemId; }
    public String getCustomName() { return customName; }
    public int getCount() { return count; }

    public void setItem(String itemId, String customName, int count) {
        this.itemId = (itemId != null) ? itemId : "";
        this.customName = (customName != null && !customName.trim().isEmpty()) ? customName.trim() : null;
        this.count = Math.max(1, count);
        this.cachedIcon = null;
    }
}

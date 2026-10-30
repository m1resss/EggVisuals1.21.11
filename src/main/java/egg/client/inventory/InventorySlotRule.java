package egg.client.inventory;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Locale;

public class InventorySlotRule {
    private final int slotIndex;
    private String itemId;
    private String customName;
    private String potionId;
    private int count = 1;
    private transient ItemStack cachedIcon;

    public InventorySlotRule(int slotIndex, String itemId, String customName, int count) {
        this(slotIndex, itemId, customName, null, count);
    }

    public InventorySlotRule(int slotIndex, String itemId, String customName, String potionId, int count) {
        this.slotIndex = slotIndex;
        this.itemId = (itemId != null) ? itemId : "";
        this.customName = (customName != null && !customName.trim().isEmpty()) ? customName.trim() : null;
        this.potionId = (potionId != null && !potionId.trim().isEmpty()) ? potionId.trim() : null;
        this.count = Math.max(1, count);
    }

    public static InventorySlotRule fromStack(int slotIndex, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new InventorySlotRule(slotIndex, "", null, null, 0);
        }
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        String cName = null;
        if (stack.contains(DataComponentTypes.CUSTOM_NAME) || stack.contains(DataComponentTypes.ITEM_NAME)) {
            cName = stack.getName().getString();
        }
        String pId = extractPotionId(stack);
        return new InventorySlotRule(slotIndex, id, cName, pId, stack.getCount());
    }

    public static String extractPotionId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (contents != null && contents.potion().isPresent()) {
                RegistryEntry<Potion> entry = contents.potion().get();
                if (entry.getKey().isPresent()) {
                    return entry.getKey().get().getValue().toString();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isPotionItem(Item item) {
        return item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.TIPPED_ARROW;
    }

    public static String cleanName(String name) {
        if (name == null) return "";
        return name.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim().toLowerCase(Locale.ROOT);
    }

    public boolean isEmpty() {
        return itemId == null || itemId.isEmpty() || itemId.equals("minecraft:air");
    }

    public boolean matches(ItemStack stack) {
        if (isEmpty()) return false;
        if (stack == null || stack.isEmpty()) return false;

        Item ruleItem = getItem();
        Item currentItem = stack.getItem();

        // Distinct apples
        if ((ruleItem == Items.GOLDEN_APPLE && currentItem == Items.ENCHANTED_GOLDEN_APPLE) ||
            (ruleItem == Items.ENCHANTED_GOLDEN_APPLE && currentItem == Items.GOLDEN_APPLE)) {
            return false;
        }

        // Potion matching
        if (isPotionItem(ruleItem) || isPotionItem(currentItem)) {
            if (ruleItem != currentItem) return false;

            // 1. If customName was specified (e.g. server custom potion like "Зелье (3 в 1)")
            if (customName != null && !customName.isEmpty()) {
                String sClean = cleanName(stack.getName().getString());
                String rClean = cleanName(customName);
                if (sClean.contains(rClean) || rClean.contains(sClean)) {
                    return true;
                }
            }

            // 2. If potionId was specified (vanilla potion effect like swiftness, healing, strength)
            if (potionId != null && !potionId.isEmpty()) {
                String stackPId = extractPotionId(stack);
                return potionId.equalsIgnoreCase(stackPId);
            }

            // 3. If neither custom name nor potionId is specified, match empty/water potion
            if (customName == null || customName.isEmpty()) {
                String stackPId = extractPotionId(stack);
                return (stackPId == null || stackPId.isEmpty() || stackPId.equals("minecraft:water"));
            }
            return false;
        }

        // Regular item matching with customName
        if (customName != null && !customName.isEmpty()) {
            String stackName = cleanName(stack.getName().getString());
            String ruleName = cleanName(customName);
            if (stackName.contains(ruleName) || ruleName.contains(stackName)) {
                return true;
            }
        }

        String sId = Registries.ITEM.getId(currentItem).toString();
        if (sId.equalsIgnoreCase(itemId)) {
            return true;
        }

        return isCompatibleFallback(stack);
    }

    private boolean isCompatibleFallback(ItemStack stack) {
        if (isEmpty()) return false;
        Item ruleItem = getItem();
        Item currentItem = stack.getItem();

        if (isSword(ruleItem) && isSword(currentItem)) return true;
        if (isPickaxe(ruleItem) && isPickaxe(currentItem)) return true;
        if (isAxe(ruleItem) && isAxe(currentItem)) return true;
        if (isShovel(ruleItem) && isShovel(currentItem)) return true;

        if ((ruleItem == Items.BOW || ruleItem == Items.CROSSBOW) &&
            (currentItem == Items.BOW || currentItem == Items.CROSSBOW)) return true;

        if (ruleItem == Items.TOTEM_OF_UNDYING && currentItem == Items.TOTEM_OF_UNDYING) return true;

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
        if (potionId != null && !potionId.isEmpty()) {
            try {
                RegistryEntry<Potion> pEntry = Registries.POTION.getEntry(Identifier.of(potionId)).orElse(null);
                if (pEntry != null) {
                    stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(pEntry));
                }
            } catch (Throwable ignored) {}
        }
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
    public String getPotionId() { return potionId; }
    public int getCount() { return count; }

    public void setItem(String itemId, String customName, int count) {
        setItem(itemId, customName, null, count);
    }

    public void setItem(String itemId, String customName, String potionId, int count) {
        this.itemId = (itemId != null) ? itemId : "";
        this.customName = (customName != null && !customName.trim().isEmpty()) ? customName.trim() : null;
        this.potionId = (potionId != null && !potionId.trim().isEmpty()) ? potionId.trim() : null;
        this.count = Math.max(1, count);
        this.cachedIcon = null;
    }
}

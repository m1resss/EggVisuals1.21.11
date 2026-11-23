package egg.client.inventory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.properties.Property;
import com.mojang.serialization.JsonOps;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class InventorySlotRule {
    private final int slotIndex;
    private String itemId;
    private String customName;
    private String potionId;
    private ComponentChanges headComponents;
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
        boolean playerHead = stack.isOf(Items.PLAYER_HEAD);
        String cName = null;
        if (stack.contains(DataComponentTypes.CUSTOM_NAME) || stack.contains(DataComponentTypes.ITEM_NAME)) {
            cName = playerHead ? headName(stack) : stack.getName().getString();
        }
        String pId = extractPotionId(stack);
        InventorySlotRule rule = new InventorySlotRule(slotIndex, id, cName, pId, stack.getCount());
        if (playerHead) {
            rule.headComponents = stack.getComponentChanges().withRemovedIf(InventorySlotRule::isOtherComponent);
        }
        return rule;
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

        if (ruleItem == Items.PLAYER_HEAD || currentItem == Items.PLAYER_HEAD) {
            return ruleItem == Items.PLAYER_HEAD && currentItem == Items.PLAYER_HEAD && matchesHead(stack);
        }

        if ((ruleItem == Items.GOLDEN_APPLE && currentItem == Items.ENCHANTED_GOLDEN_APPLE) ||
            (ruleItem == Items.ENCHANTED_GOLDEN_APPLE && currentItem == Items.GOLDEN_APPLE)) {
            return false;
        }

        if (isPotionItem(ruleItem) || isPotionItem(currentItem)) {
            if (ruleItem != currentItem) return false;

            if (customName != null && !customName.isEmpty()) {
                String sClean = cleanName(stack.getName().getString());
                String rClean = cleanName(customName);
                if (sClean.contains(rClean) || rClean.contains(sClean)) {
                    return true;
                }
            }

            if (potionId != null && !potionId.isEmpty()) {
                String stackPId = extractPotionId(stack);
                return potionId.equalsIgnoreCase(stackPId);
            }

            if (customName == null || customName.isEmpty()) {
                String stackPId = extractPotionId(stack);
                return (stackPId == null || stackPId.isEmpty() || stackPId.equals("minecraft:water"));
            }
            return false;
        }

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

    private boolean matchesHead(ItemStack stack) {
        if (headComponents == null) {
            if (customName != null) {
                return cleanName(customName).equals(cleanName(headName(stack)));
            }
            return stack.getComponentChanges().withRemovedIf(InventorySlotRule::isOtherComponent).isEmpty();
        }

        ItemStack expected = getIconStack();
        return headName(expected).equals(headName(stack))
                && sameLore(expected, stack)
                && sameHeadTexture(expected.get(DataComponentTypes.PROFILE), stack.get(DataComponentTypes.PROFILE));
    }

    private static String headName(ItemStack stack) {
        Text text = stack.getCustomName();
        if (text == null && stack.hasChangedComponent(DataComponentTypes.ITEM_NAME)) {
            text = stack.get(DataComponentTypes.ITEM_NAME);
        }
        return (text == null ? stack.getName() : text).getString();
    }

    private static boolean sameLore(ItemStack expected, ItemStack actual) {
        List<Text> expectedLines = expected.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT).lines();
        List<Text> actualLines = actual.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT).lines();
        if (expectedLines.size() != actualLines.size()) return false;
        for (int i = 0; i < expectedLines.size(); i++) {
            if (!expectedLines.get(i).getString().equals(actualLines.get(i).getString())) return false;
        }
        return true;
    }

    private static boolean sameHeadTexture(ProfileComponent expected, ProfileComponent actual) {
        if (Objects.equals(expected, actual)) return true;
        if (expected == null || actual == null) return false;
        var expectedBody = expected.getOverride().body();
        var actualBody = actual.getOverride().body();
        if (expectedBody.isPresent() || actualBody.isPresent()) return expectedBody.equals(actualBody);

        Set<String> expectedTextures = skinTextures(expected);
        return !expectedTextures.isEmpty() && expectedTextures.equals(skinTextures(actual));
    }

    private static Set<String> skinTextures(ProfileComponent profile) {
        return profile.getGameProfile().properties().get("textures").stream()
                .map(InventorySlotRule::skinTexture)
                .collect(Collectors.toSet());
    }

    private static String skinTexture(Property property) {
        String value = property.value();
        try {
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            if (JsonParser.parseString(decoded) instanceof JsonObject payload
                    && payload.get("textures") instanceof JsonObject textures
                    && textures.get("SKIN") instanceof JsonObject skin
                    && skin.get("url") instanceof JsonPrimitive url && url.isString()) {
                String path = url.getAsString();
                return path.startsWith("http://textures.minecraft.net/") ? "https://" + path.substring(7) : path;
            }
        } catch (IllegalArgumentException | JsonParseException ignored) {}
        return value;
    }

    private static boolean isOtherComponent(ComponentType<?> type) {
        return type != DataComponentTypes.CUSTOM_NAME && type != DataComponentTypes.ITEM_NAME
                && type != DataComponentTypes.LORE && type != DataComponentTypes.PROFILE;
    }

    JsonElement encodeHead() {
        if (headComponents == null) return null;
        return ComponentChanges.CODEC.encodeStart(JsonOps.INSTANCE, headComponents).result().orElse(null);
    }

    void loadHead(JsonElement data) {
        if (data == null || !data.isJsonObject() || getItem() != Items.PLAYER_HEAD) return;
        headComponents = ComponentChanges.CODEC.parse(JsonOps.INSTANCE, data).result()
                .map(changes -> changes.withRemovedIf(InventorySlotRule::isOtherComponent)).orElse(null);
        cachedIcon = null;
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
        if (headComponents != null) {
            stack.applyChanges(headComponents);
        } else if (customName != null && !customName.isEmpty()) {
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
        this.headComponents = null;
        this.cachedIcon = null;
    }
}

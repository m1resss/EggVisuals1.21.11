package egg.client.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public class InventoryPreset {
    private String name;
    private final Map<Integer, InventorySlotRule> rules = new LinkedHashMap<>();

    public InventoryPreset(String name) {
        this.name = name;
        for (int i = 0; i <= 40; i++) {
            rules.put(i, new InventorySlotRule(i, "", null, 0));
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public InventorySlotRule getRule(int slot) {
        return rules.computeIfAbsent(slot, s -> new InventorySlotRule(s, "", null, 0));
    }

    public void setRule(int slot, InventorySlotRule rule) {
        rules.put(slot, rule);
    }

    public void setSlotItem(int slot, String itemId, String customName, int count) {
        getRule(slot).setItem(itemId, customName, count);
    }

    public void clearSlot(int slot) {
        getRule(slot).setItem("", null, 0);
    }

    public Map<Integer, InventorySlotRule> getRules() {
        return rules;
    }

    public void snapshotFromPlayer(PlayerInventory playerInv) {
        if (playerInv == null) return;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = playerInv.getStack(i);
            rules.put(i, InventorySlotRule.fromStack(i, stack));
        }

        for (int i = 9; i < 36; i++) {
            ItemStack stack = playerInv.getStack(i);
            rules.put(i, InventorySlotRule.fromStack(i, stack));
        }

        for (int i = 36; i <= 39; i++) {
            ItemStack stack = playerInv.getStack(i);
            rules.put(i, InventorySlotRule.fromStack(i, stack));
        }

        ItemStack offhand = playerInv.getStack(40);
        rules.put(40, InventorySlotRule.fromStack(40, offhand));
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", name);
        JsonArray arr = new JsonArray();
        for (Map.Entry<Integer, InventorySlotRule> entry : rules.entrySet()) {
            InventorySlotRule r = entry.getValue();
            if (r != null && !r.isEmpty()) {
                JsonObject rObj = new JsonObject();
                rObj.addProperty("slot", r.getSlotIndex());
                rObj.addProperty("itemId", r.getItemId());
                if (r.getCustomName() != null) {
                    rObj.addProperty("customName", r.getCustomName());
                }
                rObj.addProperty("count", r.getCount());
                arr.add(rObj);
            }
        }
        obj.add("slots", arr);
        return obj;
    }

    public static InventoryPreset fromJson(JsonObject obj) {
        String name = obj.has("name") ? obj.get("name").getAsString() : "Custom";
        InventoryPreset preset = new InventoryPreset(name);
        if (obj.has("slots") && obj.get("slots").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("slots")) {
                if (el.isJsonObject()) {
                    JsonObject sObj = el.getAsJsonObject();
                    int slot = sObj.has("slot") ? sObj.get("slot").getAsInt() : -1;
                    if (slot >= 0 && slot <= 40) {
                        String itemId = sObj.has("itemId") ? sObj.get("itemId").getAsString() : "";
                        String customName = sObj.has("customName") ? sObj.get("customName").getAsString() : null;
                        int count = sObj.has("count") ? sObj.get("count").getAsInt() : 1;
                        preset.setSlotItem(slot, itemId, customName, count);
                    }
                }
            }
        }
        return preset;
    }
}

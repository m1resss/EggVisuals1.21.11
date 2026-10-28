package delta.config;

import delta.util.StringUtils;
import delta.core.Interface;
import lombok.Generated;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;

public class NBTCondition {
    private final String a;

    @Generated
    public String a() {
        return this.a;
    }

    public NBTCondition(String value) {
        this.a = value;
    }

    public boolean a(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        var ops = Interface.aM_.world != null ? Interface.aM_.world.getRegistryManager().getOps(NbtOps.INSTANCE) : NbtOps.INSTANCE;
        NbtElement nbt = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
        return nbt != null && nbt.toString().replaceAll("§.", "").toLowerCase().replaceAll("\\s+", StringUtils.a).trim().contains(this.a.replaceAll("§.", "").toLowerCase().replaceAll("\\s+", StringUtils.a).trim());
    }
}

package egg.client.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;

public class EntityUtil {

    private EntityUtil() {
    }

    public static boolean isInvisible(Entity entity) {
        if (entity == null) return false;
        if (entity.isInvisible()) return true;
        if (entity instanceof LivingEntity living) {
            return living.hasStatusEffect(StatusEffects.INVISIBILITY);
        }
        return false;
    }

    public static boolean hasArmor(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return !living.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
                    || !living.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                    || !living.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
                    || !living.getEquippedStack(EquipmentSlot.FEET).isEmpty();
        }
        return false;
    }

    public static boolean isNakedInvisible(Entity entity) {
        if (!isInvisible(entity)) return false;
        return !hasArmor(entity);
    }
}

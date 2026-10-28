package egg.client.module.combat;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.util.FriendManager;
import net.minecraft.entity.Entity;

public class NoFriendDamage extends Module {
    public NoFriendDamage() {
        super("No Friend Damage", "Не позволяет наносить урон вашим друзьям", Category.COMBAT);
    }

    public boolean shouldCancelAttack(Entity target) {
        if (!isEnabled() || target == null) return false;
        return FriendManager.getInstance().isFriend(target.getName().getString());
    }
}

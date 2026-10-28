package platform.inject.accessors;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("itemUseCooldown")
    int getItemUseCooldown();

    @Accessor("itemUseCooldown")
    @Mutable
    void setItemUseCooldown(int cooldown);

    @Accessor("attackCooldown")
    int getAttackCooldown();

    @Accessor("attackCooldown")
    @Mutable
    void setAttackCooldown(int cooldown);
}

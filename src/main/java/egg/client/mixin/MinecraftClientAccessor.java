package egg.client.mixin;

import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("itemUseCooldown")
    void setItemUseCooldown(int cooldown);

    @Accessor("itemUseCooldown")
    int getItemUseCooldown();

    @Accessor("session")
    Session getSession();

    @Mutable
    @Accessor("session")
    void setSession(Session session);

    @Accessor("gameProfileFuture")
    CompletableFuture<ProfileResult> getGameProfileFuture();

    @Mutable
    @Accessor("gameProfileFuture")
    void setGameProfileFuture(CompletableFuture<ProfileResult> gameProfileFuture);
}

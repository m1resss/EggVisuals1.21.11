package egg.client.util;

import egg.client.render.msdf.AnimationUtil;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;

public interface IStatusEffectInstance {
    AnimationUtil getAnimation();

    int getInitialDuration();

    void setInitialDuration(int i);

    int getDuration();

    int getAmplifier();

    RegistryEntry<StatusEffect> getEffectType();
}

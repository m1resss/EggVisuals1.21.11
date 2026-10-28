package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class FullBright extends Module {
    private final ModeSetting mode = new ModeSetting("Режим", "Ночное зрение", "Режим полного освещения", "Ночное зрение", "Гамма");

    public FullBright() {
        super("FullBright", "Полное освещение мира без факелов и темноты", Category.ESP);
        addSettings(mode);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled()) {
            if (client.player != null && client.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                StatusEffectInstance effect = client.player.getStatusEffect(StatusEffects.NIGHT_VISION);
                if (effect != null && effect.getDuration() <= 320) {
                    client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
                }
            }
            return;
        }

        if (client.player != null) {
            client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
        }
    }
}

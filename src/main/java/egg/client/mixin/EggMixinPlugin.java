package egg.client.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class EggMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        FabricLoader loader = FabricLoader.getInstance();
        return switch (mixinClassName) {
            case "egg.client.mixin.PearlWorldRendererMixin" -> !loader.isModLoaded("sodium");
            case "egg.client.mixin.SodiumPearlTargetMixin" -> loader.isModLoaded("sodium");
            case "egg.client.mixin.SmoothPlayerAnimationsConfigMixin" -> loader.isModLoaded("spa_packs");
            case "egg.client.mixin.LocomotionConfigMixin", "egg.client.mixin.LocomotionBlockAnimationsMixin" -> loader.isModLoaded("locomotion");
            default -> true;
        };
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}

package platform.inject.mixin;

import delta.core.Interface;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.List;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({DebugHud.class})
public class DebugHudMixin implements Interface {
    @ModifyReturnValue(method = {"getLeftText"}, at = {@At("RETURN")})
    private List<String> onGetLeftText(List<String> original) {
        return original;
    }

    @ModifyReturnValue(method = {"getRightText"}, at = {@At("RETURN")})
    private List<String> onGetRightText(List<String> original) {
        return original;
    }
}

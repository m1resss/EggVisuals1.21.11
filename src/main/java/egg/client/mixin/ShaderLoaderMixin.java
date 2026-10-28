package egg.client.mixin;

import egg.client.render.msdf.pipeline.ShaderStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.GpuDevice;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.client.gl.ShaderSourceGetter;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShaderLoader.class)
public class ShaderLoaderMixin {

    @WrapOperation(
            method = "apply(Lnet/minecraft/client/gl/ShaderLoader$Definitions;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuDevice;precompilePipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/gl/ShaderSourceGetter;)Lcom/mojang/blaze3d/pipeline/CompiledRenderPipeline;")
    )
    private CompiledRenderPipeline delta$wrapPrecompile(GpuDevice instance, RenderPipeline pipeline, ShaderSourceGetter getter, Operation<CompiledRenderPipeline> original) {
        ShaderSourceGetter wrapped = (id, type) -> {
            String custom = ShaderStore.getSource(id, type);
            if (custom != null) {
                return custom;
            }
            return getter.get(id, type);
        };
        return original.call(instance, pipeline, wrapped);
    }

    @Inject(method = "getSource", at = @At("HEAD"), cancellable = true)
    private void delta$provideEmbeddedSource(Identifier id, ShaderType type,
                                             CallbackInfoReturnable<String> cir) {
        String source = ShaderStore.getSource(id, type);
        if (source != null) {
            cir.setReturnValue(source);
        }
    }
}

package platform;


import delta.core.Delta;
import delta.core.NativeMethodLookup;
import delta.api.Compile;
import net.fabricmc.api.ClientModInitializer;

public class Initializer implements ClientModInitializer {
    @Compile
    public void onInitializeClient() {
        delta.render.DeltaWorldRenderTypes.initialize();
        new Delta();
    }

    static {
        NativeMethodLookup.lookup(Initializer.class, 1);
    }
}

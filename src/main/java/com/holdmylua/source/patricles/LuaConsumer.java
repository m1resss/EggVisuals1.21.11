package com.holdmylua.source.patricles;

import com.holdmylua.source.patricles.Particle;
import java.util.function.Consumer;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

public class LuaConsumer
implements Consumer<Particle> {
    private final LuaFunction function;

    public LuaConsumer(LuaFunction function) {
        this.function = function;
    }

    @Override
    public void accept(Particle particle) {
        try {
            this.function.call(CoerceJavaToLua.coerce((Object)particle));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}

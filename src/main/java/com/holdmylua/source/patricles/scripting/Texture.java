package com.holdmylua.source.patricles.scripting;

import com.holdmylua.source.annotation.Safe;
import net.minecraft.util.Identifier;

public class Texture {
    @Safe
    public Identifier of(String namespace, String path) {
        return Identifier.of((String)namespace, (String)path);
    }
}

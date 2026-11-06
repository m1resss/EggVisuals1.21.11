package com.holdmylua.source.scripting.script_wrappers;

import com.holdmylua.source.annotation.Safe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class S {
    @Safe
    public void playSound(String id, double volume) {
        MinecraftClient.getInstance().player.playSound(SoundEvent.of((Identifier)Identifier.ofVanilla((String)id)), (float)volume, 1.0f);
    }
}

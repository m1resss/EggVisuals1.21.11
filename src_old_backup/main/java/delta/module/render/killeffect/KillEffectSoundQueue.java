package delta.module.render.killeffect;

import static delta.core.Interface.aM_;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class KillEffectSoundQueue {
    private final List<PendingSound> pendingSounds = new ArrayList<>();

    public void clear() {
        this.pendingSounds.clear();
    }

    public void schedule(SoundEvent sound, float volume, long delayMs) {
        if (sound == null) {
            return;
        }
        if (delayMs <= 0L) {
            playSound(sound, volume);
            return;
        }
        this.pendingSounds.add(new PendingSound(sound, volume, System.currentTimeMillis() + delayMs));
    }

    public void tick() {
        if (this.pendingSounds.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<PendingSound> iterator = this.pendingSounds.iterator();
        while (iterator.hasNext()) {
            PendingSound pending = iterator.next();
            if (now >= pending.playAt) {
                playSound(pending.sound, pending.volume);
                iterator.remove();
            }
        }
    }

    public static void playSound(SoundEvent sound, float volume) {
        if (aM_.getSoundManager() != null) {
            aM_.getSoundManager().play(PositionedSoundInstance.ambient(sound, 1.0f, volume));
        }
    }

    public static record PendingSound(SoundEvent sound, float volume, long playAt) {}
}

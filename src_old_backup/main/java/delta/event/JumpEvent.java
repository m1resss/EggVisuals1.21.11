package delta.event;

import delta.core.Event;
import delta.core.IEvent;

import lombok.Generated;
import net.minecraft.entity.LivingEntity;

public class JumpEvent extends Event implements IEvent {
    private final LivingEntity a;

    @Generated
    public JumpEvent(LivingEntity livingEntity) {
        this.a = livingEntity;
    }

    @Generated
    public LivingEntity b() {
        return this.a;
    }
}

package delta.event;

import delta.core.Event;
import delta.core.IEvent;

import lombok.Generated;

public class HotbarEvent extends Event implements IEvent {
    private final int a;

    @Generated
    public HotbarEvent(int slot) {
        this.a = slot;
    }

    @Generated
    public int b() {
        return this.a;
    }
}

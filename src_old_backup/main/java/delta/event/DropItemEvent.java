package delta.event;

import delta.core.Event;
import delta.core.IEvent;

import lombok.Generated;

public class DropItemEvent extends Event implements IEvent {
    private int a;

    @Generated
    public DropItemEvent(int slot) {
        this.a = slot;
    }

    @Generated
    public int b() {
        return this.a;
    }
}

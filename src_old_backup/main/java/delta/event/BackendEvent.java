package delta.event;

import delta.core.Event;
import delta.core.IEvent;

import delta.core.Packet;
import lombok.Generated;

public class BackendEvent extends Event implements IEvent {
    private final Packet a;
    private final Phase b;

    public enum Phase {
        RECEIVE,
        CLOSE
    }

    @Generated
    public Packet d() {
        return this.a;
    }

    @Generated
    public Phase e() {
        return this.b;
    }

    public BackendEvent(Packet packet, Phase type) {
        this.a = packet;
        this.b = type;
    }

    public BackendEvent(Phase type) {
        this.b = type;
        this.a = null;
    }

    public boolean b() {
        return this.b == Phase.RECEIVE;
    }

    public boolean c() {
        return this.b == Phase.CLOSE;
    }
}

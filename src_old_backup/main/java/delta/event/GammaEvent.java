package delta.event;

import delta.core.Event;
import delta.core.IEvent;

import lombok.Generated;

public class GammaEvent extends Event implements IEvent {
    private double a;

    @Generated
    public void a(double gamma) {
        this.a = gamma;
    }

    @Generated
    public GammaEvent(double gamma) {
        this.a = gamma;
    }

    @Generated
    public double b() {
        return this.a;
    }
}

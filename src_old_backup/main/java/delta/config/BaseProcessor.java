package delta.config;

import delta.core.EventManager;

import delta.core.Interface;

public abstract class BaseProcessor implements Interface {
    public abstract void setup();

    public abstract void unSetup();

    public BaseProcessor() {
        EventManager.a(this);
    }
}

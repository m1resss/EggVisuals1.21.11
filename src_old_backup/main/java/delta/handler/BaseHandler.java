package delta.handler;


import delta.handler.Handler_2;
import delta.core.EventManager;

public class BaseHandler {
    public BaseHandler() {
        if (!getClass().isAnnotationPresent(Handler_2.class)) {
            throw new IllegalStateException("Обработчик " + getClass().getSimpleName() + " должен иметь @Handler!");
        }
        EventManager.a(this);
    }
}

package delta.module.misc;

import delta.handler.BaseHandler;
import delta.handler.Handler_2;

@Handler_2
public class AFKHandler extends BaseHandler {
    private long a = 0L;

    public boolean a() {
        return System.currentTimeMillis() < this.a;
    }

    public void a(int seconds) {
        this.a = System.currentTimeMillis() + (seconds * 1000L);
    }
}

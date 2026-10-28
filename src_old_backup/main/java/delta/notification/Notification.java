package delta.notification;


import delta.render.AnimationUtil;
import delta.util.CounterUtil;
import lombok.Generated;
import net.minecraft.item.ItemStack;

public class Notification {
    private final AnimationUtil a;
    private final CounterUtil b;
    private final Object c;
    private final Object d;
    private final int e;
    private int f;
    private String title;
    private float animatedY = -1.0f;
    private float animatedX = -1.0f;

    @Generated
    public void a(int time) {
        this.f = time;
    }

    @Generated
    public AnimationUtil a() {
        return this.a;
    }

    @Generated
    public CounterUtil b() {
        return this.b;
    }

    @Generated
    public Object c() {
        return this.c;
    }

    @Generated
    public Object d() {
        return this.d;
    }

    @Generated
    public int e() {
        return this.e;
    }

    @Generated
    public int f() {
        return this.f;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public float getAnimatedY() {
        return this.animatedY;
    }

    public void setAnimatedY(float animatedY) {
        this.animatedY = animatedY;
    }

    public float getAnimatedX() {
        return this.animatedX;
    }

    public void setAnimatedX(float animatedX) {
        this.animatedX = animatedX;
    }

    public Notification(Object symbol, int color, String title, Object message, int time) {
        this.a = new AnimationUtil();
        this.b = new CounterUtil();
        if (!(symbol instanceof String) && !(symbol instanceof ItemStack)) {
            throw new IllegalArgumentException("Icon must be either String or ItemStack");
        }
        this.d = symbol;
        this.e = color;
        this.title = title;
        this.c = message;
        this.f = time;
    }

    public Notification(Object symbol, int color, Object message, int time) {
        this(symbol, color, null, message, time);
    }

    public Notification(Object symbol, Object message, int time) {
        this(symbol, -1, null, message, time);
    }
}

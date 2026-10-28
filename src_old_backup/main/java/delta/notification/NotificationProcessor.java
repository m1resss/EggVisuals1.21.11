package delta.notification;

import delta.core.NativeMethodLookup;
import static delta.core.Interface.aM_;
import delta.render.EasingList;

import delta.config.BaseProcessor;
import delta.core.EventTarget;
import delta.core.Interface;
import delta.event.DrawEvent;
import delta.event.TickEvent;
import delta.notification.Notification;

import delta.api.Compile;
import java.util.ArrayList;
import java.util.List;
import lombok.Generated;
import net.minecraft.client.gui.screen.ChatScreen;

public class NotificationProcessor extends BaseProcessor implements Interface {
    private final Notification b = new Notification("a", -1, "Egg Client", "Пример отображения уведомления", 0);
    private final List<Notification> c = new ArrayList<>();

    @Override
    @Compile
    public void setup() {
    }

    static {
        NativeMethodLookup.lookup(NotificationProcessor.class, 34);
    }

    @Generated
    public Notification a() {
        return this.b;
    }

    @Generated
    public List<Notification> b() {
        return this.c;
    }

    @Override
    public void unSetup() {
    }

    @EventTarget
    public void a(DrawEvent event) {
        if (event.b() && !b().isEmpty()) {
            for (Notification notification : b()) {
                notification.a().a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());
            }
        }
    }

    @EventTarget
    public void a(TickEvent event) {
        List<Notification> notifications = new ArrayList<>(b());
        notifications.remove(this.b);
        boolean preview = (aM_.currentScreen instanceof ChatScreen) && notifications.isEmpty();
        if (!b().contains(this.b)) {
            b().add(this.b);
        }
        for (Notification notification : new ArrayList<>(b())) {
            if (notification == this.b) {
                notification.a().a(preview);
                if (!preview && notification.a().c() == 0.0f) {
                    b().remove(this.b);
                }
            } else {
                boolean finished = notification.b().a(notification.f() - 100);
                notification.a().a(!finished);
                if (finished && notification.a().c() == 0.0f) {
                    b().remove(notification);
                }
            }
        }
    }

    public void a(Notification notification) {
        int time = notification.f();
        notification.a(time + (((int) b().stream().filter(current -> {
            return current.f() >= time;
        }).filter(current2 -> {
            return (current2.f() - time) % 50 == 0;
        }).count()) * 50));
        b().add(notification);

        try {
            delta.ui.widget.NotificationWidget widget = delta.ui.widget.NotificationWidget.getInstance();
            if (widget != null && widget.isSoundEnabled()) {
                aM_.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.ambient(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 1.8f, 1.0f));
            }
        } catch (Exception ignored) {
        }
    }
}

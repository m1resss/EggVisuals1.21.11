package delta.core;







import delta.core.NativeMethodLookup;
import delta.render.ScaleUtil;
import static delta.core.Interface.aM_;
import delta.core.EventManager;
import delta.core.Interface;
import delta.core.Module;
import delta.render.EasingList;

import delta.core.Client;
import delta.core.EventTarget;
import delta.core.Processor_2;
import delta.core.User;
import delta.event.DrawEvent;
import delta.event.KeyEvent;

import delta.api.Compile;
import delta.api.Ultra;
import java.io.File;
import lombok.Generated;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

public class Delta {
    private static Delta a;
    private Processor_2 b;
    private Client d;
    private User e;
    private static volatile User jc$unifiedPendingUser$;
    private static volatile Delta jc$unifiedClient$;

    @Compile
    @Ultra
    protected void a() {
        this.e = new User("1", "cracked by t.me/soezproject", "cracked by t.me/soezproject", "Владелец", "01.01.2099 00:00", "");
        a = this;
        jc$bindUnifiedClient$(this);
        this.b = new Processor_2();
        this.d = new Client(false);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> this.a(client));
        EventManager.a(this);
        this.b.a();
    }

    @Compile
    @Ultra
    protected void b() {
        this.b.b();
    }

    @Compile
    @Ultra
    public String c() {
        if (new File("ide\\fruzek").exists()) {
            return "fruzek";
        }
        if (new File("ide\\dezz").exists()) {
            return "dezz";
        }
        return null;
    }

    static {
        NativeMethodLookup.lookup(Delta.class, 0);
    }

    public static void jc$publishUnifiedUser$(User user) {
        jc$unifiedPendingUser$ = user;
        Delta delta = jc$unifiedClient$;
        if (delta != null) {
            delta.e = user;
        }
    }

    private static void jc$bindUnifiedClient$(Delta delta) {
        jc$unifiedClient$ = delta;
        User user = jc$unifiedPendingUser$;
        if (user != null) {
            delta.e = user;
        }
    }

    @Generated
    public void a(Processor_2 processor) {
        this.b = processor;
    }

    @Generated
    public void a(Client client) {
        this.d = client;
    }

    @Generated
    public void a(User user) {
        this.e = user;
    }

    @Generated
    public static Delta h() {
        return a;
    }

    @Generated
    public Processor_2 d() {
        return this.b;
    }

    @Generated
    public Client f() {
        return this.d;
    }

    @Generated
    public User g() {
        return this.e;
    }

    public Delta() {
        a();
    }

    public void a(MinecraftClient client) {
        b();
    }

    @EventTarget
    public void a(KeyEvent event) {
        if (event.d() == 1 && Interface.aM_.currentScreen == null && event.b() == 344) {
            MinecraftClient mc = Interface.aM_;
            mc.setScreen(new delta.ui.screen.ModernGUIScreen(Text.literal("")));
        }
    }

    @EventTarget(a = 0)
    public void a(DrawEvent event) {
        if (event.b()) {
            ScaleUtil.a(event.i(), 2);
            for (Module module : h().d().t().e()) {
                module.f().a(0.0f, 1.0f, 0.3f, EasingList.i, event.g());
                module.f().a(module.m());
                module.g().a(0.0f, 1.0f, 0.3f, EasingList.i, event.g());
                module.g().a(module.n());
            }
        }
    }

    @EventTarget(a = 4)
    public void b(DrawEvent event) {
        if (event.b()) {
            ScaleUtil.a(event.i());
        }
    }
}

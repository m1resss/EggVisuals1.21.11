package delta.core;

import delta.config.BaseProcessor;
import delta.core.NativeMethodLookup;
import delta.core.Delta;
import delta.lib.log4j.LoggerFactory;

import delta.core.Interface;

import delta.network.AccountProcessor;
import delta.collector.BatchProcessor;
import delta.collector.CollectorProcessor;
import delta.command.CommandProcessor;
import delta.cosmetic.CosmeticsProcessor;
import delta.discord.DiscordProcessor;
import delta.ui.element.DragProcessor;
import delta.render.Draw2DProcessor;
import delta.render.Draw3DProcessor;
import delta.friend.FriendProcessor;
import delta.handler.HandlerProcessor;
import delta.lib.log4j.Logger_2;
import delta.macro.MacrosProcessor;
import delta.config.ModuleProcessor;
import delta.notification.NotificationProcessor;
import delta.config.ResourcePacksProcessor;
import delta.handler.RotationProcessor;
import delta.staff.StaffProcessor;
import delta.config.ThemeProcessor;
import delta.api.Compile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Generated;

public class Processor_2 implements Interface {

    @Generated
    private static final Logger_2 b;
    private final List<BaseProcessor> c;
    private final MacrosProcessor d;
    private final FriendProcessor e;
    private final StaffProcessor f;
    private final DiscordProcessor g;
    private final AccountProcessor h;
    private final Draw2DProcessor i;
    private final Draw3DProcessor j;
    private final RotationProcessor k;
    private final BatchProcessor l;
    private final NotificationProcessor m;
    private final ResourcePacksProcessor n;
    private final ThemeProcessor o;
    private final CollectorProcessor p;
    private final CosmeticsProcessor r;
    private final DragProcessor s;
    private final ModuleProcessor t;
    private final CommandProcessor u;
    private final HandlerProcessor v;

    @Compile
    public void a() {
        Collections.addAll(this.c, this.d, this.p, this.r, this.e, this.m, this.n, this.f, this.o, this.h, this.t, this.k, this.g, this.i, this.s, this.j, this.u, this.l, this.v);
        this.c.forEach(new Consumer() {
            @Override
            public void accept(Object obj) {
                ((BaseProcessor) obj).setup();
            }
        });
        System.out.println("setup - ".concat(String.valueOf(this.c.stream().map(new Function() {
            @Override
            public Object apply(Object obj) {
                return ((BaseProcessor) obj).getClass().getSimpleName();
            }
        }).toList())));
    }

    static {
        NativeMethodLookup.lookup(Processor_2.class, 18);
        b = LoggerFactory.a((Class<?>) Processor_2.class);
    }

    public Processor_2() {
        Delta.h().a(this);
        this.c = new ArrayList();
        this.d = new MacrosProcessor();
        this.e = new FriendProcessor();
        this.f = new StaffProcessor();
        this.g = new DiscordProcessor();
        this.h = new AccountProcessor();
        this.i = new Draw2DProcessor();
        this.j = new Draw3DProcessor();
        this.k = new RotationProcessor();
        this.l = new BatchProcessor();
        this.m = new NotificationProcessor();
        this.n = new ResourcePacksProcessor();
        this.o = new ThemeProcessor();
        this.p = new CollectorProcessor();
        this.r = new CosmeticsProcessor();
        this.s = new DragProcessor();
        this.t = new ModuleProcessor();
        this.u = new CommandProcessor();
        this.v = new HandlerProcessor();
    }

    @Generated
    public List<BaseProcessor> c() {
        return this.c;
    }

    @Generated
    public MacrosProcessor d() {
        return this.d;
    }

    @Generated
    public FriendProcessor e() {
        return this.e;
    }

    @Generated
    public StaffProcessor f() {
        return this.f;
    }

    @Generated
    public DiscordProcessor g() {
        return this.g;
    }

    @Generated
    public AccountProcessor h() {
        return this.h;
    }

    @Generated
    public Draw2DProcessor i() {
        return this.i;
    }

    @Generated
    public Draw3DProcessor j() {
        return this.j;
    }

    @Generated
    public RotationProcessor k() {
        return this.k;
    }

    @Generated
    public BatchProcessor l() {
        return this.l;
    }

    @Generated
    public NotificationProcessor m() {
        return this.m;
    }

    @Generated
    public ResourcePacksProcessor n() {
        return this.n;
    }

    @Generated
    public ThemeProcessor o() {
        return this.o;
    }

    @Generated
    public CollectorProcessor p() {
        return this.p;
    }

    @Generated
    public CosmeticsProcessor r() {
        return this.r;
    }

    @Generated
    public DragProcessor s() {
        return this.s;
    }

    @Generated
    public ModuleProcessor t() {
        return this.t;
    }

    @Generated
    public CommandProcessor u() {
        return this.u;
    }

    @Generated
    public HandlerProcessor v() {
        return this.v;
    }

    public void b() {
        this.c.forEach(processor -> {
            try {
                processor.unSetup();
            } catch (Throwable th) {
            }
        });
        System.out.println("unSetup - " + String.valueOf(this.c.stream().map(processor2 -> {
            return processor2.getClass().getSimpleName();
        }).toList()));
    }
}

package delta.config;

import delta.collector.ServerItem;
import delta.config.ConverterUtil;
import delta.core.NativeMethodLookup;
import delta.core.Module;

import delta.command.CommandProcessor;
import delta.command.LayoutCommand;
import delta.config.ConfigProcessor;
import delta.core.Delta;
import delta.core.EventTarget;
import delta.core.Processor_2;
import delta.event.KeyEvent;
import delta.setting.BindSetting;
import delta.setting.Setting;

import delta.ambience.Ambience;
import delta.render.Animations;
import delta.module.render.AspectRatio;
import delta.ui.screen.AssistantScreen;
import delta.module.player.AucReissue;
import delta.module.player.AutoAccept;
import delta.module.player.AutoAuth;
import delta.module.player.AutoEat;
import delta.module.player.SmartTrashDrop;
import delta.module.misc.ContainerSearcher;
import delta.module.combat.AutoSwap;
import delta.module.render.HitboxColor;
import delta.module.render.BoardSpoofer;
import delta.module.misc.ChatHelper;
import delta.module.render.ChinaHat;
import delta.module.misc.ClanUpgrader;
import delta.module.render.Crosshair;
import delta.module.render.Hitmarker;
import delta.module.player.ElytraHelper;
import delta.module.render.EntityESP;
import delta.module.player.FastEXP;
import delta.module.render.FullBright;
import delta.module.render.HandsShader;
import delta.module.render.Interface_2;
import delta.module.combat.ItemHelper;
import delta.module.render.ItemPhysic;
import delta.module.player.ItemScroller;
import delta.lib.json.JSONArray;
import delta.lib.json.JSONObject;
import delta.module.player.LockSlot;
import delta.module.misc.NoCommands;
import delta.module.movement.NoDelay;
import delta.module.combat.NoFriendDamage;
import delta.module.render.MotionBlur;
import delta.module.render.Particles;
import delta.module.render.Pointers;
import delta.module.render.Predictions;
import delta.ui.screen.RadialScreen;
import delta.module.render.Removals;
import delta.module.misc.ServerAssistant;
import delta.module.render.ShaderESP;
import delta.module.combat.ShiftTAP;
import delta.module.render.ShulkerPreview;
import delta.module.player.SoundReducer;
import delta.module.movement.Sprint;
import delta.module.misc.StreamerMode;
import delta.module.player.Structures;
import delta.module.render.ArrayListModule;
import delta.module.render.HpCounter;
import delta.module.render.KillEffect;
import delta.module.render.SwingAnimation;
import delta.module.render.TargetESP;
import delta.module.combat.TapeMouse;
import delta.module.player.ThirdPerson;
import delta.module.player.UseTracker;
import delta.module.render.ViewModel;
import delta.module.render.SmoothEat;
import delta.module.render.WaterBlobs;
import delta.module.render.Puddles;
import delta.module.render.Trail;
import delta.module.render.BreakBlock;
import delta.api.Compile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.Generated;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.registry.DefaultedRegistry;
import net.minecraft.registry.Registries;

public class ModuleProcessor extends ConfigProcessor<Module> {
    private final Structures j = new Structures();
    private final AutoAuth k = new AutoAuth();
    private final Sprint o = new Sprint();
    private final LockSlot p = new LockSlot();
    private final SoundReducer r = new SoundReducer();
    private final TapeMouse u = new TapeMouse();
    private final ItemScroller w = new ItemScroller();
    private final BoardSpoofer x = new BoardSpoofer();
    private final UseTracker z = new UseTracker();
    private final ShiftTAP A = new ShiftTAP();
    private final ElytraHelper F = new ElytraHelper();
    private final AutoAccept K = new AutoAccept();
    private final AutoSwap L = new AutoSwap();
    private final ThirdPerson M = new ThirdPerson();
    private final Animations Q = new Animations();
    private final SwingAnimation R = new SwingAnimation();
    private final AucReissue S = new AucReissue();
    private final EntityESP aa = new EntityESP();
    private final HitboxColor hitboxColor = new HitboxColor();
    private final NoFriendDamage ac = new NoFriendDamage();
    private final ShaderESP ad = new ShaderESP();
    private final ItemPhysic ag = new ItemPhysic();
    private final Removals ai = new Removals();
    private final ServerAssistant aj = new ServerAssistant();
    private final NoCommands am = new NoCommands();
    private final ViewModel ao = new ViewModel();
    private final ClanUpgrader ar = new ClanUpgrader();
    private final ChatHelper as = new ChatHelper();
    private final Crosshair au = new Crosshair();
    private final ShulkerPreview aw = new ShulkerPreview();
    private final NoDelay ax = new NoDelay();
    private final ChinaHat ay = new ChinaHat();
    private final AspectRatio aB = new AspectRatio();
    private final Predictions aC = new Predictions();
    private final StreamerMode aE = new StreamerMode();
    private final Ambience aF = new Ambience();
    private final FastEXP aI = new FastEXP();
    private final ItemHelper aK = new ItemHelper();
    private final Pointers aL = new Pointers();
    private final FullBright aO = new FullBright();
    private final HandsShader aT = new HandsShader();
    private Interface_2 bd;
    private final HpCounter bf = new HpCounter();
    private final KillEffect bg = new KillEffect();
    private final ArrayListModule bh = new ArrayListModule();
    private final MotionBlur bj = new MotionBlur();
    private final Particles bk = new Particles();
    private final SmoothEat bm = new SmoothEat();
    private final Hitmarker bn = new Hitmarker();
    private final TargetESP targetESP = new TargetESP();
    private final WaterBlobs waterBlobs = new WaterBlobs();
    private final Puddles puddles = new Puddles();
    private final AutoEat autoEat = new AutoEat();
    private final SmartTrashDrop smartTrashDrop = new SmartTrashDrop();
    private final ContainerSearcher containerSearcher = new ContainerSearcher();
    private final Trail trail = new Trail();
    private final BreakBlock breakBlock = new BreakBlock();
    private final delta.module.render.CubesModule cubesModule = new delta.module.render.CubesModule();
    private final delta.module.render.FogModule fogModule = new delta.module.render.FogModule();
    private final delta.module.cosmetics.CosmeticsModule cosmeticsModule = new delta.module.cosmetics.CosmeticsModule();
    private final delta.module.misc.DiscordRPC discordRPC = new delta.module.misc.DiscordRPC();
    private final delta.module.render.AstralParticles astralParticles = new delta.module.render.AstralParticles();
    private final delta.module.render.AstralRain astralRain = new delta.module.render.AstralRain();
    private final delta.module.render.Globals globals = new delta.module.render.Globals();
    private final AssistantScreen assistantScreen = new AssistantScreen(Text.literal("Assistant"));

    private void a(Module... modules) {
        this.d.addAll(Arrays.asList(modules));
    }

    @Override
    protected String b() {
        return "modules.json";
    }

    @Override
    @Compile
    public void setup() {
        this.bd = new Interface_2();
        a(this.o, this.ax, this.L, this.aK, this.ac, this.A, this.u, this.targetESP,
          this.Q, this.bh, this.aB, this.hitboxColor, this.x, this.ay, this.au, this.aa, this.aO, this.aT, this.bn,
          this.bf, this.bd, this.ag, this.bg, this.bj, this.bk, this.aL, this.aC, this.ai, this.ad, this.aw,
          this.bm, this.R, this.ao, this.aF, this.K, this.k, this.F, this.w, this.aI, this.p, this.j, this.r,
          this.M, this.z, this.S, this.as, this.ar, this.am, this.aj, this.aE, this.waterBlobs, this.puddles,
          this.autoEat, this.smartTrashDrop, this.containerSearcher, this.trail, this.breakBlock,
          this.cubesModule, this.fogModule, this.cosmeticsModule, this.discordRPC, this.astralParticles, this.astralRain, this.globals);
        super.setup();
    }

    @Override
    @Compile
    protected List<Module> a(String json) {
        if (json == null || json.isBlank() || json.trim().startsWith("[")) {
            return null;
        }
        JSONObject jSONObject = new JSONObject(json);
        JSONArray jSONArrayI = jSONObject.i("modules");
        if (jSONArrayI == null) {
            throw new NullPointerException();
        }
        for (int i = 0; i < jSONArrayI.a(); i++) {
            final JSONObject jSONObjectJ = jSONArrayI.j(i);
            if (jSONObjectJ == null) {
                throw new NullPointerException();
            }
            final String strL = jSONObjectJ.l("name");
            List<Module> listE = e();
            if (listE == null) {
                throw new NullPointerException();
            }
            Stream<Module> stream = listE.stream();
            Predicate<? super Module> predicate = obj -> ((Module) obj).j().equalsIgnoreCase(strL);
            if (stream == null) {
                throw new NullPointerException();
            }
            Stream<Module> streamFilter = stream.filter(predicate);
            if (streamFilter == null) {
                throw new NullPointerException();
            }
            Optional<Module> optionalFindFirst = streamFilter.findFirst();
            Consumer<? super Module> consumer = obj -> ModuleProcessor.a(jSONObjectJ, (Module) obj);
            if (optionalFindFirst == null) {
                throw new NullPointerException();
            }
            optionalFindFirst.ifPresent(consumer);
        }
        JSONArray jSONArrayY = jSONObject.y("layouts");
        if (jSONArrayY != null) {
            Delta deltaH = Delta.h();
            if (deltaH == null) {
                throw new NullPointerException();
            }
            Processor_2 processor_2D = deltaH.d();
            if (processor_2D == null) {
                throw new NullPointerException();
            }
            CommandProcessor commandProcessorU = processor_2D.u();
            if (commandProcessorU == null) {
                throw new NullPointerException();
            }
            LayoutCommand layoutCommandE = commandProcessorU.e();
            if (layoutCommandE == null) {
                throw new NullPointerException();
            }
            List<LayoutCommand.a> listC = layoutCommandE.c();
            if (listC == null) {
                throw new NullPointerException();
            }
            listC.clear();
            for (int i2 = 0; i2 < jSONArrayY.a(); i2++) {
                JSONObject jSONObjectJ2 = jSONArrayY.j(i2);
                if (jSONObjectJ2 == null) {
                    throw new NullPointerException();
                }
                DefaultedRegistry class_7922Var = Registries.ITEM;
                Identifier class_2960VarMethod_60654 = Identifier.of(jSONObjectJ2.l("item"));
                if (class_7922Var == null) {
                    throw new NullPointerException();
                }
                Item class_1792Var = (Item) class_7922Var.get(class_2960VarMethod_60654);
                if (class_1792Var == null) {
                    throw new NullPointerException();
                }
                listC.add(new LayoutCommand.a(jSONObjectJ2.l("name"), class_1792Var.getDefaultStack(), jSONObjectJ2.h("slot")));
            }
        }
        JSONArray jSONArrayY2 = jSONObject.y("assistant");
        if (jSONArrayY2 != null) {
            final AssistantScreen assistantScreenQ = this.assistantScreen;
            if (assistantScreenQ == null) {
                throw new NullPointerException();
            }
            int iA = assistantScreenQ.a().a();
            assistantScreenQ.a(iA);
            for (int i3 = 0; i3 < jSONArrayY2.a(); i3++) {
                final String strL2 = jSONArrayY2.b(i3);
                Stream stream2 = Arrays.stream(ServerItem.values());
                Predicate predicate2 = obj -> ((ServerItem) obj).b().equalsIgnoreCase(strL2);
                if (stream2 == null) {
                    throw new NullPointerException();
                }
                Stream streamFilter2 = stream2.filter(predicate2);
                if (streamFilter2 == null) {
                    throw new NullPointerException();
                }
                Optional optionalFindFirst2 = streamFilter2.findFirst();
                final int i5 = i3;
                Consumer consumer2 = obj -> assistantScreenQ.a(i5, (ServerItem) obj);
                if (optionalFindFirst2 == null) {
                    throw new NullPointerException();
                }
                optionalFindFirst2.ifPresent(consumer2);
            }
        }
        return new ArrayList<>(e());
    }

    @Override
    @Compile
    protected String a(List<Module> data) {
        JSONArray jSONArray = new JSONArray();
        if (data == null) {
            throw new NullPointerException();
        }
        Iterator<Module> it = data.iterator();
        if (it == null) {
            throw new NullPointerException();
        }
        while (it.hasNext()) {
            Module next = it.next();
            JSONObject jSONObject = new JSONObject();
            if (next != null && !(next instanceof Module)) {
                throw new ClassCastException();
            }
            Module module = next;
            if (module == null) {
                throw new NullPointerException();
            }
            jSONObject.c("name", module.j());
            jSONObject.b("activated", module.m());
            jSONObject.b("bind", module.p());
            JSONObject jSONObject2 = new JSONObject();
            List<Setting<?>> listE = module.e();
            if (listE == null) {
                throw new NullPointerException();
            }
            Iterator<Setting<?>> it2 = listE.iterator();
            if (it2 == null) {
                throw new NullPointerException();
            }
            while (it2.hasNext()) {
                Setting<?> next2 = it2.next();
                if (next2 != null && !(next2 instanceof Setting)) {
                    throw new ClassCastException();
                }
                Setting<?> setting = next2;
                if (setting == null) {
                    throw new NullPointerException();
                }
                if (setting.j()) {
                    jSONObject2.c(setting.i(), ConverterUtil.a(setting));
                }
            }
            jSONObject.c("settings", jSONObject2);
            jSONArray.a(jSONObject);
        }
        JSONArray jSONArray2 = new JSONArray();
        Delta deltaH = Delta.h();
        if (deltaH == null) {
            throw new NullPointerException();
        }
        Processor_2 processor_2D = deltaH.d();
        if (processor_2D == null) {
            throw new NullPointerException();
        }
        CommandProcessor commandProcessorU = processor_2D.u();
        if (commandProcessorU == null) {
            throw new NullPointerException();
        }
        LayoutCommand layoutCommandE = commandProcessorU.e();
        if (layoutCommandE == null) {
            throw new NullPointerException();
        }
        List<LayoutCommand.a> listC = layoutCommandE.c();
        if (listC == null) {
            throw new NullPointerException();
        }
        for (LayoutCommand.a aVar : listC) {
            JSONObject jSONObject3 = new JSONObject();
            if (aVar == null) {
                throw new NullPointerException();
            }
            jSONObject3.c("name", aVar.a());
            DefaultedRegistry class_7922Var = Registries.ITEM;
            ItemStack class_1799VarB = aVar.b();
            if (class_1799VarB == null) {
                throw new NullPointerException();
            }
            Item class_1792VarMethod_7909 = class_1799VarB.getItem();
            if (class_7922Var == null) {
                throw new NullPointerException();
            }
            Identifier class_2960VarMethod_10221 = class_7922Var.getId(class_1792VarMethod_7909);
            if (class_2960VarMethod_10221 == null) {
                throw new NullPointerException();
            }
            jSONObject3.c("item", class_2960VarMethod_10221.toString());
            jSONObject3.b("slot", aVar.c());
            jSONArray2.a(jSONObject3);
        }
        JSONArray jSONArray3 = new JSONArray();
        AssistantScreen assistantScreenQ = this.assistantScreen;
        if (assistantScreenQ != null) {
            final RadialScreen radialScreenA = assistantScreenQ.a();
            if (radialScreenA != null) {
                for (int i = 0; i < radialScreenA.a(); i++) {
                    Stream stream = Arrays.stream(ServerItem.values());
                    final int i2 = i;
                    Predicate predicate = obj -> ((ServerItem) obj).a(radialScreenA.c(i2));
                    if (stream != null) {
                        Stream streamFilter = stream.filter(predicate);
                        if (streamFilter != null) {
                            Optional optionalFindFirst = streamFilter.findFirst();
                            Consumer consumer = obj -> jSONArray3.a(((ServerItem) obj).b());
                            if (optionalFindFirst != null) {
                                optionalFindFirst.ifPresent(consumer);
                            }
                        }
                    }
                }
            }
        }
        JSONObject jSONObject4 = new JSONObject();
        jSONObject4.a("modules", jSONArray);
        jSONObject4.a("layouts", jSONArray2);
        jSONObject4.a("assistant", jSONArray3);
        return jSONObject4.toString(4);
    }

    private static void a(JSONObject jSONObject, Module module) {
        if (jSONObject == null) {
            throw new NullPointerException();
        }
        if (module == null) {
            throw new NullPointerException();
        }
        if (jSONObject.c("activated") && jSONObject.k("activated") != module.m()) {
            module.a();
        }
        if (jSONObject.c("bind")) {
            module.a(jSONObject.h("bind"));
        }
        JSONObject jSONObjectJ = jSONObject.j("settings");
        if (jSONObjectJ == null) {
            throw new NullPointerException();
        }
        for (String str : jSONObjectJ.b()) {
            if (str == null) {
                throw new NullPointerException();
            }
            List<Setting<?>> listE = module.e();
            if (listE == null) {
                throw new NullPointerException();
            }
            Stream<Setting<?>> stream = listE.stream();
            Predicate<? super Setting<?>> predicate = obj -> ((Setting) obj).i().equalsIgnoreCase(str);
            if (stream == null) {
                throw new NullPointerException();
            }
            Stream<Setting<?>> streamFilter = stream.filter(predicate);
            if (streamFilter == null) {
                throw new NullPointerException();
            }
            Optional<Setting<?>> optionalFindFirst = streamFilter.findFirst();
            Consumer<? super Setting<?>> consumer = obj -> ConverterUtil.a((Setting<?>) obj, jSONObjectJ.a(str));
            if (optionalFindFirst == null) {
                throw new NullPointerException();
            }
            optionalFindFirst.ifPresent(consumer);
        }
    }

    public void b(String configName) {
        String fileName = configName.endsWith(".json") ? configName : configName + ".json";
        try {
            File dir = d();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, fileName);
            java.nio.file.Files.writeString(file.toPath(), a(this.d));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean c(String configName) {
        String fileName = configName.endsWith(".json") ? configName : configName + ".json";
        try {
            File file = new File(d(), fileName);
            if (!file.exists()) {
                return false;
            }
            String content = java.nio.file.Files.readString(file.toPath());
            a(content);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean d(String configName) {
        String fileName = configName.endsWith(".json") ? configName : configName + ".json";
        File file = new File(d(), fileName);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    @EventTarget
    public void a(KeyEvent event) {
        if (event == null) {
            throw new NullPointerException();
        }
        List<Module> listE = e();
        if (listE == null) {
            throw new NullPointerException();
        }
        for (Module module : listE) {
            if (module == null) {
                throw new NullPointerException();
            }
            if (module.p() == event.b()) {
                module.a();
            }
            List<Setting<?>> listE2 = module.e();
            if (listE2 == null) {
                throw new NullPointerException();
            }
            Iterator<Setting<?>> it = listE2.iterator();
            if (it == null) {
                throw new NullPointerException();
            }
            while (it.hasNext()) {
                Setting<?> next = it.next();
                if (next != null && !(next instanceof Setting)) {
                    throw new ClassCastException();
                }
                Setting<?> setting = next;
                if (setting == null) {
                    throw new NullPointerException();
                }
                if (setting instanceof BindSetting) {
                    BindSetting bindSetting = (BindSetting) setting;
                    if (bindSetting.c().intValue() == event.b()) {
                        if (event.d() == 1 && bindSetting.k() != null) {
                            bindSetting.k().execute();
                        } else if (event.d() == 0 && bindSetting.l() != null) {
                            bindSetting.l().execute();
                        }
                    }
                }
            }
        }
    }

    @Generated
    public Structures j() {
        return this.j;
    }

    @Generated
    public AutoAuth k() {
        return this.k;
    }

    @Generated
    public Sprint o() {
        return this.o;
    }

    @Generated
    public LockSlot p() {
        return this.p;
    }

    @Generated
    public SoundReducer r() {
        return this.r;
    }


    @Generated
    public TapeMouse u() {
        return this.u;
    }

    @Generated
    public ItemScroller w() {
        return this.w;
    }

    @Generated
    public BoardSpoofer x() {
        return this.x;
    }

    @Generated
    public UseTracker z() {
        return this.z;
    }

    @Generated
    public ShiftTAP A() {
        return this.A;
    }

    @Generated
    public ElytraHelper F() {
        return this.F;
    }

    @Generated
    public AutoAccept K() {
        return this.K;
    }

    @Generated
    public AutoSwap L() {
        return this.L;
    }

    @Generated
    public ThirdPerson M() {
        return this.M;
    }

    @Generated
    public Animations Q() {
        return this.Q;
    }

    @Generated
    public SwingAnimation R() {
        return this.R;
    }

    @Generated
    public AucReissue S() {
        return this.S;
    }


    @Generated
    public EntityESP aa() {
        return this.aa;
    }

    @Generated
    public HitboxColor getHitboxColor() {
        return this.hitboxColor;
    }

    @Generated
    public NoFriendDamage ac() {
        return this.ac;
    }

    @Generated
    public ShaderESP ad() {
        return this.ad;
    }

    @Generated
    public ItemPhysic ag() {
        return this.ag;
    }

    @Generated
    public Removals ai() {
        return this.ai;
    }

    @Generated
    public ServerAssistant aj() {
        return this.aj;
    }

    @Generated
    public NoCommands am() {
        return this.am;
    }

    @Generated
    public ViewModel ao() {
        return this.ao;
    }

    @Generated
    public ClanUpgrader ar() {
        return this.ar;
    }

    @Generated
    public ChatHelper as() {
        return this.as;
    }

    @Generated
    public Crosshair au() {
        return this.au;
    }

    @Generated
    public ShulkerPreview aw() {
        return this.aw;
    }

    @Generated
    public NoDelay ax() {
        return this.ax;
    }

    @Generated
    public ChinaHat ay() {
        return this.ay;
    }

    @Generated
    public AspectRatio aB() {
        return this.aB;
    }

    @Generated
    public Predictions aC() {
        return this.aC;
    }

    @Generated
    public StreamerMode aE() {
        return this.aE;
    }

    @Generated
    public Ambience aF() {
        return this.aF;
    }

    @Generated
    public FastEXP aI() {
        return this.aI;
    }

    @Generated
    public ItemHelper aK() {
        return this.aK;
    }

    @Generated
    public Pointers aL() {
        return this.aL;
    }

    @Generated
    public FullBright aO() {
        return this.aO;
    }

    @Generated
    public HandsShader aT() {
        return this.aT;
    }

    @Generated
    public Interface_2 bd() {
        return this.bd;
    }

    @Generated
    public HpCounter bf() {
        return this.bf;
    }

    @Generated
    public KillEffect bg() {
        return this.bg;
    }

    @Generated
    public ArrayListModule bh() {
        return this.bh;
    }

    @Generated
    public MotionBlur bj() {
        return this.bj;
    }

    @Generated
    public Particles bk() {
        return this.bk;
    }

    @Generated
    public SmoothEat bm() {
        return this.bm;
    }

    @Generated
    public SmoothEat smoothEat() {
        return this.bm;
    }

    @Generated
    public Hitmarker bn() {
        return this.bn;
    }

    @Generated
    public TargetESP getTargetESP() {
        return this.targetESP;
    }

    @Generated
    public AssistantScreen getAssistantScreen() {
        return this.assistantScreen;
    }

    @Generated
    public WaterBlobs getWaterBlobs() {
        return this.waterBlobs;
    }

    @Generated
    public Puddles getPuddles() {
        return this.puddles;
    }

    @Generated
    public AutoEat getAutoEat() {
        return this.autoEat;
    }

    @Generated
    public SmartTrashDrop getSmartTrashDrop() {
        return this.smartTrashDrop;
    }

    @Generated
    public ContainerSearcher getContainerSearcher() {
        return this.containerSearcher;
    }

    @Generated
    public Trail getTrail() {
        return this.trail;
    }

    @Generated
    public BreakBlock getBreakBlock() {
        return this.breakBlock;
    }

    public delta.module.render.AstralParticles getAstralParticles() {
        return this.astralParticles;
    }

    public delta.module.render.AstralRain getAstralRain() {
        return this.astralRain;
    }

    public delta.module.render.Globals getGlobals() {
        return this.globals;
    }

    public void save() {
        unSetup();
    }
}

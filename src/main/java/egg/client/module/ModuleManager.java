package egg.client.module;

import egg.client.module.combat.HitColor;
import egg.client.module.combat.ShiftTAP;
import egg.client.module.combat.TapeMouse;
import egg.client.module.misc.ClientSettings;
import egg.client.module.misc.StreamerMode;
import egg.client.module.movement.NoJumpDelay;
import egg.client.module.player.FastPlace;
import egg.client.module.movement.Sprint;
import egg.client.module.player.*;
import egg.client.module.render.*;
import net.minecraft.client.MinecraftClient;

import java.util.*;

public class ModuleManager {
    private static ModuleManager instance;
    private final List<Module> modules = new ArrayList<>();
    private final Map<Class<? extends Module>, Module> moduleClassMap = new HashMap<>();

    public ModuleManager() {

        register(new HitColor());
        register(new TapeMouse());
        register(new ShiftTAP());
        register(new egg.client.module.combat.NoFriendDamage());
        register(new egg.client.module.combat.AutoSwap());

        register(new Sprint());
        register(new NoJumpDelay());
        register(new FastPlace());

        register(new Interface());
        register(new PotionHUD());
        register(new FullBright());
        register(new NoRender());
        register(new Crosshair());
        register(new Hitmarker());
        register(new ItemPhysic());
        register(new TargetESP());
        register(new ViewModel());
        register(new AspectRatio());
        register(new BreakBlock());
        register(new SmoothEat());
        register(new TotemPopConfetti());
        register(new Trajectories());
        register(new ItemRadius());
        register(new TargetHUD());
        register(new HitSparks());

        register(new egg.client.module.player.InventorySorter());
        register(new AutoRespawn());
        register(new NoChatBackground());
        register(new FastEXP());
        register(new AutoEat());
        register(new AutoAccept());
        register(new ItemScroller());
        register(new SoundReducer());
        register(new ThirdPerson());
        register(new AutoAuth());

        register(new ClientSettings());
        register(new StreamerMode());
        register(new egg.client.module.misc.Globals());
        register(new egg.client.module.render.WorldTweaks());
        register(new egg.client.module.misc.ZakoOptimization());
        register(new egg.client.module.cosmetic.CosmeticsModule());

        /*
                register(new egg.client.module.cosmetic.MeleeSkins());
        */
    }

    public static ModuleManager getInstance() {
        if (instance == null) {
            instance = new ModuleManager();
        }
        return instance;
    }

    private void register(Module module) {
        modules.add(module);
        moduleClassMap.put(module.getClass(), module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getModulesByCategory(Category category) {
        List<Module> list = new ArrayList<>();
        for (Module m : modules) {
            if (m.getCategory() == category) {
                list.add(m);
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        return (T) moduleClassMap.get(clazz);
    }

    public Module getModuleByName(String name) {
        for (Module m : modules) {
            if (m.getName().equalsIgnoreCase(name)) {
                return m;
            }
        }
        return null;
    }

    public void onTick(MinecraftClient client) {
        // egg.client.cases.CaseManager.getInstance().onTick(client);
        for (Module m : modules) {
            if (m.isEnabled()) {
                m.onTick(client);
                if (m instanceof BreakBlock bb) {
                    bb.onTick();
                } else if (m instanceof TargetESP te) {
                    te.onTick();
                } else if (m instanceof egg.client.module.render.WorldTweaks wt) {
                    wt.onTick();
                }
            }
        }
    }

    public void onKey(int key, int action) {
        if (action != 1) return;
        for (Module m : modules) {
            if (m.isBlocked()) continue;
            if (m.getKey() != 0 && m.getKey() == key) {
                if (m instanceof egg.client.module.combat.AutoSwap as) {
                    if (as.isEnabled()) {
                        as.triggerSwap();
                    } else {
                        as.toggle();
                    }
                } else {
                    m.toggle();
                }
            }
        }
    }
}

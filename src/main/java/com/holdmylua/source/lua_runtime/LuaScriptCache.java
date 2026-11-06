package com.holdmylua.source.lua_runtime;

import com.holdmylua.source.global.GlobalsStorage;
import com.holdmylua.source.global.LuaContext;
import com.holdmylua.source.lua_runtime.LuaScriptManager;
import com.holdmylua.source.patricles.Particle;
import com.holdmylua.source.patricles.ParticleManager;
import com.holdmylua.source.patricles.scripting.Texture;
import com.holdmylua.source.scripting.custom_api.KeyBindManager;
import com.holdmylua.source.scripting.script_wrappers.C;
import com.holdmylua.source.scripting.script_wrappers.Easings;
import com.holdmylua.source.scripting.script_wrappers.I;
import com.holdmylua.source.scripting.script_wrappers.JSItems;
import com.holdmylua.source.scripting.script_wrappers.JSTags;
import com.holdmylua.source.scripting.script_wrappers.M;
import com.holdmylua.source.scripting.script_wrappers.P;
import com.holdmylua.source.scripting.script_wrappers.S;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

public class LuaScriptCache {
    public static int swingSpeed = 9;
    private final Globals globals;
    private final LuaValue chunk;
    private boolean canRun = true;
    private static final LuaContext context = new LuaContext();
    private final M mInstance = new M();
    private final I iInstance = new I();
    private final Texture textureInstance = new Texture();
    private final JSItems jsItemsInstance = new JSItems();
    private final JSTags jsTagsInstance = new JSTags();
    private final P pInstance = new P();
    private final Easings easingsInstance = new Easings();
    private final KeyBindManager keyBindManagerInstance = new KeyBindManager();
    private final S sInstance = new S();
    private final C cInstance = new C();
    private final ParticleManager particleManagerInstance = new ParticleManager();

    public LuaScriptCache(String sourceCode) throws IOException {
        this.globals = LuaScriptManager.getInstance().sharedGlobals;
        this.globals.set("M", CoerceJavaToLua.coerce((Object)this.mInstance));
        this.globals.set("I", CoerceJavaToLua.coerce((Object)this.iInstance));
        this.globals.set("Texture", CoerceJavaToLua.coerce((Object)this.textureInstance));
        this.globals.set("Items", CoerceJavaToLua.coerce((Object)this.jsItemsInstance));
        this.globals.set("Tags", CoerceJavaToLua.coerce((Object)this.jsTagsInstance));
        this.globals.set("P", CoerceJavaToLua.coerce((Object)this.pInstance));
        this.globals.set("Easings", CoerceJavaToLua.coerce((Object)this.easingsInstance));
        this.globals.set("KeyBindManager", CoerceJavaToLua.coerce((Object)this.keyBindManagerInstance));
        this.globals.set("S", CoerceJavaToLua.coerce((Object)this.sInstance));
        this.globals.set("C", CoerceJavaToLua.coerce((Object)this.cInstance));
        this.globals.set("particleManager", CoerceJavaToLua.coerce((Object)this.particleManagerInstance));
        this.globals.set("swingSpeed", (LuaValue)LuaValue.valueOf((int)swingSpeed));
        this.globals.set("registry", CoerceJavaToLua.coerce(GlobalsStorage.registry));
        this.globals.set("renderAsBlock", CoerceJavaToLua.coerce(GlobalsStorage.renderAsBlock));
        this.globals.set("translateItem", CoerceJavaToLua.coerce(GlobalsStorage.translateItem));
        this.globals.set("itemSwingSpeed", CoerceJavaToLua.coerce(GlobalsStorage.itemSwingSpeed));
        this.globals.set("animator", CoerceJavaToLua.coerce((Object)GlobalsStorage.modelPartAnimator));
        this.globals.set("renderAsBlock", CoerceJavaToLua.coerce(GlobalsStorage.renderAsBlock));
        this.globals.set("translateItem", CoerceJavaToLua.coerce(GlobalsStorage.translateItem));
        this.globals.set("itemSwingSpeed", CoerceJavaToLua.coerce(GlobalsStorage.itemSwingSpeed));
        this.globals.set("useDuration", CoerceJavaToLua.coerce(GlobalsStorage.useDuration));
        this.globals.set("usingItem", CoerceJavaToLua.coerce(GlobalsStorage.usingItem));
        this.globals.set("debugger", CoerceJavaToLua.coerce((Object)GlobalsStorage.debugTextRenderer));
        this.globals.set("applyBlockRotation", CoerceJavaToLua.coerce(GlobalsStorage.applyBlockRotation));
        this.globals.set("context", CoerceJavaToLua.coerce((Object)context));
        this.chunk = this.globals.load(sourceCode);
    }

    public void execute(MatrixStack matrices, boolean bl, HashMap<String, Object> registry, float swingProgress, ItemStack item, AbstractClientPlayerEntity player, Hand hand, boolean mainHand, float deltaTime, float equipProgress, float mainHandSwingProgress, float offHandSwingProgress, boolean mainHandSwitchEvent, boolean offHandSwitchEvent, boolean swingMHand, boolean swingOHand, boolean interact, boolean blockBreaking, List<Particle> particles) {
        if (!this.canRun) {
            return;
        }
        try {
            context.update(matrices, bl, swingProgress, item, player, hand, mainHand, deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
            this.chunk.invoke();
        }
        catch (Exception e) {
            System.err.println("[HoldMyItems] Lua runtime error: " + e.getMessage());
            SystemToast.show((ToastManager)MinecraftClient.getInstance().getToastManager(), (SystemToast.Type)SystemToast.Type.PACK_LOAD_FAILURE, (Text)Text.of((String)"HMI Lua Runtime error!"), (Text)Text.of((String)e.getMessage()));
            this.canRun = false;
        }
    }
}

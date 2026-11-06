package com.holdmylua.source.lua_runtime.resource_controller;

import com.holdmylua.source.global.GlobalsStorage;
import com.holdmylua.source.lua_runtime.LuaScriptCache;
import com.holdmylua.source.lua_runtime.ModelScriptCache;
import com.holdmylua.source.lua_runtime.ScriptHolder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public class LuaAnimationResourceLoader
implements SimpleSynchronousResourceReloadListener {
    public Identifier getFabricId() {
        return Identifier.of((String)"holdmyitems", (String)"lua_animation_loader");
    }

    private String preprocessScript(String script) {
        if (script == null || script.isEmpty()) {
            return script;
        }
        Pattern globalPattern = Pattern.compile("global\\.(\\w+)\\s*=\\s*([^;]+)\\s*;");
        Pattern placeholderPattern = Pattern.compile("\\$\\{(\\w+)\\}");
        HashSet<String> persistVars = new HashSet<String>();
        StringBuilder processed = new StringBuilder();
        boolean respackoptsLoaded = FabricLoader.getInstance().isModLoaded("respackopts");
        for (String line : script.split("\\r?\\n")) {
            if (!respackoptsLoaded) {
                Matcher placeholderMatcher = placeholderPattern.matcher(line);
                StringBuffer placeholderBuffer = new StringBuffer();
                while (placeholderMatcher.find()) {
                    placeholderMatcher.appendReplacement(placeholderBuffer, "0");
                }
                placeholderMatcher.appendTail(placeholderBuffer);
                line = placeholderBuffer.toString();
            }
            Matcher globalMatcher = globalPattern.matcher(line);
            StringBuffer lineBuffer = new StringBuffer();
            boolean found = false;
            while (globalMatcher.find()) {
                String varName = globalMatcher.group(1);
                String value = globalMatcher.group(2).trim();
                persistVars.add(varName);
                globalMatcher.appendReplacement(lineBuffer, "local " + varName + " = registry:getOrDefault('" + varName + "', " + value + ")");
                found = true;
            }
            globalMatcher.appendTail(lineBuffer);
            if (found) {
                processed.append(lineBuffer.toString());
            } else {
                processed.append(line);
            }
            processed.append("\n");
        }
        if (!persistVars.isEmpty()) {
            processed.append("\n-- PERSIST VARIABLES\n");
            for (String varName : persistVars) {
                processed.append("registry:put('").append(varName).append("', ").append(varName).append(")\n");
            }
        }
        return processed.toString();
    }

    public void reload(ResourceManager manager) {
        GlobalsStorage.renderAsBlock.clear();
        GlobalsStorage.translateItem.clear();
        GlobalsStorage.registry.clear();
        GlobalsStorage.useDuration.clear();
        GlobalsStorage.applyBlockRotation.clear();
        this.loadSingle(manager, "holdmyitems/hand_pose.lua", script -> {
            try {
                ScriptHolder.handScriptCache = new LuaScriptCache((String)script);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        this.loadSingle(manager, "holdmyitems/item_pose.lua", script -> {
            try {
                ScriptHolder.itemScriptCache = new LuaScriptCache((String)script);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        this.loadSingle(manager, "holdmyitems/hand_relative_pose.lua", script -> {
            try {
                ScriptHolder.handRelativeScriptCache = new LuaScriptCache((String)script);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        this.loadSingle(manager, "holdmyitems/item_model.lua", script -> {
            try {
                ScriptHolder.itemModelCache = new ModelScriptCache((String)script);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        ScriptHolder.handAddonsCache = this.loadMultiple(manager, "holdmyitems/hand_addon.lua");
        ScriptHolder.handRelativeAddonsCache = this.loadMultiple(manager, "holdmyitems/hand_relative_addon.lua");
        ScriptHolder.itemAddonsCache = this.loadMultiple(manager, "holdmyitems/item_addon.lua");
        ScriptHolder.itemModelAddonsCache = this.loadMultipleModel(manager, "holdmyitems/item_model_addon.lua");
    }

    private void loadSingle(ResourceManager manager, String path, Consumer<String> consumer) {
        Identifier id = Identifier.of((String)"minecraft", (String)path);
        try {
            Resource resource = manager.getResource(id).orElse(null);
            if (resource == null) {
                consumer.accept("");
                return;
            }
            try (InputStream stream = resource.getInputStream();){
                String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                content = this.preprocessScript(content);
                consumer.accept(content);
            }
        }
        catch (Exception e) {
            consumer.accept("");
            e.printStackTrace();
        }
    }

    private ArrayList<LuaScriptCache> loadMultiple(ResourceManager manager, String path) {
        Identifier id = Identifier.of((String)"minecraft", (String)path);
        ArrayList<LuaScriptCache> caches = new ArrayList<LuaScriptCache>();
        try {
            List<Resource> resources = manager.getAllResources(id);
            for (Resource resource : resources) {
                InputStream stream = resource.getInputStream();
                try {
                    String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    content = this.preprocessScript(content);
                    caches.add(new LuaScriptCache(content));
                }
                finally {
                    if (stream == null) continue;
                    stream.close();
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return caches;
    }

    private ArrayList<ModelScriptCache> loadMultipleModel(ResourceManager manager, String path) {
        Identifier id = Identifier.of((String)"minecraft", (String)path);
        ArrayList<ModelScriptCache> caches = new ArrayList<ModelScriptCache>();
        try {
            List<Resource> resources = manager.getAllResources(id);
            for (Resource resource : resources) {
                InputStream stream = resource.getInputStream();
                try {
                    String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    content = this.preprocessScript(content);
                    caches.add(new ModelScriptCache(content));
                }
                finally {
                    if (stream == null) continue;
                    stream.close();
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return caches;
    }
}

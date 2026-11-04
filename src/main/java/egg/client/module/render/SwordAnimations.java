package egg.client.module.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.ButtonSetting;
import egg.client.ui.overlay.SwordAnimationOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;

public class SwordAnimations extends Module {
    private static SwordAnimations instance;
    private static final File CONFIG_FILE = new File("egg_sword_anim.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class Transform {
        public final float anchorX, anchorY, anchorZ;
        public final float moveX, moveY, moveZ;
        public final float rotX, rotY, rotZ;
        public final float scale;

        public Transform(float anchorX, float anchorY, float anchorZ,
                         float moveX, float moveY, float moveZ,
                         float rotX, float rotY, float rotZ, float scale) {
            this.anchorX = anchorX; this.anchorY = anchorY; this.anchorZ = anchorZ;
            this.moveX = moveX; this.moveY = moveY; this.moveZ = moveZ;
            this.rotX = rotX; this.rotY = rotY; this.rotZ = rotZ;
            this.scale = scale;
        }
    }

    public enum Preset {
        BLOCK_HIT("Block Hit", "Классический блокхит 1.7/1.8",
                0.0f, -0.05f, -0.7f,
                1.05f, -0.7f, -1.1f, -120.0f, -135.0f, -60.0f,
                1.05f, -0.7f, -1.1f, -120.0f, -180.0f, -60.0f,
                true, 1.8f, 1.0f),

        BONK("Bonk", "Удар дубинкой сверху",
                0.0f, -0.4f, -0.65f,
                0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, -65.0f, 0.0f, 0.0f,
                true, 1.7f, 1.0f),

        ROTATE_360("Rotate 360", "Полный оборот меча",
                0.0f, -0.4f, -0.65f,
                0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, -360.0f, 0.0f, 0.0f,
                false, 1.5f, 1.0f),

        FROM_ME("From Me", "Взмах от себя",
                0.0f, 0.0f, -1.1f,
                0.2f, 0.0f, -0.1f, -135.0f, 45.0f, 60.0f,
                0.2f, 0.0f, -0.3f, -180.0f, 45.0f, 60.0f,
                true, 1.8f, 1.0f),

        SLASH("Slash", "Рубящий боковой срез",
                0.0f, -0.25f, -0.6f,
                0.15f, 0.05f, 0.0f, -25.0f, 35.0f, -20.0f,
                -0.35f, -0.2f, -0.25f, -95.0f, -50.0f, 35.0f,
                true, 1.8f, 1.0f),

        SMOOTH_PUSH("Smooth Push", "Плавный тычок вперед",
                0.0f, -0.2f, -0.8f,
                0.0f, 0.0f, 0.0f, -15.0f, 0.0f, 0.0f,
                0.0f, 0.05f, -0.55f, -55.0f, 15.0f, 0.0f,
                true, 2.0f, 1.0f),

        EXHIBITION("Exhibition", "Высокая стойка 1.7",
                0.0f, -0.1f, -0.8f,
                0.5f, -0.4f, -0.6f, -70.0f, -70.0f, -30.0f,
                0.2f, -0.2f, -0.8f, -120.0f, -110.0f, -50.0f,
                true, 1.6f, 1.0f),

        SWIPE("Swipe", "Быстрый горизонтальный срез",
                -0.1f, -0.3f, -0.5f,
                0.2f, 0.0f, 0.0f, 0.0f, 60.0f, 0.0f,
                -0.3f, 0.0f, 0.0f, 0.0f, -60.0f, 0.0f,
                true, 2.2f, 1.0f);

        public final String title;
        public final String desc;
        public final float anchorX, anchorY, anchorZ;
        public final float startMoveX, startMoveY, startMoveZ;
        public final float startRotX, startRotY, startRotZ;
        public final float endMoveX, endMoveY, endMoveZ;
        public final float endRotX, endRotY, endRotZ;
        public final boolean swingBack;
        public final float speed;
        public final float scale;

        Preset(String title, String desc,
               float anchorX, float anchorY, float anchorZ,
               float startMoveX, float startMoveY, float startMoveZ,
               float startRotX, float startRotY, float startRotZ,
               float endMoveX, float endMoveY, float endMoveZ,
               float endRotX, float endRotY, float endRotZ,
               boolean swingBack, float speed, float scale) {
            this.title = title; this.desc = desc;
            this.anchorX = anchorX; this.anchorY = anchorY; this.anchorZ = anchorZ;
            this.startMoveX = startMoveX; this.startMoveY = startMoveY; this.startMoveZ = startMoveZ;
            this.startRotX = startRotX; this.startRotY = startRotY; this.startRotZ = startRotZ;
            this.endMoveX = endMoveX; this.endMoveY = endMoveY; this.endMoveZ = endMoveZ;
            this.endRotX = endRotX; this.endRotY = endRotY; this.endRotZ = endRotZ;
            this.swingBack = swingBack; this.speed = speed; this.scale = scale;
        }
    }

    private final ButtonSetting configureBtn = new ButtonSetting("Настроить", "Открыть панель настройки анимаций меча", () -> {
        SwordAnimationOverlay.open();
    });

    // Flexible parameters
    public Preset currentPreset = Preset.BLOCK_HIT;
    public float anchorX = 0.0f, anchorY = -0.05f, anchorZ = -0.7f;
    public float startMoveX = 1.05f, startMoveY = -0.7f, startMoveZ = -1.1f;
    public float startRotX = -120.0f, startRotY = -135.0f, startRotZ = -60.0f;
    public float endMoveX = 1.05f, endMoveY = -0.7f, endMoveZ = -1.1f;
    public float endRotX = -120.0f, endRotY = -180.0f, endRotZ = -60.0f;
    public boolean swingBack = true;
    public float speed = 1.8f;
    public float scale = 1.0f;
    public boolean onlyAura = false;

    public SwordAnimations() {
        super("Sword Animations", "Кастомные анимации и положение меча при ударе с гибкой настройкой", Category.ESP);
        instance = this;
        addSettings(configureBtn);
        loadConfig();
    }

    public static SwordAnimations getInstance() {
        return instance;
    }

    public Transform calculateTransform(float swingProgress) {
        float f = MathHelper.clamp(swingProgress, 0.0f, 1.0f);
        if (this.swingBack) {
            f = MathHelper.sin(MathHelper.sqrt(f) * (float) Math.PI);
        }

        float mX = MathHelper.lerp(f, startMoveX, endMoveX);
        float mY = MathHelper.lerp(f, startMoveY, endMoveY);
        float mZ = MathHelper.lerp(f, startMoveZ, endMoveZ);

        float rX = MathHelper.lerp(f, startRotX, endRotX);
        float rY = MathHelper.lerp(f, startRotY, endRotY);
        float rZ = MathHelper.lerp(f, startRotZ, endRotZ);

        return new Transform(anchorX, anchorY, anchorZ, mX, mY, mZ, rX, rY, rZ, scale);
    }

    public void applyPreset(Preset p) {
        if (p == null) return;
        this.currentPreset = p;
        this.anchorX = p.anchorX; this.anchorY = p.anchorY; this.anchorZ = p.anchorZ;
        this.startMoveX = p.startMoveX; this.startMoveY = p.startMoveY; this.startMoveZ = p.startMoveZ;
        this.startRotX = p.startRotX; this.startRotY = p.startRotY; this.startRotZ = p.startRotZ;
        this.endMoveX = p.endMoveX; this.endMoveY = p.endMoveY; this.endMoveZ = p.endMoveZ;
        this.endRotX = p.endRotX; this.endRotY = p.endRotY; this.endRotZ = p.endRotZ;
        this.swingBack = p.swingBack;
        this.speed = p.speed;
        this.scale = p.scale;
        saveConfig();
    }

    public boolean isEligibleItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.MAIN_HAND) {
            return false;
        }
        Item item = stack.getItem();
        return item != Items.AIR
                && item != Items.FILLED_MAP
                && item != Items.CROSSBOW
                && item != Items.BOW
                && item != Items.TRIDENT
                && item.getUseAction(stack) != UseAction.DRINK
                && item.getUseAction(stack) != UseAction.EAT;
    }

    public void saveConfig() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("preset", currentPreset != null ? currentPreset.name() : Preset.BLOCK_HIT.name());
            json.addProperty("anchorX", anchorX);
            json.addProperty("anchorY", anchorY);
            json.addProperty("anchorZ", anchorZ);
            json.addProperty("startMoveX", startMoveX);
            json.addProperty("startMoveY", startMoveY);
            json.addProperty("startMoveZ", startMoveZ);
            json.addProperty("startRotX", startRotX);
            json.addProperty("startRotY", startRotY);
            json.addProperty("startRotZ", startRotZ);
            json.addProperty("endMoveX", endMoveX);
            json.addProperty("endMoveY", endMoveY);
            json.addProperty("endMoveZ", endMoveZ);
            json.addProperty("endRotX", endRotX);
            json.addProperty("endRotY", endRotY);
            json.addProperty("endRotZ", endRotZ);
            json.addProperty("swingBack", swingBack);
            json.addProperty("speed", speed);
            json.addProperty("scale", scale);
            json.addProperty("onlyAura", onlyAura);

            try (FileWriter writer = new FileWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Throwable ignored) {}
    }

    public void loadConfig() {
        if (!CONFIG_FILE.exists()) {
            applyPreset(Preset.BLOCK_HIT);
            return;
        }
        try (FileReader reader = new FileReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("preset")) {
                try {
                    currentPreset = Preset.valueOf(json.get("preset").getAsString());
                } catch (Throwable ignored) {}
            }
            if (json.has("anchorX")) anchorX = json.get("anchorX").getAsFloat();
            if (json.has("anchorY")) anchorY = json.get("anchorY").getAsFloat();
            if (json.has("anchorZ")) anchorZ = json.get("anchorZ").getAsFloat();
            if (json.has("startMoveX")) startMoveX = json.get("startMoveX").getAsFloat();
            if (json.has("startMoveY")) startMoveY = json.get("startMoveY").getAsFloat();
            if (json.has("startMoveZ")) startMoveZ = json.get("startMoveZ").getAsFloat();
            if (json.has("startRotX")) startRotX = json.get("startRotX").getAsFloat();
            if (json.has("startRotY")) startRotY = json.get("startRotY").getAsFloat();
            if (json.has("startRotZ")) startRotZ = json.get("startRotZ").getAsFloat();
            if (json.has("endMoveX")) endMoveX = json.get("endMoveX").getAsFloat();
            if (json.has("endMoveY")) endMoveY = json.get("endMoveY").getAsFloat();
            if (json.has("endMoveZ")) endMoveZ = json.get("endMoveZ").getAsFloat();
            if (json.has("endRotX")) endRotX = json.get("endRotX").getAsFloat();
            if (json.has("endRotY")) endRotY = json.get("endRotY").getAsFloat();
            if (json.has("endRotZ")) endRotZ = json.get("endRotZ").getAsFloat();
            if (json.has("swingBack")) swingBack = json.get("swingBack").getAsBoolean();
            if (json.has("speed")) speed = json.get("speed").getAsFloat();
            if (json.has("scale")) scale = json.get("scale").getAsFloat();
            if (json.has("onlyAura")) onlyAura = json.get("onlyAura").getAsBoolean();
        } catch (Throwable ignored) {
            applyPreset(Preset.BLOCK_HIT);
        }
    }
}

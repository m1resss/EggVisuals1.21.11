package delta.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import delta.core.Interface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

public class CustomProfileManager implements Interface {
    private static final CustomProfileManager INSTANCE = new CustomProfileManager();

    private static final Identifier AVATAR_TEX_ID = Identifier.of("delta", "custom_profile/avatar");
    private static final Identifier BANNER_TEX_ID = Identifier.of("delta", "custom_profile/banner");

    private final File configFile = new File("egg_profile.json");

    public static class ProfileSetup {
        public String name;
        public byte[] avatarBytes;
        public byte[] bannerBytes;
        public boolean customColors;
        public int ringColor1;
        public int ringColor2;
        public int extractedColor1;
        public int extractedColor2;
        public Identifier bannerTexId;
        public Identifier avatarTexId;

        public ProfileSetup(String name) {
            this.name = name;
        }
    }

    private final List<ProfileSetup> setups = new ArrayList<>();
    private String currentSetupName = "Дефолт";

    private Identifier customAvatarTexture = null;
    private NativeImageBackedTexture avatarNativeTexture = null;
    private byte[] avatarBytes = null;

    private Identifier customBannerTexture = null;
    private NativeImageBackedTexture bannerNativeTexture = null;
    private byte[] bannerBytes = null;

    private boolean customColors = false; // false = auto extracted from avatar, true = manual picker
    private int ringColor1 = 0xFF3B82F6;
    private int ringColor2 = 0xFF8B5CF6;

    private int extractedColor1 = 0xFF3B82F6;
    private int extractedColor2 = 0xFF8B5CF6;

    public static CustomProfileManager getInstance() {
        return INSTANCE;
    }

    private CustomProfileManager() {
        loadConfig();
    }

    public Identifier getAvatarTexture() {
        return this.customAvatarTexture;
    }

    public Identifier getBannerTexture() {
        return this.customBannerTexture;
    }

    public boolean hasCustomAvatar() {
        return this.customAvatarTexture != null;
    }

    public boolean hasCustomBanner() {
        return this.customBannerTexture != null;
    }

    public boolean isCustomColors() {
        return this.customColors;
    }

    public void setCustomColors(boolean customColors) {
        this.customColors = customColors;
        saveConfig();
    }

    public int getRingColor1() {
        return this.customColors ? this.ringColor1 : this.extractedColor1;
    }

    public void setRingColor1(int col) {
        this.ringColor1 = col;
        saveConfig();
    }

    public int getRingColor2() {
        return this.customColors ? this.ringColor2 : this.extractedColor2;
    }

    public void setRingColor2(int col) {
        this.ringColor2 = col;
        saveConfig();
    }

    private final File avatarCacheFile = new File("egg_custom_avatar.png");
    private final File bannerCacheFile = new File("egg_custom_banner.png");

    public void chooseAvatarFile() {
        openFileDialogAsync("Выберите аватарку", (file) -> {
            try {
                byte[] bytes = convertImageToPng(file);
                if (bytes != null && bytes.length > 0) {
                    try {
                        Files.write(avatarCacheFile.toPath(), bytes);
                    } catch (Throwable ignored) {}
                    setAvatarFromBytes(bytes);
                    saveConfig();
                }
            } catch (Throwable ignored) {}
        });
    }

    public void chooseBannerFile() {
        openFileDialogAsync("Выберите баннер", (file) -> {
            try {
                byte[] bytes = convertImageToPng(file);
                if (bytes != null && bytes.length > 0) {
                    try {
                        Files.write(bannerCacheFile.toPath(), bytes);
                    } catch (Throwable ignored) {}
                    setBannerFromBytes(bytes);
                    saveConfig();
                }
            } catch (Throwable ignored) {}
        });
    }

    public void resetAvatar() {
        try {
            if (avatarCacheFile.exists()) avatarCacheFile.delete();
        } catch (Throwable ignored) {}
        MinecraftClient.getInstance().send(() -> {
            try {
                TextureManager tm = MinecraftClient.getInstance().getTextureManager();
                if (customAvatarTexture != null) {
                    tm.destroyTexture(customAvatarTexture);
                }
                if (avatarNativeTexture != null) {
                    avatarNativeTexture.close();
                    avatarNativeTexture = null;
                }
            } catch (Throwable ignored) {}
            this.customAvatarTexture = null;
            this.avatarBytes = null;
            this.extractedColor1 = 0xFF3B82F6;
            this.extractedColor2 = 0xFF8B5CF6;
        });
        saveConfig();
    }

    public void resetBanner() {
        try {
            if (bannerCacheFile.exists()) bannerCacheFile.delete();
        } catch (Throwable ignored) {}
        MinecraftClient.getInstance().send(() -> {
            try {
                TextureManager tm = MinecraftClient.getInstance().getTextureManager();
                if (customBannerTexture != null) {
                    tm.destroyTexture(customBannerTexture);
                }
                if (bannerNativeTexture != null) {
                    bannerNativeTexture.close();
                    bannerNativeTexture = null;
                }
            } catch (Throwable ignored) {}
            this.customBannerTexture = null;
            this.bannerBytes = null;
        });
        saveConfig();
    }

    private void setAvatarFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;
        this.avatarBytes = bytes;
        MinecraftClient.getInstance().send(() -> {
            try {
                NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
                if (img != null) {
                    extractColorsFromAvatar(img);
                    TextureManager tm = MinecraftClient.getInstance().getTextureManager();
                    if (customAvatarTexture != null) {
                        tm.destroyTexture(customAvatarTexture);
                    }
                    if (avatarNativeTexture != null) {
                        avatarNativeTexture.close();
                    }
                    avatarNativeTexture = new NativeImageBackedTexture(() -> "custom_avatar", img);
                    Identifier newId = Identifier.of("delta", "custom_profile/avatar_" + System.currentTimeMillis());
                    tm.registerTexture(newId, avatarNativeTexture);
                    this.customAvatarTexture = newId;
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    private void setBannerFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;
        this.bannerBytes = bytes;
        MinecraftClient.getInstance().send(() -> {
            try {
                NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
                if (img != null) {
                    TextureManager tm = MinecraftClient.getInstance().getTextureManager();
                    if (customBannerTexture != null) {
                        tm.destroyTexture(customBannerTexture);
                    }
                    if (bannerNativeTexture != null) {
                        bannerNativeTexture.close();
                    }
                    bannerNativeTexture = new NativeImageBackedTexture(() -> "custom_banner", img);
                    Identifier newId = Identifier.of("delta", "custom_profile/banner_" + System.currentTimeMillis());
                    tm.registerTexture(newId, bannerNativeTexture);
                    this.customBannerTexture = newId;
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    private byte[] convertImageToPng(File file) {
        try {
            BufferedImage img = ImageIO.read(file);
            if (img != null) {
                BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = argb.createGraphics();
                g.drawImage(img, 0, 0, null);
                g.dispose();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(argb, "png", baos);
                return baos.toByteArray();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void extractColorsFromAvatar(NativeImage img) {
        try {
            int w = img.getWidth();
            int h = img.getHeight();
            int stepX = Math.max(1, w / 24);
            int stepY = Math.max(1, h / 24);

            long rSumTop = 0, gSumTop = 0, bSumTop = 0, countTop = 0;
            long rSumBot = 0, gSumBot = 0, bSumBot = 0, countBot = 0;

            for (int y = 0; y < h; y += stepY) {
                boolean isTop = y < h / 2;
                for (int x = 0; x < w; x += stepX) {
                    int c = img.getColorArgb(x, y);
                    int a = (c >> 24) & 0xFF;
                    if (a < 30) continue;
                    int r = (c >> 16) & 0xFF;
                    int g = (c >> 8) & 0xFF;
                    int b = c & 0xFF;

                    if (isTop) {
                        rSumTop += r;
                        gSumTop += g;
                        bSumTop += b;
                        countTop++;
                    } else {
                        rSumBot += r;
                        gSumBot += g;
                        bSumBot += b;
                        countBot++;
                    }
                }
            }

            if (countTop > 0) {
                int r1 = (int) (rSumTop / countTop);
                int g1 = (int) (gSumTop / countTop);
                int b1 = (int) (bSumTop / countTop);
                this.extractedColor1 = (0xFF << 24) | (r1 << 16) | (g1 << 8) | b1;
            } else {
                this.extractedColor1 = 0xFF3B82F6;
            }

            if (countBot > 0) {
                int r2 = (int) (rSumBot / countBot);
                int g2 = (int) (gSumBot / countBot);
                int b2 = (int) (bSumBot / countBot);
                this.extractedColor2 = (0xFF << 24) | (r2 << 16) | (g2 << 8) | b2;
            } else {
                this.extractedColor2 = 0xFF8B5CF6;
            }

            // Ensure distinct colors if top and bottom are identical
            if (this.extractedColor1 == this.extractedColor2) {
                float[] hsb = Color.RGBtoHSB((this.extractedColor1 >> 16) & 0xFF, (this.extractedColor1 >> 8) & 0xFF, this.extractedColor1 & 0xFF, null);
                this.extractedColor2 = 0xFF000000 | Color.HSBtoRGB((hsb[0] + 0.15f) % 1.0f, hsb[1], Math.max(0.4f, hsb[2]));
            }
        } catch (Throwable ignored) {}
    }

    private void openFileDialogAsync(String title, java.util.function.Consumer<File> onSelected) {
        new Thread(() -> {
            String path = null;

            // 1. Windows Native TopMost OpenFileDialog via PowerShell (100% reliable on Windows)
            try {
                File scriptFile = new File("choose_image.ps1");
                if (!scriptFile.exists()) {
                    String scriptContent = "Add-Type -AssemblyName System.Windows.Forms\n"
                            + "$form = New-Object System.Windows.Forms.Form\n"
                            + "$form.TopMost = $true\n"
                            + "$d = New-Object System.Windows.Forms.OpenFileDialog\n"
                            + "if ($args.Count -gt 0) { $d.Title = $args[0] } else { $d.Title = 'Выберите изображение' }\n"
                            + "$d.Filter = 'Изображения (*.png;*.jpg;*.jpeg;*.bmp)|*.png;*.jpg;*.jpeg;*.bmp|Все файлы (*.*)|*.*'\n"
                            + "$d.Multiselect = $false\n"
                            + "if ($d.ShowDialog($form) -eq [System.Windows.Forms.DialogResult]::OK) {\n"
                            + "    [Console]::WriteLine($d.FileName)\n"
                            + "}\n"
                            + "$form.Dispose()\n";
                    Files.writeString(scriptFile.toPath(), scriptContent, StandardCharsets.UTF_8);
                }

                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", scriptFile.getAbsolutePath(), title);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && new File(line).exists()) {
                            path = line;
                            break;
                        }
                    }
                }
                process.waitFor();
            } catch (Throwable t) {
                t.printStackTrace();
            }

            // 2. Fallback: TinyFileDialogs
            if (path == null || path.trim().isEmpty()) {
                try {
                    path = TinyFileDialogs.tinyfd_openFileDialog(
                            title,
                            "",
                            null,
                            "Изображения (*.png, *.jpg, *.jpeg, *.bmp)",
                            false
                    );
                } catch (Throwable ignored) {}
            }

            // 3. Fallback: AWT FileDialog
            if (path == null || path.trim().isEmpty()) {
                try {
                    FileDialog dialog = new FileDialog((Frame) null, title, FileDialog.LOAD);
                    dialog.setFile("*.png");
                    dialog.setVisible(true);
                    if (dialog.getFile() != null && dialog.getDirectory() != null) {
                        path = new File(dialog.getDirectory(), dialog.getFile()).getAbsolutePath();
                    }
                } catch (Throwable ignored) {}
            }

            if (path != null && !path.trim().isEmpty()) {
                File file = new File(path);
                if (file.exists() && file.isFile()) {
                    try {
                        onSelected.accept(file);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        }, "ProfileFileDialogThread").start();
    }

    private void loadConfig() {
        if (avatarCacheFile.exists()) {
            try {
                byte[] b = Files.readAllBytes(avatarCacheFile.toPath());
                setAvatarFromBytes(b);
            } catch (Throwable ignored) {}
        }
        if (bannerCacheFile.exists()) {
            try {
                byte[] b = Files.readAllBytes(bannerCacheFile.toPath());
                setBannerFromBytes(b);
            } catch (Throwable ignored) {}
        }

        if (!configFile.exists()) return;
        try (FileReader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("custom_colors")) {
                this.customColors = json.get("custom_colors").getAsBoolean();
            }
            if (json.has("ring_col1")) {
                this.ringColor1 = json.get("ring_col1").getAsInt();
            }
            if (json.has("ring_col2")) {
                this.ringColor2 = json.get("ring_col2").getAsInt();
            }
            if (json.has("extracted_col1")) {
                this.extractedColor1 = json.get("extracted_col1").getAsInt();
            }
            if (json.has("extracted_col2")) {
                this.extractedColor2 = json.get("extracted_col2").getAsInt();
            }
            if (json.has("current_setup_name")) {
                this.currentSetupName = json.get("current_setup_name").getAsString();
            }
            if (this.avatarBytes == null && json.has("avatar_data")) {
                String b64 = json.get("avatar_data").getAsString();
                byte[] b = Base64.getDecoder().decode(b64);
                setAvatarFromBytes(b);
            }
            if (this.bannerBytes == null && json.has("banner_data")) {
                String b64 = json.get("banner_data").getAsString();
                byte[] b = Base64.getDecoder().decode(b64);
                setBannerFromBytes(b);
            }

            // Load presets
            if (json.has("setups")) {
                this.setups.clear();
                JsonArray arr = json.getAsJsonArray("setups");
                int idx = 0;
                for (JsonElement el : arr) {
                    try {
                        JsonObject obj = el.getAsJsonObject();
                        String name = obj.get("name").getAsString();
                        ProfileSetup ps = new ProfileSetup(name);
                        ps.customColors = obj.has("custom_colors") && obj.get("custom_colors").getAsBoolean();
                        ps.ringColor1 = obj.has("ring_col1") ? obj.get("ring_col1").getAsInt() : 0xFF3B82F6;
                        ps.ringColor2 = obj.has("ring_col2") ? obj.get("ring_col2").getAsInt() : 0xFF8B5CF6;
                        ps.extractedColor1 = obj.has("extracted_col1") ? obj.get("extracted_col1").getAsInt() : ps.ringColor1;
                        ps.extractedColor2 = obj.has("extracted_col2") ? obj.get("extracted_col2").getAsInt() : ps.ringColor2;

                        if (obj.has("avatar_data")) {
                            ps.avatarBytes = Base64.getDecoder().decode(obj.get("avatar_data").getAsString());
                            ps.avatarTexId = Identifier.of("delta", "custom_profile/setup_av_" + idx);
                            registerTextureBytes(ps.avatarTexId, ps.avatarBytes);
                        }
                        if (obj.has("banner_data")) {
                            ps.bannerBytes = Base64.getDecoder().decode(obj.get("banner_data").getAsString());
                            ps.bannerTexId = Identifier.of("delta", "custom_profile/setup_ban_" + idx);
                            registerTextureBytes(ps.bannerTexId, ps.bannerBytes);
                        }
                        this.setups.add(ps);
                        idx++;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private void registerTextureBytes(Identifier id, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;
        MinecraftClient.getInstance().send(() -> {
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                NativeImageBackedTexture nativeTex = new NativeImageBackedTexture(() -> "custom_id", image);
                MinecraftClient.getInstance().getTextureManager().registerTexture(id, nativeTex);
            } catch (Throwable ignored) {}
        });
    }

    public List<ProfileSetup> getSetups() {
        return this.setups;
    }

    public String getCurrentSetupName() {
        return this.currentSetupName;
    }

    public void setCurrentSetupName(String name) {
        this.currentSetupName = name;
        saveConfig();
    }

    public void saveCurrentAsSetup(String name) {
        if (name == null || name.trim().isEmpty()) {
            name = "Сетап " + (this.setups.size() + 1);
        }
        name = name.trim();

        ProfileSetup target = null;
        for (ProfileSetup s : this.setups) {
            if (s.name.equalsIgnoreCase(name)) {
                target = s;
                break;
            }
        }
        if (target == null) {
            target = new ProfileSetup(name);
            this.setups.add(target);
        }

        target.avatarBytes = this.avatarBytes;
        target.bannerBytes = this.bannerBytes;
        target.customColors = this.customColors;
        target.ringColor1 = this.ringColor1;
        target.ringColor2 = this.ringColor2;
        target.extractedColor1 = this.extractedColor1;
        target.extractedColor2 = this.extractedColor2;

        int idx = this.setups.indexOf(target);
        if (target.avatarBytes != null) {
            target.avatarTexId = Identifier.of("delta", "custom_profile/setup_av_" + idx);
            registerTextureBytes(target.avatarTexId, target.avatarBytes);
        }
        if (target.bannerBytes != null) {
            target.bannerTexId = Identifier.of("delta", "custom_profile/setup_ban_" + idx);
            registerTextureBytes(target.bannerTexId, target.bannerBytes);
        }

        this.currentSetupName = name;
        saveConfig();
    }

    public void applySetup(ProfileSetup setup) {
        if (setup == null) return;
        this.currentSetupName = setup.name;
        this.customColors = setup.customColors;
        this.ringColor1 = setup.ringColor1;
        this.ringColor2 = setup.ringColor2;
        this.extractedColor1 = setup.extractedColor1;
        this.extractedColor2 = setup.extractedColor2;

        if (setup.avatarBytes != null && setup.avatarBytes.length > 0) {
            try {
                Files.write(avatarCacheFile.toPath(), setup.avatarBytes);
            } catch (Throwable ignored) {}
            setAvatarFromBytes(setup.avatarBytes);
        } else {
            resetAvatar();
        }

        if (setup.bannerBytes != null && setup.bannerBytes.length > 0) {
            try {
                Files.write(bannerCacheFile.toPath(), setup.bannerBytes);
            } catch (Throwable ignored) {}
            setBannerFromBytes(setup.bannerBytes);
        } else {
            resetBanner();
        }

        saveConfig();
    }

    public void deleteSetup(ProfileSetup setup) {
        if (setup != null) {
            MinecraftClient.getInstance().send(() -> {
                try {
                    TextureManager tm = MinecraftClient.getInstance().getTextureManager();
                    if (setup.avatarTexId != null) {
                        tm.destroyTexture(setup.avatarTexId);
                    }
                    if (setup.bannerTexId != null) {
                        tm.destroyTexture(setup.bannerTexId);
                    }
                } catch (Throwable ignored) {}
            });
            this.setups.remove(setup);
            if (this.currentSetupName != null && this.currentSetupName.equalsIgnoreCase(setup.name)) {
                this.currentSetupName = this.setups.isEmpty() ? "Дефолт" : this.setups.get(0).name;
            }
            saveConfig();
        }
    }

    public void saveConfig() {
        try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
            JsonObject json = new JsonObject();
            json.addProperty("custom_colors", this.customColors);
            json.addProperty("ring_col1", this.ringColor1);
            json.addProperty("ring_col2", this.ringColor2);
            json.addProperty("extracted_col1", this.extractedColor1);
            json.addProperty("extracted_col2", this.extractedColor2);
            json.addProperty("current_setup_name", this.currentSetupName);

            if (this.avatarBytes != null && this.avatarBytes.length > 0) {
                json.addProperty("avatar_data", Base64.getEncoder().encodeToString(this.avatarBytes));
            }
            if (this.bannerBytes != null && this.bannerBytes.length > 0) {
                json.addProperty("banner_data", Base64.getEncoder().encodeToString(this.bannerBytes));
            }

            JsonArray arr = new JsonArray();
            for (ProfileSetup s : this.setups) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", s.name);
                obj.addProperty("custom_colors", s.customColors);
                obj.addProperty("ring_col1", s.ringColor1);
                obj.addProperty("ring_col2", s.ringColor2);
                obj.addProperty("extracted_col1", s.extractedColor1);
                obj.addProperty("extracted_col2", s.extractedColor2);
                if (s.avatarBytes != null && s.avatarBytes.length > 0) {
                    obj.addProperty("avatar_data", Base64.getEncoder().encodeToString(s.avatarBytes));
                }
                if (s.bannerBytes != null && s.bannerBytes.length > 0) {
                    obj.addProperty("banner_data", Base64.getEncoder().encodeToString(s.bannerBytes));
                }
                arr.add(obj);
            }
            json.add("setups", arr);

            writer.write(json.toString());
        } catch (Throwable ignored) {}
    }
}

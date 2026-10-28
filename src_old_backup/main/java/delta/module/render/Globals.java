package delta.module.render;

import static delta.core.Interface.aM_;

import delta.config.ThemeInfo;
import delta.core.Category;
import delta.core.Delta;
import delta.core.EventTarget;
import delta.core.Interface;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.DrawEvent;
import delta.event.TickEvent;
import delta.notification.Notification;
import delta.render.ColorUtil;
import delta.setting.BooleanSetting;
import delta.setting.ModeSetting;
import delta.setting.SliderSetting;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ModuleRegister(a = "Globals", b = "Отображает пиксельное яйцо визуала перед никами других пользователей визуала", c = Category.Render)
public class Globals extends Module implements Interface {

    private static Globals instance;

    private final ModeSetting networkMode = new ModeSetting("Режим сети", "Облако", "Облако", "Локальный");
    private final BooleanSetting globalSync = new BooleanSetting("Все серверы (Глобально)", false);
    private final BooleanSetting markSelf = new BooleanSetting("Себя помечать", true);
    private final BooleanSetting renderInESP = new BooleanSetting("Отображать в ESP", true);
    private final BooleanSetting renderIn3D = new BooleanSetting("Отображать в 3D", true);
    private final BooleanSetting renderInTab = new BooleanSetting("Отображать в TAB", true);
    private final BooleanSetting notifyPeer = new BooleanSetting("Уведомление о собрате", true);
    private final BooleanSetting syncCosmetics = new BooleanSetting("Синхронизация косметики", true);

    private final SliderSetting logoScale = new SliderSetting("Размер яйца", 1.0f, 0.6f, 1.6f, 0.05f);
    private final BooleanSetting glowEffect = new BooleanSetting("Свечение", true);
    private final ModeSetting glowColorMode = new ModeSetting("Цвет свечения", "Тема", "Тема", "Золотой", "Фиолетовый", "Неон", "Белый");

    private final GlobalsNetClient netClient;
    private final Map<String, Long> peersByName = new ConcurrentHashMap<>();
    private final Map<UUID, Long> peersByUuid = new ConcurrentHashMap<>();
    private final Set<String> notifiedPeers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static class PeerCosmetics {
        public final String wings;
        public final String backpacks;
        public final String hats;
        public final String masks;
        public final String pets;
        public final String sword;

        public PeerCosmetics(String wings, String backpacks, String hats, String masks, String pets, String sword) {
            this.wings = wings != null ? wings : "";
            this.backpacks = backpacks != null ? backpacks : "";
            this.hats = hats != null ? hats : "";
            this.masks = masks != null ? masks : "";
            this.pets = pets != null ? pets : "";
            this.sword = sword != null ? sword : "";
        }
    }

    private final Map<String, PeerCosmetics> peerCosmeticsByName = new ConcurrentHashMap<>();
    private final Map<UUID, PeerCosmetics> peerCosmeticsByUuid = new ConcurrentHashMap<>();

    private String lastKnownServer = "";

    // 9x11 Pixel Egg sprite
    // ' ' = transparent
    // '#' = dark outline
    // '*' = eggshell base
    // 'W' = shine / reflection
    // 'Y' = cute small golden yolk star / detail
    private static final String[] PIXEL_EGG = new String[]{
            "   ###   ",
            "  #***#  ",
            " #*W***# ",
            " #*WW**# ",
            "#*W***Y*#",
            "#*****Y*#",
            "#****YY*#",
            "#*******#",
            " #*****# ",
            "  #***#  ",
            "   ###   "
    };

    public Globals() {
        instance = this;
        this.netClient = new GlobalsNetClient(this);
        a(this.networkMode, this.globalSync, this.syncCosmetics, this.markSelf, this.renderInESP,
          this.renderIn3D, this.renderInTab, this.notifyPeer,
          this.logoScale, this.glowEffect, this.glowColorMode);
    }

    public static Globals getInstance() {
        return instance;
    }

    public boolean isSyncCosmetics() {
        return this.syncCosmetics.c();
    }

    public PeerCosmetics getPeerCosmetics(Entity entity) {
        if (!m() || !this.syncCosmetics.c()) return null;
        if (entity instanceof PlayerEntity player) {
            PeerCosmetics c = this.peerCosmeticsByUuid.get(player.getUuid());
            if (c == null) {
                c = this.peerCosmeticsByName.get(player.getName().getString().toLowerCase());
            }
            return c;
        }
        return null;
    }

    @Override
    public void b() {
        super.b();
        this.peersByName.clear();
        this.peersByUuid.clear();
        this.peerCosmeticsByName.clear();
        this.peerCosmeticsByUuid.clear();
        this.notifiedPeers.clear();
        if (this.networkMode.l("Облако")) {
            this.netClient.start();
        }
    }

    @Override
    public void c() {
        super.c();
        this.netClient.stop();
        this.peersByName.clear();
        this.peersByUuid.clear();
        this.peerCosmeticsByName.clear();
        this.peerCosmeticsByUuid.clear();
        this.notifiedPeers.clear();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!m()) return;

        if (this.networkMode.l("Облако")) {
            this.netClient.start();
        } else {
            this.netClient.stop();
        }

        // Server detection
        String currentServer = "singleplayer";
        if (aM_.player != null && !aM_.isInSingleplayer() && aM_.getCurrentServerEntry() != null) {
            currentServer = aM_.getCurrentServerEntry().address;
        }

        if (!currentServer.equals(this.lastKnownServer)) {
            this.lastKnownServer = currentServer;
            this.peersByName.clear();
            this.peersByUuid.clear();
            this.peerCosmeticsByName.clear();
            this.peerCosmeticsByUuid.clear();
            this.notifiedPeers.clear();
            this.netClient.updateServer(currentServer);
        }

        // Prune expired peers (>25 seconds)
        long now = System.currentTimeMillis();
        this.peersByName.entrySet().removeIf(entry -> {
            boolean expired = (now - entry.getValue() > 25000L);
            if (expired) {
                this.peerCosmeticsByName.remove(entry.getKey());
            }
            return expired;
        });
        this.peersByUuid.entrySet().removeIf(entry -> {
            boolean expired = (now - entry.getValue() > 25000L);
            if (expired) {
                this.peerCosmeticsByUuid.remove(entry.getKey());
            }
            return expired;
        });
    }

    public void onMessageReceived(String topic, String payload) {
        if (!m() || payload == null || payload.trim().isEmpty()) return;

        try {
            String[] parts = payload.split(":", -1);
            if (parts.length >= 2) {
                String name = parts[0].trim();
                String uuidStr = parts[1].trim();

                if (aM_.player != null && name.equalsIgnoreCase(aM_.player.getName().getString())) {
                    return; // Ignore own echoes
                }

                long now = System.currentTimeMillis();
                this.peersByName.put(name.toLowerCase(), now);

                UUID peerUuid = null;
                try {
                    peerUuid = UUID.fromString(uuidStr);
                    this.peersByUuid.put(peerUuid, now);
                } catch (Exception ignored) {
                }

                if (parts.length >= 5) {
                    String cData = parts[4].trim();
                    if (!cData.isEmpty()) {
                        String[] cParts = cData.split(";", -1);
                        String wings = cParts.length > 0 ? cParts[0] : "";
                        String backpacks = cParts.length > 1 ? cParts[1] : "";
                        String hats = cParts.length > 2 ? cParts[2] : "";
                        String masks = cParts.length > 3 ? cParts[3] : "";
                        String pets = cParts.length > 4 ? cParts[4] : "";
                        String sword = cParts.length > 5 ? cParts[5] : "";
                        PeerCosmetics pc = new PeerCosmetics(wings, backpacks, hats, masks, pets, sword);
                        this.peerCosmeticsByName.put(name.toLowerCase(), pc);
                        if (peerUuid != null) {
                            this.peerCosmeticsByUuid.put(peerUuid, pc);
                        }
                    }
                }

                if (this.notifyPeer.c() && !this.notifiedPeers.contains(name.toLowerCase())) {
                    this.notifiedPeers.add(name.toLowerCase());
                    if (Delta.h() != null && Delta.h().d() != null && Delta.h().d().m() != null) {
                        Delta.h().d().m().a(new Notification(
                                "🥚",
                                ColorUtil.a(255, 215, 0, 255),
                                "Globals",
                                "Собрат найден: " + name,
                                3000
                        ));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public boolean isGlobalUser(Entity entity) {
        if (!m()) return false;
        if (entity instanceof PlayerEntity player) {
            if (player == aM_.player) {
                return this.markSelf.c();
            }
            return isGlobalUser(player.getName().getString(), player.getUuid());
        }
        return false;
    }

    public boolean isGlobalUser(PlayerEntityRenderState state) {
        if (!m()) return false;
        if (aM_.world != null) {
            Entity entity = aM_.world.getEntityById(state.id);
            if (entity != null) {
                return isGlobalUser(entity);
            }
        }
        return false;
    }

    public boolean isGlobalUser(String name, UUID uuid) {
        if (!m()) return false;

        if (aM_.player != null) {
            if (name != null && name.equalsIgnoreCase(aM_.player.getName().getString())) {
                return this.markSelf.c();
            }
            if (uuid != null && uuid.equals(aM_.player.getUuid())) {
                return this.markSelf.c();
            }
        }

        long now = System.currentTimeMillis();
        if (name != null) {
            Long seen = this.peersByName.get(name.toLowerCase());
            if (seen != null && (now - seen) < 25000L) {
                return true;
            }
        }

        if (uuid != null) {
            Long seen = this.peersByUuid.get(uuid);
            if (seen != null && (now - seen) < 25000L) {
                return true;
            }
        }

        return false;
    }

    public float getESPLogoWidth(float textHeight, float fontSize) {
        float scale = this.logoScale.c();
        float eggH = textHeight * scale * 0.95f;
        float eggW = eggH * (9.0f / 11.0f);
        return eggW;
    }

    public float renderESPLogo(DrawEvent event, float x, float y, float textHeight, float fontSize) {
        float scale = this.logoScale.c();
        float eggH = textHeight * scale * 0.95f;
        float eggW = eggH * (9.0f / 11.0f);
        float eggY = y + (textHeight - eggH) * 0.5f;

        MatrixStack matrices = event.h();

        if (this.glowEffect.c()) {
            int glowCol = getGlowColor(95);
            event.d().a(matrices, x - 1.5f, eggY - 1.5f, eggW + 3.0f, eggH + 3.0f, 3.5f, glowCol);
        }

        // Draw crisp procedural pixel egg
        float pw = eggW / 9.0f;
        float ph = eggH / 11.0f;

        int outlineCol = ColorUtil.a(18, 18, 24, 230);
        int shellCol = ColorUtil.a(254, 250, 240, 255);
        int shineCol = ColorUtil.a(255, 255, 255, 255);
        int yolkCol = ColorUtil.a(255, 205, 60, 255);

        for (int r = 0; r < 11; r++) {
            String row = PIXEL_EGG[r];
            for (int c = 0; c < 9; c++) {
                char ch = row.charAt(c);
                if (ch == ' ') continue;

                int col = switch (ch) {
                    case '#' -> outlineCol;
                    case 'W' -> shineCol;
                    case 'Y' -> yolkCol;
                    default -> shellCol;
                };

                float px = x + c * pw;
                float py = eggY + r * ph;
                event.d().a(matrices, px, py, pw + 0.15f, ph + 0.15f, 0.0f, col);
            }
        }

        return x + eggW;
    }

    public Text format3DLabel(Text original) {
        MutableText prefix = Text.literal("§6🥚 ");
        return prefix.append(original);
    }

    public Text formatTabName(Text original) {
        MutableText prefix = Text.literal("§6🥚 ");
        return prefix.append(original);
    }

    private int getGlowColor(int alpha) {
        if (this.glowColorMode.l("Золотой")) return ColorUtil.a(255, 215, 0, alpha);
        if (this.glowColorMode.l("Фиолетовый")) return ColorUtil.a(170, 50, 255, alpha);
        if (this.glowColorMode.l("Неон")) return ColorUtil.a(0, 255, 220, alpha);
        if (this.glowColorMode.l("Белый")) return ColorUtil.a(255, 255, 255, alpha);

        int themeRgb = -1;
        try {
            themeRgb = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
        } catch (Exception ignored) {
        }
        return ColorUtil.a((themeRgb >> 16) & 0xFF, (themeRgb >> 8) & 0xFF, themeRgb & 0xFF, alpha);
    }

    public boolean isGlobalSync() {
        return this.globalSync.c();
    }

    public boolean isRenderInESP() {
        return this.renderInESP.c();
    }

    public boolean isRenderIn3D() {
        return this.renderIn3D.c();
    }

    public boolean isRenderInTab() {
        return this.renderInTab.c();
    }
}

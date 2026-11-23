package egg.client.module.render;

import egg.client.command.waypoint.WaypointManager;
import egg.client.config.ConfigManager;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.ModuleManager;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ButtonSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.IconRenderer;
import egg.client.render.Render2D;
import egg.client.render.hud.HudContextMenu;
import egg.client.render.hud.HudSnapHelper;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.ProjectUtil;
import egg.client.util.animation.Animation;
import egg.client.util.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EventTracker extends Module {
    private static EventTracker instance;

    public static EventTracker getInstance() {
        return instance;
    }

    private static final Identifier ARROW_ICON = Identifier.of("egg", "textures/events/arrow.png");

    public enum EventType {
        MYSTIC("Мистический сундук", Identifier.of("egg", "textures/events/mystic.png"), 900_000L, 0xFFEAB308),
        BEACON("Маяк Убийца", Identifier.of("egg", "textures/events/beacon.png"), 360_000L, 0xFFEF4444),
        ALTAR("Мистический Алтарь", Identifier.of("egg", "textures/events/altar.png"), 360_000L, 0xFFA855F7),
        VULCAN("Вулкан", Identifier.of("egg", "textures/events/vulcan.png"), 300_000L, 0xFFF97316),
        METEOR("Метеорит", Identifier.of("egg", "textures/events/meteor.png"), 180_000L, 0xFF38BDF8),
        BOSS("Босс", Identifier.of("egg", "textures/events/boss.png"), 180_000L, 0xFFF43F5E),
        PACKAGE("Посылка", Identifier.of("egg", "textures/events/airdrop.png"), 180_000L, 0xFF22C55E),
        CONTAINER("Контейнер", Identifier.of("egg", "textures/events/airdrop.png"), 180_000L, 0xFF10B981),
        GRUZ("Груз", Identifier.of("egg", "textures/events/airdrop.png"), 180_000L, 0xFF14B8A6),
        SHIP("Таинственный корабль", Identifier.of("egg", "textures/events/ship.png"), 300_000L, 0xFF06B6D4),
        DEATH_CHEST("Сундук смерти", Identifier.of("egg", "textures/events/mystic.png"), 300_000L, 0xFFDC2626);

        private final String defaultTitle;
        private final Identifier icon;
        private final long defaultDurationMs;
        private final int accentColor;

        EventType(String defaultTitle, Identifier icon, long defaultDurationMs, int accentColor) {
            this.defaultTitle = defaultTitle;
            this.icon = icon;
            this.defaultDurationMs = defaultDurationMs;
            this.accentColor = accentColor;
        }

        public String getDefaultTitle() {
            return defaultTitle;
        }

        public Identifier getIcon() {
            return icon;
        }

        public long getDefaultDurationMs() {
            return defaultDurationMs;
        }

        public int getAccentColor() {
            return accentColor;
        }
    }

    public static class ActiveEvent {
        public final EventType type;
        public final String title;
        public final int x;
        public final int y;
        public final int z;
        public final long startTimeMs;
        public long durationMs;
        public boolean opened;
        public final Animation anim = new Animation(0.0f, 260L, Easing.EASE_OUT_CUBIC);

        public ActiveEvent(EventType type, String title, int x, int y, int z, long durationMs) {
            this.type = type;
            this.title = title != null && !title.isEmpty() ? title : type.getDefaultTitle();
            this.x = x;
            this.y = y;
            this.z = z;
            this.startTimeMs = System.currentTimeMillis();
            this.durationMs = durationMs > 0L ? durationMs : type.getDefaultDurationMs();
            this.opened = false;
            this.anim.setTarget(1.0f);
        }

        public long getRemainingMs() {
            if (opened) return 0L;
            long elapsed = System.currentTimeMillis() - startTimeMs;
            return Math.max(0L, durationMs - elapsed);
        }

        public double getDistance(MinecraftClient mc) {
            if (mc == null || mc.player == null) return 0.0;
            double dx = this.x - mc.player.getX();
            double dz = this.z - mc.player.getZ();
            return Math.hypot(dx, dz);
        }

        public float getAngleDeg(MinecraftClient mc) {
            if (mc == null || mc.player == null) return 0.0f;
            double dx = this.x - mc.player.getX();
            double dz = this.z - mc.player.getZ();
            float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
            return MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        }
    }

    private final BooleanSetting showHeader = new BooleanSetting("Шапка", true, "Отображать заголовок Ивенты");
    private final BooleanSetting crosshairArrow = new BooleanSetting("Стрелка у прицела", true, "Показывать указатель к ближайшему ивенту вокруг прицела");
    private final BooleanSetting hideOnScreen = new BooleanSetting("Скрывать на экране", true, "Скрывать стрелку у прицела, когда ивент или метка видны на экране");
    private final SliderSetting arrowDistance = new SliderSetting("Радиус стрелки", 55.0, 30.0, 120.0, 5.0, "Расстояние стрелки от центра экрана");
    private final BooleanSetting autoWaypoint = new BooleanSetting("Авто-метка", true, "Автоматически ставить 3D метку на ивент на 10 минут");
    private final BooleanSetting soundNotify = new BooleanSetting("Звук", true, "Звуковое оповещение при появлении ивента");
    private final SliderSetting rounding = new SliderSetting("Скругление", 6.0, 0.0, 16.0, 0.5, "Скругление углов плашки");
    private final SliderSetting opacity = new SliderSetting("Непрозрачность", 175.0, 10.0, 255.0, 5.0, "Прозрачность фона карточки");
    private final SliderSetting posX = new SliderSetting("X", 0.015, 0.0, 1.0, 0.001, "Позиция X");
    private final SliderSetting posY = new SliderSetting("Y", 0.65, 0.0, 1.0, 0.001, "Позиция Y");
    private final SliderSetting scale = new SliderSetting("Размер", 1.0, 0.5, 1.5, 0.05, "Масштаб элемента");
    private final ButtonSetting addTestEvent = new ButtonSetting("Тестовый ивент", "Добавить пример ивента для проверки", this::addSampleEvent);

    private final Animation arrowAlphaAnim = new Animation(0.0f, 200L, Easing.EASE_OUT_CUBIC);
    private final Vector2f scratchArrowPos = new Vector2f();
    private final List<ActiveEvent> activeEvents = new ArrayList<>();
    private String pendingEventName = null;

    private boolean dragging = false;
    private float dragOffsetX = 0.0f;
    private float dragOffsetY = 0.0f;
    private final HudContextMenu contextMenu = new HudContextMenu("Ивенты");
    private HudSnapHelper.SnapResult currentSnap = null;

    private float lastX = 20.0f;
    private float lastY = 240.0f;
    private float lastW = 168.0f;
    private float lastH = 70.0f;
    private float animatedY = -1.0f;

    public EventTracker() {
        super("EventTracker", "Отслеживает ивенты сервера, выводит таймер и указывает направление", Category.ESP);
        instance = this;

        posX.setVisible(() -> false);
        posY.setVisible(() -> false);

        contextMenu.addToggle("Шапка", () -> showHeader.isEnabled(), () -> showHeader.toggle());
        contextMenu.addToggle("Стрелка у прицела", () -> crosshairArrow.isEnabled(), () -> crosshairArrow.toggle());
        contextMenu.addToggle("Скрывать на экране", () -> hideOnScreen.isEnabled(), () -> hideOnScreen.toggle());
        contextMenu.addToggle("Авто-метка", () -> autoWaypoint.isEnabled(), () -> autoWaypoint.toggle());
        contextMenu.addToggle("Звук", () -> soundNotify.isEnabled(), () -> soundNotify.toggle());

        addSettings(showHeader, crosshairArrow, hideOnScreen, arrowDistance, autoWaypoint, soundNotify, rounding, opacity, scale, posX, posY, addTestEvent);
        setEnabled(true);
    }

    public float getLastX() { return lastX; }
    public float getLastY() { return lastY; }
    public float getLastW() { return lastW; }
    public float getLastH() { return lastH; }

    @Override
    public void onDisable() {
        animatedY = -1.0f;
        arrowAlphaAnim.setValue(0.0f);
    }

    public void addSampleEvent() {
        activeEvents.removeIf(e -> "Мистический сундук".equals(e.title));
        MinecraftClient mc = MinecraftClient.getInstance();
        int px = mc != null && mc.player != null ? (int) mc.player.getX() + 450 : 1250;
        int py = 70;
        int pz = mc != null && mc.player != null ? (int) mc.player.getZ() - 620 : -840;
        activeEvents.add(new ActiveEvent(EventType.MYSTIC, "Мистический сундук", px, py, pz, 720_000L));
        if (autoWaypoint.isEnabled()) {
            WaypointManager.getInstance().add("Мистический сундук", px, py, pz, 600_000L);
        }
    }

    public void onChatMessage(String rawMessage) {
        if (!isEnabled() || rawMessage == null) return;
        String message = rawMessage.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
        if (message.isEmpty()) return;

        String lower = message.toLowerCase(Locale.ROOT);

        if (lower.contains("залутан") || lower.contains("исчез") || lower.contains("завершен") || lower.contains("закончился")) {
            for (ActiveEvent e : activeEvents) {
                if (lower.contains(e.type.getDefaultTitle().toLowerCase(Locale.ROOT)) || lower.contains(e.title.toLowerCase(Locale.ROOT))) {
                    e.anim.setTarget(0.0f);
                    if (autoWaypoint.isEnabled()) {
                        WaypointManager.getInstance().remove(e.title);
                    }
                }
            }
            return;
        }

        if (lower.contains("открыт") || lower.contains("открылся")) {
            for (ActiveEvent e : activeEvents) {
                if (lower.contains(e.type.getDefaultTitle().toLowerCase(Locale.ROOT)) || lower.contains(e.title.toLowerCase(Locale.ROOT))) {
                    e.opened = true;
                }
            }
            return;
        }

        long parsedDuration = -1L;
        Matcher timeMatcher = Pattern.compile("через\\s+(\\d+)\\s*(мин|м|min)", Pattern.CASE_INSENSITIVE).matcher(message);
        if (timeMatcher.find()) {
            try {
                parsedDuration = Long.parseLong(timeMatcher.group(1)) * 60_000L;
            } catch (Exception ignored) {}
        } else {
            Matcher secMatcher = Pattern.compile("через\\s+(\\d+)\\s*(сек|с|sec)", Pattern.CASE_INSENSITIVE).matcher(message);
            if (secMatcher.find()) {
                try {
                    parsedDuration = Long.parseLong(secMatcher.group(1)) * 1_000L;
                } catch (Exception ignored) {}
            }
        }

        Matcher bracketMatcher = Pattern.compile("\\[([^\\]]+)\\]").matcher(message);
        Matcher coordMatcher3 = Pattern.compile("координатах?\\s*:?\\s*(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)", Pattern.CASE_INSENSITIVE).matcher(message);
        Matcher coordMatcher2 = Pattern.compile("координатах?\\s*:?\\s*(-?\\d+)\\s+(-?\\d+)", Pattern.CASE_INSENSITIVE).matcher(message);
        Matcher coordMatcherXYZ = Pattern.compile("X:\\s*(-?\\d+)[^0-9-]+(?:Y:\\s*(-?\\d+)[^0-9-]+)?Z:\\s*(-?\\d+)", Pattern.CASE_INSENSITIVE).matcher(message);

        String foundName = null;
        int fx = 0, fy = 70, fz = 0;
        boolean foundCoords = false;

        if (bracketMatcher.find()) {
            foundName = bracketMatcher.group(1).trim();
        }

        if (coordMatcherXYZ.find()) {
            try {
                fx = Integer.parseInt(coordMatcherXYZ.group(1));
                if (coordMatcherXYZ.group(2) != null) {
                    fy = Integer.parseInt(coordMatcherXYZ.group(2));
                }
                fz = Integer.parseInt(coordMatcherXYZ.group(3));
                foundCoords = true;
            } catch (Exception ignored) {}
        } else if (coordMatcher3.find()) {
            try {
                fx = Integer.parseInt(coordMatcher3.group(1));
                fy = Integer.parseInt(coordMatcher3.group(2));
                fz = Integer.parseInt(coordMatcher3.group(3));
                foundCoords = true;
            } catch (Exception ignored) {}
        } else if (coordMatcher2.find()) {
            try {
                fx = Integer.parseInt(coordMatcher2.group(1));
                fz = Integer.parseInt(coordMatcher2.group(2));
                foundCoords = true;
            } catch (Exception ignored) {}
        }

        if (foundCoords) {
            String nameToMatch = foundName != null ? foundName : pendingEventName;
            if (nameToMatch != null) {
                EventType matchedType = matchEventType(nameToMatch);
                if (matchedType != null) {
                    registerEvent(matchedType, nameToMatch, fx, fy, fz, parsedDuration);
                    pendingEventName = null;
                    return;
                }
            } else {
                for (EventType type : EventType.values()) {
                    if (lower.contains(type.getDefaultTitle().toLowerCase(Locale.ROOT))) {
                        registerEvent(type, type.getDefaultTitle(), fx, fy, fz, parsedDuration);
                        return;
                    }
                }
            }
        } else {
            for (EventType type : EventType.values()) {
                if (message.equalsIgnoreCase(type.getDefaultTitle()) || lower.contains(type.getDefaultTitle().toLowerCase(Locale.ROOT))) {
                    pendingEventName = type.getDefaultTitle();
                    break;
                }
            }
        }
    }

    private EventType matchEventType(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        for (EventType t : EventType.values()) {
            if (lower.contains(t.getDefaultTitle().toLowerCase(Locale.ROOT))) {
                return t;
            }
        }
        if (lower.contains("мистик") || lower.contains("сундук")) return EventType.MYSTIC;
        if (lower.contains("маяк")) return EventType.BEACON;
        if (lower.contains("алтар")) return EventType.ALTAR;
        if (lower.contains("вулкан")) return EventType.VULCAN;
        if (lower.contains("метеор")) return EventType.METEOR;
        if (lower.contains("босс")) return EventType.BOSS;
        if (lower.contains("посылк") || lower.contains("груз") || lower.contains("контейнер") || lower.contains("аирдроп")) return EventType.PACKAGE;
        if (lower.contains("корабл")) return EventType.SHIP;
        return null;
    }

    private void registerEvent(EventType type, String title, int x, int y, int z, long durationMs) {
        activeEvents.removeIf(e -> e.type == type);
        ActiveEvent event = new ActiveEvent(type, title, x, y, z, durationMs);
        activeEvents.add(event);

        if (autoWaypoint.isEnabled()) {
            WaypointManager.getInstance().add(event.title, x, y, z, 600_000L);
        }

        if (soundNotify.isEnabled()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.player != null) {
                mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 1.2f);
            }
        }
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        updateEvents();

        boolean inChat = mc.currentScreen != null && mc.currentScreen.getClass().getSimpleName().contains("ChatScreen");
        if (activeEvents.isEmpty() && !inChat) {
            return;
        }

        renderHudCard(context, inChat);

        if (crosshairArrow.isEnabled() && !activeEvents.isEmpty() && mc.options.getPerspective().isFirstPerson()) {
            renderCrosshairPointer(context);
        } else {
            arrowAlphaAnim.setValue(0.0f);
        }
    }

    private void updateEvents() {
        activeEvents.removeIf(e -> {
            if (e.anim.getValue() <= 0.005f && e.anim.getTarget() == 0.0f) {
                return true;
            }
            if (!e.opened && e.getRemainingMs() <= 0L && e.durationMs > 0L) {
                e.opened = true;
            }
            return false;
        });
    }

    private void renderHudCard(DrawContext context, boolean inChat) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        float s = scale.getValueFloat();
        float radius = rounding.getValueFloat() * s;

        List<ActiveEvent> displayList = new ArrayList<>(activeEvents);
        if (displayList.isEmpty() && inChat) {
            displayList.add(new ActiveEvent(EventType.MYSTIC, "Мистический сундук", (int) mc.player.getX() + 500, 70, (int) mc.player.getZ() - 400, 600_000L));
        }

        float cardW = 168.0f * s;
        float headerH = showHeader.get() ? 19.0f * s : 4.0f * s;
        float itemH = 34.0f * s;
        float cardH = headerH + Math.max(1, displayList.size()) * itemH + 4.0f * s;

        Interface iface = ModuleManager.getInstance().getModule(Interface.class);
        int baseBg = iface != null ? iface.getWatermarkBgColor() : 0xFF14151E;
        float op = opacity.getValueFloat() / 255.0f;
        int bgAlpha = (int) (op * 255.0f);
        int finalBg = ColorUtil.rgba(ColorUtil.red(baseBg), ColorUtil.green(baseBg), ColorUtil.blue(baseBg), bgAlpha);
        int borderColor = ColorUtil.rgba(255, 255, 255, 22);

        float userX = posX.getValueFloat() * (screenW - cardW);
        float userY = posY.getValueFloat() * (screenH - cardH);
        float px = Math.clamp(userX, 4.0f, screenW - cardW - 4.0f);
        float py = Math.clamp(userY, 15.0f, screenH - cardH - 15.0f);

        float targetY = iface != null ? iface.getAdjustedY(px, py, cardW, cardH) : py;
        if (dragging || animatedY < 0.0f) {
            animatedY = dragging ? py : targetY;
        } else if (Math.abs(animatedY - targetY) > 0.1f) {
            animatedY += (targetY - animatedY) * 0.25f;
        } else {
            animatedY = targetY;
        }
        py = animatedY;

        this.lastX = px;
        this.lastY = py;
        this.lastW = cardW;
        this.lastH = cardH;

        Draw2DProcessor.INSTANCE.drawGlow(null, px, py, cardW, cardH, radius, ColorUtil.rgba(0, 0, 0, 55), 6.0f * s);
        Draw2DProcessor.INSTANCE.drawPanelBlur(null, px, py, cardW, cardH, radius, 1.0f);
        Draw2DProcessor.INSTANCE.drawRect(null, px, py, cardW, cardH, radius, finalBg);
        Draw2DProcessor.INSTANCE.drawOutline(null, px, py, cardW, cardH, radius, 0.8f, borderColor);

        if (iface != null && iface.isHudThemeGradient()) {
            int gradC = iface.computeHudGradientColor(iface.getHudGradientStrength(), 1.0f);
            Draw2DProcessor.INSTANCE.drawGradient(null, px, py, cardW, cardH, radius, gradC, 0x00000000, 0x00000000, gradC);
        }

        if (showHeader.get()) {
            Fonts.d.a(null, "Ивенты", px + 8.0f * s, py + 5.0f * s, 10.5f * s, 0xFFFFFFFF);
            Draw2DProcessor.INSTANCE.drawRect(null, px + 7.0f * s, py + headerH - 1.5f * s, cardW - 14.0f * s, 1.0f, 0.0f, ColorUtil.rgba(255, 255, 255, 18));
        }

        DrawBatcher.flushPending();
        if (iface != null && iface.isHudThemeGradient()) {
            Render2D.drawDiagonalSheen(context, px, py, cardW, cardH, radius, 3400L, 1.0f);
        }

        float curY = py + headerH + 2.0f * s;
        for (ActiveEvent event : displayList) {
            float anim = event.anim.getValue();
            if (anim <= 0.005f) continue;

            float rowCenterY = curY + itemH / 2.0f;
            int accentColor = event.type.getAccentColor();

            float iconBgSize = 22.0f * s;
            float iconBgX = px + 8.0f * s;
            float iconBgY = rowCenterY - iconBgSize / 2.0f;
            int iconBgCol = ColorUtil.withAlphaFloat(accentColor, 0.16f * anim);
            int iconBorderCol = ColorUtil.withAlphaFloat(accentColor, 0.35f * anim);

            Draw2DProcessor.INSTANCE.drawRect(null, iconBgX, iconBgY, iconBgSize, iconBgSize, 6.0f * s, iconBgCol);
            Draw2DProcessor.INSTANCE.drawOutline(null, iconBgX, iconBgY, iconBgSize, iconBgSize, 6.0f * s, 0.8f, iconBorderCol);
            DrawBatcher.flushPending();

            float iconRenderSize = 14.0f * s;
            float iconX = iconBgX + (iconBgSize - iconRenderSize) / 2.0f;
            float iconY = iconBgY + (iconBgSize - iconRenderSize) / 2.0f;
            IconRenderer.drawTextureIcon(context, event.type.getIcon(), iconX, iconY, iconRenderSize, iconRenderSize, ColorUtil.rgba(255, 255, 255, (int) (245 * anim)));

            float textX = iconBgX + iconBgSize + 7.0f * s;
            float titleFontSize = 9.8f * s;
            float titleY = rowCenterY - 9.0f * s;
            Fonts.b.a(null, event.title, textX, titleY, titleFontSize, ColorUtil.rgba(240, 243, 250, (int) (255 * anim)));

            double dist = event.getDistance(mc);
            String coordDistText = String.format(Locale.US, "%d, %d · %.0fm", event.x, event.z, dist);
            float subFontSize = 8.5f * s;
            float subY = rowCenterY + 1.5f * s;
            Fonts.sfRegular.a(null, coordDistText, textX, subY, subFontSize, ColorUtil.rgba(160, 168, 185, (int) (230 * anim)));

            float arrowSize = 12.0f * s;
            float arrowCenterX = px + cardW - 12.0f * s;
            float arrowCenterY = rowCenterY - 4.0f * s;
            float angleDeg = event.getAngleDeg(mc);

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(arrowCenterX, arrowCenterY);
            context.getMatrices().rotate((float) Math.toRadians(angleDeg));
            context.getMatrices().translate(-arrowSize / 2.0f, -arrowSize / 2.0f);
            IconRenderer.drawTextureIcon(context, ARROW_ICON, 0, 0, arrowSize, arrowSize, ColorUtil.rgba(255, 255, 255, (int) (220 * anim)));
            context.getMatrices().popMatrix();

            String timeStr;
            int timeColor;
            if (event.opened) {
                timeStr = "ОТКРЫТ";
                timeColor = ColorUtil.rgba(74, 222, 128, (int) (255 * anim));
            } else {
                long remMs = event.getRemainingMs();
                long sec = (remMs / 1000L) % 60L;
                long min = remMs / 60_000L;
                timeStr = String.format(Locale.US, "%02d:%02d", min, sec);
                timeColor = ColorUtil.rgba(225, 230, 242, (int) (255 * anim));
            }

            float timeWidth = Fonts.b.a(timeStr, 8.5f * s);
            float timeX = px + cardW - 7.0f * s - timeWidth;
            float timeY = rowCenterY + 3.0f * s;
            Fonts.b.a(null, timeStr, timeX, timeY, 8.5f * s, timeColor);

            DrawBatcher.flushPending();
            curY += itemH;
        }
    }

    private void renderCrosshairPointer(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ActiveEvent closest = null;
        double minDistance = Double.MAX_VALUE;

        for (ActiveEvent e : activeEvents) {
            double d = e.getDistance(mc);
            if (d < minDistance) {
                minDistance = d;
                closest = e;
            }
        }
        if (closest == null) {
            arrowAlphaAnim.setTarget(0.0f);
            return;
        }

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        if (hideOnScreen.isEnabled()) {
            boolean onScreen = ProjectUtil.toScreen(closest.x + 0.5, closest.y, closest.z + 0.5, scratchArrowPos)
                    && scratchArrowPos.x != Float.MAX_VALUE && scratchArrowPos.y != Float.MAX_VALUE
                    && scratchArrowPos.x >= -30.0f && scratchArrowPos.x <= screenW + 30.0f
                    && scratchArrowPos.y >= -30.0f && scratchArrowPos.y <= screenH + 30.0f;
            arrowAlphaAnim.setTarget(onScreen ? 0.0f : 1.0f);
        } else {
            arrowAlphaAnim.setTarget(1.0f);
        }

        float alpha = arrowAlphaAnim.getValue();
        if (alpha <= 0.01f) return;

        float cx = screenW / 2.0f;
        float cy = screenH / 2.0f;

        float angleDeg = closest.getAngleDeg(mc);
        double rad = Math.toRadians(angleDeg - 90.0f);

        float r = arrowDistance.getValueFloat();
        float ax = cx + (float) Math.cos(rad) * r;
        float ay = cy + (float) Math.sin(rad) * r;

        float arrowSize = 14.0f;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(ax, ay);
        context.getMatrices().rotate((float) Math.toRadians(angleDeg));
        context.getMatrices().translate(-arrowSize / 2.0f, -arrowSize / 2.0f);
        int arrowColor = ColorUtil.withAlphaFloat(Theme.getAccent(), alpha);
        IconRenderer.drawTextureIcon(context, ARROW_ICON, 0, 0, arrowSize, arrowSize, arrowColor);
        context.getMatrices().popMatrix();

        String distText = String.format(Locale.US, "%.0fm", minDistance);
        float tw = Fonts.b.a(distText, 8.5f);
        float textX = ax - tw / 2.0f;
        float textY = ay + (arrowSize / 2.0f) + 4.0f;
        int textCol = ColorUtil.rgba(255, 255, 255, (int) (255 * alpha));
        Fonts.b.a(null, distText, textX, textY, 8.5f, textCol);
        DrawBatcher.flushPending();
    }

    public void onChatMouseDragged(double mx, double my, int button) {
        if (this.dragging && button == 0) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return;
            float screenW = mc.getWindow().getScaledWidth();
            float screenH = mc.getWindow().getScaledHeight();

            float proposedX = (float) mx - dragOffsetX;
            float proposedY = (float) my - dragOffsetY;

            HudSnapHelper.SnapResult snap = HudSnapHelper.calculateSnap("event_tracker", proposedX, proposedY, lastW, lastH, screenW, screenH);
            this.currentSnap = snap;

            float maxAvailX = Math.max(1.0f, screenW - lastW);
            float maxAvailY = Math.max(1.0f, screenH - lastH);

            posX.setValue(Math.clamp(snap.snappedX / maxAvailX, 0.0, 1.0));
            posY.setValue(Math.clamp(snap.snappedY / maxAvailY, 0.0, 1.0));
        }
    }

    public void renderChatOverlay(DrawContext context, int mouseX, int mouseY) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        var bounds = new HudSnapHelper.HudElementBounds("event_tracker", lastX, lastY, lastW, lastH);
        int scalePercent = Math.round(scale.getValueFloat() * 100.0f);
        if (dragging) {
            onChatMouseDragged(mouseX, mouseY, 0);
            HudSnapHelper.renderDragOverlay(bounds, currentSnap, screenW, screenH, scalePercent);
        } else {
            HudSnapHelper.renderHoverOverlay(bounds, screenW, screenH, mouseX, mouseY, scalePercent);
        }

        if (contextMenu.isOpen()) {
            contextMenu.setScale(scale.getValueFloat());
            contextMenu.render(context, mouseX, mouseY);
        }
    }

    public boolean onChatMouseClicked(double mouseX, double mouseY, int button) {
        if (!isEnabled()) return false;
        if (contextMenu.isOpen()) {
            contextMenu.setScale(scale.getValueFloat());
            if (contextMenu.mouseClicked(mouseX, mouseY, button)) return true;
        }

        if (button == 0) {
            if (mouseX >= lastX && mouseX <= lastX + lastW && mouseY >= lastY && mouseY <= lastY + lastH) {
                dragging = true;
                dragOffsetX = (float) mouseX - lastX;
                dragOffsetY = (float) mouseY - lastY;
                return true;
            }
        } else if (button == 1) {
            if (mouseX >= lastX && mouseX <= lastX + lastW && mouseY >= lastY && mouseY <= lastY + lastH) {
                contextMenu.setScale(scale.getValueFloat());
                contextMenu.open((float) mouseX, (float) mouseY);
                return true;
            }
        }
        return false;
    }

    public void onChatMouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.dragging) {
            this.dragging = false;
            this.currentSnap = null;
            ConfigManager.save();
        }
    }

    public boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isEnabled()) return false;
        boolean inside = mouseX >= lastX - 2.0f && mouseX <= lastX + lastW + 2.0f && mouseY >= lastY - 2.0f && mouseY <= lastY + lastH + 2.0f;
        if (inside) {
            double cur = scale.getValue();
            double step = amount > 0 ? 0.05 : -0.05;
            scale.setValue(Math.clamp(Math.round((cur + step) * 100.0) / 100.0, 0.5, 1.5));
            ConfigManager.save();
            return true;
        }
        return false;
    }
}

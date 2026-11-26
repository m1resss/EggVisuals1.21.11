package egg.client.ui.screen;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import egg.client.module.misc.DiscordRPC;
import egg.client.render.EggStartupAnimation;
import egg.client.render.StartupOverlayControl;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Font;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.ScissorUtil;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.render.shader.Pipelines;
import egg.client.ui.ClickGuiScreen;
import egg.client.util.AccountManager;
import egg.client.util.ColorUtil;
import egg.client.util.EggUpdateChecker;
import egg.client.util.GuiSoundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EggTitleScreen extends Screen {
    private static final String SUPPORT_URL = "https://www.donationalerts.com/r/imireessa";
    private static final String TELEGRAM_URL = DiscordRPC.TELEGRAM_URL;

    private static final Identifier ICON_SINGLEPLAYER = Identifier.of("egg", "textures/icons/menu_singleplayer.png");
    private static final Identifier ICON_MULTIPLAYER = Identifier.of("egg", "textures/icons/menu_multiplayer.png");
    private static final Identifier ICON_ACCOUNTS = Identifier.of("egg", "textures/icons/menu_accounts.png");
    private static final Identifier ICON_SLIDERS = Identifier.of("egg", "textures/icons/menu_sliders.png");
    private static final Identifier ICON_HEART = Identifier.of("egg", "textures/icons/menu_heart.png");
    private static final Identifier ICON_TG = Identifier.of("egg", "textures/icons/menu_tg.png");
    private static final Identifier ICON_DA = Identifier.of("egg", "textures/icons/menu_da.png");
    private static final Identifier ICON_GEAR = Identifier.of("egg", "textures/icons/menu_gear.png");
    private static final Identifier ICON_EXIT = Identifier.of("egg", "textures/icons/menu_exit.png");
    private static final Identifier ICON_CHANGELOG = Identifier.of("egg", "textures/icons/menu_changelog.png");
    private static final Identifier ICON_TRASH = Identifier.of("egg", "textures/icons/gui_trash.png");

    private static final String[] MAIN_LABELS = {"Одиночная игра", "Мультиплеер", "Аккаунты", "Настройки", "Поддержать"};
    private static final Identifier[] MAIN_ICONS = {ICON_SINGLEPLAYER, ICON_MULTIPLAYER, ICON_ACCOUNTS, ICON_SLIDERS, ICON_HEART};
    private static final Identifier[] CIRCLE_ICONS = {ICON_TG, ICON_DA, ICON_GEAR, ICON_EXIT};

    private static final float ACCOUNT_ROW_HEIGHT = 18.0f;
    private static final float ACCOUNT_ROW_STRIDE = 21.0f;
    private static final float ACCOUNT_DELETE_SIZE = 14.0f;
    private static final String RESTORE_ACCOUNT_LABEL = "Вернуть основной";

    private static final float CL_BTN_X = 14.0f;
    private static final float CL_BTN_Y = 14.0f;
    private static final float CL_BTN_SIZE = 24.0f;

    private static final int TAG_PLUS_COLOR = 0xFF72EE9A;
    private static final int TAG_MINUS_COLOR = 0xFFFF6670;
    private static final int TAG_CHANGE_COLOR = 0xFFFFD866;

    private static final List<ChangeDay> CHANGELOG_DAYS = List.of(
            new ChangeDay("ChangeLog 26.11.2026", List.of(
                    new ChangeItem("[+]", "Rollback: запись до 1.5 минут геймплея и сохранение отката в MP4 по бинду (как мгновенный повтор в NVIDIA)"),
                    new ChangeItem("[+]", "MainMenu & Accounts: новое главное меню, экран загрузки без логотипа Mojang, панель ченджлога и быстрая смена пиратских аккаунтов"),
                    new ChangeItem("[+]", "HitEffect: эффекты при ударе («Разрез» и «Линза») с усилением на критах"),
                    new ChangeItem("[+]", "HitReach: круги дистанции удара на земле (можно бить, крит, КД, вне радиуса)"),
                    new ChangeItem("[+]", "PearlPredict: траектория эндер-жемчуга, метка попадания и камера предпросмотра с поддержкой Sodium"),
                    new ChangeItem("[+]", "ShulkerPreview: просмотр содержимого шалкеров и контейнеров при наведении с заполненностью"),
                    new ChangeItem("[+]", "ContainerSearch & ItemComparison: поиск предметов по названию и описанию в сундуках и сравнение вещей по Alt"),
                    new ChangeItem("[+]", "TargetESP: новые стили «Скобы», «Железная пыль» и «Фокус»"),
                    new ChangeItem("[+]", "KillEffect: новые эффекты «Вознесение», «Врос в мир», «Лед», «Пепел» и эффект душ"),
                    new ChangeItem("[+]", "JumpCircles: новый стиль «Фаза»"),
                    new ChangeItem("[+]", "BreakBlock: новый стиль «Резонанс»"),
                    new ChangeItem("[+]", "Trail: новый стиль «Комикс»"),
                    new ChangeItem("[+]", "TargetHUD: добавлен второй раздельный стиль с головой, HP и слотами экипировки"),
                    new ChangeItem("[+]", "Lyrics: переделан"),
                    new ChangeItem("[+]", "Fun: добавлен режим «Bedrock Бег»"),
                    new ChangeItem("[+]", "Команды: добавлена команда .hw для HolyWorld, улучшены .confetti и .help"),
                    new ChangeItem("[//]", "ItemRadius: исправлен рендер и добавлены зоны предметов для FunTime (дезориентация, явная пыль, огненный смерч, божья аура, снежок, трапка, пласт, бур) и HolyWorld (стан, трапки, светильник Джейка)"),
                    new ChangeItem("[//]", "Inventory: новый интерфейс сохранений с просмотром инвентаря"),
                    new ChangeItem("[//]", "WorldTweaks: переработка погоды и атмосферы (лужи, снег, туман, дымка, мгла, шторм и звёздное небо)"),
                    new ChangeItem("[//]", "TotemPop: новый эффект тотема с душами"),
                    new ChangeItem("[//]", "Trajectories: обновлены линии траекторий и добавлен радиус зоны взрывных и туманных зелий"),
                    new ChangeItem("[//]", "ClickGUI & HUD: обновлён фон и темы, открытие ClickGUI в главном меню, скроллбар в списках, прилипание HUD"),
                    new ChangeItem("[//]", "Sprint: пофикшен теперь работает коректно"),
                    new ChangeItem("[//]", "CosmeticsPanel & NameTags: доработано превью косметики и исправлен рендер тегов"),
                    new ChangeItem("[-]", "Удалено: мини-игра Chess (.chess) и устаревшие модули NoJumpDelay, AutoAuth, AutoEat, AutoRespawn, NoChatBackground"),
                    new ChangeItem(null, "Также пофикшены некоторые мне известные баги")
            )),
            new ChangeDay("ChangeLog 18.11.2026", List.of(
                    new ChangeItem("[+]", "Cosmetics (Часы): объёмные 3д часы (Rolex, Patek Philippe, AP, Richard Mille, Jacob & Co, Apple Ultra, G-Shock и др.) с живым ходом стрелок, анимированными механизмами и осмотром от 1-го лица на клавишу J"),
                    new ChangeItem("[+]", "Cosmetics (Мечи): 33 кастомных 3д меча с уникальными формами клинков и свечением"),
                    new ChangeItem("[+]", "SwingAnimation: кастомные анимации удара и взмаха рукой с выбором режимов и настройкой скорости (пока не много вариаций но прикольные есть)"),
                    new ChangeItem("[+]", "HitSounds: звуки попаданий по противникам с выбором звуков и настройкой громкости"),
                    new ChangeItem("[+]", "TickSync: новый модуль"),
                    new ChangeItem("[//]", "MotionBlur: добавлен новый режим Natural с глубинным размытием"),
                    new ChangeItem("[//]", "ZakoOptimization: модуль обновлён до версии 2.1.8"),
                    new ChangeItem("[//]", "JumpCircles: красивая космическая переработка"),
                    new ChangeItem("[//]", "ClickGUI: почти полностью изменено"),
                    new ChangeItem("[//]", "HUD: фиксы")
            ))
    );

    private static final long START_NANOS = System.nanoTime();
    private static final TitleMask TITLE_MASK = buildTitleMask();

    private final float[] mainHover = new float[5];
    private final float[] circleHover = new float[4];

    private final MenuBackgroundBatch bgBatch = new MenuBackgroundBatch();
    private final AsciiEggBatch eggBatch = new AsciiEggBatch();

    private long lastFrameNanos;
    private float tiltX;
    private float tiltY;

    private boolean changelogOpen;
    private float changelogAnim;
    private float changelogBtnHover;
    private float changelogScroll;
    private float changelogTargetScroll;
    private float changelogMaxScroll;
    private float cachedWrapWidth = -1.0f;
    private List<WrappedLine> cachedLines = List.of();
    private float cachedContentHeight;

    private boolean accountsOpen;
    private float accountsAnim;
    private final StringBuilder accountInput = new StringBuilder();
    private int accountCursor;
    private boolean accountInputFocused;
    private boolean accountInputAllSelected;
    private float accountLoginHover;
    private float accountRandomHover;
    private float accountCloseHover;
    private float accountRestoreHover;
    private float accountScroll;
    private float accountTargetScroll;
    private String accountStatusMessage;
    private boolean accountStatusIsError;
    private long accountStatusUntilNanos;

    private boolean updateDismissed;
    private float updateAnim;
    private float updateHover;
    private float updateCloseHover;

    private boolean exitDialogOpen;
    private float exitDialogAnim;
    private float yesHover;
    private float noHover;
    private float yesOffX;
    private float yesOffY;
    private float yesTargetX;
    private float yesTargetY;
    private long lastDodgeNanos;

    public EggTitleScreen() {
        super(Text.literal("Egg Visuals"));
    }

    @Override
    protected void init() {
        super.init();
        EggUpdateChecker.checkAsync();
        if (client != null) {
            EggStartupAnimation.ensureAtlas(client);
            AccountManager.getInstance().captureOriginal(client);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return exitDialogOpen || accountsOpen || changelogOpen;
    }

    @Override
    public void close() {
        if (exitDialogOpen) {
            closeExitDialog();
        } else if (accountsOpen) {
            closeAccountsDialog();
        } else if (changelogOpen) {
            changelogOpen = false;
        }
    }

    @Override
    public void removed() {
        super.removed();
        bgBatch.close();
        eggBatch.close();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (client == null || client.getWindow() == null) return;

        float revealAlpha = 1.0f;
        boolean underOverlay = false;
        if (client.getOverlay() instanceof StartupOverlayControl startup) {
            revealAlpha = startup.egg$getMenuRevealOpacity();
            underOverlay = true;
            if (revealAlpha <= 0.002f) {
                return;
            }
        }

        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.016f : Math.min((now - lastFrameNanos) / 1_000_000_000.0f, 0.05f);
        lastFrameNanos = now;

        float sw = width;
        float sh = height;
        float fbW = client.getWindow().getFramebufferWidth();
        float fbH = client.getWindow().getFramebufferHeight();

        float normMouseX = MathHelper.clamp((mouseX - sw * 0.5f) / Math.max(1.0f, sw * 0.5f), -1.0f, 1.0f);
        float normMouseY = MathHelper.clamp((mouseY - sh * 0.5f) / Math.max(1.0f, sh * 0.5f), -1.0f, 1.0f);
        float tiltBlend = 1.0f - (float) Math.exp(-dt * 7.5f);
        tiltX = MathHelper.lerp(tiltBlend, tiltX, normMouseX);
        tiltY = MathHelper.lerp(tiltBlend, tiltY, normMouseY);

        float hoverBlend = 1.0f - (float) Math.exp(-dt * 14.0f);
        EggUpdateChecker.UpdateInfo updateInfo = updateDismissed ? null : EggUpdateChecker.getAvailableUpdate();
        updateAnim = MathHelper.lerp(1.0f - (float) Math.exp(-dt * 11.0f), updateAnim, updateInfo != null ? 1.0f : 0.0f);
        if (updateInfo == null && updateAnim < 0.004f) {
            updateAnim = 0.0f;
        }
        changelogAnim = MathHelper.lerp(1.0f - (float) Math.exp(-dt * 12.0f), changelogAnim, changelogOpen ? 1.0f : 0.0f);
        if (!changelogOpen && changelogAnim < 0.004f) {
            changelogAnim = 0.0f;
        }
        changelogScroll = MathHelper.lerp(1.0f - (float) Math.exp(-dt * 16.0f), changelogScroll, changelogTargetScroll);
        if (Math.abs(changelogScroll - changelogTargetScroll) < 0.05f) {
            changelogScroll = changelogTargetScroll;
        }

        exitDialogAnim = MathHelper.lerp(1.0f - (float) Math.exp(-dt * 13.0f), exitDialogAnim, exitDialogOpen ? 1.0f : 0.0f);
        if (!exitDialogOpen && exitDialogAnim < 0.005f) {
            exitDialogAnim = 0.0f;
            yesOffX = 0.0f;
            yesOffY = 0.0f;
            yesTargetX = 0.0f;
            yesTargetY = 0.0f;
        }

        accountsAnim = MathHelper.lerp(1.0f - (float) Math.exp(-dt * 13.0f), accountsAnim, accountsOpen ? 1.0f : 0.0f);
        if (!accountsOpen && accountsAnim < 0.005f) {
            accountsAnim = 0.0f;
        }
        accountScroll = MathHelper.lerp(1.0f - (float) Math.exp(-dt * 16.0f), accountScroll, accountTargetScroll);
        if (Math.abs(accountScroll - accountTargetScroll) < 0.05f) {
            accountScroll = accountTargetScroll;
        }

        Layout layout = computeLayout(sw, sh);
        float eggCenterNormX = 0.31f;
        double timeSec = (now - START_NANOS) / 1_000_000_000.0;

        boolean prevBatch = DrawBatcher.isEnabled();
        DrawBatcher.setEnabledSilent(true);
        try {
            bgBatch.queue(fbW, fbH, eggCenterNormX, revealAlpha);
            eggBatch.queue(client, sw, sh, sw * eggCenterNormX, sh * 0.49f,
                    layout.colX + layout.colW * 0.5f, layout.titleY, layout.titleW,
                    timeSec, tiltX, tiltY, revealAlpha);

            boolean allowMenuHover = !underOverlay && !exitDialogOpen && exitDialogAnim < 0.05f
                    && !accountsOpen && accountsAnim < 0.05f && mouseX >= 0 && mouseY >= 0;

            boolean hovClBtn = allowMenuHover && hitCircle(mouseX, mouseY,
                    CL_BTN_X + CL_BTN_SIZE * 0.5f, CL_BTN_Y + CL_BTN_SIZE * 0.5f, CL_BTN_SIZE * 0.5f);
            changelogBtnHover = MathHelper.lerp(hoverBlend, changelogBtnHover, hovClBtn ? 1.0f : 0.0f);

            ChangelogRect clRect = computeChangelogRect(sw, sh);
            float clPanelHovFactor = changelogOpen ? changelogAnim : 0.0f;
            boolean mouseBlockedByPanel = clPanelHovFactor > 0.25f && hitRect(mouseX, mouseY, clRect.x, clRect.y, clRect.w, clRect.h);

            String currentUsername = AccountManager.getInstance().getCurrentUsername(client);

            for (int i = 0; i < MAIN_LABELS.length; i++) {
                float by = layout.buttonY(i);
                boolean hov = allowMenuHover && !mouseBlockedByPanel && hitRect(mouseX, mouseY, layout.colX, by, layout.colW, layout.btnH);
                mainHover[i] = MathHelper.lerp(hoverBlend, mainHover[i], hov ? 1.0f : 0.0f);

                int fill = ColorUtil.interpolate(
                        ColorUtil.rgba(16, 18, 24, 224),
                        ColorUtil.rgba(25, 28, 37, 242),
                        mainHover[i]
                );
                int border = ColorUtil.interpolate(
                        ColorUtil.rgba(38, 42, 54, 255),
                        ColorUtil.rgba(78, 86, 108, 255),
                        mainHover[i]
                );
                fill = ColorUtil.withAlphaFloat(fill, revealAlpha);
                border = ColorUtil.withAlphaFloat(border, revealAlpha);

                Draw2DProcessor.INSTANCE.drawRect(null, layout.colX, by, layout.colW, layout.btnH, 6.0f, fill);
                Draw2DProcessor.INSTANCE.drawOutline(null, layout.colX, by, layout.colW, layout.btnH, 6.0f, 1.0f, border);

                float iconSize = 11.0f;
                float iconX = layout.colX + 12.0f;
                float iconY = by + (layout.btnH - iconSize) * 0.5f;
                int iconCol = ColorUtil.interpolate(0xFFD6DCE6, 0xFFFFFFFF, mainHover[i]);
                Draw2DProcessor.INSTANCE.a(null, MAIN_ICONS[i], iconX, iconY, iconSize, iconSize, 0.0f,
                        ColorUtil.withAlphaFloat(iconCol, revealAlpha));

                Font font = Fonts.b;
                float fontSize = 9.5f;
                float textX = iconX + iconSize + 9.0f;
                float textY = font.a(fontSize, by + layout.btnH * 0.5f);
                int textCol = ColorUtil.interpolate(0xFFDDE3EE, 0xFFFFFFFF, mainHover[i]);
                font.a(null, MAIN_LABELS[i], textX, textY, fontSize, ColorUtil.withAlphaFloat(textCol, revealAlpha));

                if (i == 2 && currentUsername != null && !currentUsername.isEmpty()) {
                    float labelEnd = textX + font.a(MAIN_LABELS[i], fontSize) + 6.0f;
                    float availW = (layout.colX + layout.colW - 10.0f) - labelEnd;
                    if (availW > 18.0f) {
                        String badge = trimTextToWidth(Fonts.sfRegular, currentUsername, 7.6f, availW);
                        float bw = Fonts.sfRegular.a(badge, 7.6f);
                        float byText = Fonts.sfRegular.a(7.6f, by + layout.btnH * 0.5f);
                        int badgeCol = ColorUtil.interpolate(0xFF7D90B0, 0xFFB0C6EA, mainHover[i]);
                        Fonts.sfRegular.a(null, badge, layout.colX + layout.colW - 10.0f - bw, byText, 7.6f,
                                ColorUtil.withAlphaFloat(badgeCol, revealAlpha));
                    }
                }
            }

            for (int i = 0; i < 4; i++) {
                float cx = layout.circleX(i);
                float cy = layout.circleY;
                boolean hov = allowMenuHover && !mouseBlockedByPanel && hitCircle(mouseX, mouseY,
                        cx + layout.circleSize * 0.5f, cy + layout.circleSize * 0.5f, layout.circleSize * 0.5f);
                circleHover[i] = MathHelper.lerp(hoverBlend, circleHover[i], hov ? 1.0f : 0.0f);

                int fill = ColorUtil.interpolate(
                        ColorUtil.rgba(16, 18, 24, 224),
                        ColorUtil.rgba(25, 28, 37, 242),
                        circleHover[i]
                );
                int border = ColorUtil.interpolate(
                        ColorUtil.rgba(40, 44, 57, 255),
                        i == 3 ? ColorUtil.rgba(168, 76, 86, 255) : ColorUtil.rgba(82, 90, 114, 255),
                        circleHover[i]
                );
                fill = ColorUtil.withAlphaFloat(fill, revealAlpha);
                border = ColorUtil.withAlphaFloat(border, revealAlpha);

                float radius = layout.circleSize * 0.5f;
                Draw2DProcessor.INSTANCE.drawRect(null, cx, cy, layout.circleSize, layout.circleSize, radius, fill);
                Draw2DProcessor.INSTANCE.drawOutline(null, cx, cy, layout.circleSize, layout.circleSize, radius, 1.0f, border);

                float iconSize = i <= 1 ? 13.5f : 11.5f;
                float iconX = cx + (layout.circleSize - iconSize) * 0.5f;
                float iconY = cy + (layout.circleSize - iconSize) * 0.5f;
                int iconCol = i <= 1 ? ColorUtil.withAlphaFloat(0xFFFFFFFF, revealAlpha)
                        : ColorUtil.withAlphaFloat(ColorUtil.interpolate(0xFFDDE3EE, 0xFFFFFFFF, circleHover[i]), revealAlpha);
                Draw2DProcessor.INSTANCE.a(null, CIRCLE_ICONS[i], iconX, iconY, iconSize, iconSize, 0.0f, iconCol);
            }

            renderChangelogButton(revealAlpha, updateInfo != null || updateAnim > 0.01f);
            if (updateInfo != null && updateAnim > 0.01f) {
                renderUpdateBanner(updateInfo, mouseX, mouseY, allowMenuHover, revealAlpha, hoverBlend);
            }

            Fonts.sfRegular.a(null, "Egg Visuals • 1.21.11", 12.0f, sh - 17.0f, 8.2f,
                    ColorUtil.withAlphaFloat(0x888E96A8, revealAlpha));

            if (!underOverlay) {
                DrawBatcher.flushPending();
                if (changelogAnim > 0.005f) {
                    float clEase = smooth(changelogAnim);
                    Draw2DProcessor.INSTANCE.getBlurPipeline().capture(14.0f * clEase, 1);
                    renderChangelogPanel(clRect, clEase);
                    DrawBatcher.flushPending();
                }
                if (accountsAnim > 0.005f) {
                    Draw2DProcessor.INSTANCE.getBlurPipeline().capture(14.0f * accountsAnim, 1);
                    renderAccountsDialog(sw, sh, mouseX, mouseY, now, hoverBlend);
                    DrawBatcher.flushPending();
                }
                if (exitDialogAnim > 0.005f) {
                    Draw2DProcessor.INSTANCE.getBlurPipeline().capture(12.0f * exitDialogAnim, 1);
                    renderExitDialog(sw, sh, mouseX, mouseY, dt, now, hoverBlend);
                    DrawBatcher.flushPending();
                }
            }
        } finally {
            DrawBatcher.setEnabledSilent(underOverlay || prevBatch);
        }
    }

    private void renderChangelogButton(float revealAlpha, boolean hideLabel) {
        float active = Math.max(changelogBtnHover, changelogOpen ? 0.65f : 0.0f);
        int fill = ColorUtil.interpolate(
                ColorUtil.rgba(16, 18, 24, 224),
                ColorUtil.rgba(25, 29, 39, 244),
                active
        );
        int border = ColorUtil.interpolate(
                ColorUtil.rgba(40, 44, 57, 255),
                ColorUtil.rgba(96, 110, 146, 255),
                active
        );
        float radius = CL_BTN_SIZE * 0.5f;
        Draw2DProcessor.INSTANCE.drawRect(null, CL_BTN_X, CL_BTN_Y, CL_BTN_SIZE, CL_BTN_SIZE, radius,
                ColorUtil.withAlphaFloat(fill, revealAlpha));
        Draw2DProcessor.INSTANCE.drawOutline(null, CL_BTN_X, CL_BTN_Y, CL_BTN_SIZE, CL_BTN_SIZE, radius, 1.0f,
                ColorUtil.withAlphaFloat(border, revealAlpha));

        float iconSize = 11.5f;
        float iconX = CL_BTN_X + (CL_BTN_SIZE - iconSize) * 0.5f;
        float iconY = CL_BTN_Y + (CL_BTN_SIZE - iconSize) * 0.5f;
        int iconCol = ColorUtil.interpolate(0xFFD6DCE6, 0xFFFFFFFF, active);
        Draw2DProcessor.INSTANCE.a(null, ICON_CHANGELOG, iconX, iconY, iconSize, iconSize, 0.0f,
                ColorUtil.withAlphaFloat(iconCol, revealAlpha));

        if (!hideLabel) {
            float labelAlpha = Math.max(changelogBtnHover, changelogAnim * 0.9f) * revealAlpha;
            if (labelAlpha > 0.03f) {
                float labelY = Fonts.sfRegular.a(8.5f, CL_BTN_Y + CL_BTN_SIZE * 0.5f);
                Fonts.sfRegular.a(null, "ChangeLog", CL_BTN_X + CL_BTN_SIZE + 8.0f, labelY, 8.5f,
                        ColorUtil.withAlphaFloat(0xFFDDE3EE, labelAlpha));
            }
        }
    }

    private void renderUpdateBanner(EggUpdateChecker.UpdateInfo info, float mouseX, float mouseY,
                                    boolean allowHover, float revealAlpha, float hoverBlend) {
        float ease = smooth(updateAnim);
        float alpha = ease * revealAlpha;
        UpdateBannerRect rect = computeUpdateBannerRect(info, ease);

        boolean hovAll = allowHover && hitRect(mouseX, mouseY, rect.x, rect.y, rect.w, rect.h);
        boolean hovClose = allowHover && hitRect(mouseX, mouseY, rect.closeX, rect.y, rect.closeW, rect.h);
        updateHover = MathHelper.lerp(hoverBlend, updateHover, hovAll && !hovClose ? 1.0f : 0.0f);
        updateCloseHover = MathHelper.lerp(hoverBlend, updateCloseHover, hovClose ? 1.0f : 0.0f);

        int fill = ColorUtil.interpolate(
                ColorUtil.rgba(22, 54, 114, 224),
                ColorUtil.rgba(30, 72, 152, 244),
                updateHover
        );
        int border = ColorUtil.interpolate(
                ColorUtil.rgba(70, 134, 240, 236),
                ColorUtil.rgba(112, 170, 255, 255),
                updateHover
        );
        float radius = rect.h * 0.5f;
        Draw2DProcessor.INSTANCE.drawRect(null, rect.x, rect.y, rect.w, rect.h, radius,
                ColorUtil.withAlphaFloat(fill, alpha));
        Draw2DProcessor.INSTANCE.drawOutline(null, rect.x, rect.y, rect.w, rect.h, radius, 1.0f,
                ColorUtil.withAlphaFloat(border, alpha));

        float dotSize = 4.5f;
        Draw2DProcessor.INSTANCE.drawRect(null, rect.x + 10.0f, rect.y + (rect.h - dotSize) * 0.5f,
                dotSize, dotSize, dotSize * 0.5f, ColorUtil.withAlphaFloat(0xFF7EC0FF, alpha));

        float textX = rect.x + 19.0f;
        float mainY = Fonts.b.a(8.2f, rect.y + rect.h * 0.5f);
        Fonts.b.a(null, rect.title, textX, mainY, 8.2f, ColorUtil.withAlphaFloat(0xFFF3F8FF, alpha));

        float sufX = textX + rect.titleW;
        float sufY = Fonts.sfRegular.a(7.8f, rect.y + rect.h * 0.5f);
        Fonts.sfRegular.a(null, rect.suffix, sufX, sufY, 7.8f, ColorUtil.withAlphaFloat(0xFFBDD8FF, alpha));

        int closeCol = ColorUtil.interpolate(0xFF9AC0F5, 0xFFFFFFFF, updateCloseHover);
        float closeTw = Fonts.sfRegular.a("x", 8.2f);
        float closeY = Fonts.sfRegular.a(8.2f, rect.y + rect.h * 0.5f);
        Fonts.sfRegular.a(null, "x", rect.closeX + (rect.closeW - closeTw) * 0.5f - 1.5f, closeY, 8.2f,
                ColorUtil.withAlphaFloat(closeCol, alpha));
    }

    private UpdateBannerRect computeUpdateBannerRect(EggUpdateChecker.UpdateInfo info, float ease) {
        String title = "Доступно обновление " + info.version();
        String suffix = "  •  Скачать";
        float titleW = Fonts.b.a(title, 8.2f);
        float suffixW = Fonts.sfRegular.a(suffix, 7.8f);
        float closeW = 18.0f;
        float w = 19.0f + titleW + suffixW + 4.0f + closeW;
        float x = CL_BTN_X + CL_BTN_SIZE + 8.0f - (1.0f - ease) * 8.0f;
        float y = CL_BTN_Y;
        float h = CL_BTN_SIZE;
        return new UpdateBannerRect(x, y, w, h, x + w - closeW, closeW, title, suffix, titleW);
    }

    private void renderChangelogPanel(ChangelogRect rect, float ease) {
        Draw2DProcessor.INSTANCE.drawRoundedBlur(rect.x, rect.y, rect.w, rect.h, 10.0f, ease);
        Draw2DProcessor.INSTANCE.drawRect(null, rect.x, rect.y, rect.w, rect.h, 10.0f,
                ColorUtil.rgba(12, 14, 20, Math.round(172 * ease)));
        Draw2DProcessor.INSTANCE.drawOutline(null, rect.x, rect.y, rect.w, rect.h, 10.0f, 1.0f,
                ColorUtil.rgba(60, 67, 86, Math.round(235 * ease)));

        float padX = 12.0f;
        float headerH = 26.0f;
        float headerTitleY = Fonts.b.a(9.6f, rect.y + headerH * 0.5f);
        Fonts.b.a(null, "История изменений", rect.x + padX, headerTitleY, 9.6f,
                ColorUtil.withAlphaFloat(0xFFF2F5FA, ease));
        String span = "18.11 — 25.11.2026";
        float spanW = Fonts.sfRegular.a(span, 7.6f);
        Fonts.sfRegular.a(null, span, rect.x + rect.w - padX - spanW, Fonts.sfRegular.a(7.6f, rect.y + headerH * 0.5f),
                7.6f, ColorUtil.withAlphaFloat(0xFF8C96AC, ease));

        Draw2DProcessor.INSTANCE.drawRect(null, rect.x + padX, rect.y + headerH, rect.w - padX * 2.0f, 0.7f, 0.0f,
                ColorUtil.rgba(72, 80, 102, Math.round(135 * ease)));

        float bodyX = rect.x + 2.0f;
        float bodyY = rect.y + headerH + 4.0f;
        float bodyW = rect.w - 4.0f;
        float bodyH = rect.h - headerH - 10.0f;

        float textColX = rect.x + padX + 18.0f;
        float wrapW = Math.max(80.0f, rect.x + rect.w - padX - 8.0f - textColX);
        ensureChangelogLayout(wrapW);
        changelogMaxScroll = Math.max(0.0f, cachedContentHeight - bodyH);
        changelogTargetScroll = MathHelper.clamp(changelogTargetScroll, 0.0f, changelogMaxScroll);
        changelogScroll = MathHelper.clamp(changelogScroll, 0.0f, changelogMaxScroll);

        ScissorUtil.a((Matrix3x2f) null, bodyX, bodyY, bodyW, bodyH);
        try {
            float curY = bodyY + 4.0f - changelogScroll;
            float bottomLimit = bodyY + bodyH;
            for (WrappedLine line : cachedLines) {
                float lineY = curY + line.yOffset;
                if (lineY + line.height >= bodyY - 4.0f && lineY <= bottomLimit + 4.0f) {
                    if (line.isDayHeader) {
                        Fonts.b.a(null, line.prefix, rect.x + padX, lineY, 8.6f,
                                ColorUtil.withAlphaFloat(0xFFF4F7FD, ease));
                    } else {
                        if (line.tag != null) {
                            Fonts.b.a(null, line.tag, rect.x + padX, lineY + 0.3f, 7.4f,
                                    ColorUtil.withAlphaFloat(tagColor(line.tag), ease));
                        }
                        float drawX = textColX;
                        if (line.prefix != null && !line.prefix.isEmpty()) {
                            Fonts.b.a(null, line.prefix, drawX, lineY, 7.6f,
                                    ColorUtil.withAlphaFloat(0xFFEDF2FA, ease));
                            drawX += Fonts.b.a(line.prefix, 7.6f);
                        }
                        if (line.rest != null && !line.rest.isEmpty()) {
                            Fonts.sfRegular.a(null, line.rest, drawX, lineY, 7.6f,
                                    ColorUtil.withAlphaFloat(0xFFB8C2D4, ease));
                        }
                    }
                }
            }
        } finally {
            ScissorUtil.a((Matrix3x2f) null);
        }

        if (changelogMaxScroll > 1.0f && bodyH > 12.0f) {
            float trackX = rect.x + rect.w - 5.5f;
            float trackW = 2.0f;
            float ratio = MathHelper.clamp(bodyH / cachedContentHeight, 0.12f, 1.0f);
            float thumbH = Math.max(18.0f, bodyH * ratio);
            float thumbTravel = bodyH - thumbH;
            float thumbY = bodyY + (changelogScroll / changelogMaxScroll) * thumbTravel;
            Draw2DProcessor.INSTANCE.drawRect(null, trackX, bodyY, trackW, bodyH, 1.0f,
                    ColorUtil.rgba(35, 40, 52, Math.round(120 * ease)));
            Draw2DProcessor.INSTANCE.drawRect(null, trackX, thumbY, trackW, thumbH, 1.0f,
                    ColorUtil.rgba(148, 162, 192, Math.round(190 * ease)));
        }
    }

    private void ensureChangelogLayout(float wrapW) {
        if (Math.abs(cachedWrapWidth - wrapW) < 0.5f && !cachedLines.isEmpty()) return;
        cachedWrapWidth = wrapW;
        List<WrappedLine> lines = new ArrayList<>();
        float y = 0.0f;
        float lineH = 10.6f;

        for (int d = 0; d < CHANGELOG_DAYS.size(); d++) {
            ChangeDay day = CHANGELOG_DAYS.get(d);
            if (d > 0) y += 8.0f;
            lines.add(new WrappedLine(y, 13.0f, true, null, day.title, null));
            y += 14.0f;

            for (ChangeItem item : day.items) {
                String full = item.text;
                int colonIdx = full.indexOf(':');
                String head = colonIdx >= 0 ? full.substring(0, colonIdx + 1) : "";
                String tail = colonIdx >= 0 ? full.substring(colonIdx + 1) : full;

                if (!head.isEmpty() && Fonts.b.a(head, 7.6f) <= wrapW * 0.72f) {
                    float headW = Fonts.b.a(head, 7.6f);
                    List<String> wrapped = wrapAfterHead(tail, wrapW - headW, wrapW, 7.6f);
                    String firstRest = wrapped.isEmpty() ? "" : wrapped.get(0);
                    lines.add(new WrappedLine(y, lineH, false, item.tag, head, firstRest));
                    y += lineH;
                    for (int wi = 1; wi < wrapped.size(); wi++) {
                        lines.add(new WrappedLine(y, lineH, false, null, null, wrapped.get(wi)));
                        y += lineH;
                    }
                } else {
                    List<String> wrapped = wrapSegments(full, wrapW, 7.6f);
                    for (int li = 0; li < wrapped.size(); li++) {
                        lines.add(new WrappedLine(y, lineH, false, li == 0 ? item.tag : null, null, wrapped.get(li)));
                        y += lineH;
                    }
                }
                y += 2.5f;
            }
        }
        cachedLines = lines;
        cachedContentHeight = y + 6.0f;
    }

    private static List<String> wrapAfterHead(String tail, float firstLineW, float nextLineW, float fontSize) {
        List<String> out = new ArrayList<>();
        String trimmed = tail.stripLeading();
        if (trimmed.isEmpty()) {
            out.add("");
            return out;
        }
        String[] words = trimmed.split("\\s+");
        StringBuilder current = new StringBuilder();
        boolean firstLine = true;
        for (String word : words) {
            float limit = firstLine ? firstLineW : nextLineW;
            String candidate = current.isEmpty()
                    ? (firstLine ? " " + word : word)
                    : current + " " + word;
            if (!current.isEmpty() && Fonts.sfRegular.a(candidate, fontSize) > limit) {
                out.add(current.toString());
                current.setLength(0);
                current.append(word);
                firstLine = false;
            } else {
                current.setLength(0);
                current.append(candidate);
            }
        }
        if (!current.isEmpty()) {
            out.add(current.toString());
        }
        return out;
    }

    private static List<String> wrapSegments(String text, float maxWidth, float fontSize) {
        List<String> out = new ArrayList<>();
        String trimmed = text.stripLeading();
        if (trimmed.isEmpty()) {
            out.add("");
            return out;
        }
        String[] words = trimmed.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && Fonts.sfRegular.a(candidate, fontSize) > maxWidth) {
                out.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                current.setLength(0);
                current.append(candidate);
            }
        }
        if (!current.isEmpty()) {
            out.add(current.toString());
        }
        return out;
    }

    private static int tagColor(String tag) {
        return switch (tag) {
            case "[+]" -> TAG_PLUS_COLOR;
            case "[-]" -> TAG_MINUS_COLOR;
            default -> TAG_CHANGE_COLOR;
        };
    }

    private ChangelogRect computeChangelogRect(float sw, float sh) {
        float panelW = MathHelper.clamp(sw * 0.44f, 252.0f, 368.0f);
        float panelY = CL_BTN_Y + CL_BTN_SIZE + 6.0f;
        float panelH = Math.max(140.0f, sh - panelY - 26.0f);
        float ease = smooth(changelogAnim);
        float panelX = MathHelper.lerp(ease, -panelW - 16.0f, 14.0f);
        return new ChangelogRect(panelX, panelY, panelW, panelH);
    }

    private void renderExitDialog(float sw, float sh, float mouseX, float mouseY, float dt, long now, float hoverBlend) {
        Draw2DProcessor.INSTANCE.drawFullscreenBlur(sw, sh, exitDialogAnim);
        Draw2DProcessor.INSTANCE.drawRect(null, 0.0f, 0.0f, sw, sh, 0.0f,
                ColorUtil.rgba(6, 7, 11, (int) (132 * exitDialogAnim)));

        float ease = smooth(exitDialogAnim);
        float modalW = 192.0f;
        float modalH = 88.0f;
        float modalX = (sw - modalW) * 0.5f;
        float modalY = (sh - modalH) * 0.5f + (1.0f - ease) * 8.0f;

        int modalBg = ColorUtil.rgba(16, 18, 25, (int) (244 * ease));
        int modalBorder = ColorUtil.rgba(52, 57, 72, (int) (255 * ease));
        Draw2DProcessor.INSTANCE.drawRect(null, modalX, modalY, modalW, modalH, 10.0f, modalBg);
        Draw2DProcessor.INSTANCE.drawOutline(null, modalX, modalY, modalW, modalH, 10.0f, 1.0f, modalBorder);

        String title = "Вы уверены?";
        float titleSize = 11.0f;
        float titleW = Fonts.b.a(title, titleSize);
        Fonts.b.a(null, title, modalX + (modalW - titleW) * 0.5f, modalY + 18.0f, titleSize,
                ColorUtil.withAlphaFloat(0xFFF2F5FA, ease));

        float btnW = 68.0f;
        float btnH = 22.0f;
        float btnGap = 14.0f;
        float btnsStartX = modalX + (modalW - (btnW * 2.0f + btnGap)) * 0.5f;
        float btnY = modalY + modalH - btnH - 16.0f;

        float noX = btnsStartX;
        float yesBaseX = btnsStartX + btnW + btnGap;
        float yesBaseY = btnY;

        updateYesEvasion(mouseX, mouseY, yesBaseX, yesBaseY, btnW, btnH, dt, now);

        float yesX = yesBaseX + yesOffX;
        float yesY = yesBaseY + yesOffY;

        boolean hovNo = exitDialogOpen && hitRect(mouseX, mouseY, noX, btnY, btnW, btnH);
        boolean hovYes = exitDialogOpen && hitRect(mouseX, mouseY, yesX, yesY, btnW, btnH);
        noHover = MathHelper.lerp(hoverBlend, noHover, hovNo ? 1.0f : 0.0f);
        yesHover = MathHelper.lerp(hoverBlend, yesHover, hovYes ? 1.0f : 0.0f);

        int noBg = ColorUtil.withAlphaFloat(ColorUtil.interpolate(ColorUtil.rgba(24, 27, 36, 240), ColorUtil.rgba(35, 39, 52, 250), noHover), ease);
        int noBrd = ColorUtil.withAlphaFloat(ColorUtil.interpolate(ColorUtil.rgba(52, 57, 72, 255), ColorUtil.rgba(96, 106, 132, 255), noHover), ease);
        Draw2DProcessor.INSTANCE.drawRect(null, noX, btnY, btnW, btnH, 6.0f, noBg);
        Draw2DProcessor.INSTANCE.drawOutline(null, noX, btnY, btnW, btnH, 6.0f, 1.0f, noBrd);

        float btnFontSize = 9.5f;
        float noTw = Fonts.b.a("Нет", btnFontSize);
        Fonts.b.a(null, "Нет", noX + (btnW - noTw) * 0.5f, Fonts.b.a(btnFontSize, btnY + btnH * 0.5f),
                btnFontSize, ColorUtil.withAlphaFloat(0xFFEAEFF7, ease));

        int yesBg = ColorUtil.withAlphaFloat(ColorUtil.interpolate(ColorUtil.rgba(46, 22, 28, 242), ColorUtil.rgba(74, 28, 37, 252), yesHover), ease);
        int yesBrd = ColorUtil.withAlphaFloat(ColorUtil.interpolate(ColorUtil.rgba(128, 54, 66, 255), ColorUtil.rgba(204, 84, 98, 255), yesHover), ease);
        Draw2DProcessor.INSTANCE.drawRect(null, yesX, yesY, btnW, btnH, 6.0f, yesBg);
        Draw2DProcessor.INSTANCE.drawOutline(null, yesX, yesY, btnW, btnH, 6.0f, 1.0f, yesBrd);

        float yesTw = Fonts.b.a("Да", btnFontSize);
        Fonts.b.a(null, "Да", yesX + (btnW - yesTw) * 0.5f, Fonts.b.a(btnFontSize, yesY + btnH * 0.5f),
                btnFontSize, ColorUtil.withAlphaFloat(0xFFFFD8DD, ease));
    }

    private void updateYesEvasion(float mouseX, float mouseY, float baseX, float baseY, float btnW, float btnH, float dt, long now) {
        if (!exitDialogOpen) return;

        float curCx = baseX + yesOffX + btnW * 0.5f;
        float curCy = baseY + yesOffY + btnH * 0.5f;
        float dx = curCx - mouseX;
        float dy = curCy - mouseY;
        float dist = (float) Math.hypot(dx, dy);

        float triggerRadius = 32.0f;
        if (dist < triggerRadius && (now - lastDodgeNanos) > 95_000_000L) {
            if (dist < 0.5f) {
                dx = 1.0f;
                dy = -0.35f;
                dist = (float) Math.hypot(dx, dy);
            }
            float dirX = dx / dist;
            float dirY = dy / dist;
            float nextX = MathHelper.clamp(yesTargetX + dirX * 18.0f, -6.0f, 44.0f);
            float nextY = MathHelper.clamp(yesTargetY + dirY * 14.0f, -20.0f, 22.0f);
            if (Math.abs(nextX - yesTargetX) > 1.0f || Math.abs(nextY - yesTargetY) > 1.0f) {
                yesTargetX = nextX;
                yesTargetY = nextY;
                lastDodgeNanos = now;
            }
        } else if (dist > 68.0f) {
            yesTargetX = MathHelper.lerp(Math.min(1.0f, dt * 2.5f), yesTargetX, 0.0f);
            yesTargetY = MathHelper.lerp(Math.min(1.0f, dt * 2.5f), yesTargetY, 0.0f);
        }

        float maxMove = 105.0f * dt;
        float stepX = yesTargetX - yesOffX;
        float stepY = yesTargetY - yesOffY;
        float stepLen = (float) Math.hypot(stepX, stepY);
        if (stepLen > maxMove && stepLen > 0.001f) {
            yesOffX += (stepX / stepLen) * maxMove;
            yesOffY += (stepY / stepLen) * maxMove;
        } else {
            yesOffX = yesTargetX;
            yesOffY = yesTargetY;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (accountsOpen && !exitDialogOpen && accountsAnim > 0.2f) {
            AccountsRect rect = computeAccountsModalRect(width, height);
            if (hitRect(mouseX, mouseY, rect.listX, rect.listY, rect.listW, rect.listH)) {
                accountTargetScroll = MathHelper.clamp(accountTargetScroll - (float) verticalAmount * ACCOUNT_ROW_STRIDE, 0.0f, rect.maxScroll);
                return true;
            }
            return true;
        }
        if (changelogOpen && !exitDialogOpen && changelogAnim > 0.2f) {
            ChangelogRect rect = computeChangelogRect(width, height);
            if (hitRect(mouseX, mouseY, rect.x, rect.y, rect.w, rect.h)) {
                changelogTargetScroll = MathHelper.clamp(changelogTargetScroll - (float) verticalAmount * 22.0f, 0.0f, changelogMaxScroll);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        if (click.button() != 0 || client == null) return super.mouseClicked(click, bl);
        if (client.getOverlay() != null) return false;

        double mx = click.x();
        double my = click.y();

        if (exitDialogOpen) {
            float modalW = 192.0f;
            float modalH = 88.0f;
            float modalX = (width - modalW) * 0.5f;
            float modalY = (height - modalH) * 0.5f;
            float btnW = 68.0f;
            float btnH = 22.0f;
            float btnGap = 14.0f;
            float btnsStartX = modalX + (modalW - (btnW * 2.0f + btnGap)) * 0.5f;
            float btnY = modalY + modalH - btnH - 16.0f;

            float noX = btnsStartX;
            float yesX = btnsStartX + btnW + btnGap + yesOffX;
            float yesY = btnY + yesOffY;

            if (hitRect(mx, my, yesX, yesY, btnW, btnH)) {
                GuiSoundManager.playClick();
                client.scheduleStop();
                return true;
            }
            if (hitRect(mx, my, noX, btnY, btnW, btnH) || !hitRect(mx, my, modalX, modalY, modalW, modalH)) {
                GuiSoundManager.playClick();
                closeExitDialog();
                return true;
            }
            return true;
        }

        if (accountsOpen) {
            AccountsRect rect = computeAccountsModalRect(width, height);
            if (!hitRect(mx, my, rect.x, rect.y, rect.w, rect.h)) {
                GuiSoundManager.playClick();
                closeAccountsDialog();
                return true;
            }
            if (hitRect(mx, my, rect.closeX, rect.closeY, rect.closeSize, rect.closeSize)) {
                GuiSoundManager.playClick();
                closeAccountsDialog();
                return true;
            }
            if (hitRect(mx, my, rect.inputX, rect.inputY, rect.inputW, rect.inputH)) {
                accountInputFocused = true;
                accountCursor = accountInput.length();
                accountInputAllSelected = false;
                return true;
            }
            accountInputFocused = false;
            accountInputAllSelected = false;

            if (hitRect(mx, my, rect.loginX, rect.inputY, rect.loginW, rect.inputH)) {
                GuiSoundManager.playClick();
                submitAccountLogin(accountInput.toString());
                return true;
            }
            if (hitRect(mx, my, rect.randX, rect.inputY, rect.randW, rect.inputH)) {
                GuiSoundManager.playClick();
                String randomNick = AccountManager.getInstance().generateRandomName();
                accountInput.setLength(0);
                accountInput.append(randomNick);
                accountCursor = accountInput.length();
                accountInputFocused = true;
                accountInputAllSelected = false;
                accountStatusMessage = null;
                return true;
            }
            if (rect.hasRestore && hitRect(mx, my, rect.restoreX, rect.restoreY, rect.restoreW, rect.restoreH)) {
                GuiSoundManager.playClick();
                AccountManager.getInstance().restoreOriginal(client);
                accountStatusMessage = null;
                return true;
            }

            if (hitRect(mx, my, rect.listX, rect.listY, rect.rowWidth(), rect.listH)) {
                AccountManager mgr = AccountManager.getInstance();
                List<String> list = mgr.getAccounts();
                float scroll = MathHelper.clamp(accountScroll, 0.0f, rect.maxScroll);
                int index = (int) ((my - rect.listY + scroll) / ACCOUNT_ROW_STRIDE);
                float rowY = rect.rowY(index, scroll);
                if (index < list.size() && my < rowY + ACCOUNT_ROW_HEIGHT) {
                    String nick = list.get(index);
                    GuiSoundManager.playClick();
                    if (hitRect(mx, my, rect.deleteX(), rect.deleteY(rowY), ACCOUNT_DELETE_SIZE, ACCOUNT_DELETE_SIZE)) {
                        mgr.removeAccount(nick);
                        accountStatusMessage = null;
                    } else {
                        submitAccountLogin(nick);
                    }
                }
            }
            return true;
        }

        if (hitCircle(mx, my, CL_BTN_X + CL_BTN_SIZE * 0.5f, CL_BTN_Y + CL_BTN_SIZE * 0.5f, CL_BTN_SIZE * 0.5f)) {
            GuiSoundManager.playClick();
            changelogOpen = !changelogOpen;
            return true;
        }

        EggUpdateChecker.UpdateInfo updateInfo = updateDismissed ? null : EggUpdateChecker.getAvailableUpdate();
        if (updateInfo != null && updateAnim > 0.25f) {
            UpdateBannerRect rect = computeUpdateBannerRect(updateInfo, smooth(updateAnim));
            if (hitRect(mx, my, rect.closeX, rect.y, rect.closeW, rect.h)) {
                GuiSoundManager.playClick();
                updateDismissed = true;
                return true;
            }
            if (hitRect(mx, my, rect.x, rect.y, rect.w, rect.h)) {
                GuiSoundManager.playClick();
                openUrl(updateInfo.url());
                return true;
            }
        }

        if (changelogOpen && changelogAnim > 0.2f) {
            ChangelogRect rect = computeChangelogRect(width, height);
            if (hitRect(mx, my, rect.x, rect.y, rect.w, rect.h)) {
                return true;
            }
            changelogOpen = false;
        }

        Layout layout = computeLayout(width, height);
        for (int i = 0; i < MAIN_LABELS.length; i++) {
            if (hitRect(mx, my, layout.colX, layout.buttonY(i), layout.colW, layout.btnH)) {
                GuiSoundManager.playClick();
                onMainAction(i);
                return true;
            }
        }

        for (int i = 0; i < 4; i++) {
            float cx = layout.circleX(i) + layout.circleSize * 0.5f;
            float cy = layout.circleY + layout.circleSize * 0.5f;
            if (hitCircle(mx, my, cx, cy, layout.circleSize * 0.5f)) {
                GuiSoundManager.playClick();
                onCircleAction(i);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (exitDialogOpen) {
                GuiSoundManager.playClick();
                closeExitDialog();
                return true;
            }
            if (accountsOpen) {
                GuiSoundManager.playClick();
                closeAccountsDialog();
                return true;
            }
            if (changelogOpen) {
                GuiSoundManager.playClick();
                changelogOpen = false;
                return true;
            }
        }
        if (accountsOpen && !exitDialogOpen) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                GuiSoundManager.playClick();
                submitAccountLogin(accountInput.toString());
                return true;
            }
            if (accountInputFocused) {
                if (isCtrlPressed() && key == GLFW.GLFW_KEY_A) {
                    accountInputAllSelected = !accountInput.isEmpty();
                    accountCursor = accountInput.length();
                    return true;
                }
                if (isCtrlPressed() && key == GLFW.GLFW_KEY_V && client != null && client.keyboard != null) {
                    String clip = client.keyboard.getClipboard();
                    if (clip != null && !clip.isEmpty()) {
                        insertFilteredAccountText(clip);
                    }
                    return true;
                }
                if (isCtrlPressed() && key == GLFW.GLFW_KEY_C && client != null && client.keyboard != null) {
                    if (!accountInput.isEmpty()) {
                        client.keyboard.setClipboard(accountInput.toString());
                    }
                    return true;
                }
                if (key == GLFW.GLFW_KEY_BACKSPACE) {
                    if (accountInputAllSelected) {
                        accountInput.setLength(0);
                        accountCursor = 0;
                        accountInputAllSelected = false;
                    } else if (accountCursor > 0) {
                        accountInput.deleteCharAt(accountCursor - 1);
                        accountCursor--;
                    }
                    return true;
                }
                if (key == GLFW.GLFW_KEY_DELETE) {
                    if (accountInputAllSelected) {
                        accountInput.setLength(0);
                        accountCursor = 0;
                        accountInputAllSelected = false;
                    } else if (accountCursor < accountInput.length()) {
                        accountInput.deleteCharAt(accountCursor);
                    }
                    return true;
                }
                if (key == GLFW.GLFW_KEY_LEFT) {
                    accountInputAllSelected = false;
                    if (accountCursor > 0) accountCursor--;
                    return true;
                }
                if (key == GLFW.GLFW_KEY_RIGHT) {
                    accountInputAllSelected = false;
                    if (accountCursor < accountInput.length()) accountCursor++;
                    return true;
                }
                if (key == GLFW.GLFW_KEY_HOME) {
                    accountInputAllSelected = false;
                    accountCursor = 0;
                    return true;
                }
                if (key == GLFW.GLFW_KEY_END) {
                    accountInputAllSelected = false;
                    accountCursor = accountInput.length();
                    return true;
                }
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (accountsOpen && !exitDialogOpen && accountInputFocused) {
            String str = input.asString();
            if (str != null && !str.isEmpty()) {
                insertFilteredAccountText(str);
            }
            return true;
        }
        return super.charTyped(input);
    }

    private void onMainAction(int index) {
        if (client == null) return;
        switch (index) {
            case 0 -> client.setScreen(new SelectWorldScreen(this));
            case 1 -> client.setScreen(new MultiplayerScreen(this));
            case 2 -> openAccountsDialog();
            case 3 -> client.setScreen(new OptionsScreen(this, client.options));
            case 4 -> openUrl(SUPPORT_URL);
        }
    }

    private void onCircleAction(int index) {
        if (client == null) return;
        switch (index) {
            case 0 -> openUrl(TELEGRAM_URL);
            case 1 -> openUrl(SUPPORT_URL);
            case 2 -> {
                ClickGuiScreen gui = ClickGuiScreen.getInstance();
                gui.setReturnScreen(this);
                client.setScreen(gui);
            }
            case 3 -> openExitDialog();
        }
    }

    private void openAccountsDialog() {
        changelogOpen = false;
        accountsOpen = true;
        accountInputFocused = true;
        accountInputAllSelected = false;
        accountScroll = 0.0f;
        accountTargetScroll = 0.0f;
        accountStatusMessage = null;
        if (client != null) {
            AccountManager mgr = AccountManager.getInstance();
            mgr.captureOriginal(client);
            String orig = mgr.getOriginalUsername();
            if (orig != null && AccountManager.isValidUsername(orig) && mgr.getAccounts().isEmpty()) {
                mgr.addOrPromote(orig);
            }
            if (accountInput.isEmpty()) {
                String cur = mgr.getCurrentUsername(client);
                if (AccountManager.isValidUsername(cur)) {
                    accountInput.append(cur);
                    accountCursor = accountInput.length();
                    accountInputAllSelected = true;
                }
            } else {
                accountCursor = accountInput.length();
            }
        }
    }

    private void closeAccountsDialog() {
        accountsOpen = false;
        accountInputFocused = false;
        accountInputAllSelected = false;
    }

    private void submitAccountLogin(String nick) {
        if (client == null) return;
        String err = AccountManager.getInstance().loginOffline(client, nick);
        if (err != null) {
            setAccountStatus(err, true);
        } else {
            String active = AccountManager.getInstance().getCurrentUsername(client);
            accountInput.setLength(0);
            accountInput.append(active);
            accountCursor = accountInput.length();
            accountInputAllSelected = true;
            accountStatusMessage = null;
        }
    }

    private void setAccountStatus(String message, boolean error) {
        this.accountStatusMessage = message;
        this.accountStatusIsError = error;
        this.accountStatusUntilNanos = System.nanoTime() + 3_500_000_000L;
    }

    private void insertFilteredAccountText(String raw) {
        if (accountInputAllSelected) {
            accountInput.setLength(0);
            accountCursor = 0;
            accountInputAllSelected = false;
        }
        int pos = MathHelper.clamp(accountCursor, 0, accountInput.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (accountInput.length() >= 16) break;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
                accountInput.insert(pos, c);
                pos++;
            }
        }
        accountCursor = pos;
    }

    private boolean isCtrlPressed() {
        if (client == null || client.getWindow() == null) return false;
        long handle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private void renderAccountsDialog(float sw, float sh, float mouseX, float mouseY, long now, float hoverBlend) {
        Draw2DProcessor.INSTANCE.drawFullscreenBlur(sw, sh, accountsAnim);
        Draw2DProcessor.INSTANCE.drawRect(null, 0.0f, 0.0f, sw, sh, 0.0f,
                ColorUtil.rgba(6, 7, 11, (int) (132 * accountsAnim)));

        float ease = smooth(accountsAnim);
        AccountsRect rect = computeAccountsModalRect(sw, sh);

        Draw2DProcessor.INSTANCE.drawRoundedBlur(rect.x, rect.y, rect.w, rect.h, 6.0f, ease);
        Draw2DProcessor.INSTANCE.drawRect(null, rect.x, rect.y, rect.w, rect.h, 6.0f,
                ColorUtil.withAlphaFloat(0xF016181F, ease));
        Draw2DProcessor.INSTANCE.drawOutline(null, rect.x, rect.y, rect.w, rect.h, 6.0f, 0.65f,
                ColorUtil.withAlphaFloat(0xC0393C49, ease));

        AccountManager mgr = AccountManager.getInstance();
        String currentName = mgr.getCurrentUsername(client);
        float titleCenter = rect.closeY + rect.closeSize * 0.5f;
        Fonts.b.a(null, "Аккаунты", rect.inputX, Fonts.b.a(10.2f, titleCenter), 10.2f,
                ColorUtil.withAlphaFloat(0xFFF0F1F6, ease));

        boolean hovClose = accountsOpen && hitRect(mouseX, mouseY, rect.closeX, rect.closeY, rect.closeSize, rect.closeSize);
        accountCloseHover = MathHelper.lerp(hoverBlend, accountCloseHover, hovClose ? 1.0f : 0.0f);
        float crossW = Fonts.sfRegular.a("×", 10.0f);
        Fonts.sfRegular.a(null, "×", rect.closeX + (rect.closeSize - crossW) * 0.5f, Fonts.sfRegular.a(10.0f, titleCenter), 10.0f,
                ColorUtil.withAlphaFloat(ColorUtil.interpolate(0xFF9295A3, 0xFFF4F4FA, accountCloseHover), ease));

        int inputBg = accountInputFocused ? 0xFF282A36 : 0xFF22242E;
        Draw2DProcessor.INSTANCE.drawRect(null, rect.inputX, rect.inputY, rect.inputW, rect.inputH, 4.0f,
                ColorUtil.withAlphaFloat(inputBg, ease));

        String typed = accountInput.toString();
        float inputTextX = rect.inputX + 8.0f;
        float inputTextY = Fonts.sfRegular.a(8.4f, rect.inputY + rect.inputH * 0.5f);
        ScissorUtil.a((Matrix3x2f) null, rect.inputX + 4.0f, rect.inputY + 1.0f, rect.inputW - 8.0f, rect.inputH - 2.0f);
        try {
            if (typed.isEmpty()) {
                Fonts.sfRegular.a(null, "Никнейм", inputTextX, inputTextY, 8.4f,
                        ColorUtil.withAlphaFloat(0xFF666F82, ease));
            } else {
                if (accountInputFocused && accountInputAllSelected) {
                    float selW = Math.min(rect.inputW - 14.0f, Fonts.sfRegular.a(typed, 8.4f));
                    Draw2DProcessor.INSTANCE.drawRect(null, inputTextX - 1.0f, rect.inputY + 4.5f, selW + 2.0f, rect.inputH - 9.0f, 2.0f,
                            ColorUtil.rgba(86, 98, 128, (int) (95 * ease)));
                }
                Fonts.sfRegular.a(null, typed, inputTextX, inputTextY, 8.4f,
                        ColorUtil.withAlphaFloat(0xFFECEFF7, ease));
            }
            if (accountInputFocused && ((now / 500_000_000L) % 2L == 0L)) {
                String beforeCursor = typed.substring(0, MathHelper.clamp(accountCursor, 0, typed.length()));
                float curX = inputTextX + Fonts.sfRegular.a(beforeCursor, 8.4f);
                Draw2DProcessor.INSTANCE.drawRect(null, curX, rect.inputY + 5.0f, 1.0f, rect.inputH - 10.0f, 0.5f,
                        ColorUtil.withAlphaFloat(0xFFC4CDDF, ease));
            }
        } finally {
            ScissorUtil.a((Matrix3x2f) null);
        }

        boolean validTyped = AccountManager.isValidUsername(typed);
        boolean hovLogin = accountsOpen && hitRect(mouseX, mouseY, rect.loginX, rect.inputY, rect.loginW, rect.inputH);
        accountLoginHover = MathHelper.lerp(hoverBlend, accountLoginHover, hovLogin ? 1.0f : 0.0f);
        int loginBg = validTyped ? ColorUtil.interpolate(0xFFBDBBE1, 0xFFD4D2F2, accountLoginHover) : 0xFF252733;
        Draw2DProcessor.INSTANCE.drawRect(null, rect.loginX, rect.inputY, rect.loginW, rect.inputH, 4.0f,
                ColorUtil.withAlphaFloat(loginBg, ease));
        float loginTw = Fonts.b.a("Войти", 8.2f);
        float loginTy = Fonts.b.a(8.2f, rect.inputY + rect.inputH * 0.5f);
        Fonts.b.a(null, "Войти", rect.loginX + (rect.loginW - loginTw) * 0.5f, loginTy, 8.2f,
                ColorUtil.withAlphaFloat(validTyped ? 0xFF212232 : 0xFF858898, ease));

        boolean hovRand = accountsOpen && hitRect(mouseX, mouseY, rect.randX, rect.inputY, rect.randW, rect.inputH);
        accountRandomHover = MathHelper.lerp(hoverBlend, accountRandomHover, hovRand ? 1.0f : 0.0f);
        int randBg = ColorUtil.interpolate(0xFF22242E, 0xFF303241, accountRandomHover);
        Draw2DProcessor.INSTANCE.drawRect(null, rect.randX, rect.inputY, rect.randW, rect.inputH, 4.0f,
                ColorUtil.withAlphaFloat(randBg, ease));
        drawDiceIcon(rect.randX + rect.randW * 0.5f, rect.inputY + rect.inputH * 0.5f,
                ColorUtil.withAlphaFloat(ColorUtil.interpolate(0xFFB4BED2, 0xFFFFFFFF, accountRandomHover), ease));

        if (rect.hasError) {
            Fonts.sfRegular.a(null, trimTextToWidth(Fonts.sfRegular, accountStatusMessage, 7.3f, rect.listW),
                    rect.inputX, rect.inputY + rect.inputH + 4.0f, 7.3f,
                    ColorUtil.withAlphaFloat(0xFFFF7A88, ease));
        }

        List<String> list = mgr.getAccounts();
        accountTargetScroll = MathHelper.clamp(accountTargetScroll, 0.0f, rect.maxScroll);
        accountScroll = MathHelper.clamp(accountScroll, 0.0f, rect.maxScroll);

        if (rect.listH > 0.0f) {
            float rowWidth = rect.rowWidth();
            ScissorUtil.a((Matrix3x2f) null, rect.listX, rect.listY, rowWidth, rect.listH);
            try {
                boolean overList = accountsOpen && hitRect(mouseX, mouseY, rect.listX, rect.listY, rowWidth, rect.listH);
                for (int i = 0; i < list.size(); i++) {
                    float rowY = rect.rowY(i, accountScroll);
                    if (rowY + ACCOUNT_ROW_HEIGHT <= rect.listY) continue;
                    if (rowY >= rect.listY + rect.listH) break;

                    String nick = list.get(i);
                    boolean active = nick.equalsIgnoreCase(currentName);
                    boolean overRow = overList && hitRect(mouseX, mouseY, rect.listX, rowY, rowWidth, ACCOUNT_ROW_HEIGHT);
                    float deleteX = rect.deleteX();
                    float deleteY = rect.deleteY(rowY);
                    boolean overDelete = overRow && hitRect(mouseX, mouseY, deleteX, deleteY, ACCOUNT_DELETE_SIZE, ACCOUNT_DELETE_SIZE);
                    int rowBg = active ? (overRow ? 0xFF323445 : 0xFF2B2D3C) : (overRow ? 0xFF292B37 : 0xFF20222B);
                    Draw2DProcessor.INSTANCE.drawRect(null, rect.listX, rowY, rowWidth, ACCOUNT_ROW_HEIGHT, 4.0f,
                            ColorUtil.withAlphaFloat(rowBg, ease));

                    float centerY = rowY + ACCOUNT_ROW_HEIGHT * 0.5f;
                    if (active) {
                        Draw2DProcessor.INSTANCE.drawRect(null, rect.listX + 7.0f, centerY - 1.5f, 3.0f, 3.0f, 1.5f,
                                ColorUtil.withAlphaFloat(0xFF83D9B5, ease));
                    }
                    float nickX = rect.listX + 15.0f;
                    Font nickFont = active ? Fonts.b : Fonts.sfRegular;
                    nickFont.a(null, trimTextToWidth(nickFont, nick, 8.5f, deleteX - nickX - 4.0f),
                            nickX, nickFont.a(8.5f, centerY), 8.5f,
                            ColorUtil.withAlphaFloat(active ? 0xFFF5F5FA : 0xFFC8CBD7, ease));

                    float iconSize = 7.0f;
                    float iconInset = (ACCOUNT_DELETE_SIZE - iconSize) * 0.5f;
                    Draw2DProcessor.INSTANCE.a(null, ICON_TRASH, deleteX + iconInset, deleteY + iconInset, iconSize, iconSize, 0.0f,
                            ColorUtil.withAlphaFloat(overDelete ? 0xFFF2919B : 0xFF85889A, ease));
                }
            } finally {
                ScissorUtil.a((Matrix3x2f) null);
            }

            if (rect.maxScroll > 0.0f) {
                float thumbH = Math.min(rect.listH, Math.max(8.0f, rect.listH * rect.listH / (rect.listH + rect.maxScroll)));
                float trackX = rect.listX + rect.listW - 1.5f;
                float thumbY = rect.listY + (accountScroll / rect.maxScroll) * (rect.listH - thumbH);
                Draw2DProcessor.INSTANCE.drawRect(null, trackX, thumbY, 1.5f, thumbH, 0.75f,
                        ColorUtil.withAlphaFloat(0xFF646779, ease));
            }
        }

        if (rect.hasRestore) {
            boolean hovRes = accountsOpen && hitRect(mouseX, mouseY, rect.restoreX, rect.restoreY, rect.restoreW, rect.restoreH);
            accountRestoreHover = MathHelper.lerp(hoverBlend, accountRestoreHover, hovRes ? 1.0f : 0.0f);
            int resCol = ColorUtil.interpolate(0xFF7D8BA6, 0xFFD0DCF0, accountRestoreHover);
            Fonts.sfRegular.a(null, RESTORE_ACCOUNT_LABEL, rect.restoreX, rect.restoreY, 7.4f,
                    ColorUtil.withAlphaFloat(resCol, ease));
        }
    }

    private static void drawDiceIcon(float cx, float cy, int color) {
        Draw2DProcessor.INSTANCE.drawOutline(null, cx - 5.0f, cy - 5.0f, 10.0f, 10.0f, 2.0f, 1.0f, color);
        Draw2DProcessor.INSTANCE.drawRect(null, cx - 3.1f, cy - 3.1f, 1.8f, 1.8f, 0.9f, color);
        Draw2DProcessor.INSTANCE.drawRect(null, cx + 1.3f, cy - 3.1f, 1.8f, 1.8f, 0.9f, color);
        Draw2DProcessor.INSTANCE.drawRect(null, cx - 0.9f, cy - 0.9f, 1.8f, 1.8f, 0.9f, color);
        Draw2DProcessor.INSTANCE.drawRect(null, cx - 3.1f, cy + 1.3f, 1.8f, 1.8f, 0.9f, color);
        Draw2DProcessor.INSTANCE.drawRect(null, cx + 1.3f, cy + 1.3f, 1.8f, 1.8f, 0.9f, color);
    }

    private static String trimTextToWidth(Font font, String text, float size, float maxWidth) {
        if (text == null || font.a(text, size) <= maxWidth) return text;
        String dots = "..";
        float dotsW = font.a(dots, size);
        int len = text.length();
        while (len > 1 && font.a(text.substring(0, len), size) + dotsW > maxWidth) {
            len--;
        }
        return text.substring(0, len) + dots;
    }

    private AccountsRect computeAccountsModalRect(float sw, float sh) {
        AccountManager mgr = AccountManager.getInstance();
        List<String> list = mgr.getAccounts();
        String orig = mgr.getOriginalUsername();
        boolean hasRestore = orig != null && !mgr.isOriginalActive(client) && list.stream().noneMatch(orig::equalsIgnoreCase);
        boolean hasError = accountStatusIsError && accountStatusMessage != null && System.nanoTime() < accountStatusUntilNanos;

        float w = Math.min(206.0f, sw - 14.0f);
        float padX = 10.0f;
        float innerW = w - padX * 2.0f;
        float headerH = 25.0f;
        float inputH = 18.0f;
        float errorH = hasError ? 12.0f : 0.0f;
        float listGap = list.isEmpty() ? 0.0f : 7.0f;
        float restoreH = hasRestore ? 13.0f : 0.0f;
        float fixedH = headerH + inputH + errorH + listGap + restoreH + 9.0f;
        float contentH = list.isEmpty() ? 0.0f : (list.size() - 1) * ACCOUNT_ROW_STRIDE + ACCOUNT_ROW_HEIGHT;
        float maxListH = 4 * ACCOUNT_ROW_STRIDE + ACCOUNT_ROW_HEIGHT;
        float listH = Math.min(contentH, Math.min(maxListH, Math.max(0.0f, sh - fixedH - 14.0f)));
        float h = fixedH + listH;

        float ease = smooth(accountsAnim);
        float x = (sw - w) * 0.5f;
        float y = (sh - h) * 0.5f + (1.0f - ease) * 5.0f;
        float closeSize = 14.0f;
        float closeX = x + w - padX - closeSize + 2.0f;
        float closeY = y + 5.0f;

        float inputY = y + headerH;
        float randW = inputH;
        float loginW = 35.0f;
        float gap = 4.0f;
        float inputW = innerW - loginW - randW - gap * 2.0f;
        float inputX = x + padX;
        float randX = inputX + inputW + gap;
        float loginX = randX + randW + gap;

        float listY = inputY + inputH + errorH + listGap;
        float restoreW = hasRestore ? Fonts.sfRegular.a(RESTORE_ACCOUNT_LABEL, 7.4f) : 0.0f;
        float restoreX = x + padX;
        float restoreY = listY + listH + 4.0f;

        return new AccountsRect(
                x, y, w, h,
                closeX, closeY, closeSize,
                inputX, inputY, inputW, inputH,
                loginX, loginW,
                randX, randW,
                x + padX, listY, innerW, listH, Math.max(0.0f, contentH - listH),
                hasError, hasRestore, restoreX, restoreY, restoreW, 10.0f
        );
    }

    private void openExitDialog() {
        exitDialogOpen = true;
        yesOffX = 0.0f;
        yesOffY = 0.0f;
        yesTargetX = 0.0f;
        yesTargetY = 0.0f;
        lastDodgeNanos = 0L;
    }

    private void closeExitDialog() {
        exitDialogOpen = false;
    }

    private static void openUrl(String url) {
        try {
            Util.getOperatingSystem().open(URI.create(url));
        } catch (Exception ignored) {
        }
    }

    private Layout computeLayout(float sw, float sh) {
        float colW = MathHelper.clamp(sw * 0.21f, 154.0f, 188.0f);
        float titleW = Math.max(colW * 1.14f, 184.0f);
        float titleCellW = titleW / TITLE_MASK.cols;
        float titleH = TITLE_MASK.rows * (titleCellW * (8.0f / 6.0f));
        boolean compact = sh < 285.0f;
        float titleGap = compact ? 11.0f : 15.0f;
        float btnH = compact ? 21.0f : 24.0f;
        float gap = compact ? 4.5f : 6.0f;
        float circleSize = compact ? 22.0f : 24.0f;
        float circleTopGap = compact ? 8.0f : 10.0f;
        int btnCount = MAIN_LABELS.length;
        float buttonsH = btnCount * btnH + (btnCount - 1) * gap + circleTopGap + circleSize;
        float totalH = titleH + titleGap + buttonsH;
        float colX = sw * 0.72f - colW * 0.5f;
        float titleY = (sh - totalH) * 0.5f;
        float startY = titleY + titleH + titleGap;
        float circleY = startY + btnCount * btnH + (btnCount - 1) * gap + circleTopGap;
        float circleGap = (colW - 4.0f * circleSize) / 3.0f;
        return new Layout(colX, startY, colW, btnH, gap, circleY, circleSize, circleGap, titleY, titleW);
    }

    private static boolean hitRect(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static boolean hitCircle(double mx, double my, float cx, float cy, float r) {
        double dx = mx - cx;
        double dy = my - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private static float smooth(float value) {
        float t = MathHelper.clamp(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static TitleMask buildTitleMask() {
        java.awt.Font awtFont;
        try (var stream = EggTitleScreen.class.getResourceAsStream("/assets/egg/font/sf_bold.ttf")) {
            if (stream != null) {
                awtFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, stream).deriveFont(java.awt.Font.BOLD, 64.0f);
            } else {
                awtFont = new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 64);
            }
        } catch (Exception ignored) {
            awtFont = new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 64);
        }

        BufferedImage probe = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g0 = probe.createGraphics();
        FontMetrics fm = g0.getFontMetrics(awtFont);
        int rawW = fm.stringWidth("Egg Visuals") + 24;
        int rawH = fm.getHeight() + 24;
        int ascent = fm.getAscent();
        g0.dispose();

        BufferedImage hi = new BufferedImage(rawW, rawH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(awtFont);
        g.setColor(Color.WHITE);
        g.drawString("Egg Visuals", 12, ascent + 12);
        g.dispose();

        int minX = rawW, minY = rawH, maxX = 0, maxY = 0;
        for (int y = 0; y < rawH; y++) {
            for (int x = 0; x < rawW; x++) {
                if (((hi.getRGB(x, y) >>> 24) & 255) > 20) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        int cw = Math.max(1, maxX - minX + 1);
        int ch = Math.max(1, maxY - minY + 1);
        int rows = 15;
        int cols = Math.max(48, Math.round((float) cw / ch * rows * (8.0f / 6.0f)));
        BufferedImage grid = new BufferedImage(cols, rows, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = grid.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(hi.getSubimage(minX, minY, cw, ch), 0, 0, cols, rows, null);
        g2.dispose();

        float[] levels = new float[cols * rows];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int al = (grid.getRGB(c, r) >>> 24) & 255;
                levels[r * cols + c] = al < 55 ? 0.0f : al / 255.0f;
            }
        }
        return new TitleMask(cols, rows, levels);
    }

    private record ChangeItem(String tag, String text) {}

    private record ChangeDay(String title, List<ChangeItem> items) {}

    private record AccountsRect(
            float x, float y, float w, float h,
            float closeX, float closeY, float closeSize,
            float inputX, float inputY, float inputW, float inputH,
            float loginX, float loginW,
            float randX, float randW,
            float listX, float listY, float listW, float listH, float maxScroll,
            boolean hasError, boolean hasRestore, float restoreX, float restoreY, float restoreW, float restoreH
    ) {
        private float rowWidth() {
            return listW - (maxScroll > 0.0f ? 5.0f : 0.0f);
        }

        private float rowY(int index, float scroll) {
            return listY + index * ACCOUNT_ROW_STRIDE - scroll;
        }

        private float deleteX() {
            return listX + rowWidth() - ACCOUNT_DELETE_SIZE - 3.0f;
        }

        private float deleteY(float rowY) {
            return rowY + (ACCOUNT_ROW_HEIGHT - ACCOUNT_DELETE_SIZE) * 0.5f;
        }
    }

    private record WrappedLine(float yOffset, float height, boolean isDayHeader, String tag, String prefix, String rest) {}

    private record ChangelogRect(float x, float y, float w, float h) {}

    private record UpdateBannerRect(float x, float y, float w, float h, float closeX, float closeW,
                                    String title, String suffix, float titleW) {}

    private record TitleMask(int cols, int rows, float[] levels) {}

    private record Layout(float colX, float startY, float colW, float btnH, float gap,
                          float circleY, float circleSize, float circleGap, float titleY, float titleW) {
        float buttonY(int index) {
            return startY + index * (btnH + gap);
        }

        float circleX(int index) {
            return colX + index * (circleSize + circleGap);
        }
    }

    private static final class MenuBackgroundBatch implements DrawBatcher.Batched {
        private static final int UNIFORM_BYTES = 16;
        private BufferAllocator allocator;
        private GpuBuffer vertexBuffer;
        private GpuBuffer uniformBuffer;
        private ByteBuffer uniformData;
        private int indexCount;
        private boolean hasData;
        private float fbW;
        private float fbH;
        private float eggNormX;
        private float alpha;

        void queue(float fbW, float fbH, float eggNormX, float alpha) {
            this.fbW = fbW;
            this.fbH = fbH;
            this.eggNormX = eggNormX;
            this.alpha = alpha;
            this.hasData = alpha > 0.002f;
            if (this.hasData) {
                DrawBatcher.register(this);
            }
        }

        @Override
        public int batchLayer() {
            return -3;
        }

        @Override
        public void uploadBatch(CommandEncoder encoder) {
            indexCount = 0;
            if (!hasData) return;

            if (allocator == null) allocator = new BufferAllocator(256);
            if (uniformData == null) uniformData = MemoryUtil.memAlloc(UNIFORM_BYTES);
            if (uniformBuffer == null) {
                uniformBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "egg:menu_bg_uniform",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        UNIFORM_BYTES
                );
            }

            uniformData.clear();
            uniformData.putFloat(fbW).putFloat(fbH).putFloat(eggNormX).putFloat(alpha);
            uniformData.flip();
            encoder.writeToBuffer(uniformBuffer.slice(0, UNIFORM_BYTES), uniformData);

            int aByte = MathHelper.clamp(Math.round(alpha * 255.0f), 0, 255);
            try {
                BufferBuilder mesh = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                mesh.vertex(-1.0f, 1.0f, 0.0f).texture(0.0f, 1.0f).color(255, 255, 255, aByte);
                mesh.vertex(-1.0f, -1.0f, 0.0f).texture(0.0f, 0.0f).color(255, 255, 255, aByte);
                mesh.vertex(1.0f, -1.0f, 0.0f).texture(1.0f, 0.0f).color(255, 255, 255, aByte);
                mesh.vertex(1.0f, 1.0f, 0.0f).texture(1.0f, 1.0f).color(255, 255, 255, aByte);
                BuiltBuffer built = mesh.endNullable();
                if (built != null) {
                    try {
                        ByteBuffer raw = built.getBuffer();
                        int bytes = raw.remaining();
                        if (vertexBuffer == null || vertexBuffer.size() < bytes) {
                            if (vertexBuffer != null) vertexBuffer.close();
                            vertexBuffer = RenderSystem.getDevice().createBuffer(
                                    () -> "egg:menu_bg_vbo",
                                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                                    Math.max(bytes, 256)
                            );
                        }
                        encoder.writeToBuffer(vertexBuffer.slice(0, bytes), raw);
                        indexCount = built.getDrawParameters().indexCount();
                    } finally {
                        built.close();
                    }
                }
            } finally {
                allocator.reset();
            }
        }

        @Override
        public void drawBatch(RenderPass pass) {
            if (!hasData || indexCount <= 0 || vertexBuffer == null || uniformBuffer == null) return;
            var indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
            pass.setPipeline(Pipelines.MENU_BG);
            pass.setUniform("MenuBgData", uniformBuffer);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.setIndexBuffer(indices.getIndexBuffer(indexCount), indices.getIndexType());
            pass.drawIndexed(0, 0, indexCount, 1);
        }

        @Override
        public void discardBatch() {
            hasData = false;
            indexCount = 0;
        }

        void close() {
            discardBatch();
            if (vertexBuffer != null) {
                vertexBuffer.close();
                vertexBuffer = null;
            }
            if (uniformBuffer != null) {
                uniformBuffer.close();
                uniformBuffer = null;
            }
            if (uniformData != null) {
                MemoryUtil.memFree(uniformData);
                uniformData = null;
            }
            if (allocator != null) {
                allocator.close();
                allocator = null;
            }
        }
    }

    private static final class AsciiEggBatch implements DrawBatcher.Batched {
        private final double[] depth = new double[EggStartupAnimation.COLUMNS * EggStartupAnimation.ROWS];
        private final float[] light = new float[depth.length];
        private BufferAllocator allocator;
        private GpuBuffer vertexBuffer;
        private int indexCount;
        private boolean hasData;
        private float sw;
        private float sh;
        private float centerX;
        private float centerY;
        private float titleCenterX;
        private float titleTopY;
        private float titleWidth;
        private double seconds;
        private float tiltX;
        private float tiltY;
        private float alpha;

        void queue(MinecraftClient client, float sw, float sh, float centerX, float centerY,
                   float titleCenterX, float titleTopY, float titleWidth,
                   double seconds, float tiltX, float tiltY, float alpha) {
            EggStartupAnimation.ensureAtlas(client);
            this.sw = sw;
            this.sh = sh;
            this.centerX = centerX;
            this.centerY = centerY;
            this.titleCenterX = titleCenterX;
            this.titleTopY = titleTopY;
            this.titleWidth = titleWidth;
            this.seconds = seconds;
            this.tiltX = tiltX;
            this.tiltY = tiltY;
            this.alpha = alpha;
            this.hasData = alpha > 0.002f && sw > 0 && sh > 0;
            if (this.hasData) {
                DrawBatcher.register(this);
            }
        }

        @Override
        public int batchLayer() {
            return -2;
        }

        @Override
        public void uploadBatch(CommandEncoder encoder) {
            indexCount = 0;
            if (!hasData) return;

            Arrays.fill(depth, Double.NEGATIVE_INFINITY);
            double yaw = seconds * 0.68 + tiltX * 0.34;
            double lean = -0.37 + tiltX * 0.16 + Math.sin(seconds * 1.05) * 0.04;
            double pitch = -tiltY * 0.22 + Math.sin(seconds * 0.85) * 0.10;
            double cosYaw = Math.cos(yaw);
            double sinYaw = Math.sin(yaw);
            double cosLean = Math.cos(lean);
            double sinLean = Math.sin(lean);
            double cosPitch = Math.cos(pitch);
            double sinPitch = Math.sin(pitch);

            for (EggStartupAnimation.ShellPoint p : EggStartupAnimation.SHELL) {
                double spinX = p.x() * cosYaw + p.z() * sinYaw;
                double spinZ = p.z() * cosYaw - p.x() * sinYaw;
                double tiltPosY = p.y() * cosPitch - spinZ * sinPitch;
                double z = spinZ * cosPitch + p.y() * sinPitch;
                double perspective = 1.0 / (1.0 - z * 0.17);
                int col = (int) Math.round((EggStartupAnimation.COLUMNS - 1) * 0.5
                        + (spinX * cosLean - tiltPosY * sinLean) * perspective / EggStartupAnimation.CELL_DEPTH_X);
                int row = (int) Math.round((EggStartupAnimation.ROWS - 1) * 0.5
                        - (tiltPosY * cosLean + spinX * sinLean) * perspective / EggStartupAnimation.CELL_DEPTH_Y);
                if (col < 0 || col >= EggStartupAnimation.COLUMNS || row < 0 || row >= EggStartupAnimation.ROWS) continue;
                int cell = row * EggStartupAnimation.COLUMNS + col;
                if (z <= depth[cell]) continue;
                depth[cell] = z;

                double normalX = p.nx() * cosYaw + p.nz() * sinYaw;
                double normalZ = p.nz() * cosYaw - p.nx() * sinYaw;
                double normalY = p.ny() * cosPitch - normalZ * sinPitch;
                double nz = normalZ * cosPitch + p.ny() * sinPitch;
                double nx = normalX * cosLean - normalY * sinLean;
                double ny = normalY * cosLean + normalX * sinLean;
                double diffuse = Math.max(0.0, -nx * 0.48 + ny * 0.58 + nz * 0.65);
                double gloss = Math.pow(Math.max(0.0, -nx * 0.30 + ny * 0.39 + nz * 0.87), 13);
                double rim = Math.pow(1.0 - Math.max(0.0, nz), 3) * Math.max(0.0, (ny - nx * 0.5) * 0.55 + 0.18);
                double bounce = Math.max(0.0, nx * 0.62 - ny * 0.38 + nz * 0.18);
                light[cell] = (float) Math.min(1.0, 0.12 + diffuse * 0.57 + gloss * 0.34 + rim * 0.42 + bounce * 0.23 + p.grain() * 0.10);
            }

            float scale = Math.min(sh * 0.78f / (EggStartupAnimation.ROWS * EggStartupAnimation.CELL_HEIGHT),
                    sw * 0.44f / (EggStartupAnimation.COLUMNS * EggStartupAnimation.CELL_WIDTH));
            float atlasW = EggStartupAnimation.GLYPH_BITS.length * EggStartupAnimation.CELL_WIDTH;
            float atlasH = 6.0f;

            int maxQuads = 2655 + TITLE_MASK.cols * TITLE_MASK.rows;
            if (allocator == null) allocator = new BufferAllocator(maxQuads * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
            try {
                BufferBuilder mesh = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                for (int row = 0; row < EggStartupAnimation.ROWS; row++) {
                    for (int col = 0; col < EggStartupAnimation.COLUMNS; col++) {
                        int cell = row * EggStartupAnimation.COLUMNS + col;
                        if (depth[cell] == Double.NEGATIVE_INFINITY) continue;

                        float gx = (col - (EggStartupAnimation.COLUMNS - 1) * 0.5f) * EggStartupAnimation.CELL_WIDTH - 2.0f;
                        float gy = (row - (EggStartupAnimation.ROWS - 1) * 0.5f) * EggStartupAnimation.CELL_HEIGHT - 2.0f;
                        float sx = centerX + gx * scale;
                        float sy = centerY + gy * scale;
                        float sizePx = 5.0f * scale;

                        float seed = cellNoise(cell);
                        float brightness = MathHelper.clamp(light[cell] + (seed - 0.5f) * 0.18f, 0.0f, 1.0f);
                        emitGlyphQuad(mesh, sx, sy, sizePx, sizePx, brightness, alpha, atlasW, atlasH);
                    }
                }

                float cellStepX = titleWidth / TITLE_MASK.cols;
                float cellStepY = cellStepX * (8.0f / 6.0f);
                float glyphSize = cellStepX * (5.0f / 6.0f);
                float titleStartX = titleCenterX - titleWidth * 0.5f;
                for (int r = 0; r < TITLE_MASK.rows; r++) {
                    for (int c = 0; c < TITLE_MASK.cols; c++) {
                        float lvl = TITLE_MASK.levels[r * TITLE_MASK.cols + c];
                        if (lvl <= 0.01f) continue;
                        float wave = 0.82f + 0.18f * (float) Math.sin(seconds * 1.8 - c * 0.11 + r * 0.15);
                        float seed = cellNoise(r * 193 + c * 37);
                        float brightness = MathHelper.clamp(lvl * wave + (seed - 0.5f) * 0.14f, 0.35f, 1.0f);
                        float sx = titleStartX + c * cellStepX;
                        float sy = titleTopY + r * cellStepY;
                        emitGlyphQuad(mesh, sx, sy, glyphSize, glyphSize, brightness, alpha, atlasW, atlasH);
                    }
                }

                BuiltBuffer built = mesh.endNullable();
                if (built != null) {
                    try {
                        ByteBuffer raw = built.getBuffer();
                        int bytes = raw.remaining();
                        if (vertexBuffer == null || vertexBuffer.size() < bytes) {
                            if (vertexBuffer != null) vertexBuffer.close();
                            vertexBuffer = RenderSystem.getDevice().createBuffer(
                                    () -> "egg:menu_glyphs_vbo",
                                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                                    Math.max(bytes, 4096)
                            );
                        }
                        encoder.writeToBuffer(vertexBuffer.slice(0, bytes), raw);
                        indexCount = built.getDrawParameters().indexCount();
                    } finally {
                        built.close();
                    }
                }
            } finally {
                allocator.reset();
            }
        }

        private void emitGlyphQuad(BufferBuilder mesh, float sx, float sy, float wPx, float hPx,
                                   float brightness, float alpha, float atlasW, float atlasH) {
            int glyph = Math.min(EggStartupAnimation.GLYPH_BITS.length - 1,
                    (int) (brightness * EggStartupAnimation.GLYPH_BITS.length));
            float smoothB = smooth(brightness * 1.35f);
            int r = MathHelper.clamp(Math.round(MathHelper.lerp(smoothB, 141, 255)), 0, 255);
            int g = MathHelper.clamp(Math.round(MathHelper.lerp(smoothB, 148, 255)), 0, 255);
            int b = MathHelper.clamp(Math.round(MathHelper.lerp(smoothB, 158, 255)), 0, 255);
            int a = MathHelper.clamp(Math.round((0.68f + brightness * 0.32f) * alpha * 255.0f), 0, 255);

            float u0 = (glyph * EggStartupAnimation.CELL_WIDTH) / atlasW;
            float u1 = (glyph * EggStartupAnimation.CELL_WIDTH + 5.0f) / atlasW;
            float v0 = 0.0f;
            float v1 = 5.0f / atlasH;

            float nx0 = (sx / sw) * 2.0f - 1.0f;
            float nx1 = ((sx + wPx) / sw) * 2.0f - 1.0f;
            float ny0 = 1.0f - (sy / sh) * 2.0f;
            float ny1 = 1.0f - ((sy + hPx) / sh) * 2.0f;

            mesh.vertex(nx0, ny0, 0.0f).texture(u0, v0).color(r, g, b, a);
            mesh.vertex(nx0, ny1, 0.0f).texture(u0, v1).color(r, g, b, a);
            mesh.vertex(nx1, ny1, 0.0f).texture(u1, v1).color(r, g, b, a);
            mesh.vertex(nx1, ny0, 0.0f).texture(u1, v0).color(r, g, b, a);
        }

        @Override
        public void drawBatch(RenderPass pass) {
            if (!hasData || indexCount <= 0 || vertexBuffer == null) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return;
            AbstractTexture texture = client.getTextureManager().getTexture(EggStartupAnimation.GLYPHS);
            if (texture == null) return;
            var indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
            pass.setPipeline(Pipelines.MENU_GLYPHS);
            pass.bindTexture("Sampler0", texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
            pass.setVertexBuffer(0, vertexBuffer);
            pass.setIndexBuffer(indices.getIndexBuffer(indexCount), indices.getIndexType());
            pass.drawIndexed(0, 0, indexCount, 1);
        }

        @Override
        public void discardBatch() {
            hasData = false;
            indexCount = 0;
        }

        void close() {
            discardBatch();
            if (vertexBuffer != null) {
                vertexBuffer.close();
                vertexBuffer = null;
            }
            if (allocator != null) {
                allocator.close();
                allocator = null;
            }
        }

        private static float cellNoise(int cell) {
            int hash = (cell + 1) * 0x9E3779B9;
            hash = (hash ^ (hash >>> 16)) * 0x85EBCA6B;
            return ((hash ^ (hash >>> 13)) & 0xFFFF) / 65535.0f;
        }
    }
}

package egg.client.ui;

import egg.client.config.ConfigManager;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.ModuleManager;
import egg.client.module.misc.ClientSettings;
import egg.client.module.render.Keybinds;
import egg.client.module.render.WorldTweaks;
import egg.client.module.setting.*;
import egg.client.render.IconRenderer;
import egg.client.render.Render2D;
import egg.client.render.font.ClientFont;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.ui.grid.GuiDotGrid;
import egg.client.ui.section.CosmeticsPanel;
import egg.client.ui.section.InventoryConfigPanel;
import egg.client.ui.theme.Theme;
import egg.client.ui.widget.WorldTweaksUI;
import egg.client.util.ColorUtil;
import egg.client.util.FpsBenchmark;
import egg.client.util.GuiSoundManager;
import egg.client.util.animation.Animation;
import egg.client.util.animation.Easing;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.IntPredicate;
import java.util.function.IntConsumer;

public final class ClickGuiScreen extends Screen {
    private static final Identifier LOGO = Identifier.of("egg", "textures/egg_logo.png");
    private static final List<Category> PAGES = List.of(Category.ESP, Category.MOVEMENT, Category.INVENTORY,
            Category.MISC, Category.MINI_GAMES, Category.COSMETICS, Category.SETTINGS);
    private static final int TEXT = 0xFFF2F3F6;
    private static final int MUTED = 0xFF939DAE;
    private static final int BORDER = 0xFF2C3038;
    private static final float ROW = 26;
    private static ClickGuiScreen instance;

    private final Animation opening = new Animation(0, 200, Easing.EASE_OUT_CUBIC);
    private final Animation theme = new Animation(0, 380, Easing.EASE_IN_OUT_QUART);
    private final List<Control> controls = new ArrayList<>();
    private final Animation pageReveal = new Animation(1, 240, Easing.EASE_OUT_CUBIC);
    private final java.util.Deque<Category> back = new java.util.ArrayDeque<>();
    private final java.util.Deque<Category> forward = new java.util.ArrayDeque<>();
    private Category page = Category.ESP;
    private Box view;
    private float zoom = 1;
    private float alpha = 1;
    private float mx;
    private float my;
    private float scroll;
    private float targetScroll;
    private float maxScroll;
    private boolean closing;
    private boolean searching;
    private String query = "";
    private ModeSetting openMode;
    private Box modeAnchor;
    private Box modePopup;
    private int modeScroll;
    private Setting editing;
    private String draft = "";
    private boolean invalidColor;
    private IntConsumer binding;
    private SliderDrag sliderDrag;
    private boolean worldDrag;
    private String tooltip;
    private String hoverMessage;
    private long hoverSince;
    private DrawContext lateContext;

    private record Box(float x, float y, float w, float h) {
        float right() { return x + w; }
        float bottom() { return y + h; }
        boolean hits(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    private record Control(Box box, boolean clipped, IntPredicate handler) {}
    private record SliderDrag(SliderSetting setting, float x, float w) {
        void update(float px) { setting.setPercent(Math.clamp((px - x) / w, 0, 1)); }
    }

    public ClickGuiScreen() {
        super(Text.literal("Egg Visual"));
    }

    public static ClickGuiScreen getInstance() {
        if (instance == null) instance = new ClickGuiScreen();
        return instance;
    }

    public void markModulesDirty() {
        controls.clear();
    }

    @Override
    protected void init() {
        closing = false;
        opening.setValue(0);
        opening.setTarget(1);
        theme.setValue(settings().isCoolMenu() ? 1 : 0);
        GuiSoundManager.playScreenOpen();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { closeWithAnimation(); }

    public void closeWithAnimation() {
        if (closing) return;
        closing = true;
        opening.setTarget(0);
        GuiSoundManager.playScreenClose();
        if (!animate()) client.setScreen(null);
    }

    @Override
    public void removed() {
        releaseDrag();
        CosmeticsPanel.mouseReleased();
        openMode = null;
        editing = null;
        binding = null;
        searching = false;
        lateContext = null;
        controls.clear();
        ConfigManager.save();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (client.world != null && ClientSettings.isBlurEnabled() && !FpsBenchmark.overrideNoBlur) {
            int strength = ClientSettings.getBlurStrength();
            var blur = client.options.getMenuBackgroundBlurriness();
            int previous = blur.getValue();
            try {
                if (previous != strength) blur.setValue(strength);
                client.gameRenderer.renderBlur();
            } finally {
                if (previous != strength) blur.setValue(previous);
            }
        }
        float dim = ClientSettings.getScreenDimOpacity() * (animate() ? opening.getValue() : 1);
        Render2D.drawRoundedRect(context, 0, 0, width, height, 0, ColorUtil.withAlphaFloat(0xFF080A10, dim));
        DrawBatcher.flushPending();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        alpha = animate() ? opening.getValue() : 1;
        if (closing && alpha <= 0.01f) {
            client.setScreen(null);
            return;
        }
        theme.setTarget(settings().isCoolMenu() ? 1 : 0);
        zoom = Math.min(ClientSettings.getGuiScale(), Math.min(width / 820f, height / 470f));
        String motion = ClientSettings.getOpenAnimationMode();
        if ("Lens".equals(motion)) zoom *= 0.94f + 0.06f * alpha;
        else if ("Волна".equals(motion)) zoom *= 0.978f + 0.022f * alpha;
        mx = localX(mouseX);
        my = localY(mouseY);
        controls.clear();
        tooltip = null;
        modeAnchor = null;
        modePopup = null;
        lateContext = context;

        float w = Math.min(1050, width * 0.89f / zoom);
        float h = Math.min(600, height * 0.82f / zoom);
        float rail = Math.clamp(w * 0.174f, 130, 176);
        Box frame = new Box(-w / 2, -h / 2, w, h);
        view = new Box(frame.x + rail + 18, frame.y + 48 + (1 - pageReveal.getValue()) * 10, w - rail - 37, h - 66);
        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().translate(width / 2f, height / 2f).scale(zoom);
            frame(context, frame, rail);
            GuiDotGrid.render(context, view.x, view.y, view.w, view.h, mx, my, alpha * 0.25f);
            if (ClientSettings.isCursorGlow() && hover(frame)) {
                Render2D.drawCircleShadow(context, mx, my, 9, 18,
                        faded(ColorUtil.withAlphaFloat(Theme.getAccent(), 0.035f)));
            }
            navigation(context, frame, rail);
            toolbar(context, frame);
            Render2D.enableScissor(context, view.x, view.y, view.right(), view.bottom());
            try {
                content(context);
                if (openMode != null) DrawBatcher.flushPending();
                modeMenu(context);
            } finally {
                Render2D.disableScissor(context);
            }
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    private void frame(DrawContext context, Box frame, float rail) {
        if (ClientSettings.isShadowEnabled()) {
            Render2D.drawShadow(context, frame.x, frame.y + 4, frame.w, frame.h, 15, 23, faded(0xB0000000));
        }
        int surface = ColorUtil.withAlphaFloat(surface(), 0.88f + ClientSettings.getGuiBgOpacity() * 0.12f);
        rect(context, frame, 15, surface);
        Render2D.drawRoundedRect(context, frame.x, frame.y, rail, frame.h,
                new org.joml.Vector4f(15, 0, 0, 15), faded(sidebarColor()));
        line(context, frame.x + rail, frame.y + 1, 1, frame.h - 2, 0xFF22252C);
        Render2D.drawRoundedOutline(context, frame.x, frame.y, frame.w, frame.h, 15, 0.7f, faded(BORDER));
    }

    private void navigation(DrawContext context, Box frame, float rail) {
        float x = frame.x + 12;
        Render2D.drawCircle(context, x + 9, frame.y + 21, 4.7f, faded(0xFFFF6058));
        Render2D.drawCircle(context, x + 24, frame.y + 21, 4.7f, faded(0xFFFFBD30));
        Render2D.drawCircle(context, x + 39, frame.y + 21, 4.7f, faded(0xFF2BC840));
        Box profile = new Box(x, frame.bottom() - 48, rail - 24, 34);
        rect(context, profile, 8, tone(0xFF1B1C21, 0xFF1A2638));
        IconRenderer.drawTextureScaled(context, LOGO, x + 7, profile.y + 8, 18, 18, 128, 128, faded(TEXT));
        title(context, fit(client.getSession().getUsername(), profile.w - 40), x + 32, profile.y + 6, 11);
        text(context, "Egg Visual", x + 32, profile.y + 19, MUTED);
        float y = frame.y + 53;
        text(context, "Модули", x + 8, y, MUTED);
        y += 18;
        for (Category category : PAGES) {
            if (category == Category.COSMETICS) {
                text(context, "Оформление", x + 8, y + 10, MUTED);
                y += 28;
            }
            Box row = new Box(x, y, rail - 24, 25);
            boolean selected = page == category && query.isBlank();
            if (selected || hover(row)) {
                rect(context, row, 7, selected ? tone(0xFF252830, 0xFF26354C) : tone(0xFF1D2026, 0xFF202B40));
            }
            IconRenderer.drawCategoryIcon(context, category, x + 9, y + 6.5f, 12, faded(selected ? TEXT : MUTED));
            text(context, category.getDisplayNameRu(), x + 29, y + 8, selected ? TEXT : MUTED);
            onClick(row, false, button -> {
                if (button != 0) return false;
                if (category != page) { back.push(page); forward.clear(); }
                selectPage(category);
                GuiSoundManager.playSelect();
                return true;
            });
            y += 29;
        }
    }

    private void selectPage(Category category) {
        page = category;
        searching = false;
        openMode = null;
        editing = null;
        query("");
        CosmeticsPanel.setSearchQuery("");
        pageReveal.setValue(ClientSettings.animationsEnabled() ? 0 : 1);
        pageReveal.setTarget(1);
    }

    private void historyButton(DrawContext context, Box box, boolean next) {
        var source = next ? forward : back;
        IconRenderer.drawCollapseIcon(context, box.x + 7, box.y + 10, next,
                faded(source.isEmpty() ? 0xFF505663 : TEXT));
        onClick(box, false, button -> {
            if (button != 0 || source.isEmpty()) return false;
            (next ? back : forward).push(page);
            selectPage(source.pop());
            return true;
        });
    }

    private void toolbar(DrawContext context, Box frame) {
        float y = frame.y + 21;
        historyButton(context, new Box(view.x, y - 10, 14, 20), false);
        historyButton(context, new Box(view.x + 16, y - 10, 14, 20), true);
        title(context, query.isBlank() ? page.getDisplayNameRu() : "Поиск модулей", view.x + 37, y - 8, 16);

        Box moon = new Box(view.x - 46, y - 11, 23, 22);
        int hoverBg = tone(0xFF252830, 0xFF27364C);
        if (hover(moon)) {
            rect(context, moon, 6, hoverBg);
            tooltip = settings().isCoolMenu() ? "Графитовая тема" : "Холодная тема";
        }
        float cx = moon.x + moon.w / 2;
        float phase = theme.getValue();
        Render2D.drawCircle(context, cx, y, 4.7f, faded(ColorUtil.withAlphaFloat(TEXT, 1 - phase)));
        Render2D.drawCircle(context, cx + 2.2f, y - 2.1f, 3.7f, faded(hover(moon) ? hoverBg : sidebarColor()));
        int sun = faded(ColorUtil.withAlphaFloat(0xFFFFD09B, phase));
        Render2D.drawCircleOutline(context, cx, y, 2.5f, 0.9f, sun);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4 + (1 - phase) * 1.2;
            float dx = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);
            Render2D.drawLineRotated(context, cx + dx * 4, y + dy * 4, cx + dx * 6, y + dy * 6, sun, 0.85f);
        }
        onClick(moon, false, button -> {
            if (button != 0) return false;
            settings().toggleCoolMenu();
            changed();
            return true;
        });

        boolean showSearch = searching || !query.isEmpty();
        float w = showSearch ? Math.min(180, view.w * 0.4f) : 24;
        Box search = new Box(frame.right() - w - 17, y - 11, w, 22);
        if (showSearch || hover(search)) rect(context, search, 6, hoverBg);
        IconRenderer.drawSearchIcon(context, search.x + 12, y, faded(showSearch ? TEXT : MUTED));
        if (showSearch) {
            String value = query.isEmpty() ? "Найти модуль" : query + (searching ? "|" : "");
            text(context, fit(value, w - 28), search.x + 24, search.y + 7, query.isEmpty() ? MUTED : TEXT);
        }
        onClick(search, false, button -> {
            if (button == 1) query("");
            else if (button != 0) return false;
            searching = true;
            return true;
        });
    }
    private void content(DrawContext context) {
        if (specialPage()) {
            if (page == Category.INVENTORY) InventoryConfigPanel.render(context, view.x, view.y, view.w, view.h, mx, my, alpha);
            else CosmeticsPanel.render(context, view.x, view.y, view.w, view.h, mx, my, alpha);
            return;
        }
        float speed = Math.clamp(0.24f * ClientSettings.getSpeedMultiplier(), 0.1f, 1);
        scroll = ClientSettings.isScrollMotionBlurEnabled() && animate() ? scroll + (targetScroll - scroll) * speed : targetScroll;
        String filter = query.strip().toLowerCase(Locale.ROOT);
        List<Module> cards = ModuleManager.getInstance().getModules().stream()
                .filter(module -> filter.isEmpty() ? module.getCategory() == page
                        : (module.getName() + " " + module.getDescription() + " " + module.getCategory().getDisplayNameRu())
                        .toLowerCase(Locale.ROOT).contains(filter))
                .sorted(Comparator.comparing(Module::isFavorite).reversed()).toList();
        float gap = 14;
        float w = (view.w - gap - 5) / 2;
        float[] y = {view.y - scroll, view.y - scroll};
        if (page == Category.SETTINGS && filter.isEmpty()) {
            float bottom = preferences(context, w, gap, y[0]);
            updateScrollbar(context, bottom);
            return;
        }
        for (int i = 0; i < cards.size(); i++) {
            int column = i % 2;
            y[column] += moduleCard(context, cards.get(i), view.x + column * (w + gap), y[column], w) + 10;
        }
        if (cards.isEmpty()) text(context, "Модули не найдены", view.x + 6, view.y + 12, MUTED);
        updateScrollbar(context, Math.max(y[0], y[1]) - 10);
    }

    private void updateScrollbar(DrawContext context, float bottom) {
        maxScroll = Math.max(0, bottom + scroll - view.bottom());
        targetScroll = Math.clamp(targetScroll, 0, maxScroll);
        if (maxScroll > 0) {
            float size = Math.max(22, view.h * view.h / (view.h + maxScroll));
            float top = view.y + (view.h - size) * Math.clamp(scroll / maxScroll, 0, 1);
            rect(context, new Box(view.right() - 2, top, 2, size), 1, 0xFF515B6C);
        }
    }

    private float preferences(DrawContext context, float w, float gap, float y) {
        List<Setting> all = settings().getSettings().stream().filter(Setting::isVisible).toList();
        List<Setting> appearance = all.stream().filter(ClickGuiScreen::isAppearanceSetting).toList();
        List<Setting> controls = all.stream().filter(setting -> !isAppearanceSetting(setting)).toList();
        float left = settingsCard(context, "Оформление", appearance, view.x, y, w);
        float right = settingsCard(context, "Управление и анимации", controls, view.x + w + gap, y, w);
        return Math.max(left, right);
    }

    private static boolean isAppearanceSetting(Setting setting) {
        return switch (setting.getName()) {
            case "Цвет интерфейса", "Масштаб GUI", "Подсветка курсора", "Точечная сетка", "Background Blur",
                    "Сила блюра", "Прозрачность меню", "Затемнение фона", "Window Shadow" -> true;
            default -> false;
        };
    }

    private float settingsCard(DrawContext context, String name, List<Setting> settings, float x, float y, float w) {
        float h = 44 + settings.size() * ROW;
        Box panel = new Box(x, y, w, h);
        rect(context, panel, 10, tone(0xFF1B1D22, 0xFF1C283B));
        Render2D.drawRoundedOutline(context, x, y, w, h, 10, 0.7f, faded(BORDER));
        title(context, name, x + 12, y + 12, 12);
        line(context, x + 12, y + 34, w - 24, 1, BORDER);
        float rowY = y + 37;
        for (Setting setting : settings) {
            Box row = new Box(x + 12, rowY, w - 24, ROW);
            if (row.bottom() > view.y && rowY < view.bottom()) setting(context, setting, row);
            rowY += ROW;
        }
        return panel.bottom();
    }

    private float moduleCard(DrawContext context, Module module, float x, float y, float w) {
        boolean preferences = module instanceof ClientSettings;
        boolean open = preferences || module.isExpanded();
        List<String> description = wrap(module.getDescription(), w - 24);
        float header = 47 + (description.size() - 1) * 11;
        float expand = preferences ? 1 : ClientSettings.animationsEnabled() ? module.getExpandAnimation().getValue() : open ? 1 : 0;
        List<Setting> settings = expand > 0 ? module.getSettings().stream().filter(Setting::isVisible)
                .filter(setting -> !(module instanceof WorldTweaks) || !isWorldWidgetSetting(setting)).toList() : List.of();
        float widgetHeight = expand > 0 && module instanceof WorldTweaks ? WorldTweaksUI.getTotalHeight() : 0;
        float h = header + expand * (8 + widgetHeight + settings.size() * ROW);
        Box card = new Box(x, y, w, h);
        if (card.bottom() <= view.y || y >= view.bottom()) return h;

        boolean hovered = hover(card) && hover(view);
        module.getHoverAnimation().setTarget(hovered ? 1 : 0);
        float highlight = module.getHoverAnimation().getValue();
        int cardColor = ColorUtil.interpolate(tone(0xFF1B1D23, 0xFF1C283B), tone(0xFF242730, 0xFF29384D), highlight);
        Render2D.drawShadow(context, x, y + 2, w, h, 10, 4, faded(0x38000000));
        rect(context, card, 10, cardColor);
        Render2D.drawRoundedOutline(context, x, y, w, h, 10, 0.7f, faded(BORDER));
        IconRenderer.drawCategoryIcon(context, module.getCategory(), x + 12, y + 10, 11,
                faded(module.isEnabled() ? Theme.getAccent() : MUTED));
        String name = preferences ? "Интерфейс и управление" : module.getName();
        title(context, fit(name, w - (preferences ? 43 : 130)), x + 29, y + 9, 11.5f);
        for (int i = 0; i < description.size(); i++) {
            text(context, description.get(i), x + 12, y + 26 + i * 11, MUTED);
        }

        if (!preferences) {
            onClick(new Box(x, y, w, header), true, button -> {
                if (button == 0) module.toggleExpanded();
                else if (button == 1 && !module.isBlocked()) module.toggle();
                else return false;
                changed();
                return true;
            });
            float right = card.right() - 12;
            Box toggle = new Box(right - 27, y + 10, 27, 14);
            drawToggle(context, toggle, module.getSwitchAnimation().getValue());
            onClick(toggle, true, button -> {
                if (button != 0 || module.isBlocked()) return false;
                module.toggle();
                ConfigManager.save();
                GuiSoundManager.playToggle(module.isEnabled());
                return true;
            });
            Box bind = new Box(right - 61, y + 8, 28, 18);
            chip(context, bind, module.getKey() == 0 ? "—" : Keybinds.formatKey(module.getKey()));
            onClick(bind, true, button -> bind(button, module::setKey));
            Box star = new Box(right - 82, y + 8, 16, 18);
            IconRenderer.drawStarIcon(context, star.x + 8, y + 17, module.isFavorite(),
                    faded(module.isFavorite() ? 0xFFFFC37C : MUTED));
            onClick(star, true, button -> {
                if (button != 0) return false;
                module.toggleFavorite();
                changed();
                return true;
            });
        }

        if (expand > 0.001f) {
            Render2D.enableScissor(context, x, y + header - 1, x + w, y + h);
            line(context, x + 12, y + header - 1, w - 24, 1, BORDER);
            float rowY = y + header + 3;
            if (widgetHeight > 0 && module instanceof WorldTweaks world) {
                Box widget = new Box(x + 12, rowY, w - 24, widgetHeight);
                WorldTweaksUI.render(context, world, widget.x, widget.y, widget.w, alpha, mx, my);
                onClick(widget, true, button -> {
                    worldDrag = WorldTweaksUI.mouseClicked(world, widget.x, widget.y, widget.w, mx, my, button);
                    return worldDrag;
                });
                rowY += widgetHeight;
            }
            for (Setting setting : settings) {
                Box row = new Box(x + 12, rowY, w - 24, ROW);
                if (row.bottom() > view.y && rowY < view.bottom() && row.bottom() <= y + h) setting(context, setting, row);
                rowY += ROW;
            }
            Render2D.disableScissor(context);
        }
        return h;
    }

    private static boolean isWorldWidgetSetting(Setting setting) {
        return switch (setting.getName()) {
            case "Режим времени", "Время суток", "Время (ч)", "Сезон мира", "Погода" -> true;
            default -> false;
        };
    }

    private void setting(DrawContext context, Setting setting, Box row) {
        float center = row.y + ROW / 2;
        float w = row.w * 0.48f;
        Box field = new Box(row.right() - w, center - 9, w, 18);
        text(context, fit(setting.getName(), row.w - w - 7), row.x, center - 4, 0xFFBEC6D2);
        if (hover(row) && hover(view) && setting.getDescription() != null && !setting.getDescription().isBlank()) {
            tooltip = setting.getDescription();
        }
        switch (setting) {
            case SliderSetting slider -> slider(context, slider, field, center);
            case BooleanSetting flag -> {
                drawToggle(context, new Box(field.right() - 27, center - 7, 27, 14), flag.getAnimation().getValue());
                onClick(field, true, button -> {
                    if (button != 0) return false;
                    flag.toggle();
                    changed();
                    return true;
                });
            }
            case ModeSetting mode -> {
                chip(context, field, fit(mode.get(), field.w - 20));
                IconRenderer.drawDropdownChevron(context, field.right() - 8, center, openMode == mode, faded(MUTED));
                if (openMode == mode) modeAnchor = field;
                onClick(field, true, button -> {
                    if (button != 0 || mode.getModes().isEmpty()) return false;
                    openMode = openMode == mode ? null : mode;
                    modeScroll = Math.max(0, mode.getIndex() - 2);
                    return true;
                });
            }
            case ColorSetting color -> {
                chip(context, field, fit(editing == color ? draft + "|" : color.getHex(), field.w - 21));
                rect(context, new Box(field.right() - 14, center - 4, 8, 8), 2, ColorUtil.withAlpha(color.get(), 255));
                if (editing == color && invalidColor) {
                    Render2D.drawRoundedOutline(context, field.x, field.y, field.w, field.h, 5, 1, faded(0xFFEF7373));
                    tooltip = "Введите #RRGGBB или #AARRGGBB";
                }
                onClick(field, true, button -> {
                    if (button == 0) edit(color, color.getHex());
                    else if (button == 1) {
                        color.reset();
                        changed();
                    } else return false;
                    return true;
                });
            }
            case StringSetting string -> {
                chip(context, field, editing == string ? draft + "|" : string.get());
                onClick(field, true, button -> {
                    if (button != 0) return false;
                    edit(string, string.get());
                    return true;
                });
            }
            case BindSetting key -> {
                chip(context, field, key.getKeyName());
                onClick(field, true, button -> bind(button, key::setKey));
            }
            case ButtonSetting action -> {
                chip(context, field, "Выполнить");
                onClick(field, true, button -> {
                    if (button != 0) return false;
                    action.run();
                    changed();
                    return true;
                });
            }
            default -> { }
        }
    }

    private void modeMenu(DrawContext context) {
        if (openMode == null) return;
        if (modeAnchor == null) {
            openMode = null;
            return;
        }
        ModeSetting mode = openMode;
        List<String> choices = mode.getModes();
        int rows = Math.min(6, choices.size());
        if (rows == 0) return;
        modeScroll = Math.clamp(modeScroll, 0, choices.size() - rows);
        float w = Math.min(view.w, Math.max(modeAnchor.w, 113));
        float h = 8 + rows * 20;
        float x = Math.clamp(modeAnchor.right() - w, view.x, view.right() - w);
        float preferredY = modeAnchor.bottom() + h <= view.bottom() ? modeAnchor.bottom() + 2 : modeAnchor.y - h - 2;
        float y = Math.clamp(preferredY, view.y, Math.max(view.y, view.bottom() - h));
        modePopup = new Box(x, y, w, h);
        rect(context, modePopup, 6, tone(0xFF16191F, 0xFF152031));
        Render2D.drawRoundedOutline(context, x, y, w, h, 6, 0.7f, faded(BORDER));
        onClick(modePopup, true, button -> true);
        for (int i = 0; i < rows; i++) {
            String choice = choices.get(modeScroll + i);
            Box row = new Box(x + 4, y + 4 + i * 20, w - 8, 20);
            if (hover(row) || mode.is(choice)) rect(context, row, 4, tone(0xFF292D36, 0xFF263852));
            text(context, fit(choice, row.w - 12), row.x + 7, row.y + 6, mode.is(choice) ? TEXT : MUTED);
            onClick(row, true, button -> {
                if (button == 0) {
                    mode.set(choice);
                    openMode = null;
                    changed();
                }
                return true;
            });
        }
    }

    private void slider(DrawContext context, SliderSetting slider, Box field, float center) {
        float w = Math.max(18, field.w - 40);
        float filled = Math.clamp(slider.getAnimation().getValue(), 0, 1) * w;
        line(context, field.x, center - 1, w, 2, 0xFF343A45);
        line(context, field.x, center - 1, filled, 2, Theme.getAccent());
        float x = field.x + filled;
        Render2D.drawCircleShadow(context, x, center, 3.4f, 2.5f, faded(ColorUtil.withAlphaFloat(Theme.getAccent(), 0.10f)));
        Render2D.drawCircle(context, x, center, 3.4f, faded(TEXT));
        String value = String.format(Locale.ROOT, "%.2f", slider.getValue()).replaceFirst("\\.?0+$", "");
        text(context, value, field.right() - ClientFont.getWidth(value, ClientFont.SF_REGULAR), center - 4, MUTED);
        onClick(new Box(field.x - 4, field.y, w + 8, field.h), true, button -> {
            if (button != 0) return false;
            sliderDrag = new SliderDrag(slider, field.x, w);
            sliderDrag.update(mx);
            return true;
        });
    }

    private void drawToggle(DrawContext context, Box box, float value) {
        value = Math.clamp(value, 0, 1);
        if (value > 0.01f) Render2D.drawShadow(context, box.x, box.y, box.w, box.h, 7, 4, faded(ColorUtil.withAlphaFloat(Theme.getAccent(), 0.22f * value)));
        rect(context, box, 7, ColorUtil.interpolate(0xFF30343D, Theme.getAccent(), value));
        Render2D.drawCircle(context, box.x + 7 + (box.w - 14) * value, box.y + 7, 5, faded(TEXT));
    }

    private void chip(DrawContext context, Box box, String value) {
        rect(context, box, 5, hover(box) ? tone(0xFF2A2D35, 0xFF283850) : tone(0xFF17191E, 0xFF172131));
        Render2D.drawRoundedOutline(context, box.x, box.y, box.w, box.h, 5, 0.65f, faded(BORDER));
        text(context, fit(value, box.w - 10), box.x + 5, box.y + 5, TEXT);
    }
    public void renderLate() {
        DrawContext context = lateContext;
        lateContext = null;
        if (context == null || closing || FpsBenchmark.overrideNoText || openMode != null) return;
        long now = System.nanoTime();
        if (!java.util.Objects.equals(hoverMessage, tooltip)) {
            hoverMessage = tooltip;
            hoverSince = now;
        }
        if (binding == null && (tooltip == null || now - hoverSince < 400_000_000L)) return;
        String message = binding != null ? "Нажмите клавишу или кнопку мыши · Esc — отмена" : tooltip;
        float w = Math.min(Math.min(235, view.w), ClientFont.getWidth(message, ClientFont.SF_REGULAR) + 15);
        List<String> lines = wrap(message, w - 14);
        float h = lines.size() * 11 + 9;
        float halfWidth = width / (2f * zoom);
        float halfHeight = height / (2f * zoom);
        float x = Math.clamp(mx + 11, -halfWidth + 5, halfWidth - w - 5);
        float y = Math.clamp(my + 12, -halfHeight + 5, halfHeight - h - 5);
        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().translate(width / 2f, height / 2f).scale(zoom);
            rect(context, new Box(x, y, w, h), 5, 0xFF11141D);
            for (int i = 0; i < lines.size(); i++) text(context, lines.get(i), x + 7, y + 5 + 11 * i, TEXT);
            DrawBatcher.flushPending();
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (closing) return true;
        mx = localX(click.x());
        my = localY(click.y());
        int button = click.button();
        if (binding != null) {
            if (button >= 2) applyBinding(1000 + button);
            else binding = null;
            return true;
        }
        if (editing != null && !commitEdit()) return true;
        if (openMode != null && (modePopup == null || !hover(modePopup))) {
            openMode = null;
            return true;
        }
        boolean wasSearching = searching;
        searching = false;
        for (Control control : List.copyOf(controls).reversed()) {
            if (control.box.hits(mx, my) && (!control.clipped || hover(view)) && control.handler.test(button)) return true;
        }
        if (wasSearching && query.isEmpty()) return true;
        if (view == null || !hover(view) || !specialPage()) return true;
        if (page == Category.INVENTORY) return InventoryConfigPanel.mouseClicked(view.x, view.y, view.w, view.h, mx, my, button);
        return CosmeticsPanel.mouseClicked(view.x, view.y, view.w, view.h, mx, my, button);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        float x = localX(click.x());
        float y = localY(click.y());
        if (sliderDrag != null) {
            sliderDrag.update(x);
            return true;
        }
        if (worldDrag) return WorldTweaksUI.mouseDragged(ModuleManager.getInstance().getModule(WorldTweaks.class), x, y);
        return specialPage() && page == Category.COSMETICS && CosmeticsPanel.mouseDragged(x, y, click.button(), dx / zoom, dy / zoom);
    }

    @Override
    public boolean mouseReleased(Click click) {
        releaseDrag();
        if (page == Category.COSMETICS) CosmeticsPanel.mouseReleased(localX(click.x()), localY(click.y()), click.button());
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        float px = localX(x);
        float py = localY(y);
        if (view == null || !view.hits(px, py)) return false;
        if (openMode != null && modePopup != null && modePopup.hits(px, py)) {
            if (vertical != 0) modeScroll = Math.clamp(modeScroll + (vertical < 0 ? 1 : -1), 0, Math.max(0, openMode.getModes().size() - 6));
            return true;
        }
        if (!specialPage()) targetScroll = Math.clamp(targetScroll - (float) vertical * 33, 0, maxScroll);
        else if (page == Category.INVENTORY) InventoryConfigPanel.mouseScrolled(vertical);
        else CosmeticsPanel.mouseScrolled(px, py, vertical, view.x, view.y, view.w, view.h);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (binding != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) binding = null;
            else applyBinding(key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE ? 0 : key);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (openMode != null) openMode = null;
            else if (editing != null) editing = null;
            else if (searching || !query.isEmpty()) {
                searching = false;
                query("");
            } else closeWithAnimation();
            return true;
        }
        boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_F && editing == null) {
            searching = true;
            return true;
        }
        if (!searching && editing == null) return super.keyPressed(input);
        String value = editing == null ? query : draft;
        if (key == GLFW.GLFW_KEY_ENTER) {
            if (editing != null) commitEdit();
            else searching = false;
        } else if (key == GLFW.GLFW_KEY_BACKSPACE && !value.isEmpty()) {
            accept(value.substring(0, value.offsetByCodePoints(value.length(), -1)));
        } else if (ctrl && key == GLFW.GLFW_KEY_A) {
            accept("");
        } else if (ctrl && key == GLFW.GLFW_KEY_V) {
            accept(value + client.keyboard.getClipboard().replace("\r", "").replace("\n", " "));
        }
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if ((!searching && editing == null) || !input.isValidChar()) return false;
        accept((editing == null ? query : draft) + input.asString());
        return true;
    }

    private void accept(String value) {
        int limit = editing instanceof ColorSetting ? 9 : 160;
        if (value.codePointCount(0, value.length()) > limit) value = value.substring(0, value.offsetByCodePoints(0, limit));
        if (editing == null) query(value);
        else {
            draft = value;
            invalidColor = false;
        }
    }

    private void edit(Setting setting, String value) {
        editing = setting;
        draft = value;
        invalidColor = false;
    }

    private boolean commitEdit() {
        if (editing instanceof ColorSetting color) {
            String value = draft.replaceFirst("^#", "");
            if (!value.matches("(?i)[0-9a-f]{6}([0-9a-f]{2})?")) {
                invalidColor = true;
                return false;
            }
            int parsed = (int) Long.parseLong(value, 16);
            color.set(value.length() == 6 ? ColorUtil.withAlpha(parsed, color.get() >>> 24) : parsed);
        } else if (editing instanceof StringSetting string) {
            string.set(draft);
        }
        editing = null;
        ConfigManager.save();
        return true;
    }

    private boolean bind(int button, IntConsumer setter) {
        if (button == 0) binding = setter;
        else if (button == 1) {
            setter.accept(0);
            changed();
        } else return false;
        return true;
    }

    private void applyBinding(int key) {
        binding.accept(key);
        binding = null;
        changed();
    }

    private void releaseDrag() {
        if (sliderDrag != null || worldDrag) ConfigManager.save();
        sliderDrag = null;
        worldDrag = false;
        WorldTweaksUI.mouseReleased();
        InventoryConfigPanel.mouseReleased();
    }

    private void query(String text) {
        query = text;
        scroll = targetScroll = maxScroll = 0;
    }

    private boolean specialPage() { return query.isBlank() && (page == Category.INVENTORY || page == Category.COSMETICS); }
    private static boolean animate() { return ClientSettings.animationsEnabled() && !"Выкл".equals(ClientSettings.getOpenAnimationMode()); }
    private static ClientSettings settings() { return ModuleManager.getInstance().getModule(ClientSettings.class); }

    private static void changed() {
        ConfigManager.save();
        GuiSoundManager.playClick();
    }

    private float localX(double screenX) { return (float) ((screenX - width / 2f) / zoom); }
    private float localY(double screenY) { return (float) ((screenY - height / 2f) / zoom); }
    private boolean hover(Box box) { return box.hits(mx, my); }

    private void onClick(Box box, boolean clipped, IntPredicate handler) {
        controls.add(new Control(box, clipped, handler));
    }

    private int sidebarColor() { return tone(0xFF111216, 0xFF111A28); }
    private int surface() { return tone(0xFF15161A, 0xFF162031); }
    private int tone(int neutral, int cool) { return ColorUtil.interpolate(neutral, cool, theme.getValue()); }
    private int faded(int color) { return ColorUtil.withAlphaFloat(color, alpha); }

    private void rect(DrawContext context, Box box, float radius, int color) {
        Render2D.drawRoundedRect(context, box.x, box.y, box.w, box.h, radius, faded(color));
    }

    private void line(DrawContext context, float x, float y, float w, float h, int color) {
        if (w > 0 && h > 0) rect(context, new Box(x, y, w, h), Math.min(w, h) / 2, color);
    }

    private void title(DrawContext context, String text, float x, float y, float size) {
        if (!FpsBenchmark.overrideNoText) Fonts.ROCKSTAR_SEMIBOLD.a(Render2D.getMatrix(context), text, x, y, size, faded(TEXT));
    }

    private void text(DrawContext context, String text, float x, float y, int color) {
        if (!FpsBenchmark.overrideNoText) ClientFont.draw(context, text, x, y, ClientFont.SF_REGULAR, faded(color));
    }

    private static String fit(String text, float w) {
        if (ClientFont.getWidth(text, ClientFont.SF_REGULAR) <= w) return text;
        while (!text.isEmpty() && ClientFont.getWidth(text + "…", ClientFont.SF_REGULAR) > w) {
            text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
        }
        return text + "…";
    }

    private static List<String> wrap(String text, float w) {
        if (ClientFont.getWidth(text, ClientFont.SF_REGULAR) <= w) return List.of(text);
        int split = text.lastIndexOf(' ');
        while (split > 0 && ClientFont.getWidth(text.substring(0, split), ClientFont.SF_REGULAR) > w) {
            split = text.lastIndexOf(' ', split - 1);
        }
        if (split <= 0) return List.of(fit(text, w));
        return List.of(text.substring(0, split), fit(text.substring(split + 1), w));
    }
}

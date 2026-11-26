package egg.client.module.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.GuiOverlays;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class ShulkerPreview extends Module {
    private static final Identifier ATLAS_ID = Identifier.of("egg", "shulker_preview_atlas_v2");
    private static final int PATCH_SIZE = 128;
    private static final int ATLAS_W = PATCH_SIZE;
    private static final int ATLAS_H = PATCH_SIZE;

    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int SLOT_COUNT = COLS * ROWS;
    private static final float PAD = 7.5f;
    private static final float HEADER_H = 20.0f;
    private static final float SLOT_STEP = 18.5f;
    private static final float SLOT_SIZE = 17.0f;
    private static final float CARD_W = PAD * 2.0f + COLS * SLOT_STEP - (SLOT_STEP - SLOT_SIZE);
    private static final float CARD_H = PAD + HEADER_H + ROWS * SLOT_STEP - (SLOT_STEP - SLOT_SIZE) + PAD;
    private static final float CARD_RADIUS = 8.0f;

    private static boolean atlasReady;

    private final BooleanSetting colorAccent = new BooleanSetting("Цвет шалкера", true, "Подсвечивать рамку и акцент цветом шалкера");
    private final BooleanSetting showEmpty = new BooleanSetting("Пустые шалкеры", true, "Показывать карточку при наведении на пустой шалкер");
    private final BooleanSetting hideVanilla = new BooleanSetting("Скрывать ванильный текст", true, "Заменять стандартный список предметов в подсказке на сетку 9x3");
    private final SliderSetting opacity = new SliderSetting("Непрозрачность", 92.0, 45.0, 100.0, 5.0, "Непрозрачность фона карточки (%)");
    private final SliderSetting scale = new SliderSetting("Масштаб", 1.0, 0.75, 1.35, 0.05, "Масштаб карточки предпросмотра");

    private ItemStack lastStack = ItemStack.EMPTY;
    private long openStartedAt = 0L;

    public ShulkerPreview() {
        super("ShulkerPreview", "Показывает содержимое шалкеров и контейнеров сеткой 9x3 при наведении", Category.ESP);
        addSettings(colorAccent, showEmpty, hideVanilla, opacity, scale);
        setEnabled(true);
    }

    public boolean canPreview(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        boolean isShulker = stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        boolean hasItems = container != null && container.streamNonEmpty().findAny().isPresent();
        if (isShulker) {
            return showEmpty.isEnabled() || hasItems;
        }
        return hasItems;
    }

    public boolean shouldSuppressVanilla(ItemStack stack) {
        if (!hideVanilla.isEnabled()) return false;
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        return lore == null || lore.lines().isEmpty();
    }

    public void renderPreview(DrawContext context, ItemStack stack, int mouseX, int mouseY,
                              int screenWidth, int screenHeight, boolean replaceVanilla) {
        if (!canPreview(stack)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null) return;

        ensureAtlas(mc);
        AbstractTexture atlasTex = mc.getTextureManager().getTexture(ATLAS_ID);
        if (atlasTex == null) return;
        GpuTextureView texView = atlasTex.getGlTextureView();
        if (texView == null) return;

        long now = System.nanoTime();
        if (stack != lastStack) {
            lastStack = stack;
            openStartedAt = now;
        }
        float openT = Math.clamp((now - openStartedAt) / 100_000_000.0f, 0.0f, 1.0f);
        float ease = 1.0f - (1.0f - openT) * (1.0f - openT);

        DefaultedList<ItemStack> items = DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            container.copyTo(items);
        }

        int filledCount = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!items.get(i).isEmpty()) filledCount++;
        }

        float baseScale = MathHelper.clamp(scale.getValueFloat(), 0.75f, 1.35f);
        float maxFit = Math.max(0.55f, (screenWidth - 10.0f) / CARD_W);
        float finalScale = Math.min(baseScale, maxFit);
        float animScale = finalScale * (0.95f + 0.05f * ease);
        float totalW = CARD_W * finalScale;
        float totalH = CARD_H * finalScale;

        float x;
        float y;
        if (replaceVanilla) {
            x = mouseX + 12.0f;
            if (x + totalW > screenWidth - 6.0f) {
                x = mouseX - 12.0f - totalW;
            }
            y = mouseY - 12.0f;
            if (y + totalH > screenHeight - 6.0f) {
                y = screenHeight - 6.0f - totalH;
            }
        } else {
            x = mouseX + 12.0f;
            if (x + totalW > screenWidth - 6.0f) {
                x = mouseX - 12.0f - totalW;
            }
            y = mouseY - totalH - 16.0f;
            if (y < 6.0f) {
                y = Math.min(screenHeight - totalH - 6.0f, mouseY + 24.0f);
            }
        }
        x = MathHelper.clamp(x, 4.0f, Math.max(4.0f, screenWidth - totalW - 4.0f));
        y = MathHelper.clamp(y, 4.0f, Math.max(4.0f, screenHeight - totalH - 4.0f));

        float alpha = MathHelper.clamp(opacity.getValueFloat() / 100.0f, 0.4f, 1.0f) * (0.45f + 0.55f * ease);
        int accent = resolveAccentColor(stack);
        int bgCol = ColorUtil.rgba(13, 15, 22, Math.round(238 * alpha));
        int shadowCol = ColorUtil.rgba(0, 0, 0, Math.round(118 * alpha));

        float centerOffsetX = (CARD_W * finalScale - CARD_W * animScale) * 0.5f;
        float centerOffsetY = (CARD_H * finalScale - CARD_H * animScale) * 0.5f;
        float drawX = x + centerOffsetX;
        float drawY = y + centerOffsetY;

        float gridX = PAD;
        float gridY = PAD + HEADER_H;

        context.createNewRootLayer();
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(drawX, drawY);
        matrices.scale(animScale, animScale);
        try {
            Matrix3x2f pose = new Matrix3x2f(matrices);
            List<NineSlice> slices = new ArrayList<>(32);
            slices.add(new NineSlice(-2.5f, 1.5f, CARD_W + 5.0f, CARD_H + 5.5f, 10.5f, shadowCol));
            slices.add(new NineSlice(0.0f, 0.0f, CARD_W, CARD_H, CARD_RADIUS, bgCol));

            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    int idx = row * COLS + col;
                    boolean occupied = !items.get(idx).isEmpty();
                    int slotBg = occupied
                            ? ColorUtil.rgba(30, 34, 48, Math.round(205 * alpha))
                            : ColorUtil.rgba(19, 22, 31, Math.round(145 * alpha));
                    float sx = gridX + col * SLOT_STEP;
                    float sy = gridY + row * SLOT_STEP;
                    slices.add(new NineSlice(sx, sy, SLOT_SIZE, SLOT_SIZE, 3.4f, slotBg));
                }
            }

            GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
            ScreenRect bounds = new ScreenRect(
                    Math.max(0, (int) Math.floor(drawX - 4.0f * animScale)),
                    Math.max(0, (int) Math.floor(drawY - 3.0f * animScale)),
                    Math.max(1, (int) Math.ceil(CARD_W * animScale + 8.0f * animScale)),
                    Math.max(1, (int) Math.ceil(CARD_H * animScale + 8.0f * animScale))
            );
            context.state.addSimpleElement(new ShulkerCardRenderState(
                    RenderPipelines.GUI_TEXTURED,
                    TextureSetup.of(texView, sampler),
                    pose,
                    bounds,
                    slices
            ));

            matrices.pushMatrix();
            matrices.translate(PAD, PAD - 0.5f);
            matrices.scale(0.68f, 0.68f);
            context.drawItem(stack, 0, 0);
            matrices.popMatrix();

            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    ItemStack item = items.get(row * COLS + col);
                    if (item.isEmpty()) continue;
                    int ix = Math.round(gridX + col * SLOT_STEP + 0.5f);
                    int iy = Math.round(gridY + row * SLOT_STEP + 0.5f);
                    context.drawItem(item, ix, iy);
                    context.drawStackOverlay(mc.textRenderer, item, ix, iy);
                }
            }
        } finally {
            matrices.popMatrix();
        }

        final String rawName = stack.getName().getString();
        final int finalFilled = filledCount;
        final float finalAlpha = alpha;
        final float overlayX = drawX;
        final float overlayY = drawY;
        final float overlayScale = animScale;
        final boolean[] occupiedFlags = new boolean[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            occupiedFlags[i] = !items.get(i).isEmpty();
        }

        GuiOverlays.defer(() -> {
            Matrix3x2f hudMat = new Matrix3x2f().translate(overlayX, overlayY).scale(overlayScale, overlayScale);

            int topTint = ColorUtil.withAlpha(accent, Math.round(40 * finalAlpha));
            Draw2DProcessor.INSTANCE.a(hudMat, 0.5f, 0.5f, CARD_W - 1.0f, HEADER_H + 5.0f,
                    new Vector4f(CARD_RADIUS, CARD_RADIUS, 0.0f, 0.0f), topTint, topTint, 0, 0);

            int rimColor = ColorUtil.interpolate(
                    ColorUtil.rgba(255, 255, 255, Math.round(48 * finalAlpha)),
                    ColorUtil.withAlpha(accent, Math.round(165 * finalAlpha)),
                    0.62f
            );
            Draw2DProcessor.INSTANCE.drawOutline(hudMat, 0.0f, 0.0f, CARD_W, CARD_H, CARD_RADIUS, 0.8f, rimColor);

            float dividerY = PAD + HEADER_H - 4.0f;
            int dividerCol = ColorUtil.interpolate(
                    ColorUtil.rgba(255, 255, 255, Math.round(24 * finalAlpha)),
                    ColorUtil.withAlpha(accent, Math.round(72 * finalAlpha)),
                    0.45f
            );
            Draw2DProcessor.INSTANCE.drawRect(hudMat, PAD, dividerY, CARD_W - PAD * 2.0f, 0.6f, 0.3f, dividerCol);

            String countLabel = finalFilled == 0 ? "Пусто" : finalFilled + "/27";
            float badgeFont = 6.3f;
            float badgeTextW = Fonts.b.a(countLabel, badgeFont);
            float badgeW = badgeTextW + 9.0f;
            float badgeH = 10.5f;
            float badgeX = CARD_W - PAD - badgeW;
            float badgeY = PAD + (11.5f - badgeH) * 0.5f;

            int badgeBg = finalFilled == 0
                    ? ColorUtil.rgba(255, 255, 255, Math.round(16 * finalAlpha))
                    : ColorUtil.withAlpha(accent, Math.round(52 * finalAlpha));
            int badgeBorder = finalFilled == 0
                    ? ColorUtil.rgba(255, 255, 255, Math.round(28 * finalAlpha))
                    : ColorUtil.withAlpha(accent, Math.round(135 * finalAlpha));
            int badgeTextCol = finalFilled == 0
                    ? ColorUtil.rgba(154, 162, 180, Math.round(230 * finalAlpha))
                    : ColorUtil.withAlpha(ColorUtil.interpolate(0xFFFFFFFF, accent, 0.25f), Math.round(248 * finalAlpha));

            Draw2DProcessor.INSTANCE.drawRect(hudMat, badgeX, badgeY, badgeW, badgeH, 3.5f, badgeBg);
            Draw2DProcessor.INSTANCE.drawOutline(hudMat, badgeX, badgeY, badgeW, badgeH, 3.5f, 0.6f, badgeBorder);
            Fonts.b.a(hudMat, countLabel, badgeX + 4.5f, Fonts.b.a(badgeFont, badgeY + badgeH * 0.5f), badgeFont, badgeTextCol);

            float titleFont = 7.8f;
            float titleX = PAD + 14.5f;
            float maxTitleW = Math.max(24.0f, badgeX - titleX - 6.0f);
            String title = trimMsdf(rawName, titleFont, maxTitleW);
            int titleCol = ColorUtil.rgba(244, 247, 252, Math.round(252 * finalAlpha));
            Fonts.b.a(hudMat, title, titleX, Fonts.b.a(titleFont, PAD + 5.6f), titleFont, titleCol);

            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    int idx = row * COLS + col;
                    boolean occupied = occupiedFlags[idx];
                    float sx = gridX + col * SLOT_STEP;
                    float sy = gridY + row * SLOT_STEP;
                    int slotBorder = occupied
                            ? ColorUtil.withAlpha(accent, Math.round(82 * finalAlpha))
                            : ColorUtil.rgba(255, 255, 255, Math.round(18 * finalAlpha));
                    Draw2DProcessor.INSTANCE.drawOutline(hudMat, sx, sy, SLOT_SIZE, SLOT_SIZE, 3.4f, 0.6f, slotBorder);
                    if (!occupied) {
                        float dotS = 1.8f;
                        Draw2DProcessor.INSTANCE.drawRect(hudMat,
                                sx + (SLOT_SIZE - dotS) * 0.5f,
                                sy + (SLOT_SIZE - dotS) * 0.5f,
                                dotS, dotS, dotS * 0.5f,
                                ColorUtil.rgba(255, 255, 255, Math.round(24 * finalAlpha)));
                    }
                }
            }

            DrawBatcher.flushPending();
        });
    }

    private static String trimMsdf(String text, float size, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (Fonts.b.a(text, size) <= maxWidth) return text;
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() > 2 && Fonts.b.a(sb + "...", size) > maxWidth) {
            sb.setLength(sb.length() - 1);
        }
        return sb + "...";
    }

    private int resolveAccentColor(ItemStack stack) {
        if (!colorAccent.isEnabled()) {
            return Theme.getAccent();
        }
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock shulker) {
            DyeColor dye = shulker.getColor();
            if (dye == null) {
                return 0xFFB275E6;
            }
            if (dye == DyeColor.BLACK) {
                return 0xFF6B748C;
            }
            return 0xFF000000 | (dye.getEntityColor() & 0xFFFFFF);
        }
        return Theme.getAccent();
    }

    private static void ensureAtlas(MinecraftClient client) {
        if (atlasReady || client == null || client.getTextureManager() == null) return;
        atlasReady = true;

        NativeImage image = new NativeImage(ATLAS_W, ATLAS_H, true);
        float half = PATCH_SIZE * 0.5f;
        float outerR = 28.0f;
        float halfExtent = half - 2.0f;

        for (int py = 0; py < PATCH_SIZE; py++) {
            float y = Math.abs((py + 0.5f) - half);
            for (int px = 0; px < PATCH_SIZE; px++) {
                float x = Math.abs((px + 0.5f) - half);
                float dx = Math.max(0.0f, x - (halfExtent - outerR));
                float dy = Math.max(0.0f, y - (halfExtent - outerR));
                float dist = (float) Math.hypot(dx, dy) - outerR;

                float fillAlpha = MathHelper.clamp(0.5f - dist / 1.4f, 0.0f, 1.0f);
                int fillArgb = (Math.round(fillAlpha * 255.0f) << 24) | 0x00FFFFFF;
                image.setColorArgb(px, py, fillArgb);
            }
        }

        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> ATLAS_ID.toString(), image);
        texture.upload();
        client.getTextureManager().registerTexture(ATLAS_ID, texture);
    }

    private record NineSlice(float x, float y, float w, float h, float corner, int color) {}

    private static final class ShulkerCardRenderState implements SimpleGuiElementRenderState {
        private final RenderPipeline pipeline;
        private final TextureSetup textureSetup;
        private final Matrix3x2f pose;
        private final ScreenRect bounds;
        private final List<NineSlice> slices;

        ShulkerCardRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose,
                               ScreenRect bounds, List<NineSlice> slices) {
            this.pipeline = pipeline;
            this.textureSetup = textureSetup;
            this.pose = pose;
            this.bounds = bounds;
            this.slices = slices;
        }

        @Override
        public void setupVertices(VertexConsumer consumer) {
            for (NineSlice slice : slices) {
                emitNineSlice(consumer, slice);
            }
        }

        private void emitNineSlice(VertexConsumer consumer, NineSlice s) {
            float c = Math.min(s.corner, Math.min(s.w, s.h) * 0.45f);
            float[] xs = {s.x, s.x + c, s.x + s.w - c, s.x + s.w};
            float[] ys = {s.y, s.y + c, s.y + s.h - c, s.y + s.h};

            float uEdge = 32.0f / ATLAS_W;
            float[] us = {0.0f, uEdge, 1.0f - uEdge, 1.0f};
            float vEdge = 32.0f / ATLAS_H;
            float[] vs = {0.0f, vEdge, 1.0f - vEdge, 1.0f};

            for (int r = 0; r < 3; r++) {
                float y0 = ys[r];
                float y1 = ys[r + 1];
                if (y1 <= y0 + 0.001f) continue;
                float v0 = vs[r];
                float v1 = vs[r + 1];
                for (int col = 0; col < 3; col++) {
                    float x0 = xs[col];
                    float x1 = xs[col + 1];
                    if (x1 <= x0 + 0.001f) continue;
                    float u0 = us[col];
                    float u1 = us[col + 1];
                    consumer.vertex(pose, x0, y0).texture(u0, v0).color(s.color);
                    consumer.vertex(pose, x0, y1).texture(u0, v1).color(s.color);
                    consumer.vertex(pose, x1, y1).texture(u1, v1).color(s.color);
                    consumer.vertex(pose, x1, y0).texture(u1, v0).color(s.color);
                }
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return pipeline;
        }

        @Override
        public TextureSetup textureSetup() {
            return textureSetup;
        }

        @Override
        public ScreenRect scissorArea() {
            return null;
        }

        @Override
        public ScreenRect bounds() {
            return bounds;
        }
    }
}

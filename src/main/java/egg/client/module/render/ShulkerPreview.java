package egg.client.module.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
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
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;

public class ShulkerPreview extends Module {
    private static final Identifier ATLAS_ID = Identifier.of("egg", "shulker_preview_atlas");
    private static final int PATCH_SIZE = 48;
    private static final int ATLAS_W = PATCH_SIZE * 2;
    private static final int ATLAS_H = PATCH_SIZE;

    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int SLOT_COUNT = COLS * ROWS;
    private static final float PAD = 7.0f;
    private static final float HEADER_H = 17.0f;
    private static final float SLOT_STEP = 18.0f;
    private static final float CARD_W = PAD * 2.0f + COLS * SLOT_STEP;
    private static final float CARD_H = PAD + HEADER_H + ROWS * SLOT_STEP + PAD;

    private static boolean atlasReady;

    private final BooleanSetting colorAccent = new BooleanSetting("Цвет шалкера", true, "Подсвечивать рамку и акцент цветом шалкера");
    private final BooleanSetting showEmpty = new BooleanSetting("Пустые шалкеры", true, "Показывать карточку при наведении на пустой шалкер");
    private final BooleanSetting hideVanilla = new BooleanSetting("Скрывать ванильный текст", true, "Заменять стандартный список предметов в подсказке на сетку 9x3");
    private final SliderSetting opacity = new SliderSetting("Непрозрачность", 90.0, 45.0, 100.0, 5.0, "Непрозрачность фона карточки (%)");
    private final SliderSetting scale = new SliderSetting("Масштаб", 1.0, 0.75, 1.35, 0.05, "Масштаб карточки предпросмотра");

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

        DefaultedList<ItemStack> items = DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            container.copyTo(items);
        }

        int filledCount = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!items.get(i).isEmpty()) filledCount++;
        }

        float s = MathHelper.clamp(scale.getValueFloat(), 0.75f, 1.35f);
        float totalW = CARD_W * s;
        float totalH = CARD_H * s;
        if (totalW > screenWidth - 8.0f) {
            s = Math.max(0.55f, (screenWidth - 8.0f) / CARD_W);
            totalW = CARD_W * s;
            totalH = CARD_H * s;
        }

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

        float alpha = MathHelper.clamp(opacity.getValueFloat() / 100.0f, 0.4f, 1.0f);
        int accent = resolveAccentColor(stack);
        int accentTint = ColorUtil.withAlpha(accent, Math.round(44 * alpha));
        int borderCol = ColorUtil.withAlpha(accent, Math.round(195 * alpha));
        int bgCol = ColorUtil.rgba(14, 16, 23, Math.round(236 * alpha));
        int shadowCol = ColorUtil.rgba(4, 5, 9, Math.round(135 * alpha));

        context.createNewRootLayer();
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(s, s);
        try {
            Matrix3x2f pose = new Matrix3x2f(matrices);
            List<NineSlice> slices = new ArrayList<>(32);
            slices.add(new NineSlice(-1.5f, 1.0f, CARD_W + 3.0f, CARD_H + 3.0f, 9.0f, shadowCol, false));
            slices.add(new NineSlice(0.0f, 0.0f, CARD_W, CARD_H, 7.5f, bgCol, false));
            slices.add(new NineSlice(1.0f, 1.0f, CARD_W - 2.0f, HEADER_H + 2.0f, 6.5f, accentTint, false));
            slices.add(new NineSlice(0.0f, 0.0f, CARD_W, CARD_H, 7.5f, borderCol, true));

            float gridX = PAD;
            float gridY = PAD + HEADER_H;
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    int idx = row * COLS + col;
                    boolean occupied = !items.get(idx).isEmpty();
                    int slotBg = occupied
                            ? ColorUtil.rgba(30, 34, 48, Math.round(195 * alpha))
                            : ColorUtil.rgba(21, 24, 34, Math.round(150 * alpha));
                    int slotBorder = occupied
                            ? ColorUtil.withAlpha(accent, Math.round(62 * alpha))
                            : ColorUtil.rgba(52, 58, 76, Math.round(70 * alpha));
                    float sx = gridX + col * SLOT_STEP + 0.5f;
                    float sy = gridY + row * SLOT_STEP + 0.5f;
                    slices.add(new NineSlice(sx, sy, 17.0f, 17.0f, 3.2f, slotBg, false));
                    slices.add(new NineSlice(sx, sy, 17.0f, 17.0f, 3.2f, slotBorder, true));
                }
            }

            GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
            ScreenRect bounds = new ScreenRect(
                    Math.max(0, (int) Math.floor(x - 3.0f * s)),
                    Math.max(0, (int) Math.floor(y - 2.0f * s)),
                    Math.max(1, (int) Math.ceil(totalW + 6.0f * s)),
                    Math.max(1, (int) Math.ceil(totalH + 6.0f * s))
            );
            context.state.addSimpleElement(new ShulkerCardRenderState(
                    RenderPipelines.GUI_TEXTURED,
                    TextureSetup.of(texView, sampler),
                    pose,
                    bounds,
                    slices
            ));

            TextRenderer tr = mc.textRenderer;
            String countText = filledCount == 0 ? "Пусто" : filledCount + "/27";
            int countW = tr.getWidth(countText);
            int maxTitleW = Math.max(40, (int) (CARD_W - PAD * 2.0f - countW - 8.0f));
            OrderedText title = trimTitle(tr, stack.getName(), maxTitleW);
            int titleColor = ColorUtil.withAlpha(0xFFF3F6FC, Math.round(250 * alpha));
            int countColor = ColorUtil.withAlpha(filledCount == 0 ? 0xFF8C96AC : 0xFFB8C4DA, Math.round(235 * alpha));

            context.drawText(tr, title, (int) PAD + 1, (int) PAD + 3, titleColor, false);
            context.drawText(tr, countText, (int) (CARD_W - PAD - countW), (int) PAD + 3, countColor, false);

            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    ItemStack item = items.get(row * COLS + col);
                    if (item.isEmpty()) continue;
                    int ix = Math.round(gridX + col * SLOT_STEP + 1.0f);
                    int iy = Math.round(gridY + row * SLOT_STEP + 1.0f);
                    context.drawItem(item, ix, iy);
                    context.drawStackOverlay(tr, item, ix, iy);
                }
            }
        } finally {
            matrices.popMatrix();
        }
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

    private static OrderedText trimTitle(TextRenderer tr, Text text, int maxWidth) {
        if (tr.getWidth(text) <= maxWidth) {
            return text.asOrderedText();
        }
        int dotsW = tr.getWidth("...");
        Text trimmed = Text.literal(tr.trimToWidth(text.getString(), Math.max(12, maxWidth - dotsW)) + "...")
                .setStyle(text.getStyle());
        return trimmed.asOrderedText();
    }

    private static void ensureAtlas(MinecraftClient client) {
        if (atlasReady || client == null || client.getTextureManager() == null) return;
        atlasReady = true;

        NativeImage image = new NativeImage(ATLAS_W, ATLAS_H, true);
        float half = PATCH_SIZE * 0.5f;
        float outerR = 14.0f;
        float halfExtent = half - 1.5f;

        for (int py = 0; py < PATCH_SIZE; py++) {
            float y = Math.abs((py + 0.5f) - half);
            for (int px = 0; px < PATCH_SIZE; px++) {
                float x = Math.abs((px + 0.5f) - half);
                float dx = Math.max(0.0f, x - (halfExtent - outerR));
                float dy = Math.max(0.0f, y - (halfExtent - outerR));
                float dist = (float) Math.hypot(dx, dy) - outerR;

                float fillAlpha = MathHelper.clamp(0.5f - dist / 1.35f, 0.0f, 1.0f);
                int fillArgb = (Math.round(fillAlpha * 255.0f) << 24) | 0x00FFFFFF;
                image.setColorArgb(px, py, fillArgb);

                float ringDist = Math.abs(dist + 0.7f);
                float strokeAlpha = MathHelper.clamp(1.05f - ringDist / 1.15f, 0.0f, 1.0f) * fillAlpha;
                int strokeArgb = (Math.round(strokeAlpha * 255.0f) << 24) | 0x00FFFFFF;
                image.setColorArgb(PATCH_SIZE + px, py, strokeArgb);
            }
        }

        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> ATLAS_ID.toString(), image);
        texture.upload();
        client.getTextureManager().registerTexture(ATLAS_ID, texture);
    }

    private record NineSlice(float x, float y, float w, float h, float corner, int color, boolean outline) {}

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

            float baseU = s.outline ? 0.5f : 0.0f;
            float uEdge = 16.0f / ATLAS_W;
            float uHalf = 0.5f;
            float[] us = {baseU, baseU + uEdge, baseU + uHalf - uEdge, baseU + uHalf};
            float vEdge = 16.0f / ATLAS_H;
            float[] vs = {0.0f, vEdge, 1.0f - vEdge, 1.0f};

            for (int r = 0; r < 3; r++) {
                float y0 = ys[r];
                float y1 = ys[r + 1];
                if (y1 <= y0 + 0.001f) continue;
                float v0 = vs[r];
                float v1 = vs[r + 1];
                for (int col = 0; col < 3; col++) {
                    if (s.outline && r == 1 && col == 1) continue;
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

package egg.client.render.hud;

import egg.client.render.Render2D;
import egg.client.render.msdf.Font;
import egg.client.render.msdf.Fonts;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.LyricLine;
import egg.client.util.ProjectUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class LyricCaption {
    private static final Font FONT = Fonts.b;
    private static final Style FALLBACK_STYLE = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("egg", "lyrics")));
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final float FONT_SIZE = 10.2f;
    private static final float FALLBACK_SCALE = 0.94f;
    private static final float TEXT_PADDING = 8f;
    private static final int MAX_ROWS = 3;

    private final BreakIterator wordBreaks = BreakIterator.getLineInstance(Locale.ROOT);
    private final BreakIterator characterBreaks = BreakIterator.getCharacterInstance(Locale.ROOT);
    private final Vector2f projection = new Vector2f();
    private LyricLine displayedLine;
    private Layout layout;
    private ClientWorld anchorWorld;
    private Vec3d anchor;
    private float alpha;
    private float animatedWidth;
    private float progress;
    private long lastFrame;
    private long lineChanged;

    public void render(DrawContext context, RenderTickCounter tickCounter, LyricLine line, float lineProgress,
                       boolean inWorld, float distance, float scale, float opacity, int textColor, boolean showProgress) {
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 1f / 60f : Math.min(0.1f, (now - lastFrame) * 1e-9f);
        lastFrame = now;
        alpha = approach(alpha, line == null ? 0f : 1f, dt, line == null ? 19f : 22f);
        if (line != null && line != displayedLine) {
            if (displayedLine == null || !displayedLine.text().equals(line.text())) {
                layout = null;
                lineChanged = now;
            }
            displayedLine = line;
            anchor = null;
        }
        if (line != null) progress = lineProgress;
        if (displayedLine == null) return;
        if (alpha < 0.015f) {
            reset();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        scale = Math.min(scale, Math.min(screenWidth, screenHeight) / 106f);
        float maxTextWidth = Math.min(280f * scale, screenWidth * 0.68f);
        if (layout == null || layout.scale != scale || layout.maxTextWidth != maxTextWidth) {
            layout = layout(displayedLine.text(), scale, maxTextWidth, client.textRenderer);
        }

        if (layout.rows.isEmpty()) return;
        float width = layout.width + 2f * TEXT_PADDING * scale;
        animatedWidth = Math.max(width, approach(animatedWidth, width, dt, 18f));
        float height = layout.lineHeight * layout.rows.size() + (showProgress ? 6.5f : 5f) * scale;
        float centerX = screenWidth * 0.5f;
        float centerY = screenHeight - Math.min(68f, screenHeight * 0.38f) - height / 2;
        float perspective = 1f;
        if (inWorld) {
            Camera camera = client.gameRenderer.getCamera();
            if (anchor == null || anchorWorld != client.world) {
                anchorWorld = client.world;
                Vector3f offset = new Vector3f(0, -0.23f * distance, -distance).rotate(camera.getRotation());
                anchor = camera.getCameraPos().add(offset.x, offset.y, offset.z);
            }
            float depth = (float) camera.getCameraPos().distanceTo(anchor);
            if (depth < 0.45f || depth > distance * 2.7f
                    || !ProjectUtil.toScreen(anchor.x, anchor.y, anchor.z, tickCounter.getTickProgress(true), projection)) return;
            centerX = projection.x;
            centerY = projection.y;
            perspective = MathHelper.clamp(distance / depth, 0.68f, 1.13f);
            if (centerX - animatedWidth * perspective / 2 < 2 || centerX + animatedWidth * perspective / 2 > screenWidth - 2
                    || centerY - height * perspective / 2 < 2 || centerY + height * perspective / 2 > screenHeight - 2) return;
        } else {
            anchor = null;
        }

        float lineFade = 0.76f + 0.24f * MathHelper.clamp((now - lineChanged) / 145_000_000f, 0f, 1f);
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate(centerX, centerY);
            matrices.scale(perspective);
            draw(context, client.textRenderer, height, opacity, ColorUtil.withAlphaFloat(textColor, alpha * lineFade), showProgress);
        } finally {
            matrices.popMatrix();
        }
    }

    private void draw(DrawContext context, TextRenderer textRenderer, float height, float opacity, int textColor, boolean showProgress) {
        float scale = layout.scale;
        float left = -animatedWidth / 2f;
        float top = -height / 2f;
        Render2D.drawRoundedRect(context, left, top, animatedWidth, height, 5f * scale,
                ColorUtil.withAlphaFloat(0xFF151820, alpha * opacity));

        if (showProgress) {
            float inset = TEXT_PADDING * scale;
            float trackWidth = animatedWidth - 2 * inset;
            float trackY = top + height - 2.8f * scale;
            float thickness = 0.9f * scale;
            Render2D.drawCapsule(context, left + inset, trackY, trackWidth, thickness,
                    ColorUtil.withAlphaFloat(0x2CFFFFFF, alpha));
            Render2D.drawCapsule(context, left + inset, trackY, trackWidth * progress, thickness,
                    ColorUtil.withAlpha(ColorUtil.interpolate(0xFFE0E3EC, Theme.getAccent(), 0.5f), Math.round(172 * alpha)));
        }

        Matrix3x2f matrix = Render2D.getMatrix(context);
        float y = top + 2.5f * scale;
        for (Row row : layout.rows) {
            if (row.fallback == null) {
                FONT.a(matrix, row.text, -row.width / 2f, y, FONT_SIZE * scale, textColor);
            } else {
                var stack = context.getMatrices();
                stack.pushMatrix();
                stack.translate(-row.width / 2f, y + 1.65f * scale);
                stack.scale(scale * FALLBACK_SCALE);
                context.drawText(textRenderer, row.fallback, 0, 0, textColor, false);
                stack.popMatrix();
            }
            y += layout.lineHeight;
        }
    }

    private Layout layout(String rawText, float scale, float maxTextWidth, TextRenderer renderer) {
        String text = WHITESPACE.matcher(Normalizer.normalize(rawText, Normalizer.Form.NFC)).replaceAll(" ").strip();
        var atlas = Font.getRenderer().getFont(FONT.getName());
        atlas.ensureLoaded();
        boolean msdf = text.codePoints().allMatch(atlas::hasGlyph);
        float maxWidth = maxTextWidth / scale;
        List<Row> rows = new ArrayList<>(MAX_ROWS);
        float width = 0;
        while (!text.isEmpty() && rows.size() < MAX_ROWS) {
            String line = text;
            float remainingWidth = textWidth(line, msdf, renderer);
            if (remainingWidth > maxWidth) {
                int end = fittingEnd(text, maxWidth, msdf, renderer, wordBreaks);
                if (remainingWidth <= maxWidth * 2f && rows.size() < MAX_ROWS - 1) {
                    int balanced = fittingEnd(text, remainingWidth * 0.52f, msdf, renderer, wordBreaks);
                    if (balanced > 0 && textWidth(text.substring(balanced).stripLeading(), msdf, renderer) <= maxWidth) {
                        end = Math.min(balanced, end);
                    }
                }
                if (end == 0) {
                    end = fittingEnd(text, maxWidth, msdf, renderer, characterBreaks);
                }
                if (rows.size() == MAX_ROWS - 1) {
                    float ellipsisWidth = textWidth("…", msdf, renderer);
                    end = fittingEnd(text, maxWidth - ellipsisWidth, msdf, renderer, characterBreaks);
                    line = text.substring(0, end).stripTrailing() + "…";
                    text = "";
                } else {
                    if (end == 0) end = characterBreaks.following(0);
                    line = text.substring(0, end).stripTrailing();
                    text = text.substring(end).stripLeading();
                }
            } else {
                text = "";
            }
            Text fallback = msdf ? null : Text.literal(line).setStyle(FALLBACK_STYLE);
            float lineWidth = (msdf ? FONT.a(line, FONT_SIZE) : renderer.getWidth(fallback) * FALLBACK_SCALE) * scale;
            rows.add(new Row(line, lineWidth, fallback));
            width = Math.max(width, lineWidth);
        }
        return new Layout(scale, maxTextWidth, width, FONT.a(FONT_SIZE) * scale, rows);
    }

    private int fittingEnd(String text, float limit, boolean msdf, TextRenderer renderer, BreakIterator breaks) {
        breaks.setText(text);
        int fit = 0;
        for (int end = breaks.next(); end != BreakIterator.DONE; end = breaks.next()) {
            if (textWidth(text.substring(0, end).stripTrailing(), msdf, renderer) > limit) break;
            fit = end;
        }
        return fit;
    }

    private static float textWidth(String text, boolean msdf, TextRenderer renderer) {
        return msdf ? FONT.a(text, FONT_SIZE) : renderer.getWidth(Text.literal(text).setStyle(FALLBACK_STYLE)) * FALLBACK_SCALE;
    }

    private static float approach(float current, float target, float dt, float speed) {
        return MathHelper.lerp(1f - (float) Math.exp(-dt * speed), current, target);
    }

    public void reset() {
        displayedLine = null;
        layout = null;
        anchor = null;
        anchorWorld = null;
        alpha = 0f;
        animatedWidth = 0f;
        lastFrame = 0L;
    }

    private record Row(String text, float width, Text fallback) {}

    private record Layout(float scale, float maxTextWidth, float width, float lineHeight, List<Row> rows) {}
}

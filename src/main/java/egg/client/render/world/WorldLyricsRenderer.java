package egg.client.render.world;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import egg.client.util.ColorUtil;
import egg.client.util.LyricLine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class WorldLyricsRenderer {
    private static final int MAX_VISIBLE = 3;
    private static final int MAX_PHRASE_LENGTH = 22;
    private static final int FADE_OUT_MS = 420;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private final ArrayDeque<Phrase> visible = new ArrayDeque<>();
    private final Vector3f right = new Vector3f();
    private final Vector3f up = new Vector3f();
    private ClientWorld world;
    private LyricLine activeLine;
    private List<String> phrases = List.of();
    private int wordsPerPhrase;
    private int phraseIndex = -1;
    private int spawnIndex;
    private BufferAllocator allocator;
    private DynamicUniformStorage<Paint> uniforms;

    public void render(Camera camera, LyricLine line, long elapsed, long duration, Style style) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (world != client.world) {
            reset();
            world = client.world;
        }
        if (!camera.isReady()) return;
        long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        if (line != activeLine || wordsPerPhrase != style.words) {
            if (line == null) {
                for (Phrase phrase : visible) phrase.end = Math.min(phrase.end, now + FADE_OUT_MS);
            }
            activeLine = line;
            wordsPerPhrase = style.words;
            phrases = line == null ? List.of() : split(line.text(), wordsPerPhrase);
            phraseIndex = -1;
        }
        if (!phrases.isEmpty()) {
            long cadence = Math.max(1, Math.min(700, duration * 4 / (5 * phrases.size())));
            int index = (int) Math.min(phrases.size() - 1, elapsed / cadence);
            if (index != phraseIndex) {
                phraseIndex = index;
                spawn(client, camera, phrases.get(index), now, cadence, style);
            }
        }

        var iterator = visible.iterator();
        while (iterator.hasNext()) {
            Phrase phrase = iterator.next();
            if (now >= phrase.end) {
                phrase.sprite.close();
                iterator.remove();
            }
        }
        if (visible.isEmpty()) return;

        if (allocator == null) allocator = new BufferAllocator(512);
        if (uniforms == null) uniforms = new DynamicUniformStorage<>("Egg lyric paint", 16, 8);
        uniforms.clear();
        right.set(1, 0, 0).rotate(camera.getRotation());
        up.set(0, 1, 0).rotate(camera.getRotation());
        Vec3d eye = camera.getCameraPos();
        for (Phrase phrase : visible) draw(phrase, eye, now, style);
    }

    private void spawn(MinecraftClient client, Camera camera, String text, long now, long cadence, Style style) {
        int lane = spawnIndex++ & 3;
        float side = (lane & 1) == 0 ? -1f : 1f;
        float elevation = switch (lane) {
            case 0 -> 0.24f;
            case 1 -> 0.13f;
            case 2 -> 0.32f;
            default -> 0.268f;
        };
        float distance = style.distance * (lane < 2 ? 1f : 1.12f);
        Vec3d eye = camera.getCameraPos();
        var offset = new Vector3f(side * distance * (lane < 2 ? 0.36f : 0.29f), distance * elevation, -distance);
        offset.rotate(camera.getRotation());
        Vec3d position = eye.add(offset.x, offset.y, offset.z);
        var hit = client.world.raycast(new RaycastContext(eye, position, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, client.getCameraEntity()));
        if (hit.getType() != HitResult.Type.MISS) {
            double available = eye.distanceTo(hit.getPos()) - 0.32;
            if (available < 1.25) return;
            position = eye.lerp(position, available / eye.distanceTo(position));
            distance = (float) available;
        }

        LyricPaintTexture sprite = LyricPaintTexture.create(text);
        float worldPerPixel = Math.min(distance * 0.00165f * style.scale, distance * 0.60f / sprite.width());
        float drift = distance * 0.068f;
        var entry = new Vector3f(side * drift, -drift * 0.53f, 0.10f).rotate(camera.getRotation());
        long lifetime = Math.clamp(cadence * 2 + 500, 1050, 1920);
        if (visible.size() == MAX_VISIBLE) visible.removeFirst().sprite.close();
        visible.addLast(new Phrase(sprite, position, new Vec3d(entry.x, entry.y, entry.z), now, now + lifetime,
                cadence < 380 ? 320f : 420f, worldPerPixel, side, lane, spawnIndex * 9.71f));
    }

    private void draw(Phrase phrase, Vec3d eye, long now, Style style) {
        float age = now - phrase.start;
        float progress = MathHelper.clamp(age / phrase.revealDuration, 0f, 1f);
        float entry = 1f - smooth(progress);
        float fade = smooth((now - phrase.start) / 130f) * smooth((phrase.end - now) / (float) FADE_OUT_MS);
        Vec3d center = phrase.position.subtract(eye).add(phrase.entry.multiply(entry));
        fade *= smooth(((float) center.length() - 0.42f) / 0.38f);
        if (fade < 0.005f) return;

        float degrees = phrase.side * (2.5f + 0.75f * phrase.lane) + phrase.side * 4.5f * entry;
        float tilt = degrees * MathHelper.RADIANS_PER_DEGREE;
        float sin = MathHelper.sin(tilt);
        float cos = MathHelper.cos(tilt);
        float zoom = 1f - entry * 0.10f;
        float halfWidth = phrase.sprite.width() * phrase.worldPerPixel * zoom * 0.5f;
        float halfHeight = phrase.sprite.height() * phrase.worldPerPixel * zoom * 0.5f;
        Vector3f axisX = new Vector3f(right).mul(cos).fma(sin, up).mul(halfWidth);
        Vector3f axisY = new Vector3f(up).mul(cos).fma(-sin, right).mul(halfHeight);
        var paint = uniforms.write(new Paint(progress, -phrase.side, phrase.seed, style.glow));
        int color = ColorUtil.withAlphaFloat(style.color, fade);
        try {
            var mesh = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            vertex(mesh, center, axisX, axisY, -1, 1, 0, 0, color);
            vertex(mesh, center, axisX, axisY, -1, -1, 0, 1, color);
            vertex(mesh, center, axisX, axisY, 1, -1, 1, 1, color);
            vertex(mesh, center, axisX, axisY, 1, 1, 1, 0, color);
            MeshDrawUtil.drawWithTextureAndUniform(Pipelines.WORLD_LYRICS, mesh.end(), "Egg world lyric",
                    phrase.sprite.texture().getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR), "LyricPaint", paint);
        } finally {
            allocator.reset();
        }
    }

    private static void vertex(BufferBuilder mesh, Vec3d center, Vector3f right, Vector3f up, int h, int v, int u, int t, int color) {
        mesh.vertex((float) center.x + h * right.x + v * up.x, (float) center.y + h * right.y + v * up.y,
                (float) center.z + h * right.z + v * up.z).texture(u, t).color(color);
    }

    private static List<String> split(String lyrics, int wordsPerPhrase) {
        String text = WHITESPACE.matcher(Normalizer.normalize(lyrics, Normalizer.Form.NFC)).replaceAll(" ").strip();
        var lines = BreakIterator.getLineInstance(Locale.ROOT);
        var characters = BreakIterator.getCharacterInstance(Locale.ROOT);
        lines.setText(text);
        characters.setText(text);
        List<String> result = new ArrayList<>();
        int start = lines.first();
        int previous = start;
        int words = 0;
        for (int end = lines.next(); end != BreakIterator.DONE; end = lines.next()) {
            if (previous > start && text.codePointCount(start, end) > MAX_PHRASE_LENGTH) {
                result.add(text.substring(start, previous).strip());
                start = previous;
                words = 0;
            }
            while (text.codePointCount(start, end) > MAX_PHRASE_LENGTH) {
                int limit = text.offsetByCodePoints(start, MAX_PHRASE_LENGTH);
                int cut = characters.isBoundary(limit) ? limit : characters.preceding(limit);
                if (cut <= start) cut = characters.following(start);
                result.add(text.substring(start, cut).strip());
                start = cut;
            }
            if (++words >= wordsPerPhrase) {
                result.add(text.substring(start, end).strip());
                start = end;
                words = 0;
            }
            previous = end;
        }
        if (start < text.length()) result.add(text.substring(start).strip());
        result.removeIf(String::isBlank);
        return result;
    }

    private static float smooth(float value) {
        float t = MathHelper.clamp(value, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public void reset() {
        for (Phrase phrase : visible) phrase.sprite.close();
        visible.clear();
        activeLine = null;
        phrases = List.of();
        phraseIndex = -1;
        spawnIndex = 0;
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
    }

    public record Style(int words, float distance, float scale, int color, float glow) {}

    private record Paint(float progress, float direction, float seed, float glow) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            buffer.putFloat(progress).putFloat(direction).putFloat(seed).putFloat(glow);
        }
    }

    private static final class Phrase {
        final LyricPaintTexture sprite;
        final Vec3d position;
        final Vec3d entry;
        final long start;
        final float revealDuration;
        final float worldPerPixel;
        final float side;
        final int lane;
        final float seed;
        long end;

        Phrase(LyricPaintTexture sprite, Vec3d position, Vec3d entry, long start, long end, float revealDuration,
               float worldPerPixel, float side, int lane, float seed) {
            this.sprite = sprite;
            this.position = position;
            this.entry = entry;
            this.start = start;
            this.end = end;
            this.revealDuration = revealDuration;
            this.worldPerPixel = worldPerPixel;
            this.side = side;
            this.lane = lane;
            this.seed = seed;
        }
    }
}

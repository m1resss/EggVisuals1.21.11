package egg.client.render.world;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class GroundFormRenderer {
    private static final long DURATION = 1_550_000_000L;
    private static final int MAX_EFFECTS = 6;
    private static final int MAX_TILES = 576;
    private static final Vector3f UP = new Vector3f(0, 1, 0);
    private final List<Formation> formations = new ArrayList<>();

    public Shape capture(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        return capture(client.getEntityRenderDispatcher().getRenderer(living), living);
    }

    private static <T extends Entity, S extends EntityRenderState> Shape capture(EntityRenderer<? super T, S> renderer, T entity) {
        if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer)) return null;
        S snapshot = renderer.createRenderState();
        renderer.updateRenderState(entity, snapshot, MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false));
        if (!(snapshot instanceof LivingEntityRenderState state)) return null;
        state.deathTime = 0;
        state.flipUpsideDown = false;
        if (state instanceof PlayerEntityRenderState player) {
            player.hatVisible = false;
            player.jacketVisible = false;
            player.leftPantsLegVisible = false;
            player.rightPantsLegVisible = false;
            player.leftSleeveVisible = false;
            player.rightSleeveVisible = false;
        }

        MatrixStack matrices = new MatrixStack();
        matrices.scale(state.baseScale, state.baseScale, state.baseScale);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - state.bodyYaw));
        matrices.scale(-1, -1, 1);
        if (state instanceof PlayerEntityRenderState) matrices.scale(0.9375f, 0.9375f, 0.9375f);
        matrices.translate(0, -1.501f, 0);

        @SuppressWarnings("unchecked")
        Model<S> model = (Model<S>) livingRenderer.getModel();
        MeshCapture capture = new MeshCapture();
        model.setAngles(snapshot);
        model.render(matrices, capture, snapshot.light, OverlayTexture.DEFAULT_UV);
        if (capture.faces.isEmpty()) return null;
        float bottom = Float.POSITIVE_INFINITY;
        float top = Float.NEGATIVE_INFINITY;
        for (Face face : capture.faces) {
            for (Vector3f vertex : face.vertices) {
                bottom = Math.min(bottom, vertex.y);
                top = Math.max(top, vertex.y);
            }
        }
        return new Shape(List.copyOf(capture.faces), bottom, Math.max(0.2f, top - bottom));
    }

    public boolean spawn(MinecraftClient client, Vec3d position, Shape shape, boolean sound) {
        if (client.world == null || client.player == null || shape == null) return false;
        ClientWorld world = client.world;
        Vec3d start = position.add(0, 0.24, 0);
        var floor = world.raycast(new RaycastContext(start, position.add(0, -6, 0),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player));
        if (floor.getType() != HitResult.Type.BLOCK) return false;

        BlockPos blockPos = floor.getBlockPos();
        BlockState block = world.getBlockState(blockPos);
        var model = client.getBlockRenderManager().getModel(block);
        List<BlockModelPart> parts = model.getParts(Random.create(block.getRenderingSeed(blockPos)));
        Surface top = resolveSurface(client, world, block, blockPos, parts, Direction.UP, model.particleSprite());
        Surface sides = resolveSurface(client, world, block, blockPos, parts, Direction.NORTH, top.sprite);
        Vec3d origin = new Vec3d(position.x, floor.getPos().y + 0.015, position.z);
        int light = WorldRenderer.getLightmapCoordinates(world, blockPos.up());
        List<Tile> tiles = makeTiles(shape, top, sides, new java.util.Random(blockPos.asLong() ^ System.nanoTime()));
        if (tiles.isEmpty()) return false;
        if (formations.size() >= MAX_EFFECTS) formations.removeFirst();
        formations.add(new Formation(world, origin, shape.height, light, tiles, System.nanoTime()));
        if (sound) {
            world.playSoundClient(origin.x, origin.y, origin.z, block.getSoundGroup().getPlaceSound(),
                    SoundCategory.PLAYERS, 0.35f, 0.8f, false);
        }
        return true;
    }

    private static Surface resolveSurface(MinecraftClient client, ClientWorld world, BlockState block, BlockPos pos,
                                          List<BlockModelPart> parts, Direction direction, Sprite fallback) {
        for (BlockModelPart part : parts) {
            List<BakedQuad> quads = part.getQuads(direction);
            if (quads.isEmpty()) quads = part.getQuads(null);
            for (BakedQuad quad : quads) {
                if (quad.face() != direction) continue;
                int tint = quad.hasTint() ? client.getBlockColors().getColor(block, world, pos, quad.tintIndex()) : -1;
                return new Surface(quad.sprite(), tint | 0xFF000000);
            }
        }
        return new Surface(fallback, 0xFFFFFFFF);
    }

    private static List<Tile> makeTiles(Shape shape, Surface top, Surface sides, java.util.Random random) {
        List<Tile> tiles = new ArrayList<>();
        for (Face face : shape.faces) {
            Vector3f a = face.vertices[0];
            Vector3f u = face.vertices[1].sub(a, new Vector3f());
            Vector3f v = face.vertices[3].sub(a, new Vector3f());
            Vector3f normal = u.cross(v, new Vector3f());
            if (normal.lengthSquared() < 0.000001f) continue;
            normal.normalize();
            Quaternionf flat = new Quaternionf().rotationTo(normal, UP);
            Surface surface = Math.abs(normal.y) > 0.65f ? top : sides;
            int cols = Math.clamp((int) Math.ceil(u.length() / 0.20f), 1, 8);
            int rows = Math.clamp((int) Math.ceil(v.length() / 0.20f), 1, 8);
            Vector3f halfU = new Vector3f(u).mul(0.5f / cols);
            Vector3f halfV = new Vector3f(v).mul(0.5f / rows);
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    if (tiles.size() == MAX_TILES) return tiles;
                    Vector3f center = new Vector3f(a).fma((col + 0.5f) / cols, u).fma((row + 0.5f) / rows, v);
                    center.y -= shape.bottom;
                    float heightRatio = Math.clamp(center.y / shape.height, 0, 1);
                    float angle = random.nextFloat() * (float) (Math.PI * 2);
                    float radius = 0.22f + 0.53f * random.nextFloat();
                    Vector3f source = new Vector3f((float) Math.cos(angle) * radius, 0.014f + random.nextFloat() * 0.026f,
                            (float) Math.sin(angle) * radius);
                    float delay = heightRatio * 0.34f + random.nextFloat() * 0.16f;
                    float u0 = (float) col / cols;
                    float v0 = (float) row / rows;
                    float u1 = (col + 1.0f) / cols;
                    float v1 = (row + 1.0f) / rows;
                    boolean swapUv = Math.abs(u.y) > Math.abs(v.y);
                    boolean flipV = (swapUv ? u.y : v.y) > 0;
                    tiles.add(new Tile(center, source, halfU, halfV, normal, flat, surface, delay, u0, v0, u1, v1, swapUv, flipV));
                }
            }
        }
        return tiles;
    }

    public void queue(MinecraftClient client, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        long now = System.nanoTime();
        formations.removeIf(formation -> formation.world != client.world || now - formation.started >= DURATION);
        for (Formation formation : formations) {
            float progress = (now - formation.started) / (float) DURATION;
            Vec3d offset = formation.position.subtract(camera.pos);
            matrices.push();
            matrices.translate(offset.x, offset.y, offset.z);
            queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(formation.tiles.getFirst().surface.sprite.getAtlasId()),
                    (entry, consumer) -> draw(formation, progress, entry, consumer));
            matrices.pop();
        }
    }

    private static void draw(Formation formation, float progress, MatrixStack.Entry entry, VertexConsumer consumer) {
        float gather = progress / 0.54f;
        float sink = smooth((progress - 0.71f) / 0.29f) * (formation.height + 0.06f);
        Quaternionf rotation = new Quaternionf();
        Quaternionf upright = new Quaternionf();
        Vector3f center = new Vector3f();
        Vector3f normal = new Vector3f();
        Vector3f vertex = new Vector3f();
        for (Tile tile : formation.tiles) {
            float time = Math.clamp((gather - tile.delay) / (1 - tile.delay), 0, 1);
            if (time <= 0) continue;
            float ease = smooth(time);
            float scale = smooth(time / 0.32f);
            rotation.set(tile.flat).slerp(upright, ease);
            center.set(tile.source).lerp(tile.center, ease);
            center.y += (float) Math.sin(time * Math.PI) * 0.11f - sink;
            normal.set(tile.normal).rotate(rotation);
            emit(consumer, entry, tile, formation.light, rotation, center, normal, vertex, scale, -1, -1, tile.u0, tile.v0);
            emit(consumer, entry, tile, formation.light, rotation, center, normal, vertex, scale, 1, -1, tile.u1, tile.v0);
            emit(consumer, entry, tile, formation.light, rotation, center, normal, vertex, scale, 1, 1, tile.u1, tile.v1);
            emit(consumer, entry, tile, formation.light, rotation, center, normal, vertex, scale, -1, 1, tile.u0, tile.v1);
        }
    }

    private static void emit(VertexConsumer consumer, MatrixStack.Entry entry, Tile tile, int light,
                             Quaternionf rotation, Vector3f center, Vector3f normal, Vector3f vertex,
                             float scale, int x, int y, float u, float v) {
        vertex.set(tile.halfU).mul(x).fma(y, tile.halfV).mul(scale).rotate(rotation).add(center);
        float textureU = tile.swapUv ? v : u;
        float textureV = tile.swapUv ? u : v;
        if (tile.flipV) textureV = 1 - textureV;
        Sprite sprite = tile.surface.sprite;
        consumer.vertex(entry, vertex).color(tile.surface.tint).texture(sprite.getFrameU(textureU), sprite.getFrameV(textureV))
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, normal);
    }

    public void clear() {
        formations.clear();
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static final class Shape {
        private final List<Face> faces;
        private final float bottom;
        private final float height;

        private Shape(List<Face> faces, float bottom, float height) {
            this.faces = faces;
            this.bottom = bottom;
            this.height = height;
        }
    }
    private record Face(Vector3f[] vertices) {}
    private record Surface(Sprite sprite, int tint) {}
    private record Tile(Vector3f center, Vector3f source, Vector3f halfU, Vector3f halfV, Vector3f normal,
                        Quaternionf flat, Surface surface, float delay, float u0, float v0, float u1, float v1,
                        boolean swapUv, boolean flipV) {}
    private record Formation(ClientWorld world, Vec3d position, float height, int light, List<Tile> tiles, long started) {}

    private static final class MeshCapture implements VertexConsumer {
        private final List<Face> faces = new ArrayList<>();
        private Vector3f[] quad = new Vector3f[4];
        private int count;

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            quad[count++] = new Vector3f(x, y, z);
            if (count == quad.length) {
                faces.add(new Face(quad));
                quad = new Vector3f[4];
                count = 0;
            }
            return this;
        }

        @Override public VertexConsumer color(int red, int green, int blue, int alpha) { return this; }
        @Override public VertexConsumer color(int argb) { return this; }
        @Override public VertexConsumer texture(float u, float v) { return this; }
        @Override public VertexConsumer overlay(int u, int v) { return this; }
        @Override public VertexConsumer light(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public VertexConsumer lineWidth(float width) { return this; }
    }
}

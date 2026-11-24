package egg.client.render.world;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.render.shader.FrameBufferPool;
import egg.client.render.shader.FullscreenPass;
import egg.client.render.shader.GlStateSnapshot;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import egg.client.render.shader.ProjectionUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Random;

public final class IceFreezeKillRenderer {
    private static final float FREEZE_RISE_SEC = 0.44f;
    private static final float SHATTER_AT_SEC = 0.72f;
    private static final int MAX_EFFECTS = 6;
    private static final int MAX_SHARDS = 260;
    private static final int MAX_SPARKLES = 160;
    private static final Identifier BLOOM_TEXTURE = Identifier.of("egg", "textures/target/bloom.png");
    private static final Identifier[] SNOWFLAKES = {
            Identifier.of("egg", "textures/world/snowflake_1.png"),
            Identifier.of("egg", "textures/world/snowflake_3.png"),
            Identifier.of("egg", "textures/world/snowflake_5.png"),
            Identifier.of("egg", "textures/world/snowflake_8.png")
    };

    private final List<FreezeEffect> effects = new ArrayList<>();
    private final List<IceShard> shards = new ArrayList<>();
    private final List<IceSparkle> sparkles = new ArrayList<>();
    private final Random random = new Random();
    private final Matrix4f invViewProj = new Matrix4f();
    private final Vector3f camRight = new Vector3f();
    private final Vector3f camUp = new Vector3f();
    private final Vector3f lightDir = new Vector3f(0.35f, 0.90f, 0.25f).normalize();

    private SimpleFramebuffer sceneCopy;
    private SimpleFramebuffer targetMask;
    private DynamicUniformStorage<FreezeUniform> uniforms;
    private BufferAllocator meshAllocator;
    private BufferAllocator quadAllocator;
    private EntityRenderState renderingModel;
    private ClientWorld lastWorld;
    private long lastFrameNanos;

    public boolean isRendering(EntityRenderState state) {
        return state != null && state == renderingModel;
    }

    public boolean spawn(MinecraftClient client, Vec3d position, double height, double width,
                         EntityRenderState model, boolean playSound) {
        if (client == null || client.world == null || client.player == null) return false;
        ClientWorld world = client.world;
        Vec3d origin = snapToCloseFloor(client, world, position);
        double h = MathHelper.clamp(height, 0.8, 2.6);
        double w = MathHelper.clamp(width, 0.45, 1.4);

        List<CrystalSpike> spikes = createCluster(w, h);
        if (effects.size() >= MAX_EFFECTS) {
            effects.removeFirst();
        }
        FreezeEffect effect = new FreezeEffect(world, origin, (float) h, (float) w, model, spikes, System.nanoTime());
        effects.add(effect);

        for (int i = 0; i < 12; i++) {
            spawnAmbientSnowflake(origin, w, h, true);
        }

        if (playSound) {
            world.playSoundClient(origin.x, origin.y + h * 0.5, origin.z, SoundEvents.BLOCK_GLASS_PLACE,
                    SoundCategory.PLAYERS, 0.65f, 1.35f, false);
            world.playSoundClient(origin.x, origin.y + h * 0.5, origin.z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.PLAYERS, 0.45f, 1.55f, false);
        }
        return true;
    }

    private Vec3d snapToCloseFloor(MinecraftClient client, ClientWorld world, Vec3d position) {
        Vec3d start = position.add(0.0, 0.30, 0.0);
        var hit = world.raycast(new RaycastContext(start, position.add(0.0, -0.55, 0.0),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return new Vec3d(position.x, hit.getPos().y, position.z);
        }
        return position;
    }

    private List<CrystalSpike> createCluster(double entityWidth, double entityHeight) {
        List<CrystalSpike> list = new ArrayList<>(9);
        float baseR = (float) (entityWidth * 0.46 + 0.14);
        float yawOffset = random.nextFloat() * MathHelper.TAU;
        float[] angles = {0.35f, 1.25f, 2.20f, 3.10f, 4.05f, 5.00f, 5.75f, 0.80f, 3.55f};
        float[] heights = {0.96f, 0.78f, 0.88f, 0.58f, 0.84f, 0.42f, 0.66f, 0.36f, 0.70f};
        float[] radii = {0.155f, 0.125f, 0.145f, 0.115f, 0.140f, 0.110f, 0.125f, 0.115f, 0.130f};
        float[] tilts = {0.42f, 0.54f, 0.48f, 0.64f, 0.45f, 0.82f, 0.58f, 0.36f, 0.52f};

        for (int i = 0; i < angles.length; i++) {
            float angle = yawOffset + angles[i] + (random.nextFloat() - 0.5f) * 0.28f;
            float dist = baseR * (i >= 7 ? 0.82f : 0.94f + random.nextFloat() * 0.24f);
            float ox = MathHelper.cos(angle) * dist;
            float oz = MathHelper.sin(angle) * dist;
            float h = heights[i] * (float) (entityHeight / 1.8) * (0.90f + random.nextFloat() * 0.20f);
            float rad = radii[i] * (0.90f + random.nextFloat() * 0.22f);
            float tilt = tilts[i] + (random.nextFloat() - 0.5f) * 0.12f;
            float delay = (i % 4) * 0.028f + random.nextFloat() * 0.02f;
            float spin = random.nextFloat() * MathHelper.TAU;
            float[] shapeJitter = new float[6];
            for (int v = 0; v < 6; v++) {
                shapeJitter[v] = 0.84f + random.nextFloat() * 0.30f;
            }
            list.add(new CrystalSpike(ox, oz, angle, tilt, h, rad, delay, spin, shapeJitter));
        }
        return list;
    }

    public void render(MatrixStack matrices, Camera camera, float tickDelta, VertexConsumerProvider immediate,
                       Vec3d camPos, boolean playSound) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            clear();
            return;
        }
        if (lastWorld != client.world) {
            clear();
            lastWorld = client.world;
        }

        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.016f : Math.min((now - lastFrameNanos) / 1_000_000_000.0f, 0.05f);
        lastFrameNanos = now;

        Iterator<FreezeEffect> effectIt = effects.iterator();
        while (effectIt.hasNext()) {
            FreezeEffect effect = effectIt.next();
            if (effect.world != client.world) {
                effectIt.remove();
                continue;
            }
            float age = (now - effect.startedNanos) / 1_000_000_000.0f;
            if (!effect.shattered && age >= SHATTER_AT_SEC) {
                effect.shattered = true;
                shatter(client, effect, playSound);
                effectIt.remove();
            } else if (!effect.shattered && random.nextFloat() < dt * 16.0f) {
                spawnAmbientSnowflake(effect.position, effect.width, effect.height, false);
            }
        }

        if (effects.isEmpty() && shards.isEmpty() && sparkles.isEmpty()) {
            return;
        }

        for (FreezeEffect effect : effects) {
            float age = (now - effect.startedNanos) / 1_000_000_000.0f;
            renderFrozenEntityPass(client, effect, age, matrices, camera, camPos);
        }

        updateShards(client, dt);
        updateSparkles(dt);

        if (!effects.isEmpty() || !shards.isEmpty()) {
            renderCrystalAndShardGeometry(now, camPos);
        }

        if (!sparkles.isEmpty()) {
            renderSparkles(camera, immediate, camPos);
        }
    }

    private void renderFrozenEntityPass(MinecraftClient client, FreezeEffect effect, float age,
                                        MatrixStack matrices, Camera camera, Vec3d camPos) {
        if (effect.model == null || !GlStateSnapshot.isOpenGl()) return;
        Framebuffer frame = client.getFramebuffer();
        if (frame == null || frame.getColorAttachmentView() == null || frame.getDepthAttachmentView() == null
                || (RenderSystem.outputColorTextureOverride != null && RenderSystem.outputColorTextureOverride != frame.getColorAttachmentView())
                || (RenderSystem.outputDepthTextureOverride != null && RenderSystem.outputDepthTextureOverride != frame.getDepthAttachmentView())) {
            return;
        }

        sceneCopy = resize(sceneCopy, "Egg ice scene", frame, false);
        targetMask = resize(targetMask, "Egg ice mask", frame, true);

        float freezeLinear = MathHelper.clamp(age / FREEZE_RISE_SEC, 0.0f, 1.0f);
        float freezeFront = MathHelper.lerp(smooth(freezeLinear), -0.08f, 1.24f);
        float shatterPulse = MathHelper.clamp((age - FREEZE_RISE_SEC) / (SHATTER_AT_SEC - FREEZE_RISE_SEC), 0.0f, 1.0f);
        float pulseGlow = shatterPulse * shatterPulse;
        float opacity = MathHelper.clamp(age / 0.05f, 0.0f, 1.0f);

        try (var ignored = FullscreenPass.guardState()) {
            var sampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
            FullscreenPass.blitUnflipped(Pipelines.COLOR_COPY, sceneCopy, frame.getColorAttachmentView(), sampler, 0);
            renderCapturedModel(client, effect, matrices, camera, camPos);
            composeFrozenEntity(frame, effect, camPos, freezeFront, pulseGlow, age, opacity);
        }
    }

    private void renderCapturedModel(MinecraftClient client, FreezeEffect effect, MatrixStack matrices,
                                     Camera camera, Vec3d camPos) {
        EntityRenderManager manager = client.getEntityRenderDispatcher();
        RenderDispatcher dispatcher = client.gameRenderer.getEntityRenderDispatcher();
        CameraRenderState camState = new CameraRenderState();
        camState.orientation = camera.getRotation();

        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                targetMask.getColorAttachment(), 0, targetMask.getDepthAttachment(), 1.0);
        GpuTextureView prevColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView prevDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = targetMask.getColorAttachmentView();
        RenderSystem.outputDepthTextureOverride = targetMask.getDepthAttachmentView();
        renderingModel = effect.model;
        try {
            Vec3d offset = effect.position.subtract(camPos);
            manager.render(effect.model, camState, offset.x, offset.y, offset.z, matrices, dispatcher.getQueue());
            dispatcher.render();
            client.getBufferBuilders().getEntityVertexConsumers().draw();
        } finally {
            renderingModel = null;
            RenderSystem.outputColorTextureOverride = prevColor;
            RenderSystem.outputDepthTextureOverride = prevDepth;
            snapshot.restore();
        }
    }

    private void composeFrozenEntity(Framebuffer frame, FreezeEffect effect, Vec3d camPos,
                                     float freezeFront, float pulseGlow, float age, float opacity) {
        if (uniforms == null) uniforms = new DynamicUniformStorage<>("EggIceFreeze", 112, 16);
        uniforms.clear();
        ProjectionUtil.getInverseViewProjection(invViewProj);
        Vec3d rel = effect.position.subtract(camPos);
        GpuBufferSlice uniformSlice = uniforms.write(new FreezeUniform(
                invViewProj,
                (float) rel.x, (float) rel.y, (float) rel.z, effect.height,
                freezeFront, pulseGlow, age, opacity,
                ProjectionUtil.isZeroToOneDepth() ? 1.0f : 0.0f
        ));

        if (quadAllocator == null) quadAllocator = new BufferAllocator(256);
        try {
            BufferBuilder mesh = new BufferBuilder(quadAllocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            mesh.vertex(-1, 1, 0).texture(0, 1).color(255, 255, 255, 255);
            mesh.vertex(-1, -1, 0).texture(0, 0).color(255, 255, 255, 255);
            mesh.vertex(1, -1, 0).texture(1, 0).color(255, 255, 255, 255);
            mesh.vertex(1, 1, 0).texture(1, 1).color(255, 255, 255, 255);
            try (var built = mesh.end()) {
                var vertices = FrameBufferPool.createBuffer(() -> "Egg ice freeze quad", built.getBuffer());
                var indices = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
                var indexBuffer = indices.getIndexBuffer(built.getDrawParameters().indexCount());
                var sampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
                try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "Egg ice freeze composite",
                        frame.getColorAttachmentView(),
                        OptionalInt.empty(),
                        frame.getDepthAttachmentView(),
                        OptionalDouble.empty())) {
                    pass.setPipeline(Pipelines.ICE_FREEZE);
                    pass.bindTexture("Sampler0", sceneCopy.getColorAttachmentView(), sampler);
                    pass.bindTexture("Sampler1", targetMask.getColorAttachmentView(), sampler);
                    pass.bindTexture("Sampler2", targetMask.getDepthAttachmentView(), sampler);
                    pass.setUniform("IceFreezeUniforms", uniformSlice);
                    pass.setVertexBuffer(0, vertices);
                    pass.setIndexBuffer(indexBuffer, indices.getIndexType());
                    pass.drawIndexed(0, 0, built.getDrawParameters().indexCount(), 1);
                }
            }
        } finally {
            quadAllocator.reset();
        }
    }

    private void renderCrystalAndShardGeometry(long now, Vec3d camPos) {
        if (meshAllocator == null) meshAllocator = new BufferAllocator(65536);
        try {
            BufferBuilder mesh = new BufferBuilder(meshAllocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (FreezeEffect effect : effects) {
                float age = (now - effect.startedNanos) / 1_000_000_000.0f;
                for (CrystalSpike spike : effect.spikes) {
                    float localT = MathHelper.clamp((age - spike.delay) / 0.28f, 0.0f, 1.0f);
                    if (localT <= 0.005f) continue;
                    float grow = easeOutBack(localT);
                    emitCrystalSpike(mesh, effect.position, spike, grow, camPos);
                }
            }

            for (IceShard shard : shards) {
                float lifeLeft = 1.0f - (shard.age / shard.life);
                float alpha = MathHelper.clamp(lifeLeft * 1.65f, 0.0f, 1.0f);
                float scale = MathHelper.clamp(lifeLeft * 2.2f, 0.0f, 1.0f);
                if (alpha <= 0.01f || scale <= 0.01f) continue;
                emitShard(mesh, shard, scale, alpha, camPos);
            }

            var built = mesh.endNullable();
            if (built != null) {
                MeshDrawUtil.drawLabeled(Pipelines.ICE_CRYSTAL, built, "egg kill ice crystals");
            }
        } finally {
            meshAllocator.reset();
        }
    }

    private void emitCrystalSpike(BufferBuilder mesh, Vec3d origin, CrystalSpike spike, float grow, Vec3d camPos) {
        float cosA = MathHelper.cos(spike.angle);
        float sinA = MathHelper.sin(spike.angle);
        Vector3f axisDir = new Vector3f(cosA * MathHelper.sin(spike.tilt), MathHelper.cos(spike.tilt), sinA * MathHelper.sin(spike.tilt)).normalize();
        Vector3f tangent = new Vector3f(-sinA, 0.0f, cosA);
        Vector3f bitangent = new Vector3f().set(axisDir).cross(tangent).normalize();
        tangent.set(bitangent).cross(axisDir).normalize();

        Quaternionf spinRot = new Quaternionf().fromAxisAngleRad(axisDir, spike.spin);
        tangent.rotate(spinRot);
        bitangent.rotate(spinRot);

        float totalH = spike.height * grow;
        float shaftH = totalH * 0.72f;
        float rBot = spike.radius;
        float rTop = spike.radius * 0.82f;

        Vector3f baseCenter = new Vector3f(
                (float) (origin.x + spike.offsetX - camPos.x),
                (float) (origin.y - 0.06 - camPos.y),
                (float) (origin.z + spike.offsetZ - camPos.z)
        );
        Vector3f shaftCenter = new Vector3f(baseCenter).fma(shaftH, axisDir);
        Vector3f tip = new Vector3f(baseCenter).fma(totalH, axisDir);

        Vector3f[] botRing = new Vector3f[6];
        Vector3f[] topRing = new Vector3f[6];
        for (int i = 0; i < 6; i++) {
            float a = (float) (i * Math.PI / 3.0);
            float cx = MathHelper.cos(a) * spike.jitter[i];
            float cz = MathHelper.sin(a) * spike.jitter[i];
            botRing[i] = new Vector3f(baseCenter).fma(cx * rBot, tangent).fma(cz * rBot, bitangent);
            topRing[i] = new Vector3f(shaftCenter).fma(cx * rTop, tangent).fma(cz * rTop, bitangent);
        }

        for (int i = 0; i < 6; i++) {
            int next = (i + 1) % 6;
            Vector3f b0 = botRing[i];
            Vector3f b1 = botRing[next];
            Vector3f t0 = topRing[i];
            Vector3f t1 = topRing[next];

            Vector3f e1 = new Vector3f(b1).sub(b0);
            Vector3f e2 = new Vector3f(t0).sub(b0);
            Vector3f faceNorm = e1.cross(e2).normalize();
            Vector3f midShaft = new Vector3f(b0).add(t1).mul(0.5f);
            int[] colShaft = computeIceColor(faceNorm, midShaft, 0.88f, false);
            emitIceQuad(mesh, b0, b1, t1, t0, colShaft);

            Vector3f pe1 = new Vector3f(t1).sub(t0);
            Vector3f pe2 = new Vector3f(tip).sub(t0);
            Vector3f tipNorm = pe1.cross(pe2).normalize();
            Vector3f midTip = new Vector3f(t0).add(t1).add(tip).mul(1.0f / 3.0f);
            int[] colTip = computeIceColor(tipNorm, midTip, 0.94f, true);
            emitIceQuad(mesh, t0, t1, tip, tip, colTip);
        }
    }

    private void emitShard(BufferBuilder mesh, IceShard shard, float scale, float alpha, Vec3d camPos) {
        Quaternionf rot = new Quaternionf()
                .rotationYXZ((float) Math.toRadians(shard.yaw),
                        (float) Math.toRadians(shard.pitch),
                        (float) Math.toRadians(shard.roll));
        float s = shard.size * scale;
        float cx = (float) (shard.x - camPos.x);
        float cy = (float) (shard.y - camPos.y);
        float cz = (float) (shard.z - camPos.z);

        if (shard.shape == 0) {
            float hx = s * 0.48f;
            float hy = s * 0.42f;
            float hz = s * 0.30f;
            float skew = s * 0.14f;
            Vector3f[] v = {
                    transformShardPoint(-hx, -hy, hz, rot, cx, cy, cz),
                    transformShardPoint(hx, -hy, hz, rot, cx, cy, cz),
                    transformShardPoint(hx - skew, hy, hz * 0.8f, rot, cx, cy, cz),
                    transformShardPoint(-hx + skew * 0.5f, hy, hz * 0.85f, rot, cx, cy, cz),
                    transformShardPoint(-hx, -hy, -hz, rot, cx, cy, cz),
                    transformShardPoint(hx, -hy, -hz, rot, cx, cy, cz),
                    transformShardPoint(hx - skew, hy, -hz * 0.8f, rot, cx, cy, cz),
                    transformShardPoint(-hx + skew * 0.5f, hy, -hz * 0.85f, rot, cx, cy, cz)
            };
            emitFacetedQuad(mesh, v[0], v[1], v[2], v[3], alpha, false);
            emitFacetedQuad(mesh, v[5], v[4], v[7], v[6], alpha, false);
            emitFacetedQuad(mesh, v[4], v[0], v[3], v[7], alpha, true);
            emitFacetedQuad(mesh, v[1], v[5], v[6], v[2], alpha, true);
            emitFacetedQuad(mesh, v[3], v[2], v[6], v[7], alpha, true);
            emitFacetedQuad(mesh, v[4], v[5], v[1], v[0], alpha, false);
        } else if (shard.shape == 1) {
            float r = s * 0.36f;
            float h = s * 0.88f;
            Vector3f tip = transformShardPoint(0.0f, h, 0.0f, rot, cx, cy, cz);
            Vector3f[] base = {
                    transformShardPoint(r, -h * 0.35f, 0.0f, rot, cx, cy, cz),
                    transformShardPoint(0.0f, -h * 0.35f, r, rot, cx, cy, cz),
                    transformShardPoint(-r, -h * 0.35f, 0.0f, rot, cx, cy, cz),
                    transformShardPoint(0.0f, -h * 0.35f, -r, rot, cx, cy, cz)
            };
            emitFacetedQuad(mesh, base[0], base[1], tip, tip, alpha, true);
            emitFacetedQuad(mesh, base[1], base[2], tip, tip, alpha, true);
            emitFacetedQuad(mesh, base[2], base[3], tip, tip, alpha, true);
            emitFacetedQuad(mesh, base[3], base[0], tip, tip, alpha, true);
            emitFacetedQuad(mesh, base[3], base[2], base[1], base[0], alpha, false);
        } else {
            float t = s * 0.17f;
            float x0 = -0.45f * s, y0 = -0.35f * s;
            float x1 = 0.50f * s, y1 = -0.25f * s;
            float x2 = 0.20f * s, y2 = 0.58f * s;
            float x3 = -0.42f * s, y3 = 0.38f * s;
            Vector3f f0 = transformShardPoint(x0, y0, t, rot, cx, cy, cz);
            Vector3f f1 = transformShardPoint(x1, y1, t, rot, cx, cy, cz);
            Vector3f f2 = transformShardPoint(x2, y2, t, rot, cx, cy, cz);
            Vector3f f3 = transformShardPoint(x3, y3, t, rot, cx, cy, cz);
            Vector3f b0 = transformShardPoint(x0, y0, -t, rot, cx, cy, cz);
            Vector3f b1 = transformShardPoint(x1, y1, -t, rot, cx, cy, cz);
            Vector3f b2 = transformShardPoint(x2, y2, -t, rot, cx, cy, cz);
            Vector3f b3 = transformShardPoint(x3, y3, -t, rot, cx, cy, cz);
            emitFacetedQuad(mesh, f0, f1, f2, f3, alpha, false);
            emitFacetedQuad(mesh, b3, b2, b1, b0, alpha, false);
            emitFacetedQuad(mesh, f0, f1, b1, b0, alpha, true);
            emitFacetedQuad(mesh, f1, f2, b2, b1, alpha, true);
            emitFacetedQuad(mesh, f2, f3, b3, b2, alpha, true);
            emitFacetedQuad(mesh, f3, f0, b0, b3, alpha, true);
        }
    }

    private void emitFacetedQuad(BufferBuilder mesh, Vector3f v0, Vector3f v1, Vector3f v2, Vector3f v3,
                                 float alpha, boolean brightRim) {
        Vector3f e1 = new Vector3f(v1).sub(v0);
        Vector3f e2 = new Vector3f(v2).sub(v0);
        Vector3f normal = e1.cross(e2);
        if (normal.lengthSquared() < 1e-8f) {
            normal.set(0.0f, 1.0f, 0.0f);
        } else {
            normal.normalize();
        }
        Vector3f center = new Vector3f(v0).add(v2).mul(0.5f);
        int[] col = computeIceColor(normal, center, alpha, brightRim);
        emitIceQuad(mesh, v0, v1, v2, v3, col);
    }

    private Vector3f transformShardPoint(float lx, float ly, float lz, Quaternionf rot, float cx, float cy, float cz) {
        Vector3f v = new Vector3f(lx, ly, lz).rotate(rot);
        return v.add(cx, cy, cz);
    }

    private int[] computeIceColor(Vector3f normal, Vector3f relPos, float alpha, boolean bright) {
        Vector3f toCam = new Vector3f(-relPos.x, -relPos.y, -relPos.z);
        if (toCam.lengthSquared() > 1e-6f) {
            toCam.normalize();
        } else {
            toCam.set(0.0f, 0.0f, 1.0f);
        }
        float diff = 0.56f + 0.44f * Math.abs(normal.dot(lightDir));
        float fresnel = (float) Math.pow(1.0f - Math.abs(normal.dot(toCam)), 1.85);
        float boost = bright ? 1.08f : 0.96f;
        int r = MathHelper.clamp(Math.round(162 * diff * boost + 90.0f * fresnel), 0, 255);
        int g = MathHelper.clamp(Math.round(220 * diff * boost + 35.0f * fresnel), 0, 255);
        int b = MathHelper.clamp(Math.round(244 * diff * boost + 12.0f * fresnel), 0, 255);
        int a = MathHelper.clamp(Math.round((bright ? 222.0f : 196.0f) * alpha), 0, 255);
        return new int[]{r, g, b, a};
    }

    private static void emitIceQuad(BufferBuilder mesh, Vector3f v0, Vector3f v1, Vector3f v2, Vector3f v3, int[] col) {
        mesh.vertex(v0.x, v0.y, v0.z).texture(0.0f, 0.0f).color(col[0], col[1], col[2], col[3]);
        mesh.vertex(v1.x, v1.y, v1.z).texture(1.0f, 0.0f).color(col[0], col[1], col[2], col[3]);
        mesh.vertex(v2.x, v2.y, v2.z).texture(1.0f, 1.0f).color(col[0], col[1], col[2], col[3]);
        mesh.vertex(v3.x, v3.y, v3.z).texture(0.0f, 1.0f).color(col[0], col[1], col[2], col[3]);
    }

    private void shatter(MinecraftClient client, FreezeEffect effect, boolean playSound) {
        Vec3d origin = effect.position;
        double h = effect.height;
        double w = effect.width;

        for (int i = 0; i < 42; i++) {
            if (shards.size() >= MAX_SHARDS) shards.removeFirst();
            double angle = random.nextDouble() * Math.PI * 2.0;
            double r = random.nextDouble() * w * 0.42;
            double sx = origin.x + Math.cos(angle) * r;
            double sy = origin.y + 0.10 + random.nextDouble() * h * 0.92;
            double sz = origin.z + Math.sin(angle) * r;

            double speed = 1.9 + random.nextDouble() * 3.1;
            double vx = Math.cos(angle) * speed + (random.nextDouble() - 0.5) * 0.8;
            double vy = 0.8 + random.nextDouble() * 3.1 + (sy - origin.y) * 0.45;
            double vz = Math.sin(angle) * speed + (random.nextDouble() - 0.5) * 0.8;

            int shape = i % 3 == 0 ? 0 : 2;
            float size = 0.14f + random.nextFloat() * 0.18f;
            float life = 0.85f + random.nextFloat() * 0.55f;
            shards.add(new IceShard(sx, sy, sz, vx, vy, vz,
                    random.nextFloat() * 360.0f, random.nextFloat() * 360.0f, random.nextFloat() * 360.0f,
                    (random.nextFloat() - 0.5f) * 520.0f, (random.nextFloat() - 0.5f) * 520.0f, (random.nextFloat() - 0.5f) * 520.0f,
                    size, life, shape));
        }

        for (CrystalSpike spike : effect.spikes) {
            for (int k = 0; k < 2; k++) {
                if (shards.size() >= MAX_SHARDS) shards.removeFirst();
                double sx = origin.x + spike.offsetX + MathHelper.cos(spike.angle) * (0.12 * (k + 1));
                double sy = origin.y + 0.12 + k * spike.height * 0.42;
                double sz = origin.z + spike.offsetZ + MathHelper.sin(spike.angle) * (0.12 * (k + 1));
                double speed = 2.4 + random.nextDouble() * 2.6;
                double vx = MathHelper.cos(spike.angle) * speed + (random.nextDouble() - 0.5) * 1.1;
                double vy = 1.6 + random.nextDouble() * 2.6;
                double vz = MathHelper.sin(spike.angle) * speed + (random.nextDouble() - 0.5) * 1.1;
                shards.add(new IceShard(sx, sy, sz, vx, vy, vz,
                        random.nextFloat() * 360.0f, random.nextFloat() * 360.0f, random.nextFloat() * 360.0f,
                        (random.nextFloat() - 0.5f) * 580.0f, (random.nextFloat() - 0.5f) * 580.0f, (random.nextFloat() - 0.5f) * 580.0f,
                        0.18f + random.nextFloat() * 0.14f, 0.95f + random.nextFloat() * 0.50f, k == 1 ? 1 : 0));
            }
        }

        for (int i = 0; i < 32; i++) {
            if (sparkles.size() >= MAX_SPARKLES) sparkles.removeFirst();
            double angle = random.nextDouble() * Math.PI * 2.0;
            double r = random.nextDouble() * w * 0.55;
            double sx = origin.x + Math.cos(angle) * r;
            double sy = origin.y + 0.15 + random.nextDouble() * h * 0.95;
            double sz = origin.z + Math.sin(angle) * r;
            double speed = 1.5 + random.nextDouble() * 2.9;
            double vx = Math.cos(angle) * speed;
            double vy = 0.6 + random.nextDouble() * 2.3;
            double vz = Math.sin(angle) * speed;
            boolean star = (i % 5) != 0;
            sparkles.add(new IceSparkle(sx, sy, sz, vx, vy, vz,
                    star ? 0.13f + random.nextFloat() * 0.12f : 0.11f + random.nextFloat() * 0.08f,
                    0.55f + random.nextFloat() * 0.50f, star, random.nextInt(SNOWFLAKES.length), random.nextFloat() * 6.28f));
        }

        if (playSound && client.world != null) {
            client.world.playSoundClient(origin.x, origin.y + h * 0.5, origin.z, SoundEvents.BLOCK_GLASS_BREAK,
                    SoundCategory.PLAYERS, 0.95f, 1.15f, false);
            client.world.playSoundClient(origin.x, origin.y + h * 0.5, origin.z, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK,
                    SoundCategory.PLAYERS, 0.65f, 1.35f, false);
        }
    }

    private void spawnAmbientSnowflake(Vec3d origin, double w, double h, boolean initial) {
        if (sparkles.size() >= MAX_SPARKLES) sparkles.removeFirst();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dist = w * (0.45 + random.nextDouble() * 0.85);
        double sx = origin.x + Math.cos(angle) * dist;
        double sy = origin.y + 0.15 + random.nextDouble() * (h + 0.35);
        double sz = origin.z + Math.sin(angle) * dist;
        double vx = Math.cos(angle) * (0.15 + random.nextDouble() * 0.25);
        double vy = 0.18 + random.nextDouble() * 0.32;
        double vz = Math.sin(angle) * (0.15 + random.nextDouble() * 0.25);
        sparkles.add(new IceSparkle(sx, sy, sz, vx, vy, vz,
                0.085f + random.nextFloat() * 0.065f, initial ? 0.55f + random.nextFloat() * 0.25f : 0.42f + random.nextFloat() * 0.25f,
                false, random.nextInt(SNOWFLAKES.length), random.nextFloat() * 6.28f));
    }

    private void updateShards(MinecraftClient client, float dt) {
        Iterator<IceShard> it = shards.iterator();
        while (it.hasNext()) {
            IceShard s = it.next();
            s.age += dt;
            if (s.age >= s.life) {
                it.remove();
                continue;
            }
            s.vy -= 12.2 * dt;
            float drag = (float) Math.exp(-1.15 * dt);
            s.vx *= drag;
            s.vy *= drag;
            s.vz *= drag;
            s.pitch += s.rotPitch * dt;
            s.yaw += s.rotYaw * dt;
            s.roll += s.rotRoll * dt;

            double nextX = s.x + s.vx * dt;
            double nextY = s.y + s.vy * dt;
            double nextZ = s.z + s.vz * dt;
            if (client.world != null) {
                BlockPos bp = BlockPos.ofFloored(nextX, nextY - 0.04, nextZ);
                BlockState bs = client.world.getBlockState(bp);
                if (!bs.isAir() && bs.isSolidBlock(client.world, bp)) {
                    if (s.vy < 0.0) {
                        s.vy = -s.vy * 0.36;
                    }
                    s.vx *= 0.72;
                    s.vz *= 0.72;
                    s.rotPitch *= 0.55f;
                    s.rotYaw *= 0.55f;
                    s.rotRoll *= 0.55f;
                    nextY = Math.max(s.y, bp.getY() + 1.03);
                }
            }
            s.x = nextX;
            s.y = nextY;
            s.z = nextZ;
        }
    }

    private void updateSparkles(float dt) {
        Iterator<IceSparkle> it = sparkles.iterator();
        while (it.hasNext()) {
            IceSparkle sp = it.next();
            sp.age += dt;
            if (sp.age >= sp.life) {
                it.remove();
                continue;
            }
            float drag = (float) Math.exp(-2.6 * dt);
            sp.vx *= drag;
            sp.vy = sp.vy * drag + 0.14 * dt;
            sp.vz *= drag;
            sp.x += sp.vx * dt;
            sp.y += sp.vy * dt;
            sp.z += sp.vz * dt;
        }
    }

    private void renderSparkles(Camera camera, VertexConsumerProvider immediate, Vec3d camPos) {
        camRight.set(1.0f, 0.0f, 0.0f).rotate(camera.getRotation());
        camUp.set(0.0f, 1.0f, 0.0f).rotate(camera.getRotation());

        VertexConsumer starBuf = immediate.getBuffer(RenderLayers.lightning());
        if (meshAllocator == null) meshAllocator = new BufferAllocator(16384);

        try {
            BufferBuilder bloomMesh = new BufferBuilder(meshAllocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            boolean hasBloom = false;

            for (IceSparkle sp : sparkles) {
                float t = sp.age / sp.life;
                float fade = MathHelper.clamp((1.0f - t) * 2.0f, 0.0f, 1.0f) * MathHelper.clamp(t * 7.0f, 0.0f, 1.0f);
                float pulse = 0.72f + 0.28f * MathHelper.sin(sp.age * 16.0f + sp.phase);
                float alpha = fade * pulse;
                if (alpha <= 0.02f) continue;

                float px = (float) (sp.x - camPos.x);
                float py = (float) (sp.y - camPos.y);
                float pz = (float) (sp.z - camPos.z);

                if (sp.isStar) {
                    float r = sp.size * (0.85f + 0.30f * pulse);
                    float w = r * 0.24f;
                    int coreA = MathHelper.clamp(Math.round(255.0f * alpha), 0, 255);
                    emitDiamondArm(starBuf, px, py, pz, camRight, camUp, r, w, coreA);
                    emitDiamondArm(starBuf, px, py, pz, camUp, camRight, r, w, coreA);

                    float hr = r * 1.45f;
                    int haloA = MathHelper.clamp(Math.round(135.0f * alpha), 0, 255);
                    emitBillboardTex(bloomMesh, px, py, pz, camRight, camUp, hr, 210, 245, 255, haloA);
                    hasBloom = true;
                }
            }

            var builtBloom = bloomMesh.endNullable();
            if (hasBloom && builtBloom != null) {
                MeshDrawUtil.drawLabeledTextured(Pipelines.AURA_BLOOM_DEPTH, builtBloom, "egg ice star bloom", BLOOM_TEXTURE);
            }
        } finally {
            meshAllocator.reset();
        }

        for (int texIdx = 0; texIdx < SNOWFLAKES.length; texIdx++) {
            try {
                BufferBuilder flakeMesh = new BufferBuilder(meshAllocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                boolean any = false;
                for (IceSparkle sp : sparkles) {
                    if (sp.isStar || sp.texIndex != texIdx) continue;
                    float t = sp.age / sp.life;
                    float fade = MathHelper.clamp((1.0f - t) * 1.8f, 0.0f, 1.0f) * MathHelper.clamp(t * 6.0f, 0.0f, 1.0f);
                    if (fade <= 0.02f) continue;
                    float px = (float) (sp.x - camPos.x);
                    float py = (float) (sp.y - camPos.y);
                    float pz = (float) (sp.z - camPos.z);
                    int a = MathHelper.clamp(Math.round(235.0f * fade), 0, 255);
                    emitBillboardTex(flakeMesh, px, py, pz, camRight, camUp, sp.size, 235, 250, 255, a);
                    any = true;
                }
                var built = flakeMesh.endNullable();
                if (any && built != null) {
                    MeshDrawUtil.drawLabeledTextured(Pipelines.AURA_TEXTURE_DEPTH, built, "egg ice snowflake", SNOWFLAKES[texIdx]);
                }
            } finally {
                meshAllocator.reset();
            }
        }
    }

    private static void emitDiamondArm(VertexConsumer buf, float px, float py, float pz,
                                       Vector3f mainAxis, Vector3f crossAxis, float len, float halfW, int a) {
        buf.vertex(px - mainAxis.x * len, py - mainAxis.y * len, pz - mainAxis.z * len).color(220, 248, 255, a);
        buf.vertex(px + crossAxis.x * halfW, py + crossAxis.y * halfW, pz + crossAxis.z * halfW).color(255, 255, 255, a);
        buf.vertex(px + mainAxis.x * len, py + mainAxis.y * len, pz + mainAxis.z * len).color(220, 248, 255, a);
        buf.vertex(px - crossAxis.x * halfW, py - crossAxis.y * halfW, pz - crossAxis.z * halfW).color(255, 255, 255, a);
    }

    private static void emitBillboardTex(BufferBuilder mesh, float px, float py, float pz,
                                         Vector3f right, Vector3f up, float size, int r, int g, int b, int a) {
        mesh.vertex(px - right.x * size - up.x * size, py - right.y * size - up.y * size, pz - right.z * size - up.z * size)
                .texture(0.0f, 1.0f).color(r, g, b, a);
        mesh.vertex(px + right.x * size - up.x * size, py + right.y * size - up.y * size, pz + right.z * size - up.z * size)
                .texture(1.0f, 1.0f).color(r, g, b, a);
        mesh.vertex(px + right.x * size + up.x * size, py + right.y * size + up.y * size, pz + right.z * size + up.z * size)
                .texture(1.0f, 0.0f).color(r, g, b, a);
        mesh.vertex(px - right.x * size + up.x * size, py - right.y * size + up.y * size, pz - right.z * size + up.z * size)
                .texture(0.0f, 0.0f).color(r, g, b, a);
    }

    private static SimpleFramebuffer resize(SimpleFramebuffer target, String name, Framebuffer source, boolean depth) {
        if (target == null) return new SimpleFramebuffer(name, source.textureWidth, source.textureHeight, depth);
        if (target.textureWidth != source.textureWidth || target.textureHeight != source.textureHeight) {
            target.resize(source.textureWidth, source.textureHeight);
        }
        return target;
    }

    private static float smooth(float value) {
        float t = MathHelper.clamp(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float easeOutBack(float t) {
        float x = MathHelper.clamp(t, 0.0f, 1.0f);
        float c1 = 1.45f;
        float c3 = c1 + 1.0f;
        float u = x - 1.0f;
        return 1.0f + c3 * u * u * u + c1 * u * u;
    }

    public void clear() {
        effects.clear();
        shards.clear();
        sparkles.clear();
        lastFrameNanos = 0L;
        if (sceneCopy != null) {
            sceneCopy.delete();
            sceneCopy = null;
        }
        if (targetMask != null) {
            targetMask.delete();
            targetMask = null;
        }
        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
        if (meshAllocator != null) {
            meshAllocator.close();
            meshAllocator = null;
        }
        if (quadAllocator != null) {
            quadAllocator.close();
            quadAllocator = null;
        }
    }

    private record CrystalSpike(float offsetX, float offsetZ, float angle, float tilt, float height, float radius,
                                float delay, float spin, float[] jitter) {}

    private static final class FreezeEffect {
        final ClientWorld world;
        final Vec3d position;
        final float height;
        final float width;
        final EntityRenderState model;
        final List<CrystalSpike> spikes;
        final long startedNanos;
        boolean shattered;

        FreezeEffect(ClientWorld world, Vec3d position, float height, float width,
                     EntityRenderState model, List<CrystalSpike> spikes, long startedNanos) {
            this.world = world;
            this.position = position;
            this.height = height;
            this.width = width;
            this.model = model;
            this.spikes = spikes;
            this.startedNanos = startedNanos;
        }
    }

    private static final class IceShard {
        double x, y, z;
        double vx, vy, vz;
        float pitch, yaw, roll;
        float rotPitch, rotYaw, rotRoll;
        final float size;
        final float life;
        final int shape;
        float age;

        IceShard(double x, double y, double z, double vx, double vy, double vz,
                 float pitch, float yaw, float roll, float rotPitch, float rotYaw, float rotRoll,
                 float size, float life, int shape) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.pitch = pitch;
            this.yaw = yaw;
            this.roll = roll;
            this.rotPitch = rotPitch;
            this.rotYaw = rotYaw;
            this.rotRoll = rotRoll;
            this.size = size;
            this.life = life;
            this.shape = shape;
        }
    }

    private static final class IceSparkle {
        double x, y, z;
        double vx, vy, vz;
        final float size;
        final float life;
        final boolean isStar;
        final int texIndex;
        final float phase;
        float age;

        IceSparkle(double x, double y, double z, double vx, double vy, double vz,
                   float size, float life, boolean isStar, int texIndex, float phase) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.size = size;
            this.life = life;
            this.isStar = isStar;
            this.texIndex = texIndex;
            this.phase = phase;
        }
    }

    private record FreezeUniform(Matrix4fc invViewProj, float relX, float relY, float relZ, float height,
                                 float freezeFront, float pulseGlow, float age, float opacity,
                                 float zeroToOneDepth) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            for (float v : invViewProj.get(new float[16])) {
                buffer.putFloat(v);
            }
            buffer.putFloat(relX).putFloat(relY).putFloat(relZ).putFloat(height);
            buffer.putFloat(freezeFront).putFloat(pulseGlow).putFloat(age).putFloat(opacity);
            buffer.putFloat(zeroToOneDepth).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
        }
    }
}

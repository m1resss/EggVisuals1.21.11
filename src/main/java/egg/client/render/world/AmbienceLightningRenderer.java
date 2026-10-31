package egg.client.render.world;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap.Type;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.ParticleBlurRenderer;
import egg.client.render.shader.Pipelines;
import egg.client.render.shader.ProjectionUtil;
import egg.client.util.ColorUtil;

public enum AmbienceLightningRenderer {
    INSTANCE;

    private static final double MIN_DISTANCE = 36.0;
    private static final double MAX_DISTANCE = 220.0;
    private static final double SOUND_SPEED = 120.0;
    private static final int MAIN_BOLT_DEPTH = 7;
    private static final int BRANCH_DEPTH = 5;
    private static final float DISPLACEMENT = 0.16F;
    private static final float FLASH_RISE = 0.025F;
    private static final float FLASH_DECAY = 0.08F;
    static final int BOLT_COLOR = ColorUtil.rgb(150, 165, 255);
    private static final float MIN_FALLOFF = 0.35F;

    private final List<Bolt> bolts = new ArrayList<>();
    private final Random random = new Random();
    private long nextStrikeTime;

    public void tick(ClientWorld clientWorld, float frequency) {
        if (clientWorld == null) return;
        long nowNanos = System.nanoTime();
        this.bolts.removeIf(bolt -> bolt.isExpired(nowNanos));

        for (Bolt bolt : this.bolts) {
            bolt.tick(clientWorld, nowNanos);
        }

        if (frequency > 0.0F) {
            if (this.nextStrikeTime == 0L) {
                this.nextStrikeTime = nowNanos + this.nextStrikeDelay(frequency);
            }

            if (nowNanos >= this.nextStrikeTime) {
                this.nextStrikeTime = nowNanos + this.nextStrikeDelay(frequency);
                Bolt bolt = this.spawnBolt(clientWorld, nowNanos);
                bolt.tick(clientWorld, nowNanos);
                this.bolts.add(bolt);
            }
        } else {
            this.nextStrikeTime = 0L;
        }
    }

    public float getFlashIntensity(Vec3d camPos) {
        if (this.bolts.isEmpty()) return 0.0F;
        long nowNanos = System.nanoTime();
        float maxIntensity = 0.0F;

        for (Bolt bolt : this.bolts) {
            float falloff = 1.0F - 0.65F * smoothstep(60.0F, 220.0F, bolt.distanceTo(camPos));
            maxIntensity = Math.max(maxIntensity, bolt.getIntensity(bolt.getAge(nowNanos)) * falloff);
        }

        return maxIntensity;
    }

    public void render(Vec3d camPos, float fogStart, float fogEnd) {
        if (this.bolts.isEmpty()) return;
        long nowNanos = System.nanoTime();

        try (
            BufferAllocator bufferAllocator = new BufferAllocator(32768);
            BufferAllocator bufferAllocator2 = new BufferAllocator(32768);
            BufferAllocator bufferAllocator3 = new BufferAllocator(32768);
        ) {
            BufferBuilder bufferBuilder = createLineBuilder(bufferAllocator);
            BufferBuilder bufferBuilder2 = createLineBuilder(bufferAllocator2);
            BufferBuilder bufferBuilder3 = createLineBuilder(bufferAllocator3);
            float bloomSize = 0.0F;
            float maxIntensity = 0.0F;

            for (Bolt bolt : this.bolts) {
                float age = bolt.getAge(nowNanos);
                float dist = bolt.distanceTo(camPos);
                float fogFactor = (float) Math.exp(-Math.min(Math.max(dist - fogStart, 0.0F) * fogEnd, 3.0F));
                float intensity = bolt.getIntensity(age) * Math.max(fogFactor, MIN_FALLOFF);

                if (intensity > 0.003F) {
                    float ppb = ProjectionUtil.getPixelsPerBlock(dist);
                    float widthCore = MathHelper.clamp(ppb * 0.35F, 2.2F, 7.0F);
                    float widthHalo = MathHelper.clamp(ppb * 1.6F, 10.0F, 34.0F);

                    bolt.render(bufferBuilder, camPos, age, intensity, widthCore * 1.4F);
                    bolt.render(bufferBuilder2, camPos, age, intensity * 0.45F, widthHalo);
                    bolt.render(bufferBuilder3, camPos, age, intensity, widthCore);

                    bloomSize = Math.max(bloomSize, MathHelper.clamp(ppb * 2.5F, 6.0F, 30.0F));
                    maxIntensity = Math.max(maxIntensity, intensity);
                }
            }

            BuiltBuffer builtBuffer = bufferBuilder.endNullable();
            if (builtBuffer != null) {
                ParticleBlurRenderer.INSTANCE.render(
                    bloomSize, Math.min(maxIntensity, 1.0F),
                    () -> MeshDrawUtil.drawLabeled(Pipelines.LIGHTNING_DEPTH, builtBuffer, "egg ambience lightning bloom")
                );
            }

            BuiltBuffer builtBuffer2 = bufferBuilder2.endNullable();
            if (builtBuffer2 != null) {
                MeshDrawUtil.drawLabeled(Pipelines.LIGHTNING_DEPTH, builtBuffer2, "egg ambience lightning halo");
            }

            BuiltBuffer builtBuffer3 = bufferBuilder3.endNullable();
            if (builtBuffer3 != null) {
                MeshDrawUtil.drawLabeled(Pipelines.LIGHTNING_DEPTH, builtBuffer3, "egg ambience lightning core");
            }
        }
    }

    public boolean hasBolts() {
        return !this.bolts.isEmpty();
    }

    public void clear() {
        this.bolts.clear();
        this.nextStrikeTime = 0L;
    }

    private static BufferBuilder createLineBuilder(BufferAllocator bufferAllocator) {
        return new BufferBuilder(bufferAllocator, DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);
    }

    private long nextStrikeDelay(float frequency) {
        double delaySec = MathHelper.lerp(frequency, 16.0, 2.2) * (0.35 + this.random.nextDouble() * 1.3);
        return (long) (delaySec * 1.0E9);
    }

    private Bolt spawnBolt(ClientWorld clientWorld, long nowNanos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d camPos = mc.gameRenderer != null && mc.gameRenderer.getCamera() != null
                ? mc.gameRenderer.getCamera().getCameraPos() : Vec3d.ZERO;

        double angle = this.random.nextDouble() * Math.PI * 2.0;
        double dist = MIN_DISTANCE + this.random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        double strikeX = camPos.x + Math.cos(angle) * dist;
        double strikeZ = camPos.z + Math.sin(angle) * dist;

        int blockX = MathHelper.floor(strikeX);
        int blockZ = MathHelper.floor(strikeZ);
        boolean isChunkLoaded = clientWorld.getChunkManager().isChunkLoaded(
                ChunkSectionPos.getSectionCoord(blockX),
                ChunkSectionPos.getSectionCoord(blockZ)
        );

        double strikeY = isChunkLoaded
                ? clientWorld.getTopY(Type.MOTION_BLOCKING, blockX, blockZ)
                : Math.min(camPos.y, clientWorld.getSeaLevel());

        float height = 100.0F + this.random.nextFloat() * 40.0F;
        long acousticDelayNanos = (long) (dist / SOUND_SPEED * 1.0E9);

        return new Bolt(this.random, strikeX, strikeY, strikeZ, height, nowNanos, acousticDelayNanos);
    }

    static float smoothstep(float edge0, float edge1, float x) {
        float t = MathHelper.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    static float[] generatePath(Random rnd, float x0, float y0, float z0, float x1, float y1, float z1, int depth) {
        int count = 1 << depth;
        float[] coords = new float[(count + 1) * 3];
        coords[0] = x0;
        coords[1] = y0;
        coords[2] = z0;
        coords[count * 3] = x1;
        coords[count * 3 + 1] = y1;
        coords[count * 3 + 2] = z1;
        subdivide(rnd, coords, 0, count);
        return coords;
    }

    private static void subdivide(Random rnd, float[] coords, int idx0, int idx1) {
        if (idx1 - idx0 >= 2) {
            int p0 = idx0 * 3;
            int p1 = idx1 * 3;
            float dx = coords[p1] - coords[p0];
            float dy = coords[p1 + 1] - coords[p0 + 1];
            float dz = coords[p1 + 2] - coords[p0 + 2];
            float len = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
            float xzLen = MathHelper.sqrt(dx * dx + dz * dz);
            float nx = xzLen > 1.0E-4F ? -dz / xzLen : 1.0F;
            float nz = xzLen > 1.0E-4F ? dx / xzLen : 0.0F;
            float bx = dy * nz / len;
            float by = (dz * nx - dx * nz) / len;
            float bz = -dy * nx / len;
            float disp1 = (rnd.nextFloat() * 2.0F - 1.0F) * len * DISPLACEMENT;
            float disp2 = (rnd.nextFloat() * 2.0F - 1.0F) * len * DISPLACEMENT;

            int midIdx = (idx0 + idx1) >>> 1;
            int pMid = midIdx * 3;
            coords[pMid] = coords[p0] + dx * 0.5F + nx * disp1 + bx * disp2;
            coords[pMid + 1] = coords[p0 + 1] + dy * 0.5F + by * disp2;
            coords[pMid + 2] = coords[p0 + 2] + dz * 0.5F + nz * disp1 + bz * disp2;

            subdivide(rnd, coords, idx0, midIdx);
            subdivide(rnd, coords, midIdx, idx1);
        }
    }

    static final class Bolt {
        final double x;
        final double y;
        final double z;
        final float height;
        private final float[] mainPath;
        private final List<Branch> branches = new ArrayList<>();
        private final float[] flashTimes;
        private final float[] flashStrengths;
        private final float duration;
        private final float thunderPitch;
        private final long startTime;
        private final long thunderTime;
        private int flashIndex;
        private boolean thunderPlayed;

        Bolt(Random rnd, double x, double y, double z, float height, long startTime, long soundDelay) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.height = height;
            this.startTime = startTime;
            this.thunderTime = startTime + soundDelay;
            this.thunderPitch = 0.8F + rnd.nextFloat() * 0.2F;

            float topOffsetHorizontalX = (rnd.nextFloat() - 0.5F) * 0.3F * height;
            float topOffsetHorizontalZ = (rnd.nextFloat() - 0.5F) * 0.3F * height;
            this.mainPath = AmbienceLightningRenderer.generatePath(
                    rnd, topOffsetHorizontalX, height, topOffsetHorizontalZ, 0.0F, 0.0F, 0.0F, MAIN_BOLT_DEPTH
            );
            this.generateBranches(rnd);

            int flashCount = 2 + rnd.nextInt(3);
            this.flashTimes = new float[flashCount];
            this.flashStrengths = new float[flashCount];
            this.flashStrengths[0] = 1.0F;

            for (int i = 1; i < flashCount; i++) {
                this.flashTimes[i] = this.flashTimes[i - 1] + 0.08F + rnd.nextFloat() * 0.22F;
                this.flashStrengths[i] = 0.55F + rnd.nextFloat() * 0.4F;
            }

            this.duration = this.flashTimes[flashCount - 1] + 0.55F;
        }

        private void generateBranches(Random rnd) {
            int branchCount = 3 + rnd.nextInt(4);

            for (int i = 0; i < branchCount; i++) {
                int seg = (int) (128 * (0.05F + rnd.nextFloat() * 0.6F)) * 3;
                float bx = this.mainPath[seg];
                float by = this.mainPath[seg + 1];
                float bz = this.mainPath[seg + 2];
                float branchLen = by * (0.2F + rnd.nextFloat() * 0.35F);
                float angle = rnd.nextFloat() * (float) (Math.PI * 2);

                float[] branchPath = generateBranchPath(rnd, bx, by, bz, branchLen, angle, BRANCH_DEPTH);
                this.branches.add(new Branch(branchPath, 0.55F + rnd.nextFloat() * 0.25F));

                if (rnd.nextBoolean()) {
                    int subSeg = (int) (32.0F * (0.25F + rnd.nextFloat() * 0.5F)) * 3;
                    float sx = branchPath[subSeg];
                    float sy = branchPath[subSeg + 1];
                    float sz = branchPath[subSeg + 2];
                    float subLen = by * (0.08F + rnd.nextFloat() * 0.12F);
                    float subAngle = rnd.nextFloat() * (float) (Math.PI * 2);
                    float[] subBranch = generateBranchPath(rnd, sx, sy, sz, subLen, subAngle, 4);
                    this.branches.add(new Branch(subBranch, 0.35F + rnd.nextFloat() * 0.15F));
                }
            }
        }

        private static float[] generateBranchPath(Random rnd, float x0, float y0, float z0, float length, float angle, int depth) {
            float spread = 0.4F + rnd.nextFloat() * 0.8F;
            float dx = MathHelper.cos(angle) * spread;
            float dz = MathHelper.sin(angle) * spread;
            float norm = MathHelper.sqrt(dx * dx + 1.0F + dz * dz);
            float scale = length / norm;
            return AmbienceLightningRenderer.generatePath(rnd, x0, y0, z0, x0 + dx * scale, y0 - scale, z0 + dz * scale, depth);
        }

        void tick(ClientWorld clientWorld, long nowNanos) {
            float age = this.getAge(nowNanos);

            while (this.flashIndex < this.flashTimes.length && age >= this.flashTimes[this.flashIndex]) {
                this.flashIndex++;
                clientWorld.setLightningTicksLeft(2);
            }

            if (!this.thunderPlayed && nowNanos >= this.thunderTime) {
                this.thunderPlayed = true;
                clientWorld.playSoundClient(
                    this.x, this.y, this.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 10000.0F, this.thunderPitch, false
                );
            }
        }

        boolean isExpired(long nowNanos) {
            return this.thunderPlayed && this.getAge(nowNanos) > this.duration;
        }

        float getAge(long nowNanos) {
            return (float) (nowNanos - this.startTime) / 1.0E9F;
        }

        float distanceTo(Vec3d camPos) {
            double dx = this.x - camPos.x;
            double dy = this.y + this.height * 0.4 - camPos.y;
            double dz = this.z - camPos.z;
            return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        float getIntensity(float age) {
            if (age < 0.0F || age > this.duration) {
                return 0.0F;
            }

            float baseIntensity = 0.2F * (float) Math.exp(-age / 0.5F);
            for (int i = 0; i < this.flashTimes.length; i++) {
                float dt = age - this.flashTimes[i];
                if (dt < 0.0F) break;

                float flashCurve = dt < FLASH_RISE ? dt / FLASH_RISE : (float) Math.exp(-(dt - FLASH_RISE) / FLASH_DECAY);
                baseIntensity = Math.max(baseIntensity, flashCurve * this.flashStrengths[i]);
            }

            return baseIntensity * (1.0F - AmbienceLightningRenderer.smoothstep(this.duration - 0.25F, this.duration, age));
        }

        void render(BufferBuilder bufferBuilder, Vec3d camPos, float age, float intensity, float lineWidth) {
            float relX = (float) (this.x - camPos.x);
            float relY = (float) (this.y - camPos.y);
            float relZ = (float) (this.z - camPos.z);

            renderPath(bufferBuilder, this.mainPath, relX, relY, relZ, intensity * 0.45F, intensity, lineWidth);
            float branchIntensity = intensity * (0.35F + 0.65F * (float) Math.exp(-age / 0.3F));

            for (Branch branch : this.branches) {
                renderPath(bufferBuilder, branch.points(), relX, relY, relZ,
                        branchIntensity * branch.alpha(), branchIntensity * branch.alpha() * 0.1F, lineWidth * 0.6F);
            }
        }

        private static void renderPath(BufferBuilder bufferBuilder, float[] coords, float ox, float oy, float oz,
                                       float alphaStart, float alphaEnd, float lineWidth) {
            int segCount = coords.length / 3 - 1;

            for (int i = 0; i < segCount; i++) {
                int p0 = i * 3;
                int p1 = p0 + 3;
                float dx = coords[p1] - coords[p0];
                float dy = coords[p1 + 1] - coords[p0 + 1];
                float dz = coords[p1 + 2] - coords[p0 + 2];
                float len = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1.0E-5F) continue;

                float nx = dx / len;
                float ny = dy / len;
                float nz = dz / len;

                int col0 = ColorUtil.withAlphaFloat(AmbienceLightningRenderer.BOLT_COLOR, MathHelper.lerp((float) i / segCount, alphaStart, alphaEnd));
                int col1 = ColorUtil.withAlphaFloat(AmbienceLightningRenderer.BOLT_COLOR, MathHelper.lerp((float) (i + 1) / segCount, alphaStart, alphaEnd));

                bufferBuilder.vertex(ox + coords[p0], oy + coords[p0 + 1], oz + coords[p0 + 2]).color(col0).normal(nx, ny, nz).lineWidth(lineWidth);
                bufferBuilder.vertex(ox + coords[p1], oy + coords[p1 + 1], oz + coords[p1 + 2]).color(col1).normal(nx, ny, nz).lineWidth(lineWidth);
            }
        }
    }

    record Branch(float[] points, float alpha) {}
}

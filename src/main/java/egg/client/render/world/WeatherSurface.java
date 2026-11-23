package egg.client.render.world;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

final class WeatherSurface {
    private static final int SIZE = 97;
    private final short[] heights = new short[SIZE * SIZE];
    private final byte[] flags = new byte[SIZE * SIZE];
    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private ClientWorld world;
    private int originX;
    private int originZ;
    private long nextUpdate;

    void update(ClientWorld world, Vec3d center) {
        int x = MathHelper.floor(center.x);
        int z = MathHelper.floor(center.z);
        int half = SIZE / 2;
        long now = System.nanoTime();
        if (this.world == world && now < nextUpdate
                && Math.abs(x - originX - half) <= 6 && Math.abs(z - originZ - half) <= 6) return;

        this.world = world;
        originX = x - half;
        originZ = z - half;
        nextUpdate = now + 450_000_000L;
        for (int dz = 0; dz < SIZE; dz++) {
            for (int dx = 0; dx < SIZE; dx++) {
                int worldX = originX + dx;
                int worldZ = originZ + dz;
                int index = dz * SIZE + dx;
                if (!world.getChunkManager().isChunkLoaded(worldX >> 4, worldZ >> 4)) {
                    heights[index] = Short.MIN_VALUE;
                    flags[index] = 0;
                    continue;
                }
                int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, worldX, worldZ);
                heights[index] = (short) top;
                BlockState state = world.getBlockState(pos.set(worldX, top - 1, worldZ));
                boolean solid = state.isSideSolidFullSquare(world, pos, Direction.UP)
                        && state.getFluidState().isEmpty();
                flags[index] = (byte) (solid ? 1 : 0);
            }
        }
    }

    int height(int x, int z) {
        int index = index(x, z);
        return index < 0 ? Short.MIN_VALUE : heights[index];
    }

    boolean solid(int x, int z) {
        int index = index(x, z);
        return index >= 0 && (flags[index] & 1) != 0;
    }

    boolean supportsSnow(double x, double z, int top) {
        int cellX = MathHelper.floor(x);
        int cellZ = MathHelper.floor(z);
        return height(cellX, cellZ) == top && solid(cellX, cellZ);
    }

    private int index(int x, int z) {
        x -= originX;
        z -= originZ;
        return x < 0 || z < 0 || x >= SIZE || z >= SIZE ? -1 : z * SIZE + x;
    }

}

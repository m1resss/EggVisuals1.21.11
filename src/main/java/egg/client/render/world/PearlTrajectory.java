package egg.client.render.world;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

public record PearlTrajectory(List<Vec3d> points, Vec3d impact) {
    public static PearlTrajectory predict(ClientWorld world, Entity source, Entity owner, Vec3d start, Vec3d velocity) {
        List<Vec3d> points = new ArrayList<>();
        points.add(start);
        Vec3d position = start;
        for (int tick = 0; tick < 160 && position.y >= world.getBottomY() - 16; tick++) {
            double drag = world.getFluidState(BlockPos.ofFloored(position)).isIn(FluidTags.WATER) ? 0.8f : 0.99f;
            velocity = velocity.add(0, -0.03, 0).multiply(drag);
            Vec3d next = position.add(velocity);
            BlockPos nextBlock = BlockPos.ofFloored(next);
            if (!world.isChunkLoaded(nextBlock.getX() >> 4, nextBlock.getZ() >> 4)) break;

            HitResult block = world.raycast(new RaycastContext(position, next,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, source));
            Vec3d end = block.getType() == HitResult.Type.MISS ? next : block.getPos();
            HitResult entity = ProjectileUtil.getEntityCollision(world, source, position, end,
                    new Box(position, end).expand(1.0), candidate -> candidate != owner && candidate.canBeHitByProjectile(), 0.3f);
            if (entity != null || block.getType() != HitResult.Type.MISS) {
                Vec3d impact = entity != null ? entity.getPos() : end;
                points.add(impact);
                return new PearlTrajectory(points, impact);
            }
            position = next;
            points.add(position);
        }
        return new PearlTrajectory(points, null);
    }

    public Vec3d pointBeforeImpact(double distance) {
        Vec3d end = points.getLast();
        for (int i = points.size() - 2; i >= 0; i--) {
            Vec3d previous = points.get(i);
            double length = end.distanceTo(previous);
            if (length > distance) return end.lerp(previous, distance / length);
            distance -= length;
            end = previous;
        }
        return end;
    }
}
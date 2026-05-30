package com.kuilunfuzhe.monvhua.util;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * 服务端射线检测工具类。
 * 通过遍历所有实体、使用包围盒射线相交检测来模拟玩家视线指向的实体判定，
 * 支持返回最近命中实体或带命中点的结果。
 */
public class RaycastHelper {

    /**
     * 获取玩家准星指向的实体（服务端版本）
     * @param player 玩家
     * @param maxDistance 最大距离
     * @return 命中的实体，没有则 null
     */
    @Nullable
    public static Entity getTargetEntity(ServerPlayerEntity player, double maxDistance) {
        ServerWorld world = player.getWorld();
        Vec3d start = player.getEyePos();
        Vec3d direction = player.getRotationVec(1.0f);
        Vec3d end = start.add(direction.multiply(maxDistance));

        Entity closestEntity = null;
        double closestDistance = maxDistance; // 初始化为最大距离，作为最近距离上界

        // 遍历所有实体，通过包围盒射线相交检测找最近命中
        for (Entity entity : world.iterateEntities()) {
            if (entity == player) continue;
            if (!entity.isAlive()) continue;

            // 平方距离预筛：快速排除超出最大距离的实体，避免不必要的包围盒计算
            double distanceToPlayerSq = entity.squaredDistanceTo(start);
            if (distanceToPlayerSq > maxDistance * maxDistance) continue;

            Box boundingBox = entity.getBoundingBox();
            var hit = boundingBox.raycast(start, end);
            if (hit.isPresent()) {
                double distance = start.distanceTo(hit.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestEntity = entity;
                }
            }
        }
        return closestEntity;
    }

    /**
     * 返回 EntityHitResult（包含命中点）
     */
    @Nullable
    public static EntityHitResult raycastEntity(ServerPlayerEntity player, double maxDistance) {
        ServerWorld world = player.getWorld();
        Vec3d start = player.getEyePos();
        Vec3d direction = player.getRotationVec(1.0f);
        Vec3d end = start.add(direction.multiply(maxDistance));

        Entity closestEntity = null;
        Vec3d closestHitPoint = null; // 保存最近命中点的精确坐标
        double closestDistance = maxDistance;

        // 与getTargetEntity相同的遍历逻辑，额外记录命中点用于返回EntityHitResult
        for (Entity entity : world.iterateEntities()) {
            if (entity == player) continue;
            if (!entity.isAlive()) continue;

            double distanceToPlayerSq = entity.squaredDistanceTo(start);
            if (distanceToPlayerSq > maxDistance * maxDistance) continue;

            Box boundingBox = entity.getBoundingBox();
            var hit = boundingBox.raycast(start, end);
            if (hit.isPresent()) {
                double distance = start.distanceTo(hit.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestEntity = entity;
                    closestHitPoint = hit.get();
                }
            }
        }

        if (closestEntity != null && closestHitPoint != null) {
            return new EntityHitResult(closestEntity, closestHitPoint);
        }
        return null;
    }
}
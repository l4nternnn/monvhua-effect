package com.kuilunfuzhe.monvhua.features.dissolve.client;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

final class DissolvePixelPositionMapper {
    private static final double SURFACE_OFFSET = 0.015D;

    private DissolvePixelPositionMapper() {
    }

    static Vec3d toWorldPos(PlayerEntity player, DissolveSkinMapper.BodyPoint point) {
        PartBox box = box(point.part());
        LocalPoint local = toLocalPoint(box, point.face(), point.u(), point.v());
        double scale = Math.max(0.5D, player.getHeight() / 1.8D);

        double yaw = Math.toRadians(player.bodyYaw);
        Vec3d playerRight = new Vec3d(Math.cos(yaw), 0.0D, Math.sin(yaw));
        Vec3d playerFront = new Vec3d(-Math.sin(yaw), 0.0D, Math.cos(yaw));

        return player.getPos()
                .add(playerRight.multiply(local.x() * scale))
                .add(0.0D, local.y() * scale, 0.0D)
                .add(playerFront.multiply(local.z() * scale));
    }

    private static PartBox box(DissolveSkinMapper.BodyPart part) {
        return switch (part) {
            case HEAD -> new PartBox(0.0D, 1.62D, 0.50D, 0.50D, 0.50D);
            case BODY -> new PartBox(0.0D, 1.10D, 0.50D, 0.75D, 0.25D);
            case LEFT_ARM -> new PartBox(0.375D, 1.10D, 0.25D, 0.75D, 0.25D);
            case RIGHT_ARM -> new PartBox(-0.375D, 1.10D, 0.25D, 0.75D, 0.25D);
            case LEFT_LEG -> new PartBox(0.125D, 0.38D, 0.25D, 0.75D, 0.25D);
            case RIGHT_LEG -> new PartBox(-0.125D, 0.38D, 0.25D, 0.75D, 0.25D);
        };
    }

    private static LocalPoint toLocalPoint(PartBox box, DissolveSkinMapper.Face face, float u, float v) {
        double halfWidth = box.width() * 0.5D;
        double halfHeight = box.height() * 0.5D;
        double halfDepth = box.depth() * 0.5D;
        double x;
        double y;
        double z;

        switch (face) {
            case FRONT -> {
                x = lerp(-halfWidth, halfWidth, u);
                y = lerp(halfHeight, -halfHeight, v);
                z = halfDepth + SURFACE_OFFSET;
            }
            case BACK -> {
                x = lerp(halfWidth, -halfWidth, u);
                y = lerp(halfHeight, -halfHeight, v);
                z = -halfDepth - SURFACE_OFFSET;
            }
            case LEFT -> {
                x = halfWidth + SURFACE_OFFSET;
                y = lerp(halfHeight, -halfHeight, v);
                z = lerp(halfDepth, -halfDepth, u);
            }
            case RIGHT -> {
                x = -halfWidth - SURFACE_OFFSET;
                y = lerp(halfHeight, -halfHeight, v);
                z = lerp(-halfDepth, halfDepth, u);
            }
            case TOP -> {
                x = lerp(-halfWidth, halfWidth, u);
                y = halfHeight + SURFACE_OFFSET;
                z = lerp(halfDepth, -halfDepth, v);
            }
            case BOTTOM -> {
                x = lerp(-halfWidth, halfWidth, u);
                y = -halfHeight - SURFACE_OFFSET;
                z = lerp(-halfDepth, halfDepth, v);
            }
            default -> throw new IllegalStateException("Unexpected face: " + face);
        }

        return new LocalPoint(box.centerX() + x, box.centerY() + y, z);
    }

    private static double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    private record PartBox(double centerX, double centerY, double width, double height, double depth) {
    }

    private record LocalPoint(double x, double y, double z) {
    }
}

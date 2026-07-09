package com.kuilunfuzhe.monvhua.features.gravity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class SurfaceGravityCollision {
    private SurfaceGravityCollision() {
    }

    public static Box boxAt(Entity entity, Direction downDirection, Vec3d pos) {
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        return boxAt(pos, downDirection, dimensions.width(), dimensions.height());
    }

    public static Box boxAt(Entity entity, Direction downDirection, Vec3d pos, EntityPose pose) {
        EntityDimensions dimensions = entity.getDimensions(pose);
        return boxAt(pos, downDirection, dimensions.width(), dimensions.height());
    }

    public static Vec3d eyePos(Entity entity, Direction downDirection, Vec3d pos) {
        Direction down = downDirection == null ? Direction.DOWN : downDirection;
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        double eye = dimensions.eyeHeight();
        return switch (down) {
            case DOWN -> new Vec3d(pos.x, pos.y + eye, pos.z);
            case UP -> new Vec3d(pos.x, pos.y - eye, pos.z);
            case NORTH -> new Vec3d(pos.x, pos.y, pos.z + eye);
            case SOUTH -> new Vec3d(pos.x, pos.y, pos.z - eye);
            case WEST -> new Vec3d(pos.x + eye, pos.y, pos.z);
            case EAST -> new Vec3d(pos.x - eye, pos.y, pos.z);
        };
    }

    public static Vec3d eyePosFromBox(Entity entity, Direction downDirection, Box box) {
        Direction down = downDirection == null ? Direction.DOWN : downDirection;
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        double eye = dimensions.eyeHeight();
        double x = (box.minX + box.maxX) * 0.5D;
        double y = (box.minY + box.maxY) * 0.5D;
        double z = (box.minZ + box.maxZ) * 0.5D;
        return switch (down) {
            case DOWN -> new Vec3d(x, box.minY + eye, z);
            case UP -> new Vec3d(x, box.maxY - eye, z);
            case NORTH -> new Vec3d(x, y, box.minZ + eye);
            case SOUTH -> new Vec3d(x, y, box.maxZ - eye);
            case WEST -> new Vec3d(box.minX + eye, y, z);
            case EAST -> new Vec3d(box.maxX - eye, y, z);
        };
    }

    public static Vec3d anchorFromBox(Direction downDirection, Box box) {
        Direction down = downDirection == null ? Direction.DOWN : downDirection;
        double x = (box.minX + box.maxX) * 0.5D;
        double y = (box.minY + box.maxY) * 0.5D;
        double z = (box.minZ + box.maxZ) * 0.5D;
        return switch (down) {
            case DOWN -> new Vec3d(x, box.minY, z);
            case UP -> new Vec3d(x, box.maxY, z);
            case NORTH -> new Vec3d(x, y, box.minZ);
            case SOUTH -> new Vec3d(x, y, box.maxZ);
            case WEST -> new Vec3d(box.minX, y, z);
            case EAST -> new Vec3d(box.maxX, y, z);
        };
    }

    public static void syncPositionToBox(Entity entity, Direction downDirection) {
        if (entity == null) {
            return;
        }
        Vec3d anchor = anchorFromBox(downDirection, entity.getBoundingBox());
        entity.setPos(anchor.x, anchor.y, anchor.z);
    }

    public static void setAnchorAndRefreshBox(Entity entity, Direction downDirection, Vec3d anchor) {
        if (entity == null || anchor == null) {
            return;
        }
        entity.setPos(anchor.x, anchor.y, anchor.z);
        refreshBox(entity, downDirection);
    }

    public static Vec3d footPosForEye(Entity entity, Direction downDirection, Vec3d eyePos) {
        Direction down = downDirection == null ? Direction.DOWN : downDirection;
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        double eye = dimensions.eyeHeight();
        return switch (down) {
            case DOWN -> new Vec3d(eyePos.x, eyePos.y - eye, eyePos.z);
            case UP -> new Vec3d(eyePos.x, eyePos.y + eye, eyePos.z);
            case NORTH -> new Vec3d(eyePos.x, eyePos.y, eyePos.z - eye);
            case SOUTH -> new Vec3d(eyePos.x, eyePos.y, eyePos.z + eye);
            case WEST -> new Vec3d(eyePos.x - eye, eyePos.y, eyePos.z);
            case EAST -> new Vec3d(eyePos.x + eye, eyePos.y, eyePos.z);
        };
    }

    public static void moveKeepingEye(Entity entity, Direction downDirection, Vec3d eyePos) {
        if (entity == null || eyePos == null) {
            return;
        }
        setAnchorAndRefreshBox(entity, downDirection, footPosForEye(entity, downDirection, eyePos));
    }

    public static void moveKeepingEyeOnSurface(Entity entity, Direction downDirection, Vec3d eyePos, Vec3d surfacePos) {
        if (entity == null || eyePos == null || surfacePos == null) {
            return;
        }
        Direction down = downDirection == null ? Direction.DOWN : downDirection;
        Vec3d anchor = footPosForEye(entity, down, eyePos);
        double gap = 0.002D;
        anchor = switch (down) {
            case DOWN -> new Vec3d(anchor.x, surfacePos.y + gap, anchor.z);
            case UP -> new Vec3d(anchor.x, surfacePos.y - gap, anchor.z);
            case NORTH -> new Vec3d(anchor.x, anchor.y, surfacePos.z + gap);
            case SOUTH -> new Vec3d(anchor.x, anchor.y, surfacePos.z - gap);
            case WEST -> new Vec3d(surfacePos.x + gap, anchor.y, anchor.z);
            case EAST -> new Vec3d(surfacePos.x - gap, anchor.y, anchor.z);
        };
        setAnchorAndRefreshBox(entity, down, anchor);
    }

    public static void refreshBox(Entity entity, Direction downDirection) {
        if (entity == null) {
            return;
        }
        entity.setBoundingBox(boxAt(entity, downDirection, entity.getPos()));
    }

    public static void restoreVanillaBox(Entity entity) {
        if (entity == null) {
            return;
        }
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        entity.setBoundingBox(dimensions.getBoxAt(entity.getPos()));
    }

    private static Box boxAt(Vec3d pos, Direction downDirection, float width, float height) {
        Direction down = downDirection == null ? Direction.DOWN : downDirection;
        double halfWidth = width * 0.5D;
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;

        return switch (down) {
            case DOWN -> new Box(x - halfWidth, y, z - halfWidth, x + halfWidth, y + height, z + halfWidth);
            case UP -> new Box(x - halfWidth, y - height, z - halfWidth, x + halfWidth, y, z + halfWidth);
            case NORTH -> new Box(x - halfWidth, y - halfWidth, z, x + halfWidth, y + halfWidth, z + height);
            case SOUTH -> new Box(x - halfWidth, y - halfWidth, z - height, x + halfWidth, y + halfWidth, z);
            case WEST -> new Box(x, y - halfWidth, z - halfWidth, x + height, y + halfWidth, z + halfWidth);
            case EAST -> new Box(x - height, y - halfWidth, z - halfWidth, x, y + halfWidth, z + halfWidth);
        };
    }
}

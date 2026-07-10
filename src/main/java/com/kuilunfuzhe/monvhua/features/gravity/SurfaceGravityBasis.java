package com.kuilunfuzhe.monvhua.features.gravity;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class SurfaceGravityBasis {
    private SurfaceGravityBasis() {
    }

    public static Basis of(Direction downDirection) {
        Direction down = downDirection == null ? Direction.DOWN : downDirection;
        Vec3d downVec = directionVector(down);
        Vec3d upVec = downVec.multiply(-1.0D);
        Vec3d seedForward = switch (down) {
            case DOWN -> new Vec3d(0.0D, 0.0D, 1.0D);
            case UP -> new Vec3d(0.0D, 0.0D, -1.0D);
            case NORTH, SOUTH, EAST, WEST -> new Vec3d(0.0D, 1.0D, 0.0D);
        };
        Vec3d forward = reject(seedForward, upVec);
        if (forward.lengthSquared() < 1.0E-8D) {
            forward = reject(new Vec3d(0.0D, 0.0D, 1.0D), upVec);
        }
        forward = forward.normalize();
        Vec3d right = downVec.crossProduct(forward).normalize();
        return new Basis(down, downVec, upVec, forward, right);
    }

    public static Vec3d directionVector(Direction direction) {
        return new Vec3d(direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ());
    }

    public static Direction dominantDirection(Vec3d vector) {
        if (vector == null || vector.lengthSquared() < 1.0E-8D) {
            return Direction.DOWN;
        }
        double absX = Math.abs(vector.x);
        double absY = Math.abs(vector.y);
        double absZ = Math.abs(vector.z);
        if (absX >= absY && absX >= absZ) {
            return vector.x >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        if (absY >= absX && absY >= absZ) {
            return vector.y >= 0.0D ? Direction.UP : Direction.DOWN;
        }
        return vector.z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    public static Vec3d eyePos(Entity entity, Direction downDirection, double baseX, double baseY, double baseZ) {
        return SurfaceGravityCollision.eyePos(entity, downDirection, new Vec3d(baseX, baseY, baseZ));
    }

    public static Vec3d look(Direction downDirection, float localYaw, float localPitch) {
        Basis basis = of(downDirection);
        double pitchRad = Math.toRadians(localPitch);
        double yawRad = Math.toRadians(localYaw);
        double cosPitch = Math.cos(pitchRad);
        Vec3d look = basis.forward().multiply(Math.cos(yawRad) * cosPitch)
                .add(basis.right().multiply(Math.sin(yawRad) * cosPitch))
                .add(basis.up().multiply(-Math.sin(pitchRad)));
        return look.lengthSquared() < 1.0E-8D ? basis.forward() : look.normalize();
    }

    public static LocalView localView(Direction downDirection, Vec3d worldLook) {
        Basis basis = of(downDirection);
        Vec3d look = worldLook == null || worldLook.lengthSquared() < 1.0E-8D ? basis.forward() : worldLook.normalize();
        double upAmount = Math.clamp(look.dotProduct(basis.up()), -1.0D, 1.0D);
        float pitch = (float) Math.toDegrees(-Math.asin(upAmount));
        Vec3d horizontal = reject(look, basis.up());
        if (horizontal.lengthSquared() < 0.0625D) {
            return new LocalView(0.0F, 0.0F);
        } else {
            horizontal = horizontal.normalize();
        }
        float yaw = (float) Math.toDegrees(Math.atan2(horizontal.dotProduct(basis.right()), horizontal.dotProduct(basis.forward())));
        return new LocalView(yaw, Math.clamp(pitch, -89.0F, 89.0F));
    }

    public static Vec3d movementForward(Direction downDirection, float localYaw) {
        Basis basis = of(downDirection);
        double yawRad = Math.toRadians(localYaw);
        Vec3d forward = basis.forward().multiply(Math.cos(yawRad)).add(basis.right().multiply(Math.sin(yawRad)));
        return forward.lengthSquared() < 1.0E-8D ? basis.forward() : forward.normalize();
    }

    public static Vec3d movementRight(Direction downDirection, float localYaw) {
        Basis basis = of(downDirection);
        Vec3d forward = movementForward(downDirection, localYaw);
        Vec3d right = basis.down().crossProduct(forward);
        return right.lengthSquared() < 1.0E-8D ? basis.right() : right.normalize();
    }

    public static Quaternionf cameraRotation(Direction downDirection, float localYaw, float localPitch) {
        Basis basis = of(downDirection);
        Vec3d look = look(downDirection, localYaw, localPitch);
        Vec3d right = basis.down().crossProduct(look);
        if (right.lengthSquared() < 1.0E-8D) {
            right = basis.right();
        } else {
            right = right.normalize();
        }
        Vec3d cameraUp = right.crossProduct(look).normalize();
        Vec3d left = right.multiply(-1.0D);
        Matrix4f matrix = new Matrix4f(
                (float) -left.x, (float) -left.y, (float) -left.z, 0.0F,
                (float) cameraUp.x, (float) cameraUp.y, (float) cameraUp.z, 0.0F,
                (float) -look.x, (float) -look.y, (float) -look.z, 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F
        );
        return matrix.getUnnormalizedRotation(new Quaternionf()).normalize();
    }

    public static Quaternionf modelRotation(Direction downDirection) {
        Basis basis = of(downDirection);
        Vec3d xAxis = basis.right().multiply(-1.0D);
        Vec3d up = basis.up();
        Vec3d forward = basis.forward();
        Matrix4f matrix = new Matrix4f(
                (float) xAxis.x, (float) xAxis.y, (float) xAxis.z, 0.0F,
                (float) up.x, (float) up.y, (float) up.z, 0.0F,
                (float) forward.x, (float) forward.y, (float) forward.z, 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F
        );
        return matrix.getUnnormalizedRotation(new Quaternionf()).normalize();
    }

    public static Vec3d reject(Vec3d vector, Vec3d axis) {
        return vector.subtract(axis.multiply(vector.dotProduct(axis)));
    }

    public record Basis(Direction downDirection, Vec3d down, Vec3d up, Vec3d forward, Vec3d right) {
    }

    public record LocalView(float yaw, float pitch) {
    }
}

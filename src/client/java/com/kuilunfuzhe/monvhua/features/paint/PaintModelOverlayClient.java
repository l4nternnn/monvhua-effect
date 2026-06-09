package com.kuilunfuzhe.monvhua.features.paint;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector4d;

public final class PaintModelOverlayClient {
    private static final double MAX_DISTANCE = 6.0D;
    private static final SurfaceBox[] COMBINED_BODY_SURFACES = new SurfaceBox[]{
            box("head", -4, -8, -4, 8, 8, 8),
            box("body_upper", -4, 0, -2, 8, 6, 4),
            box("body_lower", -4, 6, -2, 8, 6, 4),
            box("left_arm_upper", -1, -2, -2, 4, 6, 4, 5, 2, 0),
            box("left_arm_lower", -1, 4, -2, 4, 6, 4, 5, 2, 0),
            box("right_arm_upper", -3, -2, -2, 4, 6, 4, -5, 2, 0),
            box("right_arm_lower", -3, 4, -2, 4, 6, 4, -5, 2, 0),
            box("left_leg_upper", -2, 0, -2, 4, 6, 4, 1.9, 12, 0),
            box("left_leg_lower", -2, 6, -2, 4, 6, 4, 1.9, 12, 0),
            box("right_leg_upper", -2, 0, -2, 4, 6, 4, -1.9, 12, 0),
            box("right_leg_lower", -2, 6, -2, 4, 6, 4, -1.9, 12, 0)
    };

    private PaintModelOverlayClient() {
    }

    public static ModelHit raycastCombinedBody(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return null;
        }
        Vec3d eye = client.player.getEyePos();
        Vec3d end = eye.add(client.player.getRotationVec(1.0F).multiply(MAX_DISTANCE));
        ModelHit best = null;
        double bestDistance = MAX_DISTANCE;

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof ItemDisplayEntity display) || !isCombinedBody(display.getItemStack())) {
                continue;
            }
            if (display.getPos().squaredDistanceTo(eye) > MAX_DISTANCE * MAX_DISTANCE + 4.0D) {
                continue;
            }
            ModelHit hit = raycastDisplay(display, eye, end);
            if (hit != null && hit.distance() < bestDistance) {
                best = hit;
                bestDistance = hit.distance();
            }
        }
        return best;
    }

    private static ModelHit raycastDisplay(ItemDisplayEntity display, Vec3d eye, Vec3d end) {
        Matrix4d worldToModel = modelToWorld(display).invert(new Matrix4d());
        Vector3d localStart = transformPosition(worldToModel, eye);
        Vector3d localEnd = transformPosition(worldToModel, end);
        Vector3d localDir = new Vector3d(localEnd).sub(localStart);
        ModelHit best = null;
        double bestT = Double.POSITIVE_INFINITY;

        for (SurfaceBox surface : COMBINED_BODY_SURFACES) {
            BoxHit hit = intersect(localStart, localDir, surface);
            if (hit != null && hit.t() >= 0.0D && hit.t() <= 1.0D && hit.t() < bestT) {
                int[] pixel = pixel(surface, hit.point(), hit.face());
                Vector3d worldPoint = transformPosition(modelToWorld(display), hit.point());
                best = new ModelHit(display.getId(), surface.name(), hit.face(), pixel[0], pixel[1],
                        Math.sqrt(squaredDistance(eye, worldPoint)));
                bestT = hit.t();
            }
        }
        return best;
    }

    private static double squaredDistance(Vec3d a, Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static Matrix4d modelToWorld(ItemDisplayEntity display) {
        NbtCompound data = customData(display.getItemStack());
        Matrix4d matrix = new Matrix4d().identity()
                .translate(display.getX(), display.getY(), display.getZ())
                .translate(
                        data.getFloat("pose_model_offset_x", 0.0F),
                        data.getFloat("pose_model_offset_y", 0.0F),
                        data.getFloat("pose_model_offset_z", 0.0F))
                .rotateX(Math.toRadians(data.getFloat("pose_model_pitch", 0.0F)))
                .rotateY(Math.toRadians(-data.getFloat("pose_model_yaw", 0.0F)))
                .rotateZ(Math.toRadians(data.getFloat("pose_model_roll", 0.0F)));
        float scale = Math.max(0.1F, data.getFloat("pose_model_scale", 1.0F));
        matrix.scale(scale);
        return matrix;
    }

    private static NbtCompound customData(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        return component == null ? new NbtCompound() : component.copyNbt();
    }

    private static boolean isCombinedBody(ItemStack stack) {
        Identifier model = stack.get(DataComponentTypes.ITEM_MODEL);
        return model != null && model.equals(Identifier.of("monvhua", "combined_body"));
    }

    private static Vector3d transformPosition(Matrix4d matrix, Vec3d pos) {
        return transformPosition(matrix, new Vector3d(pos.x, pos.y, pos.z));
    }

    private static Vector3d transformPosition(Matrix4d matrix, Vector3d pos) {
        Vector4d result = matrix.transform(new Vector4d(pos.x, pos.y, pos.z, 1.0D));
        return new Vector3d(result.x, result.y, result.z);
    }

    private static BoxHit intersect(Vector3d start, Vector3d dir, SurfaceBox box) {
        double tMin = 0.0D;
        double tMax = 1.0D;
        Direction enterFace = Direction.SOUTH;

        AxisHit x = axis(start.x, dir.x, box.minX(), box.maxX(), Direction.WEST, Direction.EAST);
        if (x == null) return null;
        if (x.tMin() > tMin) {
            tMin = x.tMin();
            enterFace = x.face();
        }
        tMax = Math.min(tMax, x.tMax());

        AxisHit y = axis(start.y, dir.y, box.minY(), box.maxY(), Direction.DOWN, Direction.UP);
        if (y == null) return null;
        if (y.tMin() > tMin) {
            tMin = y.tMin();
            enterFace = y.face();
        }
        tMax = Math.min(tMax, y.tMax());

        AxisHit z = axis(start.z, dir.z, box.minZ(), box.maxZ(), Direction.NORTH, Direction.SOUTH);
        if (z == null) return null;
        if (z.tMin() > tMin) {
            tMin = z.tMin();
            enterFace = z.face();
        }
        tMax = Math.min(tMax, z.tMax());

        if (tMin > tMax) {
            return null;
        }
        return new BoxHit(tMin, new Vector3d(start).fma(tMin, dir), enterFace);
    }

    private static AxisHit axis(double start, double dir, double min, double max, Direction minFace, Direction maxFace) {
        if (Math.abs(dir) < 1.0E-7D) {
            return start >= min && start <= max ? new AxisHit(0.0D, 1.0D, minFace) : null;
        }
        double inv = 1.0D / dir;
        double t1 = (min - start) * inv;
        double t2 = (max - start) * inv;
        if (t1 <= t2) {
            return new AxisHit(t1, t2, minFace);
        }
        return new AxisHit(t2, t1, maxFace);
    }

    private static int[] pixel(SurfaceBox box, Vector3d point, Direction face) {
        double u;
        double v;
        switch (face) {
            case UP, DOWN -> {
                u = (point.x - box.minX()) / Math.max(0.0001D, box.maxX() - box.minX());
                v = (point.z - box.minZ()) / Math.max(0.0001D, box.maxZ() - box.minZ());
            }
            case NORTH, SOUTH -> {
                u = (point.x - box.minX()) / Math.max(0.0001D, box.maxX() - box.minX());
                v = 1.0D - (point.y - box.minY()) / Math.max(0.0001D, box.maxY() - box.minY());
            }
            case WEST, EAST -> {
                u = (point.z - box.minZ()) / Math.max(0.0001D, box.maxZ() - box.minZ());
                v = 1.0D - (point.y - box.minY()) / Math.max(0.0001D, box.maxY() - box.minY());
            }
            default -> {
                u = 0.5D;
                v = 0.5D;
            }
        }
        return new int[]{
                MathHelper.clamp((int) (u * ModelPaintData.SIZE), 0, ModelPaintData.SIZE - 1),
                MathHelper.clamp((int) (v * ModelPaintData.SIZE), 0, ModelPaintData.SIZE - 1)
        };
    }

    private static SurfaceBox box(String name, double x, double y, double z, double width, double height, double depth) {
        return box(name, x, y, z, width, height, depth, 0.0D, 0.0D, 0.0D);
    }

    private static SurfaceBox box(String name, double x, double y, double z, double width, double height, double depth,
                                  double originX, double originY, double originZ) {
        double minX = (originX + x) / 16.0D;
        double maxX = (originX + x + width) / 16.0D;
        double minY = -(originY + y + height) / 16.0D;
        double maxY = -(originY + y) / 16.0D;
        double minZ = (originZ + z) / 16.0D;
        double maxZ = (originZ + z + depth) / 16.0D;
        return new SurfaceBox(name, minX, minY, minZ, maxX, maxY, maxZ);
    }

    public record ModelHit(int entityId, String surface, Direction face, int x, int y, double distance) {
    }

    private record SurfaceBox(String name, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    }

    private record BoxHit(double t, Vector3d point, Direction face) {
    }

    private record AxisHit(double tMin, double tMax, Direction face) {
    }
}

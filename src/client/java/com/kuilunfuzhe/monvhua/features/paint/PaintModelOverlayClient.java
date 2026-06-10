package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.model.CombinedBodyModelData;
import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.renderer.body.special.CombinedBodySpecialModelRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector4d;

public final class PaintModelOverlayClient {
    private static final double MAX_DISTANCE = 6.0D;
    private static final double TRIANGLE_EPSILON = 1.0E-7D;
    private static final SurfaceDef[] COMBINED_BODY_SURFACES = new SurfaceDef[]{
            def("head", new Part[]{Part.HEAD}, cuboid(-4, -8, -4, 8, 8, 8)),
            def("body_upper", new Part[]{Part.BODY}, cuboid(-4, 0, -2, 8, 6, 4)),
            def("body_lower", new Part[]{Part.BODY, Part.WAIST}, cuboid(-4, 0, -2, 8, 6, 4)),
            def("left_arm_upper", new Part[]{Part.LEFT_ARM}, cuboid(-1, -2, -2, 4, 6, 4), cuboid(-1, -2, -2, 3, 6, 4)),
            def("left_arm_lower", new Part[]{Part.LEFT_ARM, Part.LEFT_FOREARM}, cuboid(-1, 0, -2, 4, 6, 4), cuboid(-1, 0, -2, 3, 6, 4)),
            def("right_arm_upper", new Part[]{Part.RIGHT_ARM}, cuboid(-3, -2, -2, 4, 6, 4), cuboid(-2, -2, -2, 3, 6, 4)),
            def("right_arm_lower", new Part[]{Part.RIGHT_ARM, Part.RIGHT_FOREARM}, cuboid(-3, 0, -2, 4, 6, 4), cuboid(-2, 0, -2, 3, 6, 4)),
            def("left_leg_upper", new Part[]{Part.LEFT_LEG}, cuboid(-2, 0, -2, 4, 6, 4)),
            def("left_leg_lower", new Part[]{Part.LEFT_LEG, Part.LEFT_LOWER_LEG}, cuboid(-2, 0, -2, 4, 6, 4)),
            def("right_leg_upper", new Part[]{Part.RIGHT_LEG}, cuboid(-2, 0, -2, 4, 6, 4)),
            def("right_leg_lower", new Part[]{Part.RIGHT_LEG, Part.RIGHT_LOWER_LEG}, cuboid(-2, 0, -2, 4, 6, 4))
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
            ModelHit hit = raycastDisplay(client, display, eye, end);
            if (hit != null && hit.distance() < bestDistance) {
                best = hit;
                bestDistance = hit.distance();
            }
        }
        return best;
    }

    private static ModelHit raycastDisplay(MinecraftClient client, ItemDisplayEntity display, Vec3d eye, Vec3d end) {
        NbtCompound data = customData(display.getItemStack());
        boolean slim = "slim".equals(data.getString("arm_model", ""));
        PlayerEntityModel model = createModel(client, slim);
        if (model == null) {
            return null;
        }
        CombinedBodySpecialModelRenderer.resetModel(model);
        CombinedBodySpecialModelRenderer.applyPoseData(model, data);
        Matrix4d rootMatrix = rootModelToWorld(display, data);
        ModelHit best = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (SurfaceDef surface : COMBINED_BODY_SURFACES) {
            Matrix4d surfaceMatrix = surfaceMatrix(rootMatrix, model, surface);
            if (surfaceMatrix == null) {
                continue;
            }
            Matrix4d worldToSurface = surfaceMatrix.invert(new Matrix4d());
            Vector3d localStart = transformPosition(worldToSurface, eye);
            Vector3d localEnd = transformPosition(worldToSurface, end);
            Vector3d localDir = new Vector3d(localEnd).sub(localStart);
            Cuboid cuboid = surface.cuboid(slim);
            BoxHit hit = intersectFaces(localStart, localDir, cuboid);
            if (hit != null && hit.t() >= 0.0D && hit.t() <= 1.0D) {
                Vector3d worldPoint = transformPosition(surfaceMatrix, hit.point());
                double distance = Math.sqrt(squaredDistance(eye, worldPoint));
                if (distance >= bestDistance) {
                    continue;
                }
                Direction paintFace = hit.face();
                String paintSurface = surface.name();
                ModelPaintData.FaceSize size = ModelPaintData.size(paintSurface, paintFace, slim);
                int[] pixel = pixel(cuboid, hit.point(), hit.face(), size.width(), size.height());
                best = new ModelHit(display.getId(), paintSurface, paintFace, pixel[0], pixel[1],
                        distance);
                bestDistance = distance;
            }
        }
        return best;
    }

    private static PlayerEntityModel createModel(MinecraftClient client, boolean slim) {
        if (client.getLoadedEntityModels() == null) {
            return null;
        }
        return new PlayerEntityModel(client.getLoadedEntityModels().getModelPart(
                slim ? ModModelLayers.COMBINED_BODY_SLIM : ModModelLayers.COMBINED_BODY), slim);
    }

    private static Matrix4d surfaceMatrix(Matrix4d rootMatrix, PlayerEntityModel model, SurfaceDef surface) {
        Matrix4d matrix = new Matrix4d(rootMatrix);
        applyModelPart(matrix, model.getRootPart());
        for (Part part : surface.path()) {
            ModelPart modelPart = modelPart(model, part);
            if (modelPart == null) {
                return null;
            }
            applyModelPart(matrix, modelPart);
        }
        return matrix;
    }

    private static void applyModelPart(Matrix4d matrix, ModelPart part) {
        matrix.translate(part.originX / 16.0D, part.originY / 16.0D, part.originZ / 16.0D)
                .rotateZ(part.roll)
                .rotateY(part.yaw)
                .rotateX(part.pitch)
                .scale(part.xScale, part.yScale, part.zScale);
    }

    private static ModelPart modelPart(PlayerEntityModel model, Part part) {
        return switch (part) {
            case HEAD -> model.head;
            case BODY -> model.body;
            case WAIST -> getChild(model.body, CombinedBodyModelData.WAIST);
            case LEFT_ARM -> model.leftArm;
            case LEFT_FOREARM -> getChild(model.leftArm, CombinedBodyModelData.LEFT_FOREARM);
            case RIGHT_ARM -> model.rightArm;
            case RIGHT_FOREARM -> getChild(model.rightArm, CombinedBodyModelData.RIGHT_FOREARM);
            case LEFT_LEG -> model.leftLeg;
            case LEFT_LOWER_LEG -> getChild(model.leftLeg, CombinedBodyModelData.LEFT_LOWER_LEG);
            case RIGHT_LEG -> model.rightLeg;
            case RIGHT_LOWER_LEG -> getChild(model.rightLeg, CombinedBodyModelData.RIGHT_LOWER_LEG);
        };
    }

    private static ModelPart getChild(ModelPart part, String childName) {
        return part != null && part.hasChild(childName) ? part.getChild(childName) : null;
    }

    private static double squaredDistance(Vec3d a, Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static Matrix4d rootModelToWorld(ItemDisplayEntity display, NbtCompound data) {
        Matrix4d matrix = new Matrix4d().identity()
                .translate(display.getX(), display.getY(), display.getZ());
        DisplayEntity.RenderState renderState = display.getRenderState();
        if (renderState != null) {
            AffineTransformation transformation = renderState.transformation().interpolate(1.0F);
            matrix.mul(new Matrix4d(transformation.getMatrix()));
        }
        matrix.rotateY(Math.PI);
        applyItemModelTransform(matrix, itemDisplayContext(display));
        matrix.translate(0.5D, 0.0D, 0.5D)
                .rotateY(Math.PI)
                .scale(-1.0D, -1.0D, 1.0D)
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

    private static ItemDisplayContext itemDisplayContext(ItemDisplayEntity display) {
        DisplayEntity.ItemDisplayEntity.Data data = display.getData();
        return data == null ? ItemDisplayContext.NONE : data.itemTransform();
    }

    private static void applyItemModelTransform(Matrix4d matrix, ItemDisplayContext context) {
        switch (context) {
            case GUI -> applyItemModelTransform(matrix, 30.0D, 45.0D, 0.0D, 0.0D, 3.0D, 0.0D, 1.0D);
            case FIXED -> applyItemModelTransform(matrix, 0.0D, 180.0D, 0.0D, 0.0D, 4.0D, 0.0D, 1.0D);
            case GROUND -> applyItemModelTransform(matrix, 0.0D, 0.0D, 0.0D, 0.0D, 3.0D, 0.0D, 0.5D);
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND ->
                    applyItemModelTransform(matrix, 45.0D, 45.0D, 0.0D, 0.0D, 3.0D, 0.0D, 0.5D, context.isLeftHand());
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND ->
                    applyItemModelTransform(matrix, 0.0D, 45.0D, 0.0D, 0.0D, 3.0D, 0.0D, 0.5D, context.isLeftHand());
            default -> matrix.translate(-0.5D, -0.5D, -0.5D);
        }
    }

    private static void applyItemModelTransform(Matrix4d matrix,
                                                double pitch, double yaw, double roll,
                                                double x, double y, double z,
                                                double scale) {
        applyItemModelTransform(matrix, pitch, yaw, roll, x, y, z, scale, false);
    }

    private static void applyItemModelTransform(Matrix4d matrix,
                                                double pitch, double yaw, double roll,
                                                double x, double y, double z,
                                                double scale, boolean leftHand) {
        double tx = leftHand ? -x : x;
        double ry = leftHand ? -yaw : yaw;
        double rz = leftHand ? -roll : roll;
        matrix.translate(tx / 16.0D, y / 16.0D, z / 16.0D)
                .rotateX(Math.toRadians(pitch))
                .rotateY(Math.toRadians(ry))
                .rotateZ(Math.toRadians(rz))
                .scale(scale, scale, scale)
                .translate(-0.5D, -0.5D, -0.5D);
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

    private static BoxHit intersectFaces(Vector3d start, Vector3d dir, Cuboid box) {
        BoxHit best = null;
        for (Direction face : Direction.values()) {
            Vector3d[] quad = quad(box, face);
            best = nearer(best, intersectTriangle(start, dir, face, quad[0], quad[1], quad[2]));
            best = nearer(best, intersectTriangle(start, dir, face, quad[0], quad[2], quad[3]));
        }
        return best;
    }

    private static BoxHit nearer(BoxHit current, BoxHit candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.t() < current.t() ? candidate : current;
    }

    private static BoxHit intersectTriangle(Vector3d start, Vector3d dir, Direction face,
                                            Vector3d v0, Vector3d v1, Vector3d v2) {
        Vector3d edge1 = new Vector3d(v1).sub(v0);
        Vector3d edge2 = new Vector3d(v2).sub(v0);
        Vector3d pvec = new Vector3d(dir).cross(edge2);
        double determinant = edge1.dot(pvec);
        if (Math.abs(determinant) < TRIANGLE_EPSILON) {
            return null;
        }

        double inverseDeterminant = 1.0D / determinant;
        Vector3d tvec = new Vector3d(start).sub(v0);
        double u = tvec.dot(pvec) * inverseDeterminant;
        if (u < -TRIANGLE_EPSILON || u > 1.0D + TRIANGLE_EPSILON) {
            return null;
        }

        Vector3d qvec = new Vector3d(tvec).cross(edge1);
        double v = dir.dot(qvec) * inverseDeterminant;
        if (v < -TRIANGLE_EPSILON || u + v > 1.0D + TRIANGLE_EPSILON) {
            return null;
        }

        double t = edge2.dot(qvec) * inverseDeterminant;
        if (t < -TRIANGLE_EPSILON || t > 1.0D + TRIANGLE_EPSILON) {
            return null;
        }
        double clampedT = MathHelper.clamp(t, 0.0D, 1.0D);
        return new BoxHit(clampedT, new Vector3d(start).fma(clampedT, dir), face);
    }

    private static Vector3d[] quad(Cuboid box, Direction face) {
        return switch (face) {
            case UP -> new Vector3d[]{
                    new Vector3d(box.minX(), box.minY(), box.minZ()),
                    new Vector3d(box.maxX(), box.minY(), box.minZ()),
                    new Vector3d(box.maxX(), box.minY(), box.maxZ()),
                    new Vector3d(box.minX(), box.minY(), box.maxZ())
            };
            case DOWN -> new Vector3d[]{
                    new Vector3d(box.minX(), box.maxY(), box.maxZ()),
                    new Vector3d(box.maxX(), box.maxY(), box.maxZ()),
                    new Vector3d(box.maxX(), box.maxY(), box.minZ()),
                    new Vector3d(box.minX(), box.maxY(), box.minZ())
            };
            case NORTH -> new Vector3d[]{
                    new Vector3d(box.minX(), box.minY(), box.minZ()),
                    new Vector3d(box.maxX(), box.minY(), box.minZ()),
                    new Vector3d(box.maxX(), box.maxY(), box.minZ()),
                    new Vector3d(box.minX(), box.maxY(), box.minZ())
            };
            case SOUTH -> new Vector3d[]{
                    new Vector3d(box.maxX(), box.minY(), box.maxZ()),
                    new Vector3d(box.minX(), box.minY(), box.maxZ()),
                    new Vector3d(box.minX(), box.maxY(), box.maxZ()),
                    new Vector3d(box.maxX(), box.maxY(), box.maxZ())
            };
            case WEST -> new Vector3d[]{
                    new Vector3d(box.minX(), box.minY(), box.maxZ()),
                    new Vector3d(box.minX(), box.minY(), box.minZ()),
                    new Vector3d(box.minX(), box.maxY(), box.minZ()),
                    new Vector3d(box.minX(), box.maxY(), box.maxZ())
            };
            case EAST -> new Vector3d[]{
                    new Vector3d(box.maxX(), box.minY(), box.minZ()),
                    new Vector3d(box.maxX(), box.minY(), box.maxZ()),
                    new Vector3d(box.maxX(), box.maxY(), box.maxZ()),
                    new Vector3d(box.maxX(), box.maxY(), box.minZ())
            };
        };
    }

    private static int[] pixel(Cuboid box, Vector3d point, Direction face, int width, int height) {
        double u;
        double v;
        switch (face) {
            case UP, DOWN -> {
                u = (point.x - box.minX()) / Math.max(0.0001D, box.maxX() - box.minX());
                v = (point.z - box.minZ()) / Math.max(0.0001D, box.maxZ() - box.minZ());
            }
            case NORTH, SOUTH -> {
                u = (point.x - box.minX()) / Math.max(0.0001D, box.maxX() - box.minX());
                v = (point.y - box.minY()) / Math.max(0.0001D, box.maxY() - box.minY());
            }
            case WEST, EAST -> {
                u = (point.z - box.minZ()) / Math.max(0.0001D, box.maxZ() - box.minZ());
                v = (point.y - box.minY()) / Math.max(0.0001D, box.maxY() - box.minY());
            }
            default -> {
                u = 0.5D;
                v = 0.5D;
            }
        }
        return new int[]{
                MathHelper.clamp((int) (u * width), 0, width - 1),
                MathHelper.clamp((int) (v * height), 0, height - 1)
        };
    }

    private static SurfaceDef def(String name, Part[] path, Cuboid cuboid) {
        return new SurfaceDef(name, path, cuboid, cuboid);
    }

    private static SurfaceDef def(String name, Part[] path, Cuboid defaultCuboid, Cuboid slimCuboid) {
        return new SurfaceDef(name, path, defaultCuboid, slimCuboid);
    }

    private static Cuboid cuboid(double x, double y, double z, double width, double height, double depth) {
        return new Cuboid(x / 16.0D, y / 16.0D, z / 16.0D,
                (x + width) / 16.0D, (y + height) / 16.0D, (z + depth) / 16.0D);
    }

    public record ModelHit(int entityId, String surface, Direction face, int x, int y, double distance) {
    }

    private record SurfaceDef(String name, Part[] path, Cuboid defaultCuboid, Cuboid slimCuboid) {
        private Cuboid cuboid(boolean slim) {
            return slim ? slimCuboid : defaultCuboid;
        }
    }

    private enum Part {
        HEAD,
        BODY,
        WAIST,
        LEFT_ARM,
        LEFT_FOREARM,
        RIGHT_ARM,
        RIGHT_FOREARM,
        LEFT_LEG,
        LEFT_LOWER_LEG,
        RIGHT_LEG,
        RIGHT_LOWER_LEG
    }

    private record Cuboid(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    }

    private record BoxHit(double t, Vector3d point, Direction face) {
    }

}

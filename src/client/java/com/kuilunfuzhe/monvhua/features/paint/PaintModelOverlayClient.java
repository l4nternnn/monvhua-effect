package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.model.CombinedBodyModelData;
import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.renderer.body.special.CombinedBodySpecialModelRenderer;
import com.kuilunfuzhe.monvhua.renderer.bodypose.skeletal.BodyPoseSkeletalPreviewRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.ProfileComponent;
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
    private static final double DEFAULT_MAX_DISTANCE = 6.0D;
    private static final double TRIANGLE_EPSILON = 1.0E-7D;
    private static final double OUTER_PIXEL_DEPTH = 0.5D;
    private static final OuterSurfaceDef[] OUTER_EDGE_SURFACES = new OuterSurfaceDef[]{
            outer("head", new Part[]{Part.HEAD, Part.HAT}, boxPixels(-4, -8, -4, 8, 8, 8), 32, 0, 0, true, true),
            outer("body_upper", new Part[]{Part.BODY, Part.JACKET}, boxPixels(-4, 0, -2, 8, 6, 4), 16, 32, 0, true, false),
            outer("body_lower", new Part[]{Part.BODY, Part.WAIST, Part.WAIST_JACKET}, boxPixels(-4, 0, -2, 8, 6, 4), 16, 32, 6, false, true),
            outer("left_arm_upper", new Part[]{Part.LEFT_ARM, Part.LEFT_SLEEVE}, boxPixels(-1, -2, -2, 4, 6, 4), boxPixels(-1, -2, -2, 3, 6, 4), 48, 48, 0, true, false),
            outer("left_arm_lower", new Part[]{Part.LEFT_ARM, Part.LEFT_FOREARM, Part.LEFT_FOREARM_SLEEVE}, boxPixels(-1, 0, -2, 4, 6, 4), boxPixels(-1, 0, -2, 3, 6, 4), 48, 48, 6, false, true),
            outer("right_arm_upper", new Part[]{Part.RIGHT_ARM, Part.RIGHT_SLEEVE}, boxPixels(-3, -2, -2, 4, 6, 4), boxPixels(-2, -2, -2, 3, 6, 4), 40, 32, 0, true, false),
            outer("right_arm_lower", new Part[]{Part.RIGHT_ARM, Part.RIGHT_FOREARM, Part.RIGHT_FOREARM_SLEEVE}, boxPixels(-3, 0, -2, 4, 6, 4), boxPixels(-2, 0, -2, 3, 6, 4), 40, 32, 6, false, true),
            outer("left_leg_upper", new Part[]{Part.LEFT_LEG, Part.LEFT_PANTS}, boxPixels(-2, 0, -2, 4, 6, 4), 0, 48, 0, true, false),
            outer("left_leg_lower", new Part[]{Part.LEFT_LEG, Part.LEFT_LOWER_LEG, Part.LEFT_LOWER_PANTS}, boxPixels(-2, 0, -2, 4, 6, 4), 0, 48, 6, false, true),
            outer("right_leg_upper", new Part[]{Part.RIGHT_LEG, Part.RIGHT_PANTS}, boxPixels(-2, 0, -2, 4, 6, 4), 0, 32, 0, true, false),
            outer("right_leg_lower", new Part[]{Part.RIGHT_LEG, Part.RIGHT_LOWER_LEG, Part.RIGHT_LOWER_PANTS}, boxPixels(-2, 0, -2, 4, 6, 4), 0, 32, 6, false, true)
    };
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
        Vec3d end = eye.add(client.player.getRotationVec(1.0F).multiply(DEFAULT_MAX_DISTANCE));
        return raycastCombinedBody(client, eye, end);
    }

    public static ModelHit raycastCombinedBody(MinecraftClient client, Vec3d eye, Vec3d end) {
        if (client.world == null || client.player == null) {
            return null;
        }
        double maxDistance = Math.max(DEFAULT_MAX_DISTANCE, eye.distanceTo(end));
        ModelHit best = null;
        double bestDistance = maxDistance;

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof ItemDisplayEntity display) || !isCombinedBody(display.getItemStack())) {
                continue;
            }
            if (display.getPos().squaredDistanceTo(eye) > maxDistance * maxDistance + 4.0D) {
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

    public static int colorAt(MinecraftClient client, ModelHit hit) {
        if (client.world == null || hit == null) {
            return 0;
        }
        Entity entity = client.world.getEntityById(hit.entityId());
        if (!(entity instanceof ItemDisplayEntity display) || !isCombinedBody(display.getItemStack())) {
            return 0;
        }
        NbtCompound data = customData(display.getItemStack());
        boolean slim = "slim".equals(data.getString("arm_model", ""));
        for (ModelPaintData.ModelFace face : ModelPaintData.readFaces(data, slim)) {
            if (!face.surface().equals(hit.surface()) || face.face() != hit.face()) {
                continue;
            }
            if (hit.x() < 0 || hit.y() < 0 || hit.x() >= face.width() || hit.y() >= face.height()) {
                return 0;
            }
            return face.pixels()[hit.y() * face.width() + hit.x()];
        }
        return 0;
    }

    private static ModelHit raycastDisplay(MinecraftClient client, ItemDisplayEntity display, Vec3d eye, Vec3d end) {
        NbtCompound data = customData(display.getItemStack());
        boolean slim = "slim".equals(data.getString("arm_model", ""));
        if ("true_skeletal".equals(data.getString("body_pose_mode", ""))) {
            BodyPoseSkeletalPreviewRenderer.PaintHit trueSkeletalHit = BodyPoseSkeletalPreviewRenderer.raycastModelPaint(
                    trueSkeletalRootModelToWorld(display, data), eye, end, data, slim);
            if (trueSkeletalHit != null) {
                return new ModelHit(display.getId(), trueSkeletalHit.surface(), trueSkeletalHit.face(),
                        trueSkeletalHit.x(), trueSkeletalHit.y(), trueSkeletalHit.distance());
            }
            return null;
        }

        PlayerEntityModel model = createModel(client, slim);
        if (model == null) {
            return null;
        }
        CombinedBodySpecialModelRenderer.resetModel(model);
        CombinedBodySpecialModelRenderer.applyPoseData(model, data);
        Matrix4d rootMatrix = rootModelToWorld(display, data);
        ModelHit best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        SkinTexturePixels skinPixels = skinPixels(client, display.getItemStack(), data);

        if (skinPixels != null) {
            for (OuterSurfaceDef surface : OUTER_EDGE_SURFACES) {
                Matrix4d surfaceMatrix = outerSurfaceMatrix(rootMatrix, model, surface);
                if (surfaceMatrix == null) {
                    continue;
                }
                Matrix4d worldToSurface = surfaceMatrix.invert(new Matrix4d());
                Vector3d localStart = transformPosition(worldToSurface, eye);
                Vector3d localEnd = transformPosition(worldToSurface, end);
                Vector3d localDir = new Vector3d(localEnd).sub(localStart);
                OuterHit hit = intersectOuterEdges(localStart, localDir, skinPixels, surface, slim);
                if (hit == null) {
                    continue;
                }
                Vector3d worldPoint = transformPosition(surfaceMatrix, hit.point());
                double distance = Math.sqrt(squaredDistance(eye, worldPoint));
                if (distance >= bestDistance) {
                    continue;
                }
                best = new ModelHit(display.getId(), hit.surface(), hit.face(), hit.x(), hit.y(), distance);
                bestDistance = distance;
            }
        }

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
            BoxHit hit = intersectFaces(localStart, localDir, cuboid, surface.hitOffset());
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

    private static SkinTexturePixels skinPixels(MinecraftClient client, ItemStack stack, NbtCompound data) {
        String localSkin = data.getString("local_skin").orElse("");
        if (!localSkin.isEmpty()) {
            return SkinTexturePixels.get(Identifier.of("monvhua", "textures/local_skin/" + localSkin + ".png"));
        }
        ProfileComponent profile = stack.get(DataComponentTypes.PROFILE);
        if (profile != null) {
            ProfileComponent resolved = profile.resolve();
            if (resolved != null) {
                SkinTextures textures = client.getSkinProvider().getSkinTextures(resolved.gameProfile(), null);
                if (textures != null) {
                    return SkinTexturePixels.get(textures.texture());
                }
            }
        }
        if (client.player != null) {
            return SkinTexturePixels.get(client.player.getSkinTextures().texture());
        }
        return null;
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

    private static Matrix4d outerSurfaceMatrix(Matrix4d rootMatrix, PlayerEntityModel model, OuterSurfaceDef surface) {
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
            case HAT -> model.hat;
            case BODY -> model.body;
            case JACKET -> model.jacket;
            case WAIST -> getChild(model.body, CombinedBodyModelData.WAIST);
            case WAIST_JACKET -> getChild(getChild(model.body, CombinedBodyModelData.WAIST), CombinedBodyModelData.WAIST_JACKET);
            case LEFT_ARM -> model.leftArm;
            case LEFT_SLEEVE -> model.leftSleeve;
            case LEFT_FOREARM -> getChild(model.leftArm, CombinedBodyModelData.LEFT_FOREARM);
            case LEFT_FOREARM_SLEEVE -> getChild(getChild(model.leftArm, CombinedBodyModelData.LEFT_FOREARM), CombinedBodyModelData.LEFT_FOREARM_SLEEVE);
            case RIGHT_ARM -> model.rightArm;
            case RIGHT_SLEEVE -> model.rightSleeve;
            case RIGHT_FOREARM -> getChild(model.rightArm, CombinedBodyModelData.RIGHT_FOREARM);
            case RIGHT_FOREARM_SLEEVE -> getChild(getChild(model.rightArm, CombinedBodyModelData.RIGHT_FOREARM), CombinedBodyModelData.RIGHT_FOREARM_SLEEVE);
            case LEFT_LEG -> model.leftLeg;
            case LEFT_PANTS -> model.leftPants;
            case LEFT_LOWER_LEG -> getChild(model.leftLeg, CombinedBodyModelData.LEFT_LOWER_LEG);
            case LEFT_LOWER_PANTS -> getChild(getChild(model.leftLeg, CombinedBodyModelData.LEFT_LOWER_LEG), CombinedBodyModelData.LEFT_LOWER_PANTS);
            case RIGHT_LEG -> model.rightLeg;
            case RIGHT_PANTS -> model.rightPants;
            case RIGHT_LOWER_LEG -> getChild(model.rightLeg, CombinedBodyModelData.RIGHT_LOWER_LEG);
            case RIGHT_LOWER_PANTS -> getChild(getChild(model.rightLeg, CombinedBodyModelData.RIGHT_LOWER_LEG), CombinedBodyModelData.RIGHT_LOWER_PANTS);
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
        Matrix4d matrix = bodyModelBaseToWorld(display);
        matrix.translate(
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

    private static Matrix4d trueSkeletalRootModelToWorld(ItemDisplayEntity display, NbtCompound data) {
        Matrix4d matrix = bodyModelBaseToWorld(display);
        matrix.scale(1.0D, -1.0D, 1.0D)
                .translate(
                        data.getFloat("pose_model_offset_x", 0.0F),
                        data.getFloat("pose_model_offset_y", 0.0F),
                        data.getFloat("pose_model_offset_z", 0.0F))
                .rotateX(Math.toRadians(-data.getFloat("pose_model_pitch", 0.0F)))
                .rotateY(Math.toRadians(data.getFloat("pose_model_yaw", 0.0F)))
                .rotateZ(Math.toRadians(-data.getFloat("pose_model_roll", 0.0F)));
        float scale = Math.max(0.1F, data.getFloat("pose_model_scale", 1.0F));
        matrix.scale(scale);
        return matrix;
    }

    private static Matrix4d bodyModelBaseToWorld(ItemDisplayEntity display) {
        Matrix4d matrix = new Matrix4d().identity()
                .translate(display.getX(), display.getY(), display.getZ());
        DisplayEntity.RenderState renderState = display.getRenderState();
        if (renderState != null) {
            AffineTransformation transformation = renderState.transformation().interpolate(1.0F);
            matrix.mul(new Matrix4d(transformation.getMatrix()));
        }
        matrix.rotateY(Math.PI);
        applyItemModelTransform(matrix, itemDisplayContext(display));
        return matrix.translate(0.5D, 0.0D, 0.5D)
                .rotateY(Math.PI)
                .scale(-1.0D, -1.0D, 1.0D);
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

    private static OuterHit intersectOuterEdges(Vector3d start, Vector3d dir, SkinTexturePixels pixels,
                                                OuterSurfaceDef surface, boolean slim) {
        PixelBox box = surface.box(slim);
        OuterHit best = null;
        for (OuterFace face : outerFaces(surface, box)) {
            for (int y = 0; y < face.height(); y++) {
                for (int x = 0; x < face.width(); x++) {
                    if (!pixels.isOpaque(face.textureX() + x, face.textureY() + y)) {
                        continue;
                    }
                    best = nearerOuter(best, intersectOuterPixelFront(start, dir, surface.name(), face, x, y));
                    best = nearerOuter(best, intersectOuterPixelSide(start, dir, surface.name(), face,
                            x, y, pixels, Edge.UP));
                    best = nearerOuter(best, intersectOuterPixelSide(start, dir, surface.name(), face,
                            x, y, pixels, Edge.RIGHT));
                    best = nearerOuter(best, intersectOuterPixelSide(start, dir, surface.name(), face,
                            x, y, pixels, Edge.DOWN));
                    best = nearerOuter(best, intersectOuterPixelSide(start, dir, surface.name(), face,
                            x, y, pixels, Edge.LEFT));
                }
            }
        }
        return best;
    }

    private static OuterHit nearerOuter(OuterHit current, OuterHit candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.t() < current.t() ? candidate : current;
    }

    private static OuterHit intersectOuterPixelFront(Vector3d start, Vector3d dir, String surfaceName,
                                                     OuterFace face, int x, int y) {
        FacePoint p00 = face.origin()
                .add(face.uStep().multiply(x))
                .add(face.vStep().multiply(y));
        FacePoint p10 = p00.add(face.uStep());
        FacePoint p11 = p10.add(face.vStep());
        FacePoint p01 = p00.add(face.vStep());
        FacePoint offset = face.normal().multiply(OUTER_PIXEL_DEPTH);
        FacePoint o00 = p00.add(offset);
        FacePoint o10 = p10.add(offset);
        FacePoint o11 = p11.add(offset);
        FacePoint o01 = p01.add(offset);

        BoxHit hit = nearer(null, intersectTriangle(start, dir, face.face(), o00.vector(), o10.vector(), o11.vector()));
        hit = nearer(hit, intersectTriangle(start, dir, face.face(), o00.vector(), o11.vector(), o01.vector()));
        if (hit == null) {
            return null;
        }
        return new OuterHit(hit.t(), hit.point(), surfaceName + "_outer_" + face.face().asString(),
                face.face(), x, y);
    }

    private static OuterHit intersectOuterPixelSide(Vector3d start, Vector3d dir, String surfaceName, OuterFace face,
                                                    int x, int y, SkinTexturePixels pixels, Edge edge) {
        if (!isOuterEdge(pixels, face, x, y, edge)) {
            return null;
        }

        FacePoint p00 = face.origin()
                .add(face.uStep().multiply(x))
                .add(face.vStep().multiply(y));
        FacePoint p10 = p00.add(face.uStep());
        FacePoint p11 = p10.add(face.vStep());
        FacePoint p01 = p00.add(face.vStep());
        FacePoint offset = face.normal().multiply(OUTER_PIXEL_DEPTH);
        FacePoint o00 = p00.add(offset);
        FacePoint o10 = p10.add(offset);
        FacePoint o11 = p11.add(offset);
        FacePoint o01 = p01.add(offset);

        FacePoint a;
        FacePoint b;
        FacePoint c;
        FacePoint d;
        Direction sideFace;
        switch (edge) {
            case UP -> {
                a = o00; b = o10; c = p10; d = p00;
                sideFace = directionFromVector(face.vStep().negate());
            }
            case RIGHT -> {
                a = o10; b = o11; c = p11; d = p10;
                sideFace = directionFromVector(face.uStep());
            }
            case DOWN -> {
                a = o11; b = o01; c = p01; d = p11;
                sideFace = directionFromVector(face.vStep());
            }
            case LEFT -> {
                a = o01; b = o00; c = p00; d = p01;
                sideFace = directionFromVector(face.uStep().negate());
            }
            default -> {
                return null;
            }
        }

        BoxHit hit = nearer(null, intersectTriangle(start, dir, sideFace, a.vector(), b.vector(), c.vector()));
        hit = nearer(hit, intersectTriangle(start, dir, sideFace, a.vector(), c.vector(), d.vector()));
        if (hit == null) {
            return null;
        }
        return new OuterHit(hit.t(), hit.point(), surfaceName + "_outer_" + face.face().asString(),
                sideFace, x, y);
    }

    private static boolean isOuterEdge(SkinTexturePixels pixels, OuterFace face, int x, int y, Edge edge) {
        return switch (edge) {
            case UP -> !isOuterFacePixelOpaque(pixels, face, x, y - 1);
            case RIGHT -> !isOuterFacePixelOpaque(pixels, face, x + 1, y);
            case DOWN -> !isOuterFacePixelOpaque(pixels, face, x, y + 1);
            case LEFT -> !isOuterFacePixelOpaque(pixels, face, x - 1, y);
        };
    }

    private static boolean isOuterFacePixelOpaque(SkinTexturePixels pixels, OuterFace face, int x, int y) {
        if (x < 0 || y < 0 || x >= face.width() || y >= face.height()) {
            return false;
        }
        return pixels.isOpaque(face.textureX() + x, face.textureY() + y);
    }

    private static OuterFace[] outerFaces(OuterSurfaceDef surface, PixelBox box) {
        int textureX = surface.textureX();
        int sideTextureY = surface.textureY() + box.depth() + surface.textureYSegment();
        OuterFace[] faces = new OuterFace[6];
        int index = 0;
        if (surface.renderStartCap()) {
            faces[index++] = new OuterFace(Direction.UP, textureX + box.depth(), surface.textureY(),
                    box.width(), box.depth(),
                    new FacePoint(box.x(), box.y(), box.z() + box.depth()),
                    new FacePoint(1.0D, 0.0D, 0.0D),
                    new FacePoint(0.0D, 0.0D, -1.0D),
                    new FacePoint(0.0D, -1.0D, 0.0D));
        }
        if (surface.renderEndCap()) {
            faces[index++] = new OuterFace(Direction.DOWN, textureX + box.depth() + box.width(), surface.textureY(),
                    box.width(), box.depth(),
                    new FacePoint(box.x(), box.y() + box.height(), box.z() + box.depth()),
                    new FacePoint(1.0D, 0.0D, 0.0D),
                    new FacePoint(0.0D, 0.0D, -1.0D),
                    new FacePoint(0.0D, 1.0D, 0.0D));
        }
        faces[index++] = new OuterFace(Direction.WEST, textureX, sideTextureY,
                box.depth(), box.height(),
                new FacePoint(box.x(), box.y(), box.z() + box.depth()),
                new FacePoint(0.0D, 0.0D, -1.0D),
                new FacePoint(0.0D, 1.0D, 0.0D),
                new FacePoint(-1.0D, 0.0D, 0.0D));
        faces[index++] = new OuterFace(Direction.NORTH, textureX + box.depth(), sideTextureY,
                box.width(), box.height(),
                new FacePoint(box.x(), box.y(), box.z()),
                new FacePoint(1.0D, 0.0D, 0.0D),
                new FacePoint(0.0D, 1.0D, 0.0D),
                new FacePoint(0.0D, 0.0D, -1.0D));
        faces[index++] = new OuterFace(Direction.EAST, textureX + box.depth() + box.width(), sideTextureY,
                box.depth(), box.height(),
                new FacePoint(box.x() + box.width(), box.y(), box.z()),
                new FacePoint(0.0D, 0.0D, 1.0D),
                new FacePoint(0.0D, 1.0D, 0.0D),
                new FacePoint(1.0D, 0.0D, 0.0D));
        faces[index++] = new OuterFace(Direction.SOUTH, textureX + box.depth() + box.width() + box.depth(), sideTextureY,
                box.width(), box.height(),
                new FacePoint(box.x() + box.width(), box.y(), box.z() + box.depth()),
                new FacePoint(-1.0D, 0.0D, 0.0D),
                new FacePoint(0.0D, 1.0D, 0.0D),
                new FacePoint(0.0D, 0.0D, 1.0D));

        if (index == faces.length) {
            return faces;
        }
        OuterFace[] compact = new OuterFace[index];
        System.arraycopy(faces, 0, compact, 0, index);
        return compact;
    }

    private static Direction directionFromVector(FacePoint vector) {
        double ax = Math.abs(vector.x());
        double ay = Math.abs(vector.y());
        double az = Math.abs(vector.z());
        if (ax >= ay && ax >= az) {
            return vector.x() < 0.0D ? Direction.WEST : Direction.EAST;
        }
        if (ay >= az) {
            return vector.y() < 0.0D ? Direction.UP : Direction.DOWN;
        }
        return vector.z() < 0.0D ? Direction.NORTH : Direction.SOUTH;
    }

    private static BoxHit intersectFaces(Vector3d start, Vector3d dir, Cuboid box, double offset) {
        BoxHit best = null;
        for (Direction face : Direction.values()) {
            Vector3d[] quad = quad(box, face, offset);
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

    private static Vector3d[] quad(Cuboid box, Direction face, double offset) {
        Vector3d normal = normal(face).mul(offset);
        Vector3d[] vertices = switch (face) {
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
        for (Vector3d vertex : vertices) {
            vertex.add(normal);
        }
        return vertices;
    }

    private static Vector3d normal(Direction face) {
        return switch (face) {
            case UP -> new Vector3d(0.0D, -1.0D, 0.0D);
            case DOWN -> new Vector3d(0.0D, 1.0D, 0.0D);
            case NORTH -> new Vector3d(0.0D, 0.0D, -1.0D);
            case SOUTH -> new Vector3d(0.0D, 0.0D, 1.0D);
            case WEST -> new Vector3d(-1.0D, 0.0D, 0.0D);
            case EAST -> new Vector3d(1.0D, 0.0D, 0.0D);
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
        return new SurfaceDef(name, path, cuboid, cuboid, 0.0D);
    }

    private static SurfaceDef def(String name, Part[] path, Cuboid defaultCuboid, Cuboid slimCuboid) {
        return new SurfaceDef(name, path, defaultCuboid, slimCuboid, 0.0D);
    }

    private static Cuboid cuboid(double x, double y, double z, double width, double height, double depth) {
        return new Cuboid(x / 16.0D, y / 16.0D, z / 16.0D,
                (x + width) / 16.0D, (y + height) / 16.0D, (z + depth) / 16.0D);
    }

    private static PixelBox boxPixels(int x, int y, int z, int width, int height, int depth) {
        return new PixelBox(x, y, z, width, height, depth);
    }

    private static OuterSurfaceDef outer(String name, Part[] path, PixelBox box,
                                         int textureX, int textureY, int textureYSegment,
                                         boolean renderStartCap, boolean renderEndCap) {
        return new OuterSurfaceDef(name, path, box, box, textureX, textureY, textureYSegment, renderStartCap, renderEndCap);
    }

    private static OuterSurfaceDef outer(String name, Part[] path, PixelBox defaultBox, PixelBox slimBox,
                                         int textureX, int textureY, int textureYSegment,
                                         boolean renderStartCap, boolean renderEndCap) {
        return new OuterSurfaceDef(name, path, defaultBox, slimBox, textureX, textureY, textureYSegment, renderStartCap, renderEndCap);
    }

    public record ModelHit(int entityId, String surface, Direction face, int x, int y, double distance) {
    }

    private record SurfaceDef(String name, Part[] path, Cuboid defaultCuboid, Cuboid slimCuboid, double hitOffset) {
        private Cuboid cuboid(boolean slim) {
            return slim ? slimCuboid : defaultCuboid;
        }
    }

    private record OuterSurfaceDef(String name, Part[] path, PixelBox defaultBox, PixelBox slimBox,
                                   int textureX, int textureY, int textureYSegment,
                                   boolean renderStartCap, boolean renderEndCap) {
        private PixelBox box(boolean slim) {
            return slim ? slimBox : defaultBox;
        }
    }

    private record PixelBox(int x, int y, int z, int width, int height, int depth) {
    }

    private record OuterFace(Direction face, int textureX, int textureY, int width, int height,
                             FacePoint origin, FacePoint uStep, FacePoint vStep, FacePoint normal) {
    }

    private record FacePoint(double x, double y, double z) {
        private FacePoint add(FacePoint other) {
            return new FacePoint(x + other.x, y + other.y, z + other.z);
        }

        private FacePoint multiply(double scalar) {
            return new FacePoint(x * scalar, y * scalar, z * scalar);
        }

        private FacePoint negate() {
            return new FacePoint(-x, -y, -z);
        }

        private Vector3d vector() {
            return new Vector3d(x / 16.0D, y / 16.0D, z / 16.0D);
        }
    }

    private enum Edge {
        UP,
        RIGHT,
        DOWN,
        LEFT
    }

    private record OuterHit(double t, Vector3d point, String surface, Direction face, int x, int y) {
    }

    private enum Part {
        HEAD,
        HAT,
        BODY,
        JACKET,
        WAIST,
        WAIST_JACKET,
        LEFT_ARM,
        LEFT_SLEEVE,
        LEFT_FOREARM,
        LEFT_FOREARM_SLEEVE,
        RIGHT_ARM,
        RIGHT_SLEEVE,
        RIGHT_FOREARM,
        RIGHT_FOREARM_SLEEVE,
        LEFT_LEG,
        LEFT_PANTS,
        LEFT_LOWER_LEG,
        LEFT_LOWER_PANTS,
        RIGHT_LEG,
        RIGHT_PANTS,
        RIGHT_LOWER_LEG,
        RIGHT_LOWER_PANTS
    }

    private record Cuboid(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    }

    private record BoxHit(double t, Vector3d point, Direction face) {
    }

}

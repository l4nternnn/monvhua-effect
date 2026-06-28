package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.features.carryentity.CarryAttachedRenderMath;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import com.kuilunfuzhe.monvhua.mixin.PlayerEntityRendererInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector4d;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PlayerSkinPaintManager {
    private static final double MAX_RAYCAST_DISTANCE = 6.0D;
    private static final double TRIANGLE_EPSILON = 1.0E-7D;
    private static final Map<UUID, PlayerPaintState> PAINT_STATES = new HashMap<>();
    private static LoadedEntityModels cachedEntityModels;
    private static PlayerEntityModel cachedDefaultModel;
    private static PlayerEntityModel cachedSlimModel;

    private static final SurfaceDef[] PLAYER_SURFACES = new SurfaceDef[]{
            def("head", Part.HEAD, cuboid(-4, -8, -4, 8, 8, 8)),
            def("body_upper", Part.BODY, cuboid(-4, 0, -2, 8, 6, 4)),
            def("body_lower", Part.BODY, cuboid(-4, 6, -2, 8, 6, 4)),
            def("left_arm_upper", Part.LEFT_ARM, cuboid(-1, -2, -2, 4, 6, 4), cuboid(-1, -2, -2, 3, 6, 4)),
            def("left_arm_lower", Part.LEFT_ARM, cuboid(-1, 4, -2, 4, 6, 4), cuboid(-1, 4, -2, 3, 6, 4)),
            def("right_arm_upper", Part.RIGHT_ARM, cuboid(-4, -2, -2, 4, 6, 4), cuboid(-3, -2, -2, 3, 6, 4)),
            def("right_arm_lower", Part.RIGHT_ARM, cuboid(-4, 4, -2, 4, 6, 4), cuboid(-3, 4, -2, 3, 6, 4)),
            def("left_leg_upper", Part.LEFT_LEG, cuboid(-2, 0, -2, 4, 6, 4)),
            def("left_leg_lower", Part.LEFT_LEG, cuboid(-2, 6, -2, 4, 6, 4)),
            def("right_leg_upper", Part.RIGHT_LEG, cuboid(-4, 0, -2, 4, 6, 4)),
            def("right_leg_lower", Part.RIGHT_LEG, cuboid(-4, 6, -2, 4, 6, 4)),
    };

    private PlayerSkinPaintManager() {
    }

    // ─── Public API ────────────────────────────────────────────────────────

    /** Returns the painted texture ID for a player, or null if unpainted. */
    public static Identifier getPaintedTexture(UUID playerUuid) {
        PlayerPaintState state = PAINT_STATES.get(playerUuid);
        return state != null && state.hasModifications() ? state.paintedTextureId : null;
    }

    public static boolean hasPaintData(UUID playerUuid) {
        return PAINT_STATES.containsKey(playerUuid);
    }

    public static boolean isPlayerPainted(UUID playerUuid) {
        PlayerPaintState state = PAINT_STATES.get(playerUuid);
        return state != null && state.hasModifications();
    }

    public static List<Integer> tickWaterClear(MinecraftClient client) {
        List<Integer> cleared = new ArrayList<>();
        if (client == null || client.world == null || PAINT_STATES.isEmpty()) {
            return cleared;
        }
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (!isPlayerPainted(player.getUuid())) {
                continue;
            }
            if (player.isTouchingWater() || player.isSubmergedInWater()) {
                resetTexture(player);
                cleared.add(player.getId());
            }
        }
        return cleared;
    }

    // ─── Raycasting ────────────────────────────────────────────────────────

    public static PlayerPaintHit raycastPlayer(MinecraftClient client) {
        if (client.world == null || client.player == null) return null;
        Vec3d eye = client.player.getEyePos();
        Vec3d end = eye.add(client.player.getRotationVec(1.0F).multiply(MAX_RAYCAST_DISTANCE));
        return raycastPlayer(client, eye, end);
    }

    public static PlayerPaintHit raycastPlayer(MinecraftClient client, Vec3d eye, Vec3d end) {
        if (client.world == null || client.player == null) return null;
        double maxDistance = Math.max(MAX_RAYCAST_DISTANCE, eye.distanceTo(end));

        PlayerPaintHit best = null;
        double bestDistance = maxDistance;

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof PlayerEntity targetPlayer) || entity == client.player) continue;
            if (entity.getPos().squaredDistanceTo(eye) > maxDistance * maxDistance + 4.0D) continue;

            PlayerPaintHit hit = raycastSinglePlayer(client, targetPlayer, eye, end);
            if (hit != null && hit.distance() < bestDistance) {
                best = hit;
                bestDistance = hit.distance();
            }
        }
        return best;
    }

    public static PlayerPaintHit approximateHit(PlayerEntity player, Vec3d hitPos, Vec3d eyePos) {
        if (player == null || hitPos == null || eyePos == null) return null;
        double localY = MathHelper.clamp((hitPos.y - player.getY()) / Math.max(0.1D, player.getScale()), 0.0D, 1.8D);
        double yaw = Math.toRadians(-player.bodyYaw + 180.0D);
        double dx = hitPos.x - player.getX();
        double dz = hitPos.z - player.getZ();
        double localX = dx * Math.cos(yaw) - dz * Math.sin(yaw);

        String surface;
        int width;
        int height;
        int x;
        int y;
        if (localY >= 1.4D) {
            surface = "head";
            width = 8;
            height = 8;
            x = MathHelper.clamp((int) Math.floor((localX + 0.25D) / 0.5D * width), 0, width - 1);
            y = MathHelper.clamp((int) Math.floor((1.8D - localY) / 0.4D * height), 0, height - 1);
        } else if (localY >= 0.7D) {
            surface = localY >= 1.05D ? "body_upper" : "body_lower";
            width = 8;
            height = 6;
            double segmentTop = localY >= 1.05D ? 1.4D : 1.05D;
            double segmentBottom = localY >= 1.05D ? 1.05D : 0.7D;
            x = MathHelper.clamp((int) Math.floor((localX + 0.25D) / 0.5D * width), 0, width - 1);
            y = MathHelper.clamp((int) Math.floor((segmentTop - localY) / Math.max(0.0001D, segmentTop - segmentBottom) * height), 0, height - 1);
        } else {
            boolean left = localX > 0.0D;
            surface = localY >= 0.35D
                    ? (left ? "left_leg_upper" : "right_leg_upper")
                    : (left ? "left_leg_lower" : "right_leg_lower");
            width = 4;
            height = 6;
            double centerX = left ? 0.125D : -0.125D;
            double segmentTop = localY >= 0.35D ? 0.7D : 0.35D;
            double segmentBottom = localY >= 0.35D ? 0.35D : 0.0D;
            x = MathHelper.clamp((int) Math.floor((localX - centerX + 0.125D) / 0.25D * width), 0, width - 1);
            y = MathHelper.clamp((int) Math.floor((segmentTop - localY) / Math.max(0.0001D, segmentTop - segmentBottom) * height), 0, height - 1);
        }
        return new PlayerPaintHit(player.getId(), surface, Direction.NORTH, x, y, eyePos.distanceTo(hitPos));
    }

    private static PlayerPaintHit raycastSinglePlayer(MinecraftClient client, PlayerEntity player, Vec3d eye, Vec3d end) {
        boolean slim = isSlimPlayer(player);
        PlayerEntityModel model = createPlayerModel(client, slim);
        if (model == null) return null;

        PlayerPose pose = applyPlayerPose(client, model, player);

        double scale = player.getScale();
        Matrix4d rootMatrix = playerToWorldMatrix(player, scale);
        if (pose != null) {
            try {
                rootMatrix = playerModelToWorldMatrix(pose);
            } catch (Exception ignored) {
            }
        }
        double bestDistance = Double.POSITIVE_INFINITY;
        PlayerPaintHit best = null;

        for (SurfaceDef surface : PLAYER_SURFACES) {
            Matrix4d surfaceMatrix = surfaceMatrix(rootMatrix, model, surface);
            if (surfaceMatrix == null) continue;

            Matrix4d worldToSurface = surfaceMatrix.invert(new Matrix4d());
            Vector3d localStart = transformPosition(worldToSurface, eye);
            Vector3d localEnd = transformPosition(worldToSurface, end);
            Vector3d localDir = new Vector3d(localEnd).sub(localStart);

            Cuboid cuboid = surface.cuboid(slim);
            BoxHit hit = intersectFaces(localStart, localDir, cuboid);
            if (hit != null && hit.t() >= 0.0D && hit.t() <= 1.0D) {
                Vector3d worldPoint = transformPosition(surfaceMatrix, hit.point());
                double distance = Math.sqrt(squaredDistance(eye, worldPoint));
                if (distance >= bestDistance) continue;

                String paintSurface = surface.name();
                ModelPaintData.FaceSize size = ModelPaintData.size(paintSurface, hit.face(), slim);
                int[] pixel = pixel(cuboid, hit.point(), hit.face(), size.width(), size.height());
                best = new PlayerPaintHit(player.getId(), paintSurface, hit.face(), pixel[0], pixel[1], distance);
                bestDistance = distance;
            }
        }
        return best;
    }

    // ─── Painting ──────────────────────────────────────────────────────────

    /**
     * Paint a circular brush stroke on the player's skin at the given surface face pixel.
     * All painting is client-side (modifies the runtime skin texture only).
     */
    public static boolean paintAt(PlayerEntity player, String surface, Direction face, int x, int y,
                                   int color, int radius, boolean clear) {
        if (player == null) return false;
        PlayerPaintState state = getOrCreateState(player);
        if (!state.ready()) return false;
        boolean slim = isSlimPlayer(player);
        ModelPaintData.FaceSize size = ModelPaintData.size(surface, face, slim);

        if (clear) {
            PaintKey key = new PaintKey(surface, face);
            PaintFaceData removed = state.paintFaces.remove(key);
            if (removed != null) {
                state.markDirty();
                uploadPaintedTexture(state, player);
                return true;
            }
            return false;
        }

        PaintKey key = new PaintKey(surface, face);
        PaintFaceData faceData = state.paintFaces.computeIfAbsent(key,
                k -> new PaintFaceData(size.width(), size.height()));

        int radiusSq = radius * radius;
        boolean changed = false;
        for (int dy = -radius + 1; dy <= radius - 1; dy++) {
            for (int dx = -radius + 1; dx <= radius - 1; dx++) {
                if (dx * dx + dy * dy > radiusSq) continue;
                int px = x + dx;
                int py = y + dy;
                if (px < 0 || px >= size.width() || py < 0 || py >= size.height()) continue;
                int pixelIndex = py * size.width() + px;
                if (faceData.pixels[pixelIndex] != color) {
                    faceData.pixels[pixelIndex] = color;
                    changed = true;
                }
            }
        }

        // Remove paint face if all pixels became transparent
        if (changed && isAllZero(faceData.pixels)) {
            state.paintFaces.remove(key);
        }

        if (changed) {
            state.markDirty();
            uploadPaintedTexture(state, player);
        }
        return changed;
    }

    public static int[] copyFacePixels(PlayerEntity player, String surface, Direction face) {
        if (player == null) {
            return new int[0];
        }
        ModelPaintData.FaceSize size = ModelPaintData.size(surface, face, isSlimPlayer(player));
        int[] pixels = new int[Math.max(1, size.width() * size.height())];
        PlayerPaintState state = PAINT_STATES.get(player.getUuid());
        if (state == null) {
            return pixels;
        }
        PaintFaceData data = state.paintFaces.get(new PaintKey(surface, face));
        if (data == null) {
            return pixels;
        }
        System.arraycopy(data.pixels, 0, pixels, 0, Math.min(pixels.length, data.pixels.length));
        return pixels;
    }

    public static boolean setFacePixels(PlayerEntity player, String surface, Direction face, int[] pixels) {
        if (player == null) {
            return false;
        }
        PlayerPaintState state = getOrCreateState(player);
        if (!state.ready()) {
            return false;
        }
        ModelPaintData.FaceSize size = ModelPaintData.size(surface, face, isSlimPlayer(player));
        int width = Math.max(1, size.width());
        int height = Math.max(1, size.height());
        int[] sanitized = sanitizeFacePixels(pixels, width, height);
        PaintKey key = new PaintKey(surface, face);
        if (isAllZero(sanitized)) {
            state.paintFaces.remove(key);
        } else {
            PaintFaceData data = state.paintFaces.computeIfAbsent(key, ignored -> new PaintFaceData(width, height));
            System.arraycopy(sanitized, 0, data.pixels, 0, data.pixels.length);
        }
        state.modified = !state.paintFaces.isEmpty();
        uploadPaintedTexture(state, player);
        return true;
    }

    /** Restore the player's original skin texture, removing all paint. */
    public static void resetTexture(PlayerEntity player) {
        if (player == null) return;
        PlayerPaintState state = PAINT_STATES.remove(player.getUuid());
        if (state != null) {
            state.destroy();
        }
    }

    // ─── State management ──────────────────────────────────────────────────

    private static PlayerPaintState getOrCreateState(PlayerEntity player) {
        return PAINT_STATES.computeIfAbsent(player.getUuid(), uuid -> new PlayerPaintState(player));
    }

    private static boolean isSlimPlayer(PlayerEntity player) {
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return false;
        SkinTextures textures = clientPlayer.getSkinTextures();
        return textures != null && textures.model() == SkinTextures.Model.SLIM;
    }

    private static PlayerEntityModel createPlayerModel(MinecraftClient client, boolean slim) {
        LoadedEntityModels loadedModels = client.getLoadedEntityModels();
        if (loadedModels == null) return null;
        if (loadedModels != cachedEntityModels) {
            cachedEntityModels = loadedModels;
            cachedDefaultModel = null;
            cachedSlimModel = null;
        }
        if (slim && cachedSlimModel != null) return cachedSlimModel;
        if (!slim && cachedDefaultModel != null) return cachedDefaultModel;
        EntityModelLayer layer = slim ? EntityModelLayers.PLAYER_SLIM : EntityModelLayers.PLAYER;
        PlayerEntityModel model = new PlayerEntityModel(loadedModels.getModelPart(layer), slim);
        if (slim) {
            cachedSlimModel = model;
        } else {
            cachedDefaultModel = model;
        }
        return model;
    }

    private static PlayerPose applyPlayerPose(MinecraftClient client, PlayerEntityModel model, PlayerEntity player) {
        for (ModelPart part : model.getRootPart().traverse()) {
            part.resetTransform();
            part.visible = true;
            part.hidden = false;
        }
        if (player instanceof AbstractClientPlayerEntity clientPlayer) {
            try {
                EntityRenderer<?, ?> renderer = client.getEntityRenderDispatcher().getRenderer(clientPlayer);
                if (renderer instanceof PlayerEntityRenderer playerRenderer) {
                    PlayerEntityRenderState state = playerRenderer.createRenderState();
                    float tickProgress = client.getRenderTickCounter().getTickProgress(true);
                    playerRenderer.updateRenderState(clientPlayer, state, tickProgress);
                    model.setAngles(state);
                    return new PlayerPose(playerRenderer, state);
                }
            } catch (Exception ignored) {
            }
        }
        float bodyYaw = player.bodyYaw;
        float headYaw = player.headYaw;
        float pitch = player.getPitch();
        float limbProgress = player.limbAnimator.getAnimationProgress();
        float limbSpeed = player.limbAnimator.getSpeed();
        float headYawDiff = headYaw - bodyYaw;

        float radPerDeg = 0.017453292F;
        model.head.yaw = headYawDiff * radPerDeg;
        model.head.pitch = pitch * radPerDeg;

        if (limbSpeed > 0.01F) {
            float swing = limbProgress;
            float speed = Math.min(limbSpeed, 1.0F);
            float armSwing = (float) (Math.sin(swing * Math.PI * 2.0F) * speed * 0.5F);
            model.leftArm.pitch = armSwing;
            model.rightArm.pitch = -armSwing;
            model.leftLeg.pitch = -armSwing;
            model.rightLeg.pitch = armSwing;
        }
        return null;
    }

    // ─── Matrix transforms ─────────────────────────────────────────────────

    private static Matrix4d playerModelToWorldMatrix(PlayerPose pose) {
        PlayerEntityRenderer renderer = pose.renderer();
        PlayerEntityRenderState state = pose.state();
        MatrixStack matrices = new MatrixStack();

        Vec3d offset = renderer.getPositionOffset(state);
        matrices.translate(state.x + offset.x, state.y + offset.y, state.z + offset.z);
        if (state.isInPose(EntityPose.SLEEPING) && state.sleepingDirection != null) {
            float eyeOffset = state.standingEyeHeight - 0.1F;
            matrices.translate(
                    -state.sleepingDirection.getOffsetX() * eyeOffset,
                    0.0F,
                    -state.sleepingDirection.getOffsetZ() * eyeOffset);
        }

        float baseScale = state.baseScale == 0.0F ? 1.0F : state.baseScale;
        matrices.scale(baseScale, baseScale, baseScale);
        ((PlayerEntityRendererInvoker) renderer).monvhua$setupTransforms(state, matrices, state.bodyYaw, baseScale);
        matrices.scale(-1.0F, -1.0F, 1.0F);
        ((PlayerEntityRendererInvoker) renderer).monvhua$scale(state, matrices);
        matrices.translate(0.0F, -1.501F, 0.0F);
        if (CarryPoseClientState.isCarried(state.id)) {
            CarryAttachedRenderMath.applyCarriedModelHorizontalTransform(
                    matrices, baseScale, CarryPoseClientState.isDragCarried(state.id));
        }

        return new Matrix4d(matrices.peek().getPositionMatrix());
    }

    private static Matrix4d playerToWorldMatrix(PlayerEntity player, double scale) {
        Matrix4d matrix = new Matrix4d().identity();
        matrix.translate(player.getX(), player.getY(), player.getZ());
        matrix.rotateY((float) Math.toRadians(-player.bodyYaw + 180.0D));
        matrix.scale(-scale, -scale, scale);
        return matrix;
    }

    private static Matrix4d surfaceMatrix(Matrix4d rootMatrix, PlayerEntityModel model, SurfaceDef surface) {
        ModelPart modelPart = modelPart(model, surface.part());
        if (modelPart == null) return null;
        Matrix4d matrix = new Matrix4d(rootMatrix);
        applyModelPart(matrix, model.getRootPart());
        applyModelPart(matrix, modelPart);
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
            case LEFT_ARM -> model.leftArm;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_LEG -> model.leftLeg;
            case RIGHT_LEG -> model.rightLeg;
        };
    }

    private static Vector3d transformPosition(Matrix4d matrix, Vec3d pos) {
        return transformPosition(matrix, new Vector3d(pos.x, pos.y, pos.z));
    }

    private static Vector3d transformPosition(Matrix4d matrix, Vector3d pos) {
        Vector4d result = matrix.transform(new Vector4d(pos.x, pos.y, pos.z, 1.0D));
        return new Vector3d(result.x, result.y, result.z);
    }

    private static double squaredDistance(Vec3d a, Vector3d b) {
        double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    // ─── Triangle intersection ─────────────────────────────────────────────

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
        if (candidate == null) return current;
        return current == null || candidate.t() < current.t() ? candidate : current;
    }

    private static BoxHit intersectTriangle(Vector3d start, Vector3d dir, Direction face,
                                            Vector3d v0, Vector3d v1, Vector3d v2) {
        Vector3d edge1 = new Vector3d(v1).sub(v0);
        Vector3d edge2 = new Vector3d(v2).sub(v0);
        Vector3d pvec = new Vector3d(dir).cross(edge2);
        double det = edge1.dot(pvec);
        if (Math.abs(det) < TRIANGLE_EPSILON) return null;

        double invDet = 1.0D / det;
        Vector3d tvec = new Vector3d(start).sub(v0);
        double u = tvec.dot(pvec) * invDet;
        if (u < -TRIANGLE_EPSILON || u > 1.0D + TRIANGLE_EPSILON) return null;

        Vector3d qvec = new Vector3d(tvec).cross(edge1);
        double v = dir.dot(qvec) * invDet;
        if (v < -TRIANGLE_EPSILON || u + v > 1.0D + TRIANGLE_EPSILON) return null;

        double t = edge2.dot(qvec) * invDet;
        if (t < -TRIANGLE_EPSILON || t > 1.0D + TRIANGLE_EPSILON) return null;
        double clampedT = MathHelper.clamp(t, 0.0D, 1.0D);
        return new BoxHit(clampedT, new Vector3d(start).fma(clampedT, dir), face);
    }

    private static Vector3d[] quad(Cuboid box, Direction face) {
        return switch (face) {
            case UP -> new Vector3d[]{
                    v(box.minX, box.minY, box.minZ), v(box.maxX, box.minY, box.minZ),
                    v(box.maxX, box.minY, box.maxZ), v(box.minX, box.minY, box.maxZ)
            };
            case DOWN -> new Vector3d[]{
                    v(box.minX, box.maxY, box.maxZ), v(box.maxX, box.maxY, box.maxZ),
                    v(box.maxX, box.maxY, box.minZ), v(box.minX, box.maxY, box.minZ)
            };
            case NORTH -> new Vector3d[]{
                    v(box.minX, box.minY, box.minZ), v(box.maxX, box.minY, box.minZ),
                    v(box.maxX, box.maxY, box.minZ), v(box.minX, box.maxY, box.minZ)
            };
            case SOUTH -> new Vector3d[]{
                    v(box.maxX, box.minY, box.maxZ), v(box.minX, box.minY, box.maxZ),
                    v(box.minX, box.maxY, box.maxZ), v(box.maxX, box.maxY, box.maxZ)
            };
            case WEST -> new Vector3d[]{
                    v(box.minX, box.minY, box.maxZ), v(box.minX, box.minY, box.minZ),
                    v(box.minX, box.maxY, box.minZ), v(box.minX, box.maxY, box.maxZ)
            };
            case EAST -> new Vector3d[]{
                    v(box.maxX, box.minY, box.minZ), v(box.maxX, box.minY, box.maxZ),
                    v(box.maxX, box.maxY, box.maxZ), v(box.maxX, box.maxY, box.minZ)
            };
        };
    }

    private static Vector3d v(double x, double y, double z) {
        return new Vector3d(x, y, z);
    }

    private static int[] pixel(Cuboid box, Vector3d point, Direction face, int width, int height) {
        double u, v;
        switch (face) {
            case UP, DOWN -> {
                u = (point.x - box.minX) / Math.max(0.0001D, box.maxX - box.minX);
                v = (box.maxZ - point.z) / Math.max(0.0001D, box.maxZ - box.minZ);
            }
            case NORTH -> {
                u = (point.x - box.minX) / Math.max(0.0001D, box.maxX - box.minX);
                v = (point.y - box.minY) / Math.max(0.0001D, box.maxY - box.minY);
            }
            case SOUTH -> {
                u = (box.maxX - point.x) / Math.max(0.0001D, box.maxX - box.minX);
                v = (point.y - box.minY) / Math.max(0.0001D, box.maxY - box.minY);
            }
            case WEST -> {
                u = (box.maxZ - point.z) / Math.max(0.0001D, box.maxZ - box.minZ);
                v = (point.y - box.minY) / Math.max(0.0001D, box.maxY - box.minY);
            }
            case EAST -> {
                u = (point.z - box.minZ) / Math.max(0.0001D, box.maxZ - box.minZ);
                v = (point.y - box.minY) / Math.max(0.0001D, box.maxY - box.minY);
            }
            default -> { u = 0.5D; v = 0.5D; }
        }
        return new int[]{
                MathHelper.clamp((int) (u * width), 0, width - 1),
                MathHelper.clamp((int) (v * height), 0, height - 1)
        };
    }

    // ─── Texture upload ────────────────────────────────────────────────────

    private static void uploadPaintedTexture(PlayerPaintState state, PlayerEntity player) {
        if (state.paintedTexture == null) return;
        NativeImage image = state.paintedTexture.getImage();
        if (image == null) return;
        int w = state.imageWidth;
        int h = state.imageHeight;

        // Start from original skin pixels
        int[] targetArgb = Arrays.copyOf(state.originalPixels, state.originalPixels.length);

        // Composite each paint face using SkinTexturePixels texture-aware mapping
        boolean slim = isSlimPlayer(player);
        for (Map.Entry<PaintKey, PaintFaceData> entry : state.paintFaces.entrySet()) {
            PaintKey key = entry.getKey();
            PaintFaceData data = entry.getValue();
            SkinTexturePixels.compositePaintFace(targetArgb, w, h,
                    key.surface(), key.face(), slim,
                    data.width, data.height, data.pixels);
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setColorArgb(x, y, targetArgb[y * w + x]);
            }
        }
        state.paintedTexture.upload();
    }

    private static SurfaceDef def(String name, Part part, Cuboid cuboid) {
        return new SurfaceDef(name, part, cuboid, cuboid);
    }

    private static SurfaceDef def(String name, Part part, Cuboid defaultCuboid, Cuboid slimCuboid) {
        return new SurfaceDef(name, part, defaultCuboid, slimCuboid);
    }

    private static Cuboid cuboid(double x, double y, double z, double width, double height, double depth) {
        return new Cuboid(x / 16.0D, (x + width) / 16.0D,
                y / 16.0D, (y + height) / 16.0D,
                z / 16.0D, (z + depth) / 16.0D);
    }

    private static boolean isAllZero(int[] pixels) {
        for (int pixel : pixels) {
            if (pixel != 0) return false;
        }
        return true;
    }

    private static int[] sanitizeFacePixels(int[] source, int width, int height) {
        int[] pixels = new int[Math.max(1, width * height)];
        if (source != null) {
            System.arraycopy(source, 0, pixels, 0, Math.min(source.length, pixels.length));
        }
        return pixels;
    }

    // ─── Records ───────────────────────────────────────────────────────────

    public record PlayerPaintHit(int entityId, String surface, Direction face, int x, int y, double distance) {
    }

    private record SurfaceDef(String name, Part part, Cuboid defaultCuboid, Cuboid slimCuboid) {
        private Cuboid cuboid(boolean slim) { return slim ? slimCuboid : defaultCuboid; }
    }

    private record Cuboid(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
    }

    private record BoxHit(double t, Vector3d point, Direction face) {
    }

    private record PlayerPose(PlayerEntityRenderer renderer, PlayerEntityRenderState state) {
    }

    private record PaintKey(String surface, Direction face) {
    }

    private enum Part {
        HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG
    }

    private static class PaintFaceData {
        final int[] pixels;
        final int width;
        final int height;

        PaintFaceData(int width, int height) {
            this.width = width;
            this.height = height;
            this.pixels = new int[width * height];
        }
    }

    // ─── PlayerPaintState ──────────────────────────────────────────────────

    private static class PlayerPaintState {
        private final Identifier originalTextureId;
        private final int[] originalPixels;
        private final int imageWidth;
        private final int imageHeight;
        private final Map<PaintKey, PaintFaceData> paintFaces = new HashMap<>();
        private NativeImageBackedTexture paintedTexture;
        private Identifier paintedTextureId;
        private boolean modified;

        PlayerPaintState(PlayerEntity player) {
            SkinTextures textures = player instanceof AbstractClientPlayerEntity cp
                    ? cp.getSkinTextures() : null;
            this.originalTextureId = textures != null ? textures.texture() : null;

            NativeImage sourceImage = loadSkinImage(originalTextureId);
            if (sourceImage == null) {
                sourceImage = loadSkinImage(DefaultSkinHelper.getSkinTextures(player.getUuid()).texture());
            }
            if (sourceImage == null) {
                sourceImage = loadSkinImage(DefaultSkinHelper.getSteve().texture());
            }
            if (sourceImage != null) {
                int w = sourceImage.getWidth();
                int h = sourceImage.getHeight();
                this.imageWidth = w;
                this.imageHeight = h;
                this.originalPixels = new int[w * h];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        this.originalPixels[y * w + x] = sourceImage.getColorArgb(x, y);
                    }
                }
                NativeImage copyImage = new NativeImage(w, h, false);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        copyImage.setColorArgb(x, y, this.originalPixels[y * w + x]);
                    }
                }
                this.paintedTextureId = Identifier.of("monvhua", "dynamic/player_paint/"
                        + Integer.toUnsignedString(Objects.hash(player.getUuid().toString()), 16));
                this.paintedTexture = new NativeImageBackedTexture(
                        () -> "monvhua painted player skin " + player.getName().getString(), copyImage);
                MinecraftClient.getInstance().getTextureManager().registerTexture(paintedTextureId, paintedTexture);
                this.modified = false;
            } else {
                this.imageWidth = 0;
                this.imageHeight = 0;
                this.originalPixels = new int[0];
                this.modified = false;
            }
        }

        boolean ready() {
            return paintedTexture != null && paintedTextureId != null && originalPixels.length > 0;
        }

        boolean hasModifications() {
            return ready() && modified && !paintFaces.isEmpty();
        }

        void markDirty() {
            this.modified = true;
        }

        void destroy() {
            if (paintedTexture != null && paintedTextureId != null) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(paintedTextureId);
                paintedTexture = null;
                paintedTextureId = null;
            }
        }

        private static NativeImage loadSkinImage(Identifier textureId) {
            if (textureId == null) return null;
            try {
                var texture = MinecraftClient.getInstance().getTextureManager().getTexture(textureId);
                if (texture instanceof NativeImageBackedTexture nativeTexture) {
                    NativeImage image = nativeTexture.getImage();
                    if (image != null) {
                        NativeImage copy = new NativeImage(image.getWidth(), image.getHeight(), false);
                        copy.copyFrom(image);
                        return copy;
                    }
                }
            } catch (Exception ignored) {
            }
            try {
                var resource = MinecraftClient.getInstance().getResourceManager()
                        .getResource(textureId);
                if (resource.isPresent()) {
                    return NativeImage.read(resource.get().getInputStream());
                }
            } catch (Exception ignored) {
            }
            return null;
        }
    }
}





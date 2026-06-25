package com.kuilunfuzhe.monvhua.renderer.bodypose.skeletal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.paint.SkinTexturePixels;
import com.kuilunfuzhe.monvhua.gui.body.bodypose.BodyPoseEditorFragment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4d;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BodyPoseSkeletalPreviewRenderer {
    private static final Identifier MODEL_ID = Identifier.of("monvhua", "models/entity/skeletal_model.bbmodel");
    private static final Identifier ANIMATION_ID = Identifier.of("monvhua", "models/entity/animation.json");
    private static final Identifier EYE_ANIMATION_ID = Identifier.of("monvhua", "models/entity/eye_on_off.json");
    private static final Identifier DRAG_ANIMATION_ID = Identifier.of("monvhua", "models/entity/drag_pose.json");
    private static final Set<String> DRAG_ROOT_BONES = Set.of("all_aontrol");
    private static final int DEFAULT_VERTEX_COLOR = 0xFFFFFFFF;
    private static final int SKIN_TONE_OVERLAY_COLOR = 0xFFFFF3EC;
    private static final int NOSE_VERTEX_COLOR = 0xFFFFEFEA;
    private static final float NOSE_U0 = 11.0F / 64.0F;
    private static final float NOSE_V0 = 13.0F / 64.0F;
    private static final float NOSE_U1 = 12.0F / 64.0F;
    private static final float NOSE_V1 = 14.0F / 64.0F;
    private static final Vector3f NOSE_ORIGIN = new Vector3f(0.0F, 37.9F, -3.001F);
    private static final Vector3f NOSE_LOCAL_A = new Vector3f(-1.075F, 0.125F, 0.991F);
    private static final Vector3f NOSE_LOCAL_B = new Vector3f(1.075F, 0.125F, 0.991F);
    private static final Vector3f NOSE_LOCAL_C = new Vector3f(1.075F, 1.875F, 0.991F);
    private static final Vector3f NOSE_LOCAL_D = new Vector3f(-1.075F, 1.875F, 0.991F);
    private static final float MODEL_UNIT = 16.0F;
    private static final float MODEL_Y_ORIGIN = 24.9207F;
    private static final float VOXEL_OUTER_THICKNESS = 0.5F;
    private static final float OUTER_LAYER_NORMAL_OFFSET = 0.002F;
    private static final double RAYCAST_EPSILON = 1.0E-7D;
    private static SkeletalModel cachedModel;
    private static boolean loadFailed;
    private static boolean loggedColoredVoxelStats;
    private static Map<String, float[]> cachedDragRotations;

    private BodyPoseSkeletalPreviewRenderer() {
    }

    public static boolean render(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                 Identifier texture, int light) {
        return render(matrices, vertexConsumers, texture, light, BodyPoseEditorFragment.getWorldSkeletalBoneRotations(),
                BodyPoseEditorFragment.getWorldSkeletalBoneOffsets(), BodyPoseEditorFragment.getWorldSkeletalBoneScales(),
                BodyPoseEditorFragment.getWorldVisibleSkeletalParts(), BodyPoseEditorFragment.isWorldSlimModel(),
                BodyPoseEditorFragment.getWorldHiddenSkeletalMeshes());
    }

    public static boolean renderEditorPreview(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                              Identifier texture, int light) {
        matrices.push();
        try {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(BodyPoseEditorFragment.getWorldModelPitch()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-BodyPoseEditorFragment.getWorldModelYaw()));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(BodyPoseEditorFragment.getWorldModelRoll()));
            float scale = BodyPoseEditorFragment.getWorldBodyScale();
            matrices.scale(scale, -scale, scale);
            return render(matrices, vertexConsumers, texture, light, BodyPoseEditorFragment.getWorldSkeletalBoneRotations(),
                    BodyPoseEditorFragment.getWorldSkeletalBoneOffsets(), BodyPoseEditorFragment.getWorldSkeletalBoneScales(),
                    BodyPoseEditorFragment.getWorldVisibleSkeletalParts(), BodyPoseEditorFragment.isWorldSlimModel(), true,
                    null, false, BodyPoseEditorFragment.getWorldHiddenSkeletalMeshes(), null);
        } finally {
            matrices.pop();
        }
    }

    public static boolean render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Identifier texture,
                                 int light, Map<String, float[]> rotations, Map<String, Float> scales,
                                 Set<String> visibleParts) {
        return render(matrices, vertexConsumers, texture, light, rotations, Map.of(), scales, visibleParts, true);
    }

    public static boolean render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Identifier texture,
                                 int light, Map<String, float[]> rotations, Map<String, float[]> offsets,
                                 Map<String, Float> scales, Set<String> visibleParts) {
        return render(matrices, vertexConsumers, texture, light, rotations, offsets, scales, visibleParts, true);
    }

    public static boolean render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Identifier texture,
                                 int light, Map<String, float[]> rotations, Map<String, float[]> offsets,
                                 Map<String, Float> scales, Set<String> visibleParts, boolean slim) {
        return render(matrices, vertexConsumers, texture, light, rotations, offsets, scales, visibleParts, slim, Set.of());
    }

    public static boolean render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Identifier texture,
                                 int light, Map<String, float[]> rotations, Map<String, float[]> offsets,
                                 Map<String, Float> scales, Set<String> visibleParts, boolean slim, RenderLayer renderLayer) {
        return render(matrices, vertexConsumers, texture, light, rotations, offsets, scales, visibleParts, slim, renderLayer, Set.of());
    }

    public static boolean render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Identifier texture,
                                 int light, Map<String, float[]> rotations, Map<String, float[]> offsets,
                                 Map<String, Float> scales, Set<String> visibleParts, boolean slim, Set<String> hiddenMeshes) {
        return render(matrices, vertexConsumers, texture, light, rotations, offsets, scales, visibleParts, slim, false,
                null, true, hiddenMeshes, null);
    }

    public static boolean render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Identifier texture,
                                 int light, Map<String, float[]> rotations, Map<String, float[]> offsets,
                                 Map<String, Float> scales, Set<String> visibleParts, boolean slim, RenderLayer renderLayer,
                                 Set<String> hiddenMeshes) {
        return render(matrices, vertexConsumers, texture, light, rotations, offsets, scales, visibleParts, slim, renderLayer,
                hiddenMeshes, null);
    }

    public static boolean render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Identifier texture,
                                 int light, Map<String, float[]> rotations, Map<String, float[]> offsets,
                                 Map<String, Float> scales, Set<String> visibleParts, boolean slim, RenderLayer renderLayer,
                                 Set<String> hiddenMeshes, NbtCompound customData) {
        return render(matrices, vertexConsumers, texture, light, rotations, offsets, scales, visibleParts, slim, true,
                renderLayer, true, hiddenMeshes, customData);
    }

    public static boolean renderDragPose(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                         Identifier texture, int light, boolean slim) {
        return renderDragPose(matrices, vertexConsumers, texture, light, slim, Set.of());
    }

    public static boolean renderDragPose(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                         Identifier texture, int light, boolean slim, Set<String> visibleParts) {
        return render(matrices, vertexConsumers, texture, light, getDragRotations(), Map.of(), Map.of(), visibleParts, slim);
    }

    public static PaintHit raycastModelPaint(Matrix4d modelToWorld, Vec3d eye, Vec3d end,
                                             NbtCompound customData, boolean slim) {
        SkeletalModel model = getModel();
        if (model == null || model.meshes.isEmpty() || modelToWorld == null || eye == null || end == null) {
            return null;
        }

        PoseState pose = poseStateFromNbt(customData);
        model.updatePose(pose.rotations(), pose.offsets(), pose.scales());
        Set<String> hiddenMeshes = normalizeMeshNames(pose.hiddenMeshes());

        Matrix4d worldToModel = new Matrix4d(modelToWorld).invert();
        Vector3d start = transformPosition(worldToModel, eye);
        Vector3d localEnd = transformPosition(worldToModel, end);
        Vector3d direction = new Vector3d(localEnd).sub(start);
        if (direction.lengthSquared() <= RAYCAST_EPSILON) {
            return null;
        }

        PaintHit best = null;
        for (Mesh mesh : model.meshes) {
            if (isMeshHidden(mesh, hiddenMeshes) || !isPaintableSkinMesh(mesh)) {
                continue;
            }
            PaintHit hit = raycastPaintMesh(mesh, model, start, direction, modelToWorld, eye,
                    pose.rotations(), pose.offsets(), pose.scales(), slim);
            if (hit != null && (best == null || hit.distance() < best.distance())) {
                best = hit;
            }
        }
        return best;
    }

    private static boolean render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Identifier texture,
                                  int light, Map<String, float[]> rotations, Map<String, float[]> offsets,
                                  Map<String, Float> scales, Set<String> visibleParts, boolean slim, boolean singleLayer,
                                  RenderLayer renderLayer, boolean coloredVoxelOuterLayers, Set<String> hiddenMeshes,
                                  NbtCompound customData) {
        SkeletalModel model = getModel();
        if (model == null || model.meshes.isEmpty()) {
            return false;
        }

        model.updatePose(rotations, offsets, scales);
        Set<String> normalizedHiddenMeshes = normalizeMeshNames(hiddenMeshes);
        SkinTexturePixels.PaintedSkinTexture paintedSkin = SkinTexturePixels.getWithModelPaint(texture, customData, slim);
        Identifier renderTexture = paintedSkin.textureId();
        SkinTexturePixels skinPixels = paintedSkin.pixels();

        RenderLayer skinLayer = renderLayer != null && renderTexture.equals(texture)
                ? renderLayer
                : RenderLayer.getEntityCutoutNoCull(renderTexture);
        RenderLayer solidColorLayer = singleLayer ? skinLayer : RenderLayer.getEntityCutoutNoCull(renderTexture);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        renderMeshes(model, matrix, vertexConsumers, skinLayer, solidColorLayer, light, visibleParts, normalizedHiddenMeshes, false,
                rotations, offsets, scales);
        if (coloredVoxelOuterLayers) {
            VoxelLayerRenderResult outerLayerResult = renderColoredVoxelSkinLayers(model, matrices, vertexConsumers.getBuffer(skinLayer), skinPixels, light,
                    visibleParts, slim, normalizedHiddenMeshes);
            logColoredVoxelStats(renderTexture, outerLayerResult);
            if (outerLayerResult.quadCount() <= 0) {
                renderMeshes(model, matrix, vertexConsumers, skinLayer, solidColorLayer, light, visibleParts, normalizedHiddenMeshes, true,
                        rotations, offsets, scales);
            }
        } else {
            renderMeshes(model, matrix, vertexConsumers, skinLayer, solidColorLayer, light, visibleParts, normalizedHiddenMeshes, true,
                    rotations, offsets, scales);
        }
        return true;
    }

    private static VoxelLayerRenderResult renderColoredVoxelSkinLayers(SkeletalModel model, MatrixStack matrices,
                                                                       VertexConsumer vertexConsumer, SkinTexturePixels pixels,
                                                                       int light, Set<String> visibleParts, boolean slim,
                                                                       Set<String> hiddenMeshes) {
        if (pixels == null) {
            return new VoxelLayerRenderResult(false, 0);
        }

        int quadCount = 0;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        for (Mesh mesh : model.meshes) {
            if (!mesh.outerLayer || (!visibleParts.isEmpty() && !visibleParts.contains(mesh.partName))) {
                continue;
            }
            if (isMeshHidden(mesh, hiddenMeshes)) {
                continue;
            }
            quadCount += renderMeshVoxelOuterLayer(mesh, model, matrix, vertexConsumer, pixels, light);
        }
        return new VoxelLayerRenderResult(true, quadCount);
    }

    private static int renderMeshVoxelOuterLayer(Mesh mesh, SkeletalModel model, Matrix4f matrix,
                                                 VertexConsumer vertices, SkinTexturePixels pixels, int light) {
        int quadCount = 0;
        Map<String, Vector3f> transformedVertices = new HashMap<>(mesh.vertices.size());
        for (Map.Entry<String, Vector3f> entry : mesh.vertices.entrySet()) {
            transformedVertices.put(entry.getKey(), toRenderPosition(transformVertex(mesh, entry.getKey(), entry.getValue(), model)));
        }

        for (Face face : mesh.faces) {
            if (face.vertices.size() < 3) {
                continue;
            }
            Vector3f p0 = transformedVertices.get(face.vertices.get(0));
            Vector3f p1 = transformedVertices.get(face.vertices.get(1));
            Vector3f p2 = transformedVertices.get(face.vertices.get(2));
            if (p0 == null || p1 == null || p2 == null) {
                continue;
            }

            Vector3f normal = new Vector3f(p1).sub(p0).cross(new Vector3f(p2).sub(p0));
            if (normal.lengthSquared() < 0.000001F) {
                normal.set(0.0F, 1.0F, 0.0F);
            } else {
                normal.normalize();
            }

            quadCount += renderFaceVoxelPixels(matrix, vertices, pixels, light,
                    p0, p1, p2,
                    face.uvs.get(face.vertices.get(0)),
                    face.uvs.get(face.vertices.get(1)),
                    face.uvs.get(face.vertices.get(2)),
                    normal);
            if (face.vertices.size() >= 4) {
                Vector3f p3 = transformedVertices.get(face.vertices.get(3));
                if (p3 != null) {
                    quadCount += renderFaceVoxelPixels(matrix, vertices, pixels, light,
                            p0, p2, p3,
                            face.uvs.get(face.vertices.get(0)),
                            face.uvs.get(face.vertices.get(2)),
                            face.uvs.get(face.vertices.get(3)),
                            normal);
                }
            }
        }
        return quadCount;
    }

    private static int renderFaceVoxelPixels(Matrix4f matrix, VertexConsumer vertices, SkinTexturePixels pixels, int light,
                                             Vector3f p0, Vector3f p1, Vector3f p2,
                                             Vector2f uv0, Vector2f uv1, Vector2f uv2, Vector3f normal) {
        if (uv0 == null || uv1 == null || uv2 == null) {
            return 0;
        }

        float minU = Math.min(uv0.x, Math.min(uv1.x, uv2.x));
        float maxU = Math.max(uv0.x, Math.max(uv1.x, uv2.x));
        float minV = Math.min(uv0.y, Math.min(uv1.y, uv2.y));
        float maxV = Math.max(uv0.y, Math.max(uv1.y, uv2.y));
        int x0 = Math.max(0, (int) Math.floor(minU * pixels.width()));
        int x1 = Math.min(pixels.width() - 1, (int) Math.ceil(maxU * pixels.width()) - 1);
        int y0 = Math.max(0, (int) Math.floor(minV * pixels.height()));
        int y1 = Math.min(pixels.height() - 1, (int) Math.ceil(maxV * pixels.height()) - 1);
        if (x1 < x0 || y1 < y0) {
            return 0;
        }

        Vector3f e1 = new Vector3f(p1).sub(p0);
        Vector3f e2 = new Vector3f(p2).sub(p0);
        Vector2f duv1 = new Vector2f(uv1).sub(uv0);
        Vector2f duv2 = new Vector2f(uv2).sub(uv0);
        float determinant = duv1.x * duv2.y - duv2.x * duv1.y;
        if (Math.abs(determinant) < 0.000001F) {
            return 0;
        }
        float invDet = 1.0F / determinant;
        Vector3f dPdu = new Vector3f(e1).mul(duv2.y).sub(new Vector3f(e2).mul(duv1.y)).mul(invDet / pixels.width());
        Vector3f dPdv = new Vector3f(e2).mul(duv1.x).sub(new Vector3f(e1).mul(duv2.x)).mul(invDet / pixels.height());

        int quadCount = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int argb = pixels.getArgb(x, y);
                if (((argb >>> 24) & 0xFF) < 128) {
                    continue;
                }
                float u = (x + 0.5F) / pixels.width();
                float v = (y + 0.5F) / pixels.height();
                if (!isUvInsideTriangle(u, v, uv0, uv1, uv2)) {
                    continue;
                }

                Vector3f center = interpolateFacePosition(p0, e1, e2, uv0, duv1, duv2, u, v, invDet);
                Vector3f halfU = new Vector3f(dPdu).mul(0.5F);
                Vector3f halfV = new Vector3f(dPdv).mul(0.5F);
                Vector3f offset = new Vector3f(normal).mul(VOXEL_OUTER_THICKNESS / MODEL_UNIT);
                Vector3f outer00 = new Vector3f(center).sub(halfU).sub(halfV);
                Vector3f outer10 = new Vector3f(center).add(halfU).sub(halfV);
                Vector3f outer11 = new Vector3f(center).add(halfU).add(halfV);
                Vector3f outer01 = new Vector3f(center).sub(halfU).add(halfV);
                Vector3f inner00 = new Vector3f(outer00).sub(offset);
                Vector3f inner10 = new Vector3f(outer10).sub(offset);
                Vector3f inner11 = new Vector3f(outer11).sub(offset);
                Vector3f inner01 = new Vector3f(outer01).sub(offset);
                float u0 = x / (float) pixels.width();
                float v0 = y / (float) pixels.height();
                float u1 = (x + 1) / (float) pixels.width();
                float v1 = (y + 1) / (float) pixels.height();
                emitVoxelTexturedQuad(matrix, vertices, outer00, outer10, outer11, outer01, normal, u0, v0, u1, v1, light);
                emitVoxelSolidUvQuad(matrix, vertices, outer00, outer10, inner10, inner00, new Vector3f(dPdv).negate().normalize(), u, v, light);
                emitVoxelSolidUvQuad(matrix, vertices, outer10, outer11, inner11, inner10, new Vector3f(dPdu).normalize(), u, v, light);
                emitVoxelSolidUvQuad(matrix, vertices, outer11, outer01, inner01, inner11, new Vector3f(dPdv).normalize(), u, v, light);
                emitVoxelSolidUvQuad(matrix, vertices, outer01, outer00, inner00, inner01, new Vector3f(dPdu).negate().normalize(), u, v, light);
                quadCount += 5;
            }
        }
        return quadCount;
    }

    private static boolean isUvInsideTriangle(float u, float v, Vector2f a, Vector2f b, Vector2f c) {
        float dX = u - c.x;
        float dY = v - c.y;
        float dX21 = c.x - b.x;
        float dY12 = b.y - c.y;
        float d = dY12 * (a.x - c.x) + dX21 * (a.y - c.y);
        float s = dY12 * dX + dX21 * dY;
        float t = (c.y - a.y) * dX + (a.x - c.x) * dY;
        if (d < 0.0F) {
            return s <= 0.00001F && t <= 0.00001F && s + t >= d - 0.00001F;
        }
        return s >= -0.00001F && t >= -0.00001F && s + t <= d + 0.00001F;
    }

    private static Vector3f interpolateFacePosition(Vector3f p0, Vector3f e1, Vector3f e2, Vector2f uv0,
                                                    Vector2f duv1, Vector2f duv2, float u, float v, float invDet) {
        float du = u - uv0.x;
        float dv = v - uv0.y;
        float a = (du * duv2.y - duv2.x * dv) * invDet;
        float b = (duv1.x * dv - du * duv1.y) * invDet;
        return new Vector3f(p0).add(new Vector3f(e1).mul(a)).add(new Vector3f(e2).mul(b));
    }

    private static void emitVoxelTexturedQuad(Matrix4f matrix, VertexConsumer vertices,
                                              Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, Vector3f normal,
                                              float u0, float v0, float u1, float v1, int light) {
        if (new Vector3f(p1).sub(p0).cross(new Vector3f(p2).sub(p0)).dot(normal) >= 0.0F) {
            emitVoxelVertex(matrix, vertices, p0, normal, u0, v0, light);
            emitVoxelVertex(matrix, vertices, p1, normal, u1, v0, light);
            emitVoxelVertex(matrix, vertices, p2, normal, u1, v1, light);
            emitVoxelVertex(matrix, vertices, p3, normal, u0, v1, light);
        } else {
            emitVoxelVertex(matrix, vertices, p0, normal, u0, v0, light);
            emitVoxelVertex(matrix, vertices, p3, normal, u0, v1, light);
            emitVoxelVertex(matrix, vertices, p2, normal, u1, v1, light);
            emitVoxelVertex(matrix, vertices, p1, normal, u1, v0, light);
        }
    }

    private static void emitVoxelSolidUvQuad(Matrix4f matrix, VertexConsumer vertices,
                                             Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, Vector3f normal,
                                             float u, float v, int light) {
        if (new Vector3f(p1).sub(p0).cross(new Vector3f(p2).sub(p0)).dot(normal) >= 0.0F) {
            emitVoxelVertex(matrix, vertices, p0, normal, u, v, light);
            emitVoxelVertex(matrix, vertices, p1, normal, u, v, light);
            emitVoxelVertex(matrix, vertices, p2, normal, u, v, light);
            emitVoxelVertex(matrix, vertices, p3, normal, u, v, light);
        } else {
            emitVoxelVertex(matrix, vertices, p0, normal, u, v, light);
            emitVoxelVertex(matrix, vertices, p3, normal, u, v, light);
            emitVoxelVertex(matrix, vertices, p2, normal, u, v, light);
            emitVoxelVertex(matrix, vertices, p1, normal, u, v, light);
        }
    }

    private static void emitVoxelVertex(Matrix4f matrix, VertexConsumer vertices, Vector3f point, Vector3f normal,
                                        float u, float v, int light) {
        vertices.vertex(matrix, point.x, point.y, point.z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(normal.x, normal.y, normal.z);
    }

    private record VoxelLayerRenderResult(boolean pixelsAvailable, int quadCount) {
    }

    public static void clearCache() {
        cachedModel = null;
        loadFailed = false;
        loggedColoredVoxelStats = false;
        cachedDragRotations = null;
    }

    private static void logColoredVoxelStats(Identifier texture, VoxelLayerRenderResult result) {
        if (loggedColoredVoxelStats) {
            return;
        }
        loggedColoredVoxelStats = true;
        MonvhuaMod.LOGGER.info("[Monvhua] true skeletal voxel outer layer: texture={}, pixelsAvailable={}, quadCount={}",
                texture, result.pixelsAvailable(), result.quadCount());
    }

    private record Point(float x, float y, float z) {
        Point add(Point other) {
            return new Point(x + other.x, y + other.y, z + other.z);
        }

        Point subtract(Point other) {
            return new Point(x - other.x, y - other.y, z - other.z);
        }

        Point multiply(float scalar) {
            return new Point(x * scalar, y * scalar, z * scalar);
        }

        Point negate() {
            return new Point(-x, -y, -z);
        }

        Point cross(Point other) {
            return new Point(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x
            );
        }

        float dot(Point other) {
            return x * other.x + y * other.y + z * other.z;
        }
    }

    private static void renderMeshes(SkeletalModel model, Matrix4f matrix, VertexConsumerProvider vertexConsumers,
                                     RenderLayer skinLayer, RenderLayer solidColorLayer,
                                     int light, Set<String> visibleParts, Set<String> hiddenMeshes, boolean outerLayer,
                                     Map<String, float[]> rotations, Map<String, float[]> offsets, Map<String, Float> scales) {
        boolean renderedNose = false;
        for (Mesh mesh : model.meshes) {
            if (mesh.outerLayer != outerLayer) {
                continue;
            }
            if (!visibleParts.isEmpty() && !visibleParts.contains(mesh.partName)) {
                continue;
            }
            if (isMeshHidden(mesh, hiddenMeshes)) {
                continue;
            }
            if ("nose".equals(mesh.name)) {
                if (!outerLayer) {
                    renderNoseOverlay(model, matrix, vertexConsumers.getBuffer(skinLayer), light, visibleParts);
                    renderedNose = true;
                }
                continue;
            }
            VertexConsumer vertexConsumer = vertexConsumers.getBuffer(mesh.usesSolidColorTexture ? solidColorLayer : skinLayer);
            renderMesh(mesh, model, matrix, vertexConsumer, light, rotations, offsets, scales);
        }
        if (!outerLayer && !renderedNose) {
            renderNoseOverlay(model, matrix, vertexConsumers.getBuffer(skinLayer), light, visibleParts);
        }
    }

    public static List<String> getEditableBoneNames() {
        SkeletalModel model = getModel();
        return model == null ? List.of() : model.editableBoneNames;
    }

    public static String getPartNameForBone(String boneName) {
        SkeletalModel model = getModel();
        if (model == null) {
            return boneName;
        }
        String lower = boneName.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("eye") || lower.contains("eyelid") || lower.contains("forehead")) {
            return "head";
        }
        return model.partNameByBone.getOrDefault(boneName, boneName);
    }

    public static List<String> getExtraEditablePoseTargetNames() {
        return List.of("eye_highlight", "eye_highlight_left", "eye_highlight_right");
    }

    public static Set<String> getHiddenMeshNamesForPoseTarget(String target) {
        String normalized = normalizeMeshName(target);
        return switch (normalized) {
            case "eye" -> Set.of("eye_left", "eye_right");
            case "eye_left" -> Set.of("eye_left");
            case "eye_right" -> Set.of("eye_right");
            case "eye_highlight" -> Set.of("eye_highlight_left", "eye_highlight_right");
            case "eye_highlight_left" -> Set.of("eye_highlight_left");
            case "eye_highlight_right" -> Set.of("eye_highlight_right");
            case "eyelid" -> Set.of("eyelid_left", "eyelid_right", "eyelid_cover_left", "eyelid_cover_right");
            case "eyelid_left" -> Set.of("eyelid_left", "eyelid_cover_left");
            case "eyelid_right" -> Set.of("eyelid_right", "eyelid_cover_right");
            default -> Set.of(normalized);
        };
    }

    public static Vector3f getPoseTargetRenderPosition(String target,
                                                       Map<String, float[]> rotations,
                                                       Map<String, float[]> offsets,
                                                       Map<String, Float> scales) {
        SkeletalModel model = getModel();
        if (model == null) {
            return null;
        }

        Map<String, float[]> safeRotations = rotations != null ? rotations : Map.of();
        Map<String, float[]> safeOffsets = offsets != null ? offsets : Map.of();
        Map<String, Float> safeScales = scales != null ? scales : Map.of();
        model.updatePose(safeRotations, safeOffsets, safeScales);

        String normalized = normalizeMeshName(target);
        Vector3f meshCenter = getMeshRenderCenter(model, normalized, safeRotations, safeOffsets, safeScales);
        if (meshCenter != null) {
            return meshCenter;
        }

        Bone bone = model.bonesByName.get(target);
        if (bone == null) {
            bone = model.bonesByName.get(normalized);
        }
        if (bone != null) {
            Vector3f weightedCenter = getBoneInfluenceRenderCenter(model, bone.name, safeRotations, safeOffsets, safeScales);
            if (weightedCenter != null) {
                return weightedCenter;
            }
            return toRenderPosition(transformBoneOrigin(model, bone));
        }
        return null;
    }

    public static Vector3f getPoseTargetBaseRenderPosition(String target) {
        SkeletalModel model = getModel();
        if (model == null) {
            return null;
        }

        model.updatePose(Map.of(), Map.of(), Map.of());
        String normalized = normalizeMeshName(target);
        Vector3f meshCenter = getMeshRenderCenter(model, normalized, Map.of(), Map.of(), Map.of());
        if (meshCenter != null) {
            return meshCenter;
        }

        Bone bone = model.bonesByName.get(target);
        if (bone == null) {
            bone = model.bonesByName.get(normalized);
        }
        if (bone == null) {
            return null;
        }
        Vector3f weightedCenter = getBoneInfluenceRenderCenter(model, bone.name, Map.of(), Map.of(), Map.of());
        if (weightedCenter != null) {
            return weightedCenter;
        }
        return toRenderPosition(transformBoneOrigin(model, bone));
    }

    private static SkeletalModel getModel() {
        if (cachedModel != null) {
            return cachedModel;
        }
        if (loadFailed) {
            return null;
        }

        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return null;
            }
            Optional<Resource> modelResource = client.getResourceManager().getResource(MODEL_ID);
            if (modelResource.isEmpty()) {
                loadFailed = true;
                return null;
            }

            JsonObject modelJson;
            try (InputStreamReader reader = new InputStreamReader(modelResource.get().getInputStream(), StandardCharsets.UTF_8)) {
                modelJson = JsonParser.parseReader(reader).getAsJsonObject();
            }

            AnimationPose animation = loadAnimation(client);
            cachedModel = SkeletalModel.load(modelJson, animation);
            return cachedModel;
        } catch (Exception e) {
            loadFailed = true;
            System.err.println("[Monvhua] Failed to load skeletal body pose preview model: " + e.getMessage());
            return null;
        }
    }

    private static AnimationPose loadAnimation(MinecraftClient client) {
        AnimationPose pose = new AnimationPose();
        loadAnimationResource(client, ANIMATION_ID, pose);
        loadAnimationResource(client, EYE_ANIMATION_ID, pose);
        return pose;
    }

    private static void loadAnimationResource(MinecraftClient client, Identifier id, AnimationPose pose) {
        try {
            Optional<Resource> resource = client.getResourceManager().getResource(id);
            if (resource.isEmpty()) {
                return;
            }
            JsonObject root;
            try (InputStreamReader reader = new InputStreamReader(resource.get().getInputStream(), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            JsonArray animations = getArray(root, "animations");
            for (JsonElement element : animations) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject animation = element.getAsJsonObject();
                String bone = getString(animation, "bone", "");
                String target = getString(animation, "target", "");
                JsonArray keyframes = getArray(animation, "keyframes");
                if (bone.isBlank() || keyframes.isEmpty()) {
                    continue;
                }
                JsonObject keyframe = keyframes.get(0).getAsJsonObject();
                JsonArray targetArray = getArray(keyframe, "target");
                if (targetArray.size() >= 3) {
                    float[] values = new float[] {
                            getFloat(targetArray, 0),
                            getFloat(targetArray, 1),
                            getFloat(targetArray, 2)
                    };
                    if ("rotation".equals(target)) {
                        pose.rotations.put(bone, values);
                    } else if ("position".equals(target)) {
                        pose.offsets.put(bone, values);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Monvhua] Failed to load skeletal body pose animation " + id + ": " + e.getMessage());
        }
    }

    private static Map<String, float[]> getDragRotations() {
        if (cachedDragRotations != null) {
            return cachedDragRotations;
        }

        AnimationPose pose = new AnimationPose();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            loadAnimationResource(client, DRAG_ANIMATION_ID, pose);
        }
        pose.rotations.keySet().removeIf(DRAG_ROOT_BONES::contains);
        cachedDragRotations = Map.copyOf(pose.rotations);
        return cachedDragRotations;
    }

    private static final class AnimationPose {
        private final Map<String, float[]> rotations = new HashMap<>();
        private final Map<String, float[]> offsets = new HashMap<>();
    }

    public record PaintHit(String surface, Direction face, int x, int y, double distance) {
    }

    private record TriangleHit(double t, double u, double v) {
    }

    private record PoseState(Map<String, float[]> rotations,
                             Map<String, float[]> offsets,
                             Map<String, Float> scales,
                             Set<String> hiddenMeshes) {
        private static final PoseState EMPTY = new PoseState(Map.of(), Map.of(), Map.of(), Set.of());
    }

    private static void renderMesh(Mesh mesh, SkeletalModel model, Matrix4f matrix,
                                   VertexConsumer vertexConsumer, int light,
                                   Map<String, float[]> rotations, Map<String, float[]> offsets, Map<String, Float> scales) {
        Map<String, Vector3f> transformedVertices = new HashMap<>(mesh.vertices.size());
        for (Map.Entry<String, Vector3f> entry : mesh.vertices.entrySet()) {
            transformedVertices.put(entry.getKey(), transformVertex(mesh, entry.getKey(), entry.getValue(), model));
        }
        applyMeshPose(mesh, transformedVertices, rotations, offsets, scales);
        transformedVertices.replaceAll((ignored, position) -> toRenderPosition(position));

        for (Face face : mesh.faces) {
            if (face.vertices.size() < 3) {
                continue;
            }
            Vector3f p0 = transformedVertices.get(face.vertices.get(0));
            Vector3f p1 = transformedVertices.get(face.vertices.get(1));
            Vector3f p2 = transformedVertices.get(face.vertices.get(2));
            if (p0 == null || p1 == null || p2 == null) {
                continue;
            }

            Vector3f normal = new Vector3f(p1).sub(p0).cross(new Vector3f(p2).sub(p0));
            if (normal.lengthSquared() < 0.000001F) {
                normal.set(0.0F, 1.0F, 0.0F);
            } else {
                normal.normalize();
            }

            Vector3f rp0 = offsetOuterLayerVertex(mesh, p0, normal);
            Vector3f rp1 = offsetOuterLayerVertex(mesh, p1, normal);
            Vector3f rp2 = offsetOuterLayerVertex(mesh, p2, normal);
            emitFaceVertex(vertexConsumer, matrix, rp0, face.uvs.get(face.vertices.get(0)), normal, light, mesh.vertexColor);
            emitFaceVertex(vertexConsumer, matrix, rp1, face.uvs.get(face.vertices.get(1)), normal, light, mesh.vertexColor);
            emitFaceVertex(vertexConsumer, matrix, rp2, face.uvs.get(face.vertices.get(2)), normal, light, mesh.vertexColor);

            if (face.vertices.size() >= 4) {
                Vector3f p3 = transformedVertices.get(face.vertices.get(3));
                Vector3f rp3 = offsetOuterLayerVertex(mesh, p3 != null ? p3 : p2, normal);
                Vector2f uv3 = face.uvs.get(face.vertices.get(3));
                emitFaceVertex(vertexConsumer, matrix, rp3, uv3, normal, light, mesh.vertexColor);
            } else {
                emitFaceVertex(vertexConsumer, matrix, rp2, face.uvs.get(face.vertices.get(2)), normal, light, mesh.vertexColor);
            }
        }
    }

    private static PaintHit raycastPaintMesh(Mesh mesh, SkeletalModel model, Vector3d start, Vector3d direction,
                                             Matrix4d modelToWorld, Vec3d eye,
                                             Map<String, float[]> rotations, Map<String, float[]> offsets,
                                             Map<String, Float> scales, boolean slim) {
        Map<String, Vector3f> transformedVertices = new HashMap<>(mesh.vertices.size());
        for (Map.Entry<String, Vector3f> entry : mesh.vertices.entrySet()) {
            transformedVertices.put(entry.getKey(), transformVertex(mesh, entry.getKey(), entry.getValue(), model));
        }
        applyMeshPose(mesh, transformedVertices, rotations, offsets, scales);
        transformedVertices.replaceAll((ignored, position) -> toRenderPosition(position));

        PaintHit best = null;
        for (Face face : mesh.faces) {
            if (face.vertices.size() < 3) {
                continue;
            }
            PaintHit hit = raycastPaintTriangle(mesh, start, direction, modelToWorld, eye, transformedVertices, face,
                    0, 1, 2, slim);
            if (hit != null && (best == null || hit.distance() < best.distance())) {
                best = hit;
            }
            if (face.vertices.size() >= 4) {
                PaintHit secondHit = raycastPaintTriangle(mesh, start, direction, modelToWorld, eye,
                        transformedVertices, face, 0, 2, 3, slim);
                if (secondHit != null && (best == null || secondHit.distance() < best.distance())) {
                    best = secondHit;
                }
            }
        }
        return best;
    }

    private static PaintHit raycastPaintTriangle(Mesh mesh, Vector3d start, Vector3d direction,
                                                 Matrix4d modelToWorld, Vec3d eye,
                                                 Map<String, Vector3f> transformedVertices, Face face,
                                                 int index0, int index1, int index2, boolean slim) {
        String vertexId0 = face.vertices.get(index0);
        String vertexId1 = face.vertices.get(index1);
        String vertexId2 = face.vertices.get(index2);
        Vector3f p0 = transformedVertices.get(vertexId0);
        Vector3f p1 = transformedVertices.get(vertexId1);
        Vector3f p2 = transformedVertices.get(vertexId2);
        Vector2f uv0 = face.uvs.get(vertexId0);
        Vector2f uv1 = face.uvs.get(vertexId1);
        Vector2f uv2 = face.uvs.get(vertexId2);
        if (p0 == null || p1 == null || p2 == null || uv0 == null || uv1 == null || uv2 == null) {
            return null;
        }

        TriangleHit triangleHit = intersectPaintTriangle(start, direction, p0, p1, p2, uv0, uv1, uv2);
        if (triangleHit == null) {
            return null;
        }
        SkinTexturePixels.PaintSurfacePixel pixel = SkinTexturePixels.paintSurfacePixel(
                (float) triangleHit.u(), (float) triangleHit.v(), mesh.outerLayer, slim);
        if (pixel == null) {
            return null;
        }

        Vector3d localPoint = new Vector3d(start).fma(triangleHit.t(), direction);
        Vector3d worldPoint = transformPosition(modelToWorld, localPoint);
        double distance = Math.sqrt(worldPoint.distanceSquared(eye.x, eye.y, eye.z));
        return new PaintHit(pixel.surface(), pixel.face(), pixel.x(), pixel.y(), distance);
    }

    private static TriangleHit intersectPaintTriangle(Vector3d start, Vector3d direction,
                                                      Vector3f p0, Vector3f p1, Vector3f p2,
                                                      Vector2f uv0, Vector2f uv1, Vector2f uv2) {
        Vector3d v0 = new Vector3d(p0.x, p0.y, p0.z);
        Vector3d edge1 = new Vector3d(p1.x, p1.y, p1.z).sub(v0);
        Vector3d edge2 = new Vector3d(p2.x, p2.y, p2.z).sub(v0);
        Vector3d pvec = new Vector3d(direction).cross(edge2);
        double determinant = edge1.dot(pvec);
        if (Math.abs(determinant) < RAYCAST_EPSILON) {
            return null;
        }

        double inverseDeterminant = 1.0D / determinant;
        Vector3d tvec = new Vector3d(start).sub(v0);
        double baryU = tvec.dot(pvec) * inverseDeterminant;
        if (baryU < -RAYCAST_EPSILON || baryU > 1.0D + RAYCAST_EPSILON) {
            return null;
        }

        Vector3d qvec = new Vector3d(tvec).cross(edge1);
        double baryV = direction.dot(qvec) * inverseDeterminant;
        if (baryV < -RAYCAST_EPSILON || baryU + baryV > 1.0D + RAYCAST_EPSILON) {
            return null;
        }

        double t = edge2.dot(qvec) * inverseDeterminant;
        if (t < -RAYCAST_EPSILON || t > 1.0D + RAYCAST_EPSILON) {
            return null;
        }

        double baryW = 1.0D - baryU - baryV;
        double u = uv0.x * baryW + uv1.x * baryU + uv2.x * baryV;
        double v = uv0.y * baryW + uv1.y * baryU + uv2.y * baryV;
        return new TriangleHit(Math.max(0.0D, Math.min(1.0D, t)), u, v);
    }

    private static boolean isPaintableSkinMesh(Mesh mesh) {
        return mesh.vertexColor == DEFAULT_VERTEX_COLOR
                && !mesh.usesSolidColorTexture
                && !"nose".equals(mesh.normalizedName);
    }

    private static void applyMeshPose(Mesh mesh, Map<String, Vector3f> vertices,
                                      Map<String, float[]> rotations, Map<String, float[]> offsets, Map<String, Float> scales) {
        List<String> targets = getMeshPoseTargets(mesh);
        if (targets.isEmpty()) {
            return;
        }
        float[] rotation = sumVectorPoseValues(rotations, targets);
        float[] offset = sumVectorPoseValues(offsets, targets);
        float scale = multiplyScalePoseValues(scales, targets);
        boolean hasRotation = rotation[0] != 0.0F || rotation[1] != 0.0F || rotation[2] != 0.0F;
        boolean hasOffset = offset[0] != 0.0F || offset[1] != 0.0F || offset[2] != 0.0F;
        boolean hasScale = scale != 1.0F;
        if (!hasRotation && !hasOffset && !hasScale) {
            return;
        }

        Vector3f center = new Vector3f();
        int count = 0;
        for (Vector3f vertex : vertices.values()) {
            center.add(vertex);
            count++;
        }
        if (count <= 0) {
            return;
        }
        center.div(count);
        Quaternionf quaternion = createRotation(rotation[0], rotation[1], rotation[2]);
        Vector3f offsetVector = new Vector3f(offset[0], offset[1], offset[2]);
        for (Vector3f vertex : vertices.values()) {
            vertex.sub(center);
            if (hasScale) {
                vertex.mul(scale);
            }
            if (hasRotation) {
                quaternion.transform(vertex);
            }
            vertex.add(center).add(offsetVector);
        }
    }

    private static float[] sumVectorPoseValues(Map<String, float[]> values, List<String> targets) {
        float[] result = new float[] { 0.0F, 0.0F, 0.0F };
        if (values == null || values.isEmpty()) {
            return result;
        }
        for (String target : targets) {
            float[] value = values.get(target);
            if (value == null) {
                continue;
            }
            result[0] += value.length > 0 ? value[0] : 0.0F;
            result[1] += value.length > 1 ? value[1] : 0.0F;
            result[2] += value.length > 2 ? value[2] : 0.0F;
        }
        return result;
    }

    private static float multiplyScalePoseValues(Map<String, Float> values, List<String> targets) {
        if (values == null || values.isEmpty()) {
            return 1.0F;
        }
        float result = 1.0F;
        for (String target : targets) {
            result *= Math.max(0.02F, values.getOrDefault(target, 1.0F));
        }
        return result;
    }

    private static List<String> getMeshPoseTargets(Mesh mesh) {
        String normalized = mesh.normalizedName;
        if ("eye_highlight_left".equals(normalized)) {
            return List.of("eye_highlight", "eye_highlight_left");
        }
        if ("eye_highlight_right".equals(normalized)) {
            return List.of("eye_highlight", "eye_highlight_right");
        }
        return List.of();
    }

    private static Set<String> normalizeMeshNames(Set<String> meshNames) {
        if (meshNames == null || meshNames.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        for (String meshName : meshNames) {
            if (meshName != null && !meshName.isBlank()) {
                normalized.add(normalizeMeshName(meshName));
            }
        }
        return normalized;
    }

    private static boolean isMeshHidden(Mesh mesh, Set<String> hiddenMeshes) {
        return hiddenMeshes != null && !hiddenMeshes.isEmpty() && hiddenMeshes.contains(mesh.normalizedName);
    }

    private static String normalizeMeshName(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(java.util.Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_')
                .replace("hightlight", "highlight");
    }

    private static void renderNoseOverlay(SkeletalModel model, Matrix4f matrix, VertexConsumer vertexConsumer,
                                          int light, Set<String> visibleParts) {
        if (!visibleParts.isEmpty() && !visibleParts.contains("head")) {
            return;
        }
        Bone head = model.bonesByName.get("head");
        if (head == null) {
            return;
        }

        Vector3f a = toRenderPosition(transformFixedPoint(head, NOSE_LOCAL_A));
        Vector3f b = toRenderPosition(transformFixedPoint(head, NOSE_LOCAL_B));
        Vector3f c = toRenderPosition(transformFixedPoint(head, NOSE_LOCAL_C));
        Vector3f d = toRenderPosition(transformFixedPoint(head, NOSE_LOCAL_D));
        Vector3f normal = new Vector3f(0.0F, 0.0F, -1.0F);

        emitFaceVertex(vertexConsumer, matrix, a, new Vector2f(NOSE_U0, NOSE_V1), normal, light, NOSE_VERTEX_COLOR);
        emitFaceVertex(vertexConsumer, matrix, b, new Vector2f(NOSE_U1, NOSE_V1), normal, light, NOSE_VERTEX_COLOR);
        emitFaceVertex(vertexConsumer, matrix, c, new Vector2f(NOSE_U1, NOSE_V0), normal, light, NOSE_VERTEX_COLOR);
        emitFaceVertex(vertexConsumer, matrix, d, new Vector2f(NOSE_U0, NOSE_V0), normal, light, NOSE_VERTEX_COLOR);
    }

    private static Vector3f transformFixedPoint(Bone head, Vector3f localPosition) {
        Vector3f transformed = new Vector3f(localPosition).add(NOSE_ORIGIN);
        head.skinMatrix.transformPosition(transformed);
        return transformed;
    }

    private static Vector3f offsetOuterLayerVertex(Mesh mesh, Vector3f position, Vector3f normal) {
        if (!mesh.outerLayer) {
            return position;
        }
        return new Vector3f(position).add(new Vector3f(normal).mul(OUTER_LAYER_NORMAL_OFFSET));
    }

    private static Vector3f transformVertex(Mesh mesh, String vertexId, Vector3f localPosition, SkeletalModel model) {
        Vector3f modelPosition = new Vector3f(localPosition).add(mesh.origin);
        List<VertexWeight> weights = mesh.weights.get(vertexId);
        if (weights == null || weights.isEmpty()) {
            return modelPosition;
        }

        Vector3f result = new Vector3f();
        float total = 0.0F;
        for (VertexWeight weight : weights) {
            Bone bone = model.bonesByName.get(weight.boneName);
            if (bone == null || weight.weight <= 0.0F) {
                continue;
            }
            Vector3f transformed = new Vector3f(modelPosition);
            bone.skinMatrix.transformPosition(transformed);
            transformed.add(model.getAccumulatedModelSpaceOffset(bone));
            result.add(transformed.mul(weight.weight));
            total += weight.weight;
        }
        if (total <= 0.0F) {
            return modelPosition;
        }
        if (total < 0.999F || total > 1.001F) {
            result.div(total);
        }
        return result;
    }

    private static Vector3f transformBoneOrigin(SkeletalModel model, Bone bone) {
        Vector3f modelPosition = new Vector3f();
        bone.currentWorldMatrix.transformPosition(modelPosition);
        modelPosition.add(model.getAccumulatedModelSpaceOffset(bone));
        return modelPosition;
    }

    private static Map<String, Vector3f> getTransformedMeshVertices(Mesh mesh, SkeletalModel model,
                                                                    Map<String, float[]> rotations,
                                                                    Map<String, float[]> offsets,
                                                                    Map<String, Float> scales) {
        Map<String, Vector3f> transformedVertices = new HashMap<>(mesh.vertices.size());
        for (Map.Entry<String, Vector3f> entry : mesh.vertices.entrySet()) {
            transformedVertices.put(entry.getKey(), transformVertex(mesh, entry.getKey(), entry.getValue(), model));
        }
        applyMeshPose(mesh, transformedVertices, rotations, offsets, scales);
        return transformedVertices;
    }

    private static PoseState poseStateFromNbt(NbtCompound customData) {
        if (customData == null) {
            return PoseState.EMPTY;
        }
        Map<String, float[]> rotations = new HashMap<>();
        Map<String, float[]> offsets = new HashMap<>();
        Map<String, Float> scales = new HashMap<>();
        Set<String> hiddenMeshes = new HashSet<>();
        NbtList bones = customData.getListOrEmpty("true_skeletal_bones");
        for (int i = 0; i < bones.size(); i++) {
            NbtCompound bone = bones.getCompound(i).orElse(null);
            if (bone == null) {
                continue;
            }
            String name = bone.getString("name", "");
            if (name.isEmpty()) {
                continue;
            }
            float pitch = bone.getFloat("pitch", 0.0F);
            float yaw = bone.getFloat("yaw", 0.0F);
            float roll = bone.getFloat("roll", 0.0F);
            float offsetX = bone.getFloat("offset_x", 0.0F);
            float offsetY = bone.getFloat("offset_y", 0.0F);
            float offsetZ = bone.getFloat("offset_z", 0.0F);
            float scale = Math.max(0.1F, bone.getFloat("scale", 1.0F));
            boolean visible = bone.getBoolean("visible", true);
            if (!visible) {
                hiddenMeshes.addAll(getHiddenMeshNamesForPoseTarget(name));
            }
            if (pitch != 0.0F || yaw != 0.0F || roll != 0.0F) {
                rotations.put(name, new float[]{ pitch, yaw, roll });
            }
            if (offsetX != 0.0F || offsetY != 0.0F || offsetZ != 0.0F) {
                offsets.put(name, new float[]{ offsetX, offsetY, offsetZ });
            }
            if (scale != 1.0F) {
                scales.put(name, scale);
            }
        }
        return new PoseState(rotations, offsets, scales, hiddenMeshes);
    }

    private static Vector3f getMeshRenderCenter(SkeletalModel model, String normalizedTarget,
                                                Map<String, float[]> rotations, Map<String, float[]> offsets,
                                                Map<String, Float> scales) {
        Set<String> targetNames = getMeshNamesForPoseTarget(normalizedTarget);
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        int count = 0;
        for (Mesh mesh : model.meshes) {
            if (!targetNames.contains(mesh.normalizedName)) {
                continue;
            }
            Map<String, Vector3f> transformedVertices = getTransformedMeshVertices(mesh, model, rotations, offsets, scales);
            for (Vector3f position : transformedVertices.values()) {
                min.min(position);
                max.max(position);
                count++;
            }
        }
        if (count <= 0) {
            return null;
        }
        return toRenderPosition(min.add(max).mul(0.5F));
    }

    private static Vector3f getBoneInfluenceRenderCenter(SkeletalModel model, String boneName,
                                                         Map<String, float[]> rotations, Map<String, float[]> offsets,
                                                         Map<String, Float> scales) {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        int count = 0;
        for (Mesh mesh : model.meshes) {
            if (!mesh.hasVertexWeightedTo(boneName)) {
                continue;
            }
            Map<String, Vector3f> transformedVertices = getTransformedMeshVertices(mesh, model, rotations, offsets, scales);
            for (String vertexId : mesh.getVertexIdsWeightedTo(boneName)) {
                Vector3f position = transformedVertices.get(vertexId);
                if (position == null) {
                    continue;
                }
                min.min(position);
                max.max(position);
                count++;
            }
        }
        if (count <= 0) {
            return null;
        }
        return toRenderPosition(min.add(max).mul(0.5F));
    }

    private static Set<String> getMeshNamesForPoseTarget(String normalizedTarget) {
        return switch (normalizedTarget) {
            case "eye" -> Set.of("eye_left", "eye_right");
            case "eye_left" -> Set.of("eye_left");
            case "eye_right" -> Set.of("eye_right");
            case "eye_highlight" -> Set.of("eye_highlight_left", "eye_highlight_right");
            case "eye_highlight_left" -> Set.of("eye_highlight_left");
            case "eye_highlight_right" -> Set.of("eye_highlight_right");
            case "eyelid" -> Set.of("eyelid_left", "eyelid_right", "eyelid_cover_left", "eyelid_cover_right");
            case "eyelid_left" -> Set.of("eyelid_left", "eyelid_cover_left");
            case "eyelid_right" -> Set.of("eyelid_right", "eyelid_cover_right");
            case "head" -> Set.of("head", "hat_layer", "forehead", "chin", "eye_white_left", "eye_white_right");
            case "torso" -> Set.of("body", "body_layer");
            case "left_arm" -> Set.of("left_arm", "left_arm_layer");
            case "right_arm" -> Set.of("right_arm", "right_arm_layer");
            case "left_leg" -> Set.of("left_leg", "left_leg_layer");
            case "right_leg" -> Set.of("right_leg", "right_leg_layer");
            default -> Set.of(normalizedTarget);
        };
    }

    private static Vector3f toRenderPosition(Vector3f modelPosition) {
        return new Vector3f(
                modelPosition.x / MODEL_UNIT,
                (modelPosition.y - MODEL_Y_ORIGIN) / MODEL_UNIT,
                modelPosition.z / MODEL_UNIT
        );
    }

    private static Vector3d transformPosition(Matrix4d matrix, Vec3d pos) {
        return transformPosition(matrix, new Vector3d(pos.x, pos.y, pos.z));
    }

    private static Vector3d transformPosition(Matrix4d matrix, Vector3d pos) {
        Vector4d result = matrix.transform(new Vector4d(pos.x, pos.y, pos.z, 1.0D));
        return new Vector3d(result.x, result.y, result.z);
    }

    private static void emitFaceVertex(VertexConsumer vertexConsumer, Matrix4f matrix, Vector3f position,
                                       Vector2f uv, Vector3f normal, int light, int color) {
        Vector2f resolvedUv = uv != null ? uv : new Vector2f();
        vertexConsumer.vertex(matrix, position.x, position.y, position.z)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF)
                .texture(resolvedUv.x, resolvedUv.y)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(normal.x, normal.y, normal.z);
    }

    private static Quaternionf createRotation(float pitch, float yaw, float roll) {
        return new Quaternionf()
                .rotateZ((float) Math.toRadians(roll))
                .rotateY((float) Math.toRadians(yaw))
                .rotateX((float) Math.toRadians(pitch));
    }

    private static Vector3f getVec3(JsonObject object, String name) {
        JsonArray array = getArray(object, name);
        return new Vector3f(getFloat(array, 0), getFloat(array, 1), getFloat(array, 2));
    }

    private static JsonArray getArray(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static float getFloat(JsonArray array, int index) {
        if (index < 0 || index >= array.size()) {
            return 0.0F;
        }
        try {
            return array.get(index).getAsFloat();
        } catch (Exception ignored) {
            return 0.0F;
        }
    }

    private static String getString(JsonObject object, String name, String fallback) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static final class SkeletalModel {
        private final List<Mesh> meshes = new ArrayList<>();
        private final Map<String, Bone> bonesByUuid = new LinkedHashMap<>();
        private final Map<String, Bone> bonesByName = new LinkedHashMap<>();
        private final List<String> editableBoneNames = new ArrayList<>();
        private final Map<String, String> partNameByBone = new HashMap<>();
        private final AnimationPose animationPose;

        private SkeletalModel(AnimationPose animationPose) {
            this.animationPose = animationPose;
        }

        static SkeletalModel load(JsonObject root, AnimationPose animationPose) {
            SkeletalModel model = new SkeletalModel(animationPose);
            Map<String, Mesh> meshesByPrefix = new HashMap<>();
            JsonArray elements = getArray(root, "elements");
            float uvWidth = 16.0F;
            float uvHeight = 16.0F;
            JsonArray textures = getArray(root, "textures");
            if (!textures.isEmpty() && textures.get(0).isJsonObject()) {
                JsonObject texture = textures.get(0).getAsJsonObject();
                uvWidth = Math.max(1.0F, getFloatValue(texture, "uv_width", 16.0F));
                uvHeight = Math.max(1.0F, getFloatValue(texture, "uv_height", 16.0F));
            }

            for (JsonElement element : elements) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                String type = getString(object, "type", "");
                if ("mesh".equals(type)) {
                    Mesh mesh = Mesh.load(object, uvWidth, uvHeight);
                    model.meshes.add(mesh);
                    meshesByPrefix.put(mesh.uuid.length() >= 6 ? mesh.uuid.substring(0, 6) : mesh.uuid, mesh);
                } else if ("armature_bone".equals(type)) {
                    Bone bone = Bone.load(object);
                    model.bonesByUuid.put(bone.uuid, bone);
                    model.bonesByName.put(bone.name, bone);
                }
            }

            for (JsonElement element : elements) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                if (!"armature_bone".equals(getString(object, "type", ""))) {
                    continue;
                }
                Bone parent = model.bonesByUuid.get(getString(object, "uuid", ""));
                for (JsonElement childElement : getArray(object, "children")) {
                    Bone child = model.bonesByUuid.get(childElement.getAsString());
                    if (child != null) {
                        child.parent = parent;
                        parent.children.add(child);
                    }
                }
                JsonObject weights = object.has("vertex_weights") && object.get("vertex_weights").isJsonObject()
                        ? object.getAsJsonObject("vertex_weights") : new JsonObject();
                for (Map.Entry<String, JsonElement> weightEntry : weights.entrySet()) {
                    String[] keyParts = weightEntry.getKey().split(":", 2);
                    if (keyParts.length != 2) {
                        continue;
                    }
                    Mesh mesh = meshesByPrefix.get(keyParts[0]);
                    if (mesh == null || !mesh.acceptsBone(parent.name)) {
                        continue;
                    }
                    mesh.weights.computeIfAbsent(keyParts[1], ignored -> new ArrayList<>())
                            .add(new VertexWeight(parent.name, Math.max(0.0F, weightEntry.getValue().getAsFloat())));
                }
            }

            for (Mesh mesh : model.meshes) {
                mesh.applyForcedWeights();
                mesh.normalizeWeights();
                for (List<VertexWeight> weights : mesh.weights.values()) {
                    for (VertexWeight weight : weights) {
                        model.partNameByBone.putIfAbsent(weight.boneName, mesh.partName);
                    }
                }
            }
            model.meshes.sort(java.util.Comparator.comparingInt(mesh -> mesh.renderPriority));
            for (String boneName : List.of(
                    "torso_low", "torso_midium", "torso_on", "head",
                    "eye", "eye_left", "eye_right", "eyelid", "eyelid_left", "eyelid_right",
                    "left_arm_on", "left_arm_low", "right_arm_on", "right_arm_low",
                    "left_leg_on", "left_leg_low", "right_leg_on", "right_leg_low")) {
                if (model.bonesByName.containsKey(boneName)) {
                    model.editableBoneNames.add(boneName);
                }
            }
            for (String boneName : model.bonesByName.keySet()) {
                if (!"all_aontrol".equals(boneName) && !model.editableBoneNames.contains(boneName)) {
                    model.editableBoneNames.add(boneName);
                }
            }
            model.updatePose(Map.of(), Map.of(), Map.of());
            return model;
        }

        void updatePose(Map<String, float[]> editorRotations, Map<String, float[]> editorOffsets, Map<String, Float> editorScales) {
            for (Bone bone : bonesByName.values()) {
                float[] baseRotation = animationPose.rotations.get(bone.name);
                float[] editorRotation = editorRotations.get(bone.name);
                bone.currentRotation.set(
                        bone.restRotation.x + value(baseRotation, 0) + value(editorRotation, 0),
                        bone.restRotation.y + value(baseRotation, 1) + value(editorRotation, 1),
                        bone.restRotation.z + value(baseRotation, 2) + value(editorRotation, 2)
                );
                float[] baseOffset = animationPose.offsets.get(bone.name);
                float[] editorOffset = editorOffsets.get(bone.name);
                Vector3f resolvedOffset = new Vector3f(
                        value(baseOffset, 0) + value(editorOffset, 0),
                        value(baseOffset, 1) + value(editorOffset, 1),
                        value(baseOffset, 2) + value(editorOffset, 2)
                );
                if (isModelSpaceOffsetBone(bone.name)) {
                    bone.currentOffset.zero();
                    bone.currentModelSpaceOffset.set(resolvedOffset);
                } else {
                    bone.currentOffset.set(resolvedOffset);
                    bone.currentModelSpaceOffset.zero();
                }
                bone.currentScale = Math.max(0.02F, editorScales.getOrDefault(bone.name, 1.0F));
            }

            for (Bone bone : bonesByName.values()) {
                if (bone.parent == null) {
                    bone.updateBindMatrix(null);
                }
            }
            for (Bone bone : bonesByName.values()) {
                if (bone.parent == null) {
                    bone.updateCurrentMatrix(null);
                }
            }
            for (Bone bone : bonesByName.values()) {
                bone.skinMatrix.set(bone.currentWorldMatrix).mul(bone.inverseBindMatrix);
            }
        }

        private static float value(float[] values, int index) {
            return values != null && index >= 0 && index < values.length ? values[index] : 0.0F;
        }

        Vector3f getAccumulatedModelSpaceOffset(Bone bone) {
            Vector3f offset = new Vector3f();
            for (Bone cursor = bone; cursor != null; cursor = cursor.parent) {
                offset.add(cursor.currentModelSpaceOffset);
            }
            return offset;
        }
    }

    private static final class Bone {
        private final String uuid;
        private final String name;
        private final Vector3f origin;
        private final Vector3f restRotation;
        private final Vector3f currentRotation = new Vector3f();
        private final Vector3f currentOffset = new Vector3f();
        private final Vector3f currentModelSpaceOffset = new Vector3f();
        private final List<Bone> children = new ArrayList<>();
        private final Matrix4f bindWorldMatrix = new Matrix4f();
        private final Matrix4f inverseBindMatrix = new Matrix4f();
        private final Matrix4f currentWorldMatrix = new Matrix4f();
        private final Matrix4f skinMatrix = new Matrix4f();
        private float currentScale = 1.0F;
        private Bone parent;

        private Bone(String uuid, String name, Vector3f origin, Vector3f restRotation) {
            this.uuid = uuid;
            this.name = name;
            this.origin = origin;
            this.restRotation = restRotation;
        }

        static Bone load(JsonObject object) {
            return new Bone(
                    getString(object, "uuid", ""),
                    getString(object, "name", ""),
                    getVec3(object, "origin"),
                    getVec3(object, "rotation")
            );
        }

        void updateBindMatrix(Matrix4f parentMatrix) {
            Matrix4f localMatrix = new Matrix4f()
                    .translate(origin)
                    .rotate(createRotation(restRotation.x, restRotation.y, restRotation.z));
            if (parentMatrix != null) {
                bindWorldMatrix.set(parentMatrix).mul(localMatrix);
            } else {
                bindWorldMatrix.set(localMatrix);
            }
            inverseBindMatrix.set(bindWorldMatrix).invert();
            for (Bone child : children) {
                child.updateBindMatrix(bindWorldMatrix);
            }
        }

        void updateCurrentMatrix(Matrix4f parentMatrix) {
            Matrix4f localMatrix = new Matrix4f()
                    .translate(origin)
                    .translate(currentOffset)
                    .rotate(createRotation(currentRotation.x, currentRotation.y, currentRotation.z))
                    .scale(currentScale);
            if (parentMatrix != null) {
                currentWorldMatrix.set(parentMatrix).mul(localMatrix);
            } else {
                currentWorldMatrix.set(localMatrix);
            }
            for (Bone child : children) {
                child.updateCurrentMatrix(currentWorldMatrix);
            }
        }
    }

    private static final class Mesh {
        private final String uuid;
        private final String name;
        private final String normalizedName;
        private final String partName;
        private final boolean outerLayer;
        private final boolean usesSolidColorTexture;
        private final int vertexColor;
        private final int renderPriority;
        private final Vector3f origin;
        private final Map<String, Vector3f> vertices = new HashMap<>();
        private final List<Face> faces = new ArrayList<>();
        private final Map<String, List<VertexWeight>> weights = new HashMap<>();
        private final Set<String> acceptedBones;

        private Mesh(String uuid, String name, Vector3f origin) {
            this.uuid = uuid;
            this.name = name;
            this.normalizedName = normalizeMeshName(name);
            this.origin = origin;
            this.partName = toPartName(name);
            this.outerLayer = isOuterLayer(name);
            this.usesSolidColorTexture = isSolidColorTextureOverlay(name);
            this.vertexColor = isSkinToneOverlay(name) ? SKIN_TONE_OVERLAY_COLOR : DEFAULT_VERTEX_COLOR;
            this.renderPriority = getRenderPriority(name);
            this.acceptedBones = acceptedBonesFor(partName);
        }

        static Mesh load(JsonObject object, float uvWidth, float uvHeight) {
            Mesh mesh = new Mesh(getString(object, "uuid", ""), getString(object, "name", ""), getVec3(object, "origin"));

            JsonObject verticesObject = object.has("vertices") && object.get("vertices").isJsonObject()
                    ? object.getAsJsonObject("vertices") : new JsonObject();
            for (Map.Entry<String, JsonElement> entry : verticesObject.entrySet()) {
                JsonArray position = entry.getValue().getAsJsonArray();
                mesh.vertices.put(entry.getKey(), new Vector3f(
                        getFloat(position, 0),
                        getFloat(position, 1),
                        getFloat(position, 2)
                ));
            }

            JsonObject facesObject = object.has("faces") && object.get("faces").isJsonObject()
                    ? object.getAsJsonObject("faces") : new JsonObject();
            for (Map.Entry<String, JsonElement> entry : facesObject.entrySet()) {
                JsonObject faceObject = entry.getValue().getAsJsonObject();
                Face face = new Face();
                JsonArray faceVertices = getArray(faceObject, "vertices");
                for (JsonElement vertex : faceVertices) {
                    face.vertices.add(vertex.getAsString());
                }
                JsonObject uvObject = faceObject.has("uv") && faceObject.get("uv").isJsonObject()
                        ? faceObject.getAsJsonObject("uv") : new JsonObject();
                for (Map.Entry<String, JsonElement> uvEntry : uvObject.entrySet()) {
                    JsonArray uv = uvEntry.getValue().getAsJsonArray();
                    face.uvs.put(uvEntry.getKey(), new Vector2f(
                            getFloat(uv, 0) / uvWidth,
                            getFloat(uv, 1) / uvHeight
                    ));
                }
                mesh.faces.add(face);
            }
            return mesh;
        }

        boolean acceptsBone(String boneName) {
            return acceptedBones.isEmpty() || acceptedBones.contains(boneName);
        }

        void normalizeWeights() {
            for (List<VertexWeight> vertexWeights : weights.values()) {
                float total = 0.0F;
                for (VertexWeight weight : vertexWeights) {
                    total += weight.weight;
                }
                if (total <= 0.0F) {
                    vertexWeights.clear();
                    continue;
                }
                if (Math.abs(total - 1.0F) > 0.001F) {
                    for (VertexWeight weight : vertexWeights) {
                        weight.weight /= total;
                    }
                }
            }
        }

        void applyForcedWeights() {
            String boneName = forcedWeightBone(name);
            if (boneName == null) {
                return;
            }
            weights.clear();
            for (String vertexId : vertices.keySet()) {
                List<VertexWeight> vertexWeights = new ArrayList<>(1);
                vertexWeights.add(new VertexWeight(boneName, 1.0F));
                weights.put(vertexId, vertexWeights);
            }
        }

        boolean hasVertexWeightedTo(String boneName) {
            for (List<VertexWeight> vertexWeights : weights.values()) {
                for (VertexWeight weight : vertexWeights) {
                    if (boneName.equals(weight.boneName) && weight.weight > 0.0F) {
                        return true;
                    }
                }
            }
            return false;
        }

        List<String> getVertexIdsWeightedTo(String boneName) {
            List<String> vertexIds = new ArrayList<>();
            for (Map.Entry<String, List<VertexWeight>> entry : weights.entrySet()) {
                for (VertexWeight weight : entry.getValue()) {
                    if (boneName.equals(weight.boneName) && weight.weight > 0.0F) {
                        vertexIds.add(entry.getKey());
                        break;
                    }
                }
            }
            return vertexIds;
        }

        private static String toPartName(String meshName) {
            String lower = meshName.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("head") || lower.contains("hat") || lower.contains("eye") || lower.contains("forehead")
                    || lower.contains("nose") || lower.contains("chin")) return "head";
            if (lower.contains("left arm")) return "left_arm";
            if (lower.contains("right arm")) return "right_arm";
            if (lower.contains("left leg")) return "left_leg";
            if (lower.contains("right leg")) return "right_leg";
            return "torso";
        }

        private static boolean isOuterLayer(String meshName) {
            String lower = meshName.toLowerCase(java.util.Locale.ROOT);
            return lower.contains("layer")
                    || lower.contains("hat")
                    || lower.contains("jacket")
                    || lower.contains("sleeve")
                    || lower.contains("pants");
        }

        private static boolean isSkinToneOverlay(String meshName) {
            String lower = meshName.toLowerCase(java.util.Locale.ROOT);
            return lower.equals("eyelid_left")
                    || lower.equals("eyelid_right")
                    || lower.equals("eyelid_cover_left")
                    || lower.equals("eyelid_cover_right")
                    || lower.equals("forehead")
                    || lower.equals("nose");
        }

        private static boolean isSolidColorTextureOverlay(String meshName) {
            String lower = meshName.toLowerCase(java.util.Locale.ROOT);
            return lower.equals("forehead");
        }

        private static String forcedWeightBone(String meshName) {
            return switch (normalizeMeshName(meshName)) {
                case "eye_left", "eye_highlight_left" -> "eye_left";
                case "eye_right", "eye_highlight_right" -> "eye_right";
                case "eye_white_left", "eye_white_right" -> "head";
                case "eyelid_left" -> "eyelid_left";
                case "eyelid_right" -> "eyelid_right";
                case "eyelid_cover_left" -> "eyelid_left";
                case "eyelid_cover_right" -> "eyelid_right";
                default -> null;
            };
        }

        private static int getRenderPriority(String meshName) {
            String lower = meshName.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("eyelid")) {
                return 40;
            }
            if (lower.equals("nose")) {
                return 30;
            }
            if (lower.equals("forehead")) {
                return 30;
            }
            if (lower.contains("eye")) {
                return 20;
            }
            return 0;
        }

        private static Set<String> acceptedBonesFor(String partName) {
            Set<String> bones = new HashSet<>();
            switch (partName) {
                case "head" -> {
                    bones.add("head");
                    bones.add("eye");
                    bones.add("eye_left");
                    bones.add("eye_right");
                    bones.add("eyelid");
                    bones.add("eyelid_left");
                    bones.add("eyelid_right");
                    bones.add("nose");
                }
                case "torso" -> {
                    bones.add("torso_low");
                    bones.add("torso_midium");
                    bones.add("torso_on");
                }
                case "left_arm" -> {
                    bones.add("left_arm_on");
                    bones.add("left_arm_low");
                }
                case "right_arm" -> {
                    bones.add("right_arm_on");
                    bones.add("right_arm_low");
                }
                case "left_leg" -> {
                    bones.add("left_leg_on");
                    bones.add("left_leg_low");
                }
                case "right_leg" -> {
                    bones.add("right_leg_on");
                    bones.add("right_leg_low");
                }
                default -> {
                }
            }
            return bones;
        }
    }

    private static boolean isModelSpaceOffsetBone(String boneName) {
        return switch (boneName) {
            case "eye", "eye_left", "eye_right", "eyelid", "eyelid_left", "eyelid_right" -> true;
            default -> false;
        };
    }

    private static final class Face {
        private final List<String> vertices = new ArrayList<>(4);
        private final Map<String, Vector2f> uvs = new HashMap<>();
    }

    private static final class VertexWeight {
        private final String boneName;
        private float weight;

        private VertexWeight(String boneName, float weight) {
            this.boneName = boneName;
            this.weight = weight;
        }
    }

    private static float getFloatValue(JsonObject object, String name, float fallback) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsFloat();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}

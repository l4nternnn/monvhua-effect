package com.kuilunfuzhe.monvhua.renderer.bodypose.skeletal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuilunfuzhe.monvhua.gui.body.bodypose.BodyPoseEditorFragment;
import dev.tr7zw.skinlayers.SkinUtil;
import dev.tr7zw.skinlayers.api.SkinLayersAPI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

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
    private static final Identifier MODEL_ID = Identifier.of("monvhua", "models/entity/skeletal_model2.bbmodel");
    private static final Identifier ANIMATION_ID = Identifier.of("monvhua", "models/entity/animation.json");
    private static final Identifier EYE_ANIMATION_ID = Identifier.of("monvhua", "models/entity/eye_on_off.json");
    private static final Identifier SOLID_COLOR_TEXTURE = Identifier.ofVanilla("textures/misc/white.png");
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
    private static final float OUTER_LAYER_NORMAL_OFFSET = 0.002F;
    private static final Map<SkinLayerCacheKey, SkinLayerMeshes> SKIN_LAYER_MESHES = new HashMap<>();

    private static SkeletalModel cachedModel;
    private static boolean loadFailed;

    private BodyPoseSkeletalPreviewRenderer() {
    }

    public static boolean render(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                 Identifier texture, int light) {
        return render(matrices, vertexConsumers, texture, light, BodyPoseEditorFragment.getWorldSkeletalBoneRotations(),
                BodyPoseEditorFragment.getWorldSkeletalBoneOffsets(), BodyPoseEditorFragment.getWorldSkeletalBoneScales(),
                BodyPoseEditorFragment.getWorldVisibleSkeletalParts(), BodyPoseEditorFragment.isWorldSlimModel());
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
                    BodyPoseEditorFragment.getWorldVisibleSkeletalParts(), BodyPoseEditorFragment.isWorldSlimModel(), true, null);
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
        return render(matrices, vertexConsumers, texture, light, rotations, offsets, scales, visibleParts, slim, false, null);
    }

    public static boolean render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Identifier texture,
                                 int light, Map<String, float[]> rotations, Map<String, float[]> offsets,
                                 Map<String, Float> scales, Set<String> visibleParts, boolean slim, RenderLayer renderLayer) {
        return render(matrices, vertexConsumers, texture, light, rotations, offsets, scales, visibleParts, slim, true, renderLayer);
    }

    private static boolean render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Identifier texture,
                                  int light, Map<String, float[]> rotations, Map<String, float[]> offsets,
                                  Map<String, Float> scales, Set<String> visibleParts, boolean slim, boolean singleLayer,
                                  RenderLayer renderLayer) {
        SkeletalModel model = getModel();
        if (model == null || model.meshes.isEmpty()) {
            return false;
        }

        model.updatePose(rotations, offsets, scales);

        VertexConsumer skinConsumer = vertexConsumers.getBuffer(renderLayer != null ? renderLayer : RenderLayer.getEntityTranslucent(texture));
        VertexConsumer solidColorConsumer = singleLayer
                ? skinConsumer
                : vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(SOLID_COLOR_TEXTURE));
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        renderMeshes(model, matrix, skinConsumer, solidColorConsumer, light, visibleParts, false);
        renderSkinLayers3dOuterMeshes(model, matrices, skinConsumer, texture, light, visibleParts, slim);
        renderMeshes(model, matrix, skinConsumer, solidColorConsumer, light, visibleParts, true);
        return true;
    }

    private static void renderMeshes(SkeletalModel model, Matrix4f matrix, VertexConsumer skinConsumer,
                                     VertexConsumer solidColorConsumer,
                                     int light, Set<String> visibleParts, boolean outerLayer) {
        boolean renderedNose = false;
        for (Mesh mesh : model.meshes) {
            if (mesh.outerLayer != outerLayer) {
                continue;
            }
            if (!visibleParts.isEmpty() && !visibleParts.contains(mesh.partName)) {
                continue;
            }
            if ("nose".equals(mesh.name)) {
                if (!outerLayer) {
                    renderNoseOverlay(model, matrix, skinConsumer, light, visibleParts);
                    renderedNose = true;
                }
                continue;
            }
            renderMesh(mesh, model, matrix, mesh.usesSolidColorTexture ? solidColorConsumer : skinConsumer, light);
        }
        if (!outerLayer && !renderedNose) {
            renderNoseOverlay(model, matrix, skinConsumer, light, visibleParts);
        }
    }

    private static boolean renderSkinLayers3dOuterMeshes(SkeletalModel model, MatrixStack matrices,
                                                         VertexConsumer vertexConsumer, Identifier texture,
                                                         int light, Set<String> visibleParts, boolean slim) {
        SkinLayerMeshes meshes = getSkinLayerMeshes(texture, slim);
        if (meshes == null) {
            return false;
        }
        boolean rendered = false;
        rendered |= renderSkinLayerPart(model, matrices, vertexConsumer, meshes.head, light, visibleParts,
                "head", "head", 0.0F, 0.0F, 0.0F);
        rendered |= renderSkinLayerPart(model, matrices, vertexConsumer, meshes.torso, light, visibleParts,
                "torso", "torso_on", 0.0F, -12.0F, 0.0F);
        rendered |= renderSkinLayerPart(model, matrices, vertexConsumer, meshes.leftArm, light, visibleParts,
                "left_arm", "left_arm_on", 0.0F, -12.0F, 0.0F);
        rendered |= renderSkinLayerPart(model, matrices, vertexConsumer, meshes.rightArm, light, visibleParts,
                "right_arm", "right_arm_on", 0.0F, -12.0F, 0.0F);
        rendered |= renderSkinLayerPart(model, matrices, vertexConsumer, meshes.leftLeg, light, visibleParts,
                "left_leg", "left_leg_on", 0.0F, -12.0F, 0.0F);
        rendered |= renderSkinLayerPart(model, matrices, vertexConsumer, meshes.rightLeg, light, visibleParts,
                "right_leg", "right_leg_on", 0.0F, -12.0F, 0.0F);
        return rendered;
    }

    private static boolean renderSkinLayerPart(SkeletalModel model, MatrixStack matrices, VertexConsumer vertexConsumer,
                                               dev.tr7zw.skinlayers.api.Mesh mesh, int light, Set<String> visibleParts, String partName,
                                               String boneName, float localX, float localY, float localZ) {
        if (mesh == null || mesh == dev.tr7zw.skinlayers.api.Mesh.EMPTY
                || (!visibleParts.isEmpty() && !visibleParts.contains(partName))) {
            return false;
        }
        Bone bone = model.bonesByName.get(boneName);
        if (bone == null) {
            return false;
        }

        matrices.push();
        Matrix4f matrix = new Matrix4f(bone.currentWorldMatrix)
                .translate(localX, localY, localZ)
                .translate(0.0F, MODEL_Y_ORIGIN, 0.0F)
                .scale(1.0F, -1.0F, 1.0F)
                .translate(0.0F, -MODEL_Y_ORIGIN, 0.0F)
                .translate(0.0F, -MODEL_Y_ORIGIN, 0.0F)
                .scale(1.0F / MODEL_UNIT);
        matrices.multiplyPositionMatrix(matrix);
        mesh.reset();
        mesh.render(null, matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV, DEFAULT_VERTEX_COLOR);
        matrices.pop();
        return true;
    }

    private static SkinLayerMeshes getSkinLayerMeshes(Identifier texture, boolean slim) {
        SkinLayerCacheKey key = new SkinLayerCacheKey(texture, slim);
        if (SKIN_LAYER_MESHES.containsKey(key)) {
            return SKIN_LAYER_MESHES.get(key);
        }

        NativeImage image = SkinUtil.getTexture(texture, null);
        if (image == null || image.getWidth() != 64 || image.getHeight() != 64) {
            SKIN_LAYER_MESHES.put(key, null);
            return null;
        }

        try {
            SkinLayerMeshes meshes = new SkinLayerMeshes(
                    SkinLayersAPI.getMeshHelper().create3DMesh(image, 8, 8, 8, 32, 0, false, 0.6F),
                    SkinLayersAPI.getMeshHelper().create3DMesh(image, 8, 12, 4, 16, 32, true, 0.0F),
                    SkinLayersAPI.getMeshHelper().create3DMesh(image, slim ? 3 : 4, 12, 4, 48, 48, true, -2.0F),
                    SkinLayersAPI.getMeshHelper().create3DMesh(image, slim ? 3 : 4, 12, 4, 40, 32, true, -2.0F),
                    SkinLayersAPI.getMeshHelper().create3DMesh(image, 4, 12, 4, 0, 48, true, 0.0F),
                    SkinLayersAPI.getMeshHelper().create3DMesh(image, 4, 12, 4, 0, 32, true, 0.0F)
            );
            SKIN_LAYER_MESHES.put(key, meshes);
            return meshes;
        } catch (Exception e) {
            System.err.println("[Monvhua] Failed to build 3D Skin Layers meshes: " + e.getMessage());
            SKIN_LAYER_MESHES.put(key, null);
            return null;
        }
    }

    private record SkinLayerCacheKey(Identifier texture, boolean slim) {
    }

    private record SkinLayerMeshes(dev.tr7zw.skinlayers.api.Mesh head,
                                   dev.tr7zw.skinlayers.api.Mesh torso,
                                   dev.tr7zw.skinlayers.api.Mesh leftArm,
                                   dev.tr7zw.skinlayers.api.Mesh rightArm,
                                   dev.tr7zw.skinlayers.api.Mesh leftLeg,
                                   dev.tr7zw.skinlayers.api.Mesh rightLeg) {
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

    private static final class AnimationPose {
        private final Map<String, float[]> rotations = new HashMap<>();
        private final Map<String, float[]> offsets = new HashMap<>();
    }

    private static void renderMesh(Mesh mesh, SkeletalModel model, Matrix4f matrix,
                                   VertexConsumer vertexConsumer, int light) {
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

            Vector3f rp0 = offsetOuterLayerVertex(mesh, p0, normal);
            Vector3f rp1 = offsetOuterLayerVertex(mesh, p1, normal);
            Vector3f rp2 = offsetOuterLayerVertex(mesh, p2, normal);
            Vector2f uv0 = face.uvs.get(face.vertices.get(0));
            Vector2f uv1 = face.uvs.get(face.vertices.get(1));
            Vector2f uv2 = face.uvs.get(face.vertices.get(2));
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

    private static Vector3f toRenderPosition(Vector3f modelPosition) {
        return new Vector3f(
                modelPosition.x / MODEL_UNIT,
                (modelPosition.y - MODEL_Y_ORIGIN) / MODEL_UNIT,
                modelPosition.z / MODEL_UNIT
        );
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

        private static String toPartName(String meshName) {
            String lower = meshName.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("head") || lower.contains("hat") || lower.contains("eye") || lower.contains("forehead") || lower.contains("nose")) return "head";
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
            return switch (meshName.toLowerCase(java.util.Locale.ROOT)) {
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

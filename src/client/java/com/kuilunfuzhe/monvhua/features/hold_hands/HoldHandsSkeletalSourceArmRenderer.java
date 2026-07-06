package com.kuilunfuzhe.monvhua.features.hold_hands;

import com.kuilunfuzhe.monvhua.renderer.bodypose.skeletal.BodyPoseSkeletalPreviewRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Set;

public final class HoldHandsSkeletalSourceArmRenderer {
    private static final float MODEL_UNIT = 16.0F;
    private static final float PLAYER_ATTACHED_MODEL_Y_ORIGIN = 37.9207F;

    private HoldHandsSkeletalSourceArmRenderer() {
    }

    public static boolean render(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                 Identifier texture, int light, Map<String, float[]> rotations,
                                 Set<String> visibleParts, boolean slim, NbtCompound customData) {
        return BodyPoseSkeletalPreviewRenderer.renderMappedSourceModel(matrices, vertexConsumers, texture, light,
                rotations, Map.of(), Map.of(), visibleParts, slim, RenderLayer.getEntityCutoutNoCull(texture),
                Set.of(), customData, HoldHandsSkeletalSourceArmRenderer::toPlayerModelPosition);
    }

    private static Vector3f toPlayerModelPosition(Vector3f modelPosition) {
        return new Vector3f(
                -modelPosition.x / MODEL_UNIT,
                (PLAYER_ATTACHED_MODEL_Y_ORIGIN - modelPosition.y) / MODEL_UNIT,
                modelPosition.z / MODEL_UNIT
        );
    }
}

package com.kuilunfuzhe.monvhua.features.swing;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** Dedicated renderer for captured swing blocks, independent of vanilla chunk meshes. */
public final class SwingStructureRenderer {
    private SwingStructureRenderer() {
    }

    public static void render(SwingEntityRenderer.State state, MatrixStack matrices,
                              VertexConsumerProvider vertices, int fallbackLight) {
        MinecraftClient client = MinecraftClient.getInstance();
        matrices.push();
        matrices.multiply((state.z ? RotationAxis.POSITIVE_Z : RotationAxis.POSITIVE_X).rotation(state.angle));
        for (SwingBlock block : state.structure.blocks()) {
            int light = fallbackLight;
            if (client.world != null) {
                Vec3d worldCenter = SwingTransform.localToWorld(Vec3d.of(block.localPos()), state.pivot,
                        state.angle, state.z);
                light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(worldCenter));
            }
            matrices.push();
            matrices.translate(block.localPos().getX() - .5, block.localPos().getY() - .5,
                    block.localPos().getZ() - .5);
            client.getBlockRenderManager().renderBlockAsEntity(block.state(), matrices, vertices,
                    light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
        matrices.pop();
    }
}

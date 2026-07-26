package com.kuilunfuzhe.monvhua.features.dissolve.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

final class DissolvePlayerRenderLayer {
    private DissolvePlayerRenderLayer() {
    }

    static void render(PlayerEntityModel model, MatrixStack matrices, VertexConsumerProvider consumers,
                       int light, Identifier edgeTexture) {
        VertexConsumer vertices = consumers.getBuffer(DissolveRenderLayers.edge(edgeTexture));
        model.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
    }
}

package com.kuilunfuzhe.monvhua.renderer.commandpanel;

import com.kuilunfuzhe.monvhua.client.commandpanel.CommandPanelItemUiState;
import com.kuilunfuzhe.monvhua.item.commandpanel.CommandPanelItem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** Draws a small translucent display plane directly on the screen bone. */
public final class CommandPanelUiLayer extends GeoRenderLayer<CommandPanelItem, GeoItemRenderer.RenderData, GeoRenderState> {
    private static final Identifier UI_TEXTURE = Identifier.of("minecraft", "textures/misc/white.png");

    public CommandPanelUiLayer(GeoItemRenderer<CommandPanelItem> renderer) {
        super(renderer);
    }

    @Override
    public void addPerBoneRender(GeoRenderState state, BakedGeoModel model,
                                 java.util.function.BiConsumer<GeoBone, software.bernie.geckolib.renderer.base.PerBoneRender<GeoRenderState>> consumer) {
        model.getBone("screen").ifPresent(screen -> consumer.accept(screen,
                (renderState, matrices, bone, renderType, vertices, light, overlay, color) -> {
                    if (!CommandPanelItemUiState.isVisible()) return;
                    drawDisplay(matrices, vertices, light, overlay);
                }));
    }

    @Override
    public void render(GeoRenderState state, MatrixStack matrices, BakedGeoModel model,
                       net.minecraft.client.render.RenderLayer renderType,
                       VertexConsumerProvider vertices, VertexConsumer buffer,
                       int light, int overlay, int color) {
        if (!CommandPanelItemUiState.isVisible()) return;
        // Diagnostic fallback: keep a visible yellow plane even if the per-bone callback
        // is skipped by a renderer implementation.
        drawDisplay(matrices, vertices, light, overlay);
    }

    private static void drawDisplay(MatrixStack matrices, VertexConsumerProvider vertices, int light, int overlay) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer buffer = vertices.getBuffer(RenderLayer.getEntityTranslucent(UI_TEXTURE));
        float x0 = -5.0f / 16.0f, x1 = 5.0f / 16.0f;
        // Leave the 0.72-unit upper and lower frame strips visible.
        float z0 = -3.2f / 16.0f, z1 = 3.2f / 16.0f;
        float y = 1.015f / 16.0f;
        buffer.vertex(matrix, x0, y, z0).color(255, 220, 0, 255).texture(0, 0).overlay(overlay).light(light).normal(0, 1, 0);
        buffer.vertex(matrix, x1, y, z0).color(255, 220, 0, 255).texture(1, 0).overlay(overlay).light(light).normal(0, 1, 0);
        buffer.vertex(matrix, x1, y, z1).color(255, 220, 0, 255).texture(1, 1).overlay(overlay).light(light).normal(0, 1, 0);
        buffer.vertex(matrix, x0, y, z1).color(255, 220, 0, 255).texture(0, 1).overlay(overlay).light(light).normal(0, 1, 0);
    }
}

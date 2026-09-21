package com.kuilunfuzhe.monvhua.renderer.commandpanel;

import com.kuilunfuzhe.monvhua.client.commandpanel.CommandPanelItemUiState;
import com.kuilunfuzhe.monvhua.item.commandpanel.CommandPanelItem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.constant.DataTickets;
import net.minecraft.item.ItemDisplayContext;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** One attached screen pass, sharing the item's pose, pivot and scale. */
public final class CommandPanelUiLayer extends GeoRenderLayer<CommandPanelItem, GeoItemRenderer.RenderData, GeoRenderState> {

    public CommandPanelUiLayer(GeoItemRenderer<CommandPanelItem> renderer) {
        super(renderer);
    }

    @Override
    public void render(GeoRenderState state, MatrixStack matrices, BakedGeoModel model,
                       net.minecraft.client.render.RenderLayer renderType,
                       VertexConsumerProvider vertices, VertexConsumer buffer,
                       int light, int overlay, int color) {
        if (!CommandPanelItemUiState.isVisible()) return;
        if (!state.getOrDefaultGeckolibData(DataTickets.ITEM_RENDER_PERSPECTIVE, ItemDisplayContext.NONE).isFirstPerson()) return;
        drawDisplay(matrices, vertices, light, overlay);
    }

    private static void drawDisplay(MatrixStack matrices, VertexConsumerProvider vertices, int light, int overlay) {
        PanelUiTexture.render(matrices, vertices, light, overlay);
    }
}

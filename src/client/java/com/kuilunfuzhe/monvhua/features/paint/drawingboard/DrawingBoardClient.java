package com.kuilunfuzhe.monvhua.features.paint.drawingboard;

import com.kuilunfuzhe.monvhua.entity.ModBlockEntities;
import com.kuilunfuzhe.monvhua.features.paint.drawingboard.client.DrawingBoardBlockEntityRenderer;
import com.kuilunfuzhe.monvhua.features.paint.drawingboard.client.DrawingBoardScreen;
import com.kuilunfuzhe.monvhua.network.drawingboard.DrawingBoardPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public final class DrawingBoardClient {
    private DrawingBoardClient() {
    }

    public static void initialize() {
        BlockEntityRendererFactories.register(
                ModBlockEntities.DRAWING_BOARD_BLOCK_ENTITY,
                DrawingBoardBlockEntityRenderer::new
        );
        ClientPlayNetworking.registerGlobalReceiver(DrawingBoardPackets.SyncS2C.ID, (packet, context) ->
                context.client().execute(() -> {
                    if (context.client().world != null
                            && context.client().world.getBlockEntity(packet.pos()) instanceof DrawingBoardBlockEntity board) {
                        board.setPixels(packet.pixels());
                    }
                    DrawingBoardScreen.receiveSync(packet.pos(), packet.pixels());
                }));
        ClientPlayNetworking.registerGlobalReceiver(DrawingBoardPackets.OpenS2C.ID, (packet, context) ->
                context.client().execute(() -> MinecraftClient.getInstance().setScreen(new DrawingBoardScreen(packet.pos()))));
    }
}

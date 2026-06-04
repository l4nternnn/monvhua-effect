package com.kuilunfuzhe.monvhua.features.cosmic_box;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.item.model.special.SpecialModelTypes;
import net.minecraft.util.Identifier;

public final class CosmicBoxClient {
    private static boolean registered;

    private CosmicBoxClient() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        registered = true;
        CosmicBoxNetworking.registerS2CPackets();
        CosmicBoxNetworking.registerC2SPackets();
        CosmicBoxRenderPipelines.initialize();
        CosmicBoxIrisCompat.initialize();
        BlockEntityRendererFactories.register(
                CosmicBoxBlockEntities.COSMIC_BOX_BLOCK_ENTITY,
                context -> new CosmicBoxBlockEntityRenderer()
        );
        SpecialModelTypes.ID_MAPPER.put(
                Identifier.of(MonvhuaMod.MOD_ID, "cosmic_box"),
                CosmicBoxSpecialModelRenderer.Unbaked.CODEC
        );
        ClientPlayNetworking.registerGlobalReceiver(CosmicBoxTargetListS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> context.client().setScreen(
                    new CosmicBoxTargetScreen(packet.pos(), packet.targets())
            ));
        });
        ClientPlayNetworking.registerGlobalReceiver(CosmicBoxBeamVisibilityS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> CosmicBoxClientState.setCanSeeBeam(packet.visible()));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CosmicBoxClientState.setCanSeeBeam(false));
    }
}

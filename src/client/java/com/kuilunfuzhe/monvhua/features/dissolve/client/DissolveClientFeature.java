package com.kuilunfuzhe.monvhua.features.dissolve.client;

import com.kuilunfuzhe.monvhua.features.dissolve.DissolveParticleTypes;
import com.kuilunfuzhe.monvhua.features.dissolve.client.particle.DissolvePixelParticle;
import com.kuilunfuzhe.monvhua.mixin.PlayerEntityRenderStateAccessor;
import com.kuilunfuzhe.monvhua.network.dissolve.DissolvePackets;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public final class DissolveClientFeature {
    private static boolean initialized;

    private DissolveClientFeature() {
    }

    public static void initializeClient() {
        if (initialized) {
            return;
        }
        initialized = true;
        ParticleFactoryRegistry.getInstance().register(
                DissolveParticleTypes.DISSOLVE_PIXEL,
                DissolvePixelParticle.Factory::new
        );
        ClientTickEvents.END_CLIENT_TICK.register(DissolveClientController::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> DissolveClientController.clearAll());
        ClientPlayNetworking.registerGlobalReceiver(DissolvePackets.StartS2C.ID, (packet, context) ->
                context.client().execute(() -> DissolveClientController.start(packet)));
        ClientPlayNetworking.registerGlobalReceiver(DissolvePackets.StopS2C.ID, (packet, context) ->
                context.client().execute(() -> DissolveClientController.stop(packet.targetUuid())));
        ClientPlayNetworking.registerGlobalReceiver(DissolvePackets.FinishS2C.ID, (packet, context) ->
                context.client().execute(() -> DissolveClientController.stop(packet.targetUuid())));
    }

    public static void overrideSkinTexture(AbstractClientPlayerEntity player, PlayerEntityRenderState state) {
        if (player == null || state == null) {
            return;
        }
        if (DissolveClientController.shouldHideName(player.getUuid())) {
            state.displayName = null;
            state.nameLabelPos = null;
            state.customName = null;
        }
        SkinTextures current = ((PlayerEntityRenderStateAccessor) state).getSkinTextures();
        if (current == null) {
            return;
        }
        Identifier dissolveTexture = DissolveClientController.skinTexture(player, current.texture());
        if (dissolveTexture == null) {
            return;
        }
        ((PlayerEntityRenderStateAccessor) state).setSkinTextures(new SkinTextures(
                dissolveTexture,
                current.textureUrl(),
                current.capeTexture(),
                current.elytraTexture(),
                current.model(),
                current.secure()
        ));
    }

    public static void renderEdge(PlayerEntityRenderState state, PlayerEntityModel model,
                                  MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        if (state == null || model == null) {
            return;
        }
        DissolveClientController.renderEdge(state, model, matrices, consumers, light);
    }
}

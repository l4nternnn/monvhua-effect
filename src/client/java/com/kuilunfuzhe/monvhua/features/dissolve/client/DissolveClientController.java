package com.kuilunfuzhe.monvhua.features.dissolve.client;

import com.kuilunfuzhe.monvhua.features.dissolve.DissolveProfile;
import com.kuilunfuzhe.monvhua.network.dissolve.DissolvePackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class DissolveClientController {
    private static final Map<UUID, ClientDissolveState> STATES = new ConcurrentHashMap<>();
    private static final Map<Integer, UUID> ENTITY_TO_UUID = new ConcurrentHashMap<>();

    private DissolveClientController() {
    }

    static void start(DissolvePackets.StartS2C packet) {
        stop(packet.targetUuid());
        ClientDissolveState state = new ClientDissolveState(
                packet.targetUuid(),
                packet.entityId(),
                packet.elapsedTicks(),
                packet.seed(),
                packet.profile(),
                new DissolveTextureController(packet.targetUuid())
        );
        STATES.put(packet.targetUuid(), state);
        ENTITY_TO_UUID.put(packet.entityId(), packet.targetUuid());
    }

    static void stop(UUID targetUuid) {
        ClientDissolveState removed = STATES.remove(targetUuid);
        if (removed != null) {
            ENTITY_TO_UUID.remove(removed.entityId());
            removed.textureController().destroy();
        }
    }

    static void clearAll() {
        for (ClientDissolveState state : new ArrayList<>(STATES.values())) {
            state.textureController().destroy();
        }
        STATES.clear();
        ENTITY_TO_UUID.clear();
    }

    static void tick(MinecraftClient client) {
        if (client == null || STATES.isEmpty()) {
            return;
        }
        if (client.world == null) {
            clearAll();
            return;
        }
        for (ClientDissolveState state : new ArrayList<>(STATES.values())) {
            state.incrementElapsed();
            Entity entity = client.world.getEntityById(state.entityId());
            if (entity instanceof PlayerEntity player) {
                DissolvePixelParticleEmitter.tick(client, player, state);
                DissolveParticleEmitter.tick(client, player, state);
            }
            if (state.elapsedTicks() > state.profile().durationTicks() + 80) {
                stop(state.targetUuid());
            }
        }
    }

    static Identifier skinTexture(AbstractClientPlayerEntity player, Identifier currentTexture) {
        ClientDissolveState state = STATES.get(player.getUuid());
        if (state == null) {
            return null;
        }
        ENTITY_TO_UUID.put(player.getId(), player.getUuid());
        state.setEntityId(player.getId());
        if (!state.textureController().ensureBase(currentTexture)) {
            return null;
        }
        state.textureController().update(state.elapsedTicks(), state.profile(), state.seed());
        return state.textureController().skinTextureId();
    }

    static boolean shouldHideName(UUID targetUuid) {
        ClientDissolveState state = STATES.get(targetUuid);
        return state != null && state.profile().hideNameTag();
    }

    static void renderEdge(PlayerEntityRenderState renderState, PlayerEntityModel model,
                           MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        UUID uuid = ENTITY_TO_UUID.get(renderState.id);
        if (uuid == null) {
            return;
        }
        ClientDissolveState state = STATES.get(uuid);
        if (state == null || state.textureController().edgeTextureId() == null) {
            return;
        }
        DissolvePlayerRenderLayer.render(model, matrices, consumers, light, state.textureController().edgeTextureId());
    }

    static final class ClientDissolveState {
        private final UUID targetUuid;
        private int entityId;
        private int elapsedTicks;
        private final long seed;
        private final DissolveProfile profile;
        private final DissolveTextureController textureController;

        ClientDissolveState(UUID targetUuid, int entityId, int elapsedTicks, long seed,
                            DissolveProfile profile, DissolveTextureController textureController) {
            this.targetUuid = targetUuid;
            this.entityId = entityId;
            this.elapsedTicks = Math.max(0, elapsedTicks);
            this.seed = seed;
            this.profile = profile == null ? DissolveProfile.DEFAULT : profile;
            this.textureController = textureController;
        }

        UUID targetUuid() {
            return targetUuid;
        }

        int entityId() {
            return entityId;
        }

        void setEntityId(int entityId) {
            if (this.entityId != entityId) {
                ENTITY_TO_UUID.remove(this.entityId);
                this.entityId = entityId;
                ENTITY_TO_UUID.put(entityId, targetUuid);
            }
        }

        int elapsedTicks() {
            return elapsedTicks;
        }

        void incrementElapsed() {
            elapsedTicks++;
        }

        long seed() {
            return seed;
        }

        DissolveProfile profile() {
            return profile;
        }

        DissolveTextureController textureController() {
            return textureController;
        }
    }
}

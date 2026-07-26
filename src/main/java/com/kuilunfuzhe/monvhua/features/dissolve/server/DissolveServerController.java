package com.kuilunfuzhe.monvhua.features.dissolve.server;

import com.kuilunfuzhe.monvhua.features.dissolve.DissolveFeature;
import com.kuilunfuzhe.monvhua.features.dissolve.DissolveLock;
import com.kuilunfuzhe.monvhua.features.dissolve.DissolveProfile;
import com.kuilunfuzhe.monvhua.network.dissolve.DissolvePackets;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DissolveServerController {
    private static final Map<UUID, ServerDissolveState> STATES = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();
    private static MinecraftServer activeServer;
    private static boolean initialized;

    private DissolveServerController() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(DissolveServerController::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> syncAllTo(handler.getPlayer())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                stop(handler.getPlayer().getUuid(), DissolveFeature.StopReason.DISCONNECT));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            if (isDissolving(player.getUuid())) {
                stop(player.getUuid(), DissolveFeature.StopReason.WORLD_CHANGE);
            }
            syncAllTo(player);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) {
                stop(player.getUuid(), DissolveFeature.StopReason.DEATH);
            }
        });
        CommandRegistrationCallback.EVENT.register(DissolveCommand::register);
    }

    public static boolean start(ServerPlayerEntity target, DissolveProfile profile) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        activeServer = target.getServer();
        UUID uuid = target.getUuid();
        stop(uuid, DissolveFeature.StopReason.CANCELLED);
        DissolveProfile cleanProfile = profile == null ? DissolveProfile.DEFAULT : profile;
        ServerDissolveState state = new ServerDissolveState(
                uuid,
                target.getId(),
                cleanProfile,
                RANDOM.nextLong(),
                0,
                target.getPos()
        );
        STATES.put(uuid, state);
        DissolveLock.lock(uuid);
        broadcastStart(target.getServer(), state);
        return true;
    }

    public static void stop(UUID targetUuid, DissolveFeature.StopReason reason) {
        if (targetUuid == null) {
            return;
        }
        ServerDissolveState removed = STATES.remove(targetUuid);
        DissolveLock.unlock(targetUuid);
        if (removed != null) {
            broadcastStop(removed, reason);
        }
    }

    public static boolean isDissolving(UUID targetUuid) {
        return targetUuid != null && STATES.containsKey(targetUuid);
    }

    private static void tick(MinecraftServer server) {
        activeServer = server;
        if (STATES.isEmpty()) {
            return;
        }
        for (ServerDissolveState state : new ArrayList<>(STATES.values())) {
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(state.targetUuid());
            if (target == null || !target.isAlive()) {
                stop(state.targetUuid(), DissolveFeature.StopReason.DEATH);
                continue;
            }
            if (state.profile().freezeTarget()) {
                target.setVelocity(Vec3d.ZERO);
                target.requestTeleport(state.anchor().x, state.anchor().y, state.anchor().z);
            }
            int nextElapsed = state.elapsedTicks() + 1;
            if (nextElapsed >= state.profile().durationTicks()) {
                finish(server, target, state);
            } else {
                STATES.put(state.targetUuid(), state.withElapsedTicks(nextElapsed));
            }
        }
    }

    private static void finish(MinecraftServer server, ServerPlayerEntity target, ServerDissolveState state) {
        STATES.remove(state.targetUuid());
        DissolveLock.unlock(state.targetUuid());
        broadcastFinish(server, state.targetUuid(), target.getId());
        if (target.isAlive() && target.getWorld() instanceof ServerWorld world) {
            target.damage(world, target.getDamageSources().magic(), Float.MAX_VALUE);
            if (target.isAlive()) {
                target.kill(world);
            }
        }
    }

    private static void syncAllTo(ServerPlayerEntity receiver) {
        MinecraftServer server = receiver.getServer();
        if (server == null || STATES.isEmpty()) {
            return;
        }
        for (ServerDissolveState state : STATES.values()) {
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(state.targetUuid());
            if (target != null && target.isAlive()) {
                ServerPlayNetworking.send(receiver, toStartPacket(state.withEntityId(target.getId())));
            }
        }
    }

    private static void broadcastStart(MinecraftServer server, ServerDissolveState state) {
        if (server == null) {
            return;
        }
        DissolvePackets.StartS2C packet = toStartPacket(state);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    private static DissolvePackets.StartS2C toStartPacket(ServerDissolveState state) {
        return new DissolvePackets.StartS2C(
                state.targetUuid(),
                state.entityId(),
                state.elapsedTicks(),
                state.seed(),
                state.profile()
        );
    }

    private static void broadcastStop(ServerDissolveState state, DissolveFeature.StopReason reason) {
        MinecraftServer server = activeServer;
        if (server == null) {
            return;
        }
        DissolvePackets.StopS2C packet = new DissolvePackets.StopS2C(state.targetUuid(), reason);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    private static void broadcastFinish(MinecraftServer server, UUID targetUuid, int entityId) {
        DissolvePackets.FinishS2C packet = new DissolvePackets.FinishS2C(targetUuid, entityId);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    private record ServerDissolveState(UUID targetUuid, int entityId, DissolveProfile profile,
                                       long seed, int elapsedTicks, Vec3d anchor) {
        ServerDissolveState withElapsedTicks(int elapsedTicks) {
            return new ServerDissolveState(targetUuid, entityId, profile, seed, elapsedTicks, anchor);
        }

        ServerDissolveState withEntityId(int entityId) {
            return new ServerDissolveState(targetUuid, entityId, profile, seed, elapsedTicks, anchor);
        }
    }
}

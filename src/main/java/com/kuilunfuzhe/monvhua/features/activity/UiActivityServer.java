package com.kuilunfuzhe.monvhua.features.activity;

import com.kuilunfuzhe.monvhua.network.activity.UiActivityPackets;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class UiActivityServer {
    private static final Map<UUID, ActivityState> ACTIVE_PLAYERS = new HashMap<>();
    private static boolean initialized;

    private UiActivityServer() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                UiActivityBubbleCommand.register(dispatcher));

        ServerPlayNetworking.registerGlobalReceiver(UiActivityPackets.StateC2S.ID, (packet, context) ->
                context.server().execute(() -> updateActivity(context.player(), packet.activity(), packet.contentId())));

        EntityTrackingEvents.START_TRACKING.register((entity, watcher) -> {
            if (entity instanceof ServerPlayerEntity trackedPlayer) {
                sendCurrentState(trackedPlayer, watcher);
            }
        });

        EntityTrackingEvents.STOP_TRACKING.register((entity, watcher) -> {
            if (entity instanceof ServerPlayerEntity trackedPlayer) {
                send(watcher, new UiActivityPackets.StateS2C(
                        trackedPlayer.getUuid(),
                        UiActivityPackets.Activity.NONE,
                        trackedPlayer.getWorld().getTime(),
                        0
                ));
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ACTIVE_PLAYERS.remove(handler.getPlayer().getUuid()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sendBubbleSize(handler.getPlayer(), UiActivityBubbleSizeStore.get(server).multiplier()));
    }

    public static void broadcastBubbleSize(MinecraftServer server, float multiplier) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendBubbleSize(player, multiplier);
        }
    }

    private static void updateActivity(ServerPlayerEntity source, UiActivityPackets.Activity nextActivity, int requestedContentId) {
        UUID uuid = source.getUuid();
        ActivityState previous = ACTIVE_PLAYERS.get(uuid);
        UiActivityPackets.Activity previousActivity = previous == null
                ? UiActivityPackets.Activity.NONE
                : previous.activity();
        int nextContentId = nextActivity == UiActivityPackets.Activity.CHAT
                && EmotionCatalog.isValidId(requestedContentId)
                ? requestedContentId
                : 0;
        int previousContentId = previous == null ? 0 : previous.contentId();
        if (previousActivity == nextActivity && previousContentId == nextContentId) {
            return;
        }

        long changedAt = source.getWorld().getTime();
        long shownAt = previousActivity != UiActivityPackets.Activity.NONE
                && nextActivity != UiActivityPackets.Activity.NONE
                ? previous.shownAtGameTime()
                : changedAt;
        if (nextActivity == UiActivityPackets.Activity.NONE) {
            ACTIVE_PLAYERS.remove(uuid);
        } else {
            ACTIVE_PLAYERS.put(uuid, new ActivityState(nextActivity, shownAt, nextContentId));
        }

        UiActivityPackets.StateS2C update = new UiActivityPackets.StateS2C(
                uuid,
                nextActivity,
                nextActivity == UiActivityPackets.Activity.NONE ? changedAt : shownAt,
                nextContentId
        );
        for (ServerPlayerEntity watcher : PlayerLookup.tracking(source)) {
            send(watcher, update);
        }
        send(source, update);
    }

    private static void sendCurrentState(ServerPlayerEntity trackedPlayer, ServerPlayerEntity watcher) {
        ActivityState state = ACTIVE_PLAYERS.get(trackedPlayer.getUuid());
        if (state == null) {
            return;
        }
        send(watcher, new UiActivityPackets.StateS2C(
                trackedPlayer.getUuid(),
                state.activity(),
                state.shownAtGameTime(),
                state.contentId()
        ));
    }

    private static void send(ServerPlayerEntity player, UiActivityPackets.StateS2C packet) {
        if (ServerPlayNetworking.canSend(player, UiActivityPackets.StateS2C.ID)) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    private static void sendBubbleSize(ServerPlayerEntity player, float multiplier) {
        if (ServerPlayNetworking.canSend(player, UiActivityPackets.BubbleSizeS2C.ID)) {
            ServerPlayNetworking.send(player, new UiActivityPackets.BubbleSizeS2C(multiplier));
        }
    }

    private record ActivityState(UiActivityPackets.Activity activity, long shownAtGameTime, int contentId) {
    }
}

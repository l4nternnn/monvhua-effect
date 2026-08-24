package com.kuilunfuzhe.monvhua.features.activity;

import com.kuilunfuzhe.monvhua.network.activity.UiActivityPackets;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class UiActivityServer {
    private static final int MAGIC_DIARY_CONTENT_ID = 14;
    private static final int CHEST_CONTENT_ID = 17;
    private static final int FOOD_CONTENT_ID = 18;
    private static final int FOOD_DURATION_TICKS = 48;
    private static final Map<UUID, ActivityState> ACTIVE_PLAYERS = new HashMap<>();
    private static final Map<UUID, TransientToken> TRANSIENT_TOKENS = new HashMap<>();
    private static final Map<UUID, ContainerScene> ACTIVE_CONTAINERS = new HashMap<>();
    private static final Map<UUID, PendingContainerUse> PENDING_CONTAINERS = new HashMap<>();
    private static final Map<UUID, PendingFoodUse> PENDING_FOOD = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE_AVATARS = new HashMap<>();
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
        ServerPlayNetworking.registerGlobalReceiver(UiActivityPackets.AvatarLayoutRequestC2S.ID,
                (packet, context) -> context.server().execute(() -> sendAvatarLayouts(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(UiActivityPackets.AvatarLayoutUpdateC2S.ID,
                (packet, context) -> context.server().execute(() -> updateAvatarLayout(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(UiActivityPackets.BubbleStyleUpdateC2S.ID,
                (packet, context) -> context.server().execute(() -> updateBubbleStyle(context.player(), packet)));

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            var blockState = world.getBlockState(hitResult.getBlockPos());
            var block = blockState.getBlock();
            var blockId = Registries.BLOCK.getId(block);
            if (!"minecraft".equals(blockId.getNamespace())
                    || (!(block instanceof ChestBlock) && !(block instanceof BarrelBlock))) {
                return ActionResult.PASS;
            }
            PENDING_CONTAINERS.put(serverPlayer.getUuid(), new PendingContainerUse(
                    serverPlayer.currentScreenHandler,
                    world.getTime()
            ));
            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            ItemStack stack = player.getStackInHand(hand);
            Item item = stack.getItem();
            var itemId = Registries.ITEM.getId(item);
            if (!"minecraft".equals(itemId.getNamespace())
                    || stack.get(DataComponentTypes.FOOD) == null
                    || stack.getUseAction() != UseAction.EAT) {
                return ActionResult.PASS;
            }
            PENDING_FOOD.put(serverPlayer.getUuid(), new PendingFoodUse(hand, item, world.getTime()));
            return ActionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(UiActivityServer::tickTransientUses);

        EntityTrackingEvents.START_TRACKING.register((entity, watcher) -> {
            if (entity instanceof ServerPlayerEntity trackedPlayer) {
                sendCurrentState(trackedPlayer, watcher);
                sendAvatar(watcher, trackedPlayer.getUuid(), resolveAvatar(trackedPlayer));
            }
        });

        EntityTrackingEvents.STOP_TRACKING.register((entity, watcher) -> {
            if (entity instanceof ServerPlayerEntity trackedPlayer) {
                send(watcher, new UiActivityPackets.StateS2C(
                        trackedPlayer.getUuid(),
                        UiActivityPackets.Activity.NONE,
                        trackedPlayer.getWorld().getTime(),
                        trackedPlayer.getWorld().getTime(),
                        0
                ));
                sendAvatar(watcher, trackedPlayer.getUuid(), UiActivityBubbleAvatarCatalog.NONE);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                clearPlayer(handler.getPlayer().getUuid()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sendPlayerSettings(handler.getPlayer(), server);
            sendAvatarLayouts(handler.getPlayer());
            for (ServerPlayerEntity tracked : server.getPlayerManager().getPlayerList()) {
                sendAvatar(handler.getPlayer(), tracked.getUuid(), resolveAvatar(tracked));
            }
        });
    }

    public static void playTransient(ServerPlayerEntity player, int contentId, int durationTicks) {
        UUID uuid = player.getUuid();
        ActivityState previous = ACTIVE_PLAYERS.get(uuid);
        if (previous != null && previous.activity() != UiActivityPackets.Activity.TRANSIENT) {
            if (previous.activity() == UiActivityPackets.Activity.CHAT
                    || previous.activity() == UiActivityPackets.Activity.WRITING
                    || previous.activity() == UiActivityPackets.Activity.INVENTORY) {
                return;
            }
        }

        long now = player.getWorld().getTime();
        long shownAt = previous != null
                && previous.activity() == UiActivityPackets.Activity.TRANSIENT
                && previous.contentId() == FOOD_CONTENT_ID
                ? previous.shownAtGameTime()
                : now;
        ACTIVE_CONTAINERS.remove(uuid);
        TRANSIENT_TOKENS.put(uuid, new TransientToken(now + Math.max(1, durationTicks)));
        ACTIVE_PLAYERS.put(uuid, new ActivityState(
                UiActivityPackets.Activity.TRANSIENT, shownAt, now, Math.max(0, contentId)));
        sendActivity(player, new UiActivityPackets.StateS2C(
                uuid, UiActivityPackets.Activity.TRANSIENT, shownAt, now, Math.max(0, contentId)));
    }

    private static void startContainerScene(ServerPlayerEntity player, ScreenHandler handler) {
        UUID uuid = player.getUuid();
        ActivityState previous = ACTIVE_PLAYERS.get(uuid);
        if (previous != null && (previous.activity() == UiActivityPackets.Activity.CHAT
                || previous.activity() == UiActivityPackets.Activity.WRITING
                || previous.activity() == UiActivityPackets.Activity.INVENTORY)) {
            return;
        }

        long now = player.getWorld().getTime();
        TRANSIENT_TOKENS.remove(uuid);
        ACTIVE_CONTAINERS.put(uuid, new ContainerScene(handler));
        ACTIVE_PLAYERS.put(uuid, new ActivityState(
                UiActivityPackets.Activity.CONTAINER_SCENE, now, now, CHEST_CONTENT_ID));
        sendActivity(player, new UiActivityPackets.StateS2C(
                uuid, UiActivityPackets.Activity.CONTAINER_SCENE, now, now, CHEST_CONTENT_ID));
    }

    public static void broadcastBubbleSize(MinecraftServer server, float multiplier) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendBubbleSize(player, multiplier);
        }
    }

    public static void broadcastBubbleStyle(MinecraftServer server, UiActivityBubbleStyle style) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendBubbleStyle(player, style);
        }
    }

    private static void updateActivity(ServerPlayerEntity source, UiActivityPackets.Activity nextActivity, int requestedContentId) {
        UUID uuid = source.getUuid();
        TRANSIENT_TOKENS.remove(uuid);
        ACTIVE_CONTAINERS.remove(uuid);
        PENDING_CONTAINERS.remove(uuid);
        PENDING_FOOD.remove(uuid);
        ActivityState previous = ACTIVE_PLAYERS.get(uuid);
        UiActivityPackets.Activity previousActivity = previous == null
                ? UiActivityPackets.Activity.NONE
                : previous.activity();
        int nextContentId = switch (nextActivity) {
            case CHAT -> EmotionCatalog.isValidId(requestedContentId) ? requestedContentId : 0;
            case WRITING -> MAGIC_DIARY_CONTENT_ID;
            default -> 0;
        };
        int previousContentId = previous == null ? 0 : previous.contentId();
        if (previousActivity == nextActivity && previousContentId == nextContentId) {
            return;
        }

        long changedAt = source.getWorld().getTime();
        long shownAt = previousActivity != UiActivityPackets.Activity.NONE
                && nextActivity != UiActivityPackets.Activity.NONE
                ? previous.shownAtGameTime()
                : changedAt;
        long effectStartedAt = previous != null && previousContentId == nextContentId
                ? previous.effectStartedAtGameTime()
                : changedAt;
        if (nextActivity == UiActivityPackets.Activity.NONE) {
            ACTIVE_PLAYERS.remove(uuid);
        } else {
            ACTIVE_PLAYERS.put(uuid, new ActivityState(nextActivity, shownAt, effectStartedAt, nextContentId));
        }

        UiActivityPackets.StateS2C update = new UiActivityPackets.StateS2C(
                uuid,
                nextActivity,
                nextActivity == UiActivityPackets.Activity.NONE ? changedAt : shownAt,
                nextActivity == UiActivityPackets.Activity.NONE ? changedAt : effectStartedAt,
                nextContentId
        );
        sendActivity(source, update);
    }

    private static void sendActivity(ServerPlayerEntity source, UiActivityPackets.StateS2C update) {
        for (ServerPlayerEntity watcher : PlayerLookup.tracking(source)) {
            send(watcher, update);
        }
        send(source, update);
        sendAvatar(source, source.getUuid(), resolveAvatar(source));
        for (ServerPlayerEntity watcher : PlayerLookup.tracking(source)) {
            sendAvatar(watcher, source.getUuid(), resolveAvatar(source));
        }
    }

    private static void tickTransientUses(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            int avatar = resolveAvatar(player);
            int previousAvatar = ACTIVE_AVATARS.getOrDefault(player.getUuid(), UiActivityBubbleAvatarCatalog.NONE);
            if (avatar != previousAvatar) {
                ACTIVE_AVATARS.put(player.getUuid(), avatar);
                for (ServerPlayerEntity watcher : PlayerLookup.tracking(player)) {
                    sendAvatar(watcher, player.getUuid(), avatar);
                }
                sendAvatar(player, player.getUuid(), avatar);
            }
            UUID uuid = player.getUuid();
            PendingContainerUse pendingContainer = PENDING_CONTAINERS.get(uuid);
            if (pendingContainer != null) {
                if (player.getWorld().getTime() - pendingContainer.requestedAt() > 2L) {
                    PENDING_CONTAINERS.remove(uuid);
                } else if (player.currentScreenHandler != pendingContainer.previousHandler()
                        && player.currentScreenHandler != player.playerScreenHandler) {
                    PENDING_CONTAINERS.remove(uuid);
                    startContainerScene(player, player.currentScreenHandler);
                }
            }

            PendingFoodUse pendingFood = PENDING_FOOD.get(uuid);
            if (pendingFood != null) {
                long age = player.getWorld().getTime() - pendingFood.requestedAt();
                ItemStack stack = player.getStackInHand(pendingFood.hand());
                if (age > 2L || stack.getItem() != pendingFood.item()) {
                    PENDING_FOOD.remove(uuid);
                } else if (player.isUsingItem() && player.getActiveHand() == pendingFood.hand()) {
                    PENDING_FOOD.remove(uuid);
                    playTransient(player, FOOD_CONTENT_ID, FOOD_DURATION_TICKS);
                }
            }
        }

        var containerIterator = ACTIVE_CONTAINERS.entrySet().iterator();
        while (containerIterator.hasNext()) {
            var entry = containerIterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            ActivityState state = ACTIVE_PLAYERS.get(entry.getKey());
            if (player == null || !player.isAlive() || state == null
                    || state.activity() != UiActivityPackets.Activity.CONTAINER_SCENE
                    || player.currentScreenHandler != entry.getValue().handler()) {
                containerIterator.remove();
                if (player != null && state != null
                        && state.activity() == UiActivityPackets.Activity.CONTAINER_SCENE) {
                    ACTIVE_PLAYERS.remove(entry.getKey());
                    long now = player.getWorld().getTime();
                    sendActivity(player, new UiActivityPackets.StateS2C(
                            player.getUuid(), UiActivityPackets.Activity.NONE, now, now, 0));
                }
            }
        }

        var iterator = TRANSIENT_TOKENS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            ActivityState state = ACTIVE_PLAYERS.get(entry.getKey());
            if (player == null || !player.isAlive() || state == null
                    || state.activity() != UiActivityPackets.Activity.TRANSIENT) {
                iterator.remove();
            } else if (player.getWorld().getTime() >= entry.getValue().expiresAt()) {
                iterator.remove();
                ACTIVE_PLAYERS.remove(entry.getKey());
                long now = player.getWorld().getTime();
                sendActivity(player, new UiActivityPackets.StateS2C(
                        player.getUuid(), UiActivityPackets.Activity.NONE, now, now, 0));
            }
        }
    }

    private static void clearPlayer(UUID uuid) {
        ACTIVE_PLAYERS.remove(uuid);
        TRANSIENT_TOKENS.remove(uuid);
        ACTIVE_CONTAINERS.remove(uuid);
        PENDING_CONTAINERS.remove(uuid);
        PENDING_FOOD.remove(uuid);
        ACTIVE_AVATARS.remove(uuid);
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
                state.effectStartedAtGameTime(),
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

    private static int resolveAvatar(ServerPlayerEntity player) {
        return UiActivityBubbleAvatarCatalog.resolveTags(player.getCommandTags());
    }

    private static void sendAvatar(ServerPlayerEntity recipient, UUID playerUuid, int avatarId) {
        if (ServerPlayNetworking.canSend(recipient, UiActivityPackets.AvatarS2C.ID)) {
            ServerPlayNetworking.send(recipient, new UiActivityPackets.AvatarS2C(playerUuid, avatarId));
        }
    }

    private static void sendAvatarLayouts(ServerPlayerEntity player) {
        if (ServerPlayNetworking.canSend(player, UiActivityPackets.AvatarLayoutStateS2C.ID)) {
            ServerPlayNetworking.send(player, new UiActivityPackets.AvatarLayoutStateS2C(
                    UiActivityBubbleAvatarLayoutStore.get().snapshot()));
        }
    }

    private static void updateAvatarLayout(ServerPlayerEntity player,
                                            UiActivityPackets.AvatarLayoutUpdateC2S packet) {
        if (!player.hasPermissionLevel(2)) {
            sendAvatarLayouts(player);
            return;
        }
        int avatarId = packet.avatarId();
        if (UiActivityBubbleAvatarCatalog.key(avatarId).isEmpty()) {
            sendAvatarLayouts(player);
            return;
        }
        UiActivityBubbleAvatarLayoutStore.get().set(
                avatarId, packet.centerX(), packet.centerY(), packet.scale());
        MinecraftServer server = player.getServer();
        if (server != null) {
            for (ServerPlayerEntity recipient : server.getPlayerManager().getPlayerList()) {
                sendAvatarLayouts(recipient);
            }
        }
    }

    private static void updateBubbleStyle(ServerPlayerEntity player,
                                          UiActivityPackets.BubbleStyleUpdateC2S packet) {
        if (!player.hasPermissionLevel(2)) {
            sendBubbleStyle(player, UiActivityBubbleStyleStore.get(player.getServer()).style());
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return;
        UiActivityBubbleStyle style = UiActivityBubbleStyle.fromId(packet.styleId());
        UiActivityBubbleStyleStore.get(server).setStyle(style);
        broadcastBubbleStyle(server, style);
    }

    private static void sendPlayerSettings(ServerPlayerEntity player, MinecraftServer server) {
        sendBubbleSize(player, UiActivityBubbleSizeStore.get(server).multiplier());
        sendBubbleStyle(player, UiActivityBubbleStyleStore.get(server).style());
    }

    private static void sendBubbleStyle(ServerPlayerEntity player, UiActivityBubbleStyle style) {
        if (ServerPlayNetworking.canSend(player, UiActivityPackets.BubbleStyleS2C.ID)) {
            ServerPlayNetworking.send(player, new UiActivityPackets.BubbleStyleS2C(style.ordinal()));
        }
    }

    private record ActivityState(UiActivityPackets.Activity activity, long shownAtGameTime,
                                 long effectStartedAtGameTime, int contentId) {
    }

    private record TransientToken(long expiresAt) {
    }

    private record ContainerScene(ScreenHandler handler) {
    }

    private record PendingContainerUse(ScreenHandler previousHandler, long requestedAt) {
    }

    private record PendingFoodUse(Hand hand, Item item, long requestedAt) {
    }
}

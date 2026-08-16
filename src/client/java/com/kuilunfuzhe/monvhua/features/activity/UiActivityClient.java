package com.kuilunfuzhe.monvhua.features.activity;

import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.network.activity.UiActivityPackets;
import com.kuilunfuzhe.monvhua.renderer.activity.UiActivityBubblePipelines;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class UiActivityClient {
    public static final int REVEAL_DURATION_TICKS = 12;
    public static final int HIDE_DURATION_TICKS = 6;

    private static final long NOT_HIDING = Long.MIN_VALUE;
    private static final Map<UUID, VisualState> REMOTE_STATES = new HashMap<>();
    private static UiActivityPackets.Activity lastSentActivity = UiActivityPackets.Activity.NONE;
    private static boolean initialized;

    private UiActivityClient() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        UiActivityBubblePipelines.initialize();

        ClientPlayNetworking.registerGlobalReceiver(UiActivityPackets.StateS2C.ID, (packet, context) ->
                context.client().execute(() -> receive(packet)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            lastSentActivity = UiActivityPackets.Activity.NONE;
            return;
        }

        UiActivityPackets.Activity current = activityFor(client.currentScreen);
        if (current != lastSentActivity
                && SafeClientNetworking.send(new UiActivityPackets.StateC2S(current))) {
            lastSentActivity = current;
        }

        long worldTime = client.world.getTime();
        REMOTE_STATES.entrySet().removeIf(entry -> {
            VisualState state = entry.getValue();
            return state.isHiding() && worldTime - state.hidingAtGameTime() > HIDE_DURATION_TICKS + 2L;
        });
    }

    public static VisualState stateFor(UUID playerUuid) {
        return REMOTE_STATES.get(playerUuid);
    }

    private static UiActivityPackets.Activity activityFor(Screen screen) {
        if (screen instanceof ChatScreen) {
            return UiActivityPackets.Activity.CHAT;
        }
        if (screen instanceof InventoryScreen || screen instanceof CreativeInventoryScreen) {
            return UiActivityPackets.Activity.INVENTORY;
        }
        return UiActivityPackets.Activity.NONE;
    }

    private static void receive(UiActivityPackets.StateS2C packet) {
        if (packet.activity() == UiActivityPackets.Activity.NONE) {
            VisualState previous = REMOTE_STATES.get(packet.playerUuid());
            if (previous == null) {
                return;
            }
            REMOTE_STATES.put(packet.playerUuid(), previous.startHiding(packet.changedAtGameTime()));
            return;
        }

        REMOTE_STATES.put(packet.playerUuid(), new VisualState(
                packet.activity(),
                packet.changedAtGameTime(),
                NOT_HIDING,
                packet.contentId()
        ));
    }

    private static void clear() {
        REMOTE_STATES.clear();
        lastSentActivity = UiActivityPackets.Activity.NONE;
    }

    public record VisualState(
            UiActivityPackets.Activity activity,
            long shownAtGameTime,
            long hidingAtGameTime,
            int contentId
    ) {
        public boolean isHiding() {
            return hidingAtGameTime != NOT_HIDING;
        }

        private VisualState startHiding(long gameTime) {
            return isHiding() ? this : new VisualState(activity, shownAtGameTime, gameTime, contentId);
        }
    }
}

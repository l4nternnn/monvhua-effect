package com.kuilunfuzhe.monvhua.features.activity;

import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.network.activity.UiActivityPackets;
import com.kuilunfuzhe.monvhua.renderer.activity.UiActivityBubblePipelines;
import com.kuilunfuzhe.monvhua.renderer.activity.UiActivityBubbleRenderer;
import com.kuilunfuzhe.monvhua.features.activity.emotion.EmotionTextureManager;
import com.kuilunfuzhe.monvhua.features.activity.emotion.EmotionPickerClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.BookSigningScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class UiActivityClient {
    public static final int REVEAL_DURATION_TICKS = 12;
    public static final int HIDE_DURATION_TICKS = 6;
    private static final int MAGIC_DIARY_CONTENT_ID = 14;

    private static final long NOT_HIDING = Long.MIN_VALUE;
    private static final Map<UUID, VisualState> REMOTE_STATES = new HashMap<>();
    private static UiActivityPackets.Activity lastSentActivity = UiActivityPackets.Activity.NONE;
    private static int lastSentContentId;
    private static int selectedContentId;
    private static boolean initialized;

    private UiActivityClient() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        UiActivityBubblePipelines.initialize();
        EmotionTextureManager.initialize();
        EmotionPickerClient.initialize();

        ClientPlayNetworking.registerGlobalReceiver(UiActivityPackets.StateS2C.ID, (packet, context) ->
                context.client().execute(() -> receive(packet)));
        ClientPlayNetworking.registerGlobalReceiver(UiActivityPackets.BubbleSizeS2C.ID, (packet, context) ->
                context.client().execute(() -> UiActivityBubbleRenderer.setSizeMultiplier(packet.multiplier())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            lastSentActivity = UiActivityPackets.Activity.NONE;
            return;
        }

        UiActivityPackets.Activity current = activityFor(client.currentScreen);
        int currentContentId = switch (current) {
            case CHAT -> selectedContentId;
            case WRITING -> MAGIC_DIARY_CONTENT_ID;
            default -> 0;
        };
        boolean leavingManualChat = lastSentActivity == UiActivityPackets.Activity.CHAT
                && current != UiActivityPackets.Activity.CHAT;
        if ((current != lastSentActivity || currentContentId != lastSentContentId)
                && SafeClientNetworking.send(new UiActivityPackets.StateC2S(current, currentContentId))) {
            lastSentActivity = current;
            lastSentContentId = currentContentId;
            if (leavingManualChat) {
                selectedContentId = 0;
            }
        }

        long worldTime = client.world.getTime();
        var iterator = REMOTE_STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            VisualState state = entry.getValue();
            if (state.isPendingHide() && shouldBeginFade(state, worldTime)) {
                entry.setValue(state.beginFade(worldTime));
            } else if (state.isHiding() && worldTime - state.hidingAtGameTime() > HIDE_DURATION_TICKS + 2L) {
                iterator.remove();
            }
        }
    }

    public static VisualState stateFor(UUID playerUuid) {
        return REMOTE_STATES.get(playerUuid);
    }

    public static int selectedContentId() {
        return selectedContentId;
    }

    public static void selectContent(int contentId) {
        selectedContentId = EmotionCatalog.isValidId(contentId) ? contentId : 0;
    }

    private static UiActivityPackets.Activity activityFor(Screen screen) {
        if (screen instanceof AbstractSignEditScreen
                || screen instanceof BookEditScreen
                || screen instanceof BookSigningScreen) {
            return UiActivityPackets.Activity.WRITING;
        }
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
            REMOTE_STATES.put(packet.playerUuid(), previous.requestHide(packet.changedAtGameTime()));
            return;
        }

        REMOTE_STATES.put(packet.playerUuid(), new VisualState(
                packet.activity(),
                packet.changedAtGameTime(),
                NOT_HIDING,
                NOT_HIDING,
                packet.contentId()
        ));
    }

    private static boolean shouldBeginFade(VisualState state, long worldTime) {
        long elapsedTicks = Math.max(0L, worldTime - state.hideRequestedAtGameTime());
        if (state.contentId() <= 0) {
            return true;
        }
        EmotionCatalog.Entry entry = EmotionCatalog.byId(state.contentId());
        if (entry == null || entry.type() == EmotionCatalog.Type.IMAGE) {
            return elapsedTicks >= EmotionTextureManager.IMAGE_EXIT_HOLD_TICKS;
        }
        return EmotionTextureManager.hasPlayedLoops(state.contentId(), elapsedTicks * 50L, 2);
    }

    private static void clear() {
        REMOTE_STATES.clear();
        lastSentActivity = UiActivityPackets.Activity.NONE;
        lastSentContentId = 0;
        selectedContentId = 0;
        UiActivityBubbleRenderer.setSizeMultiplier(UiActivityBubbleSize.DEFAULT_MULTIPLIER);
    }

    public record VisualState(
            UiActivityPackets.Activity activity,
            long shownAtGameTime,
            long hideRequestedAtGameTime,
            long hidingAtGameTime,
            int contentId
    ) {
        public boolean isHiding() {
            return hidingAtGameTime != NOT_HIDING;
        }

        public boolean isPendingHide() {
            return hideRequestedAtGameTime != NOT_HIDING && !isHiding();
        }

        private VisualState requestHide(long gameTime) {
            return isHiding() || isPendingHide()
                    ? this
                    : new VisualState(activity, shownAtGameTime, gameTime, NOT_HIDING, contentId);
        }

        private VisualState beginFade(long gameTime) {
            return new VisualState(activity, shownAtGameTime, hideRequestedAtGameTime, gameTime, contentId);
        }
    }
}

package com.kuilunfuzhe.monvhua.features.activity;

import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.network.activity.UiActivityPackets;
import com.kuilunfuzhe.monvhua.renderer.activity.UiActivityBubbleRenderer;
import com.kuilunfuzhe.monvhua.renderer.activity.UiActivityBubbleTextureRenderer;
import com.kuilunfuzhe.monvhua.features.activity.emotion.EmotionTextureManager;
import com.kuilunfuzhe.monvhua.features.activity.emotion.EmotionPickerClient;
import com.kuilunfuzhe.monvhua.features.activity.emotion.FoodAnimation;
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
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class UiActivityClient {
    public static final int REVEAL_DURATION_TICKS = 12;
    public static final int HIDE_DURATION_TICKS = 6;
    private static final int MAGIC_DIARY_CONTENT_ID = 14;

    private static final long NOT_HIDING = Long.MIN_VALUE;
    private static final Map<UUID, VisualState> REMOTE_STATES = new HashMap<>();
    private static final Map<UUID, SleepState> SLEEP_STATES = new HashMap<>();
    private static final long SLEEP_CYCLE_TICKS = 104L;
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
        EmotionTextureManager.initialize();
        EmotionPickerClient.initialize();

        ClientPlayNetworking.registerGlobalReceiver(UiActivityPackets.StateS2C.ID, (packet, context) ->
                context.client().execute(() -> receive(packet)));
        ClientPlayNetworking.registerGlobalReceiver(UiActivityPackets.BubbleSizeS2C.ID, (packet, context) ->
                context.client().execute(() -> UiActivityBubbleRenderer.setSizeMultiplier(packet.multiplier())));
        // DISCONNECT may be fired from the connection thread. Keep state changes on the
        // client executor; GPU resources are released after world teardown in tick().
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(UiActivityClient::clear));
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            if (client.world == null) {
                // Defer GPU destruction until the world render batches are gone.
                UiActivityBubbleTextureRenderer.clear();
            }
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
        updateSleepStates(client, worldTime);
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
        VisualState remote = REMOTE_STATES.get(playerUuid);
        if (remote != null) {
            return remote;
        }
        SleepState sleep = SLEEP_STATES.get(playerUuid);
        return sleep == null ? null : sleep.visualState();
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
            REMOTE_STATES.put(packet.playerUuid(), previous.requestHide(packet.shownAtGameTime()));
            return;
        }

        VisualState previous = REMOTE_STATES.get(packet.playerUuid());
        long shownAt = packet.shownAtGameTime();
        if (packet.activity() == UiActivityPackets.Activity.TRANSIENT
                && FoodAnimation.isEatFood(packet.contentId())
                && previous != null
                && FoodAnimation.isEatFood(previous.contentId())
                && previous.activity() == UiActivityPackets.Activity.TRANSIENT) {
            shownAt = previous.shownAtGameTime();
        }
        REMOTE_STATES.put(packet.playerUuid(), new VisualState(
                packet.activity(),
                shownAt,
                packet.effectStartedAtGameTime(),
                NOT_HIDING,
                NOT_HIDING,
                packet.contentId()
        ));
    }

    /** Tracks sleeping poses locally because they are entity state, not UI activity packets. */
    private static void updateSleepStates(MinecraftClient client, long worldTime) {
        for (PlayerEntity player : client.world.getPlayers()) {
            UUID uuid = player.getUuid();
            SleepState current = SLEEP_STATES.get(uuid);
            if (player.isSleeping()) {
                if (current == null) {
                    VisualState visual = new VisualState(
                            UiActivityPackets.Activity.TRANSIENT,
                            worldTime,
                            worldTime,
                            NOT_HIDING,
                            NOT_HIDING,
                            11
                    );
                    SLEEP_STATES.put(uuid, new SleepState(visual, NOT_HIDING));
                } else if (current.finishAtGameTime() != NOT_HIDING) {
                    // Waking and sleeping again before the current round ends resumes looping.
                    SLEEP_STATES.put(uuid, new SleepState(current.visualState(), NOT_HIDING));
                }
                continue;
            }

            if (current == null) {
                continue;
            }
            long finishAt = current.finishAtGameTime();
            if (finishAt == NOT_HIDING) {
                long elapsed = Math.max(0L, worldTime - current.visualState().effectStartedAtGameTime());
                long completedRounds = elapsed / SLEEP_CYCLE_TICKS + 1L;
                finishAt = current.visualState().effectStartedAtGameTime()
                        + completedRounds * SLEEP_CYCLE_TICKS;
                SLEEP_STATES.put(uuid, new SleepState(current.visualState(), finishAt));
            } else if (worldTime >= finishAt) {
                SLEEP_STATES.remove(uuid);
            }
        }
    }

    private static boolean shouldBeginFade(VisualState state, long worldTime) {
        long elapsedTicks = Math.max(0L, worldTime - state.hideRequestedAtGameTime());
        if (state.activity() == UiActivityPackets.Activity.TRANSIENT) {
            return true;
        }
        if (state.contentId() <= 0) {
            return true;
        }
        EmotionCatalog.Entry entry = EmotionCatalog.byId(state.contentId());
        if (entry == null || entry.type() == EmotionCatalog.Type.IMAGE) {
            return elapsedTicks >= EmotionTextureManager.IMAGE_EXIT_HOLD_TICKS;
        }
        if (entry.type() == EmotionCatalog.Type.BLOCK_DISPLAY) {
            return elapsedTicks >= 10L;
        }
        return EmotionTextureManager.hasPlayedLoops(state.contentId(), elapsedTicks * 50L, 2);
    }

    private static void clear() {
        REMOTE_STATES.clear();
        SLEEP_STATES.clear();
        UiActivityBubbleRenderer.clearPending();
        lastSentActivity = UiActivityPackets.Activity.NONE;
        lastSentContentId = 0;
        selectedContentId = 0;
        UiActivityBubbleRenderer.setSizeMultiplier(UiActivityBubbleSize.DEFAULT_MULTIPLIER);
    }

    public record VisualState(
            UiActivityPackets.Activity activity,
            long shownAtGameTime,
            long effectStartedAtGameTime,
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
                    : new VisualState(activity, shownAtGameTime, effectStartedAtGameTime, gameTime, NOT_HIDING, contentId);
        }

        private VisualState beginFade(long gameTime) {
            return new VisualState(activity, shownAtGameTime, effectStartedAtGameTime,
                    hideRequestedAtGameTime, gameTime, contentId);
        }
    }

    private record SleepState(VisualState visualState, long finishAtGameTime) {
    }
}

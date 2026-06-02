package com.kuilunfuzhe.monvhua.features.action;

import com.kuilunfuzhe.monvhua.network.action.TimelineStateS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TimelineScheduler {
    private static final Map<UUID, PlayerTimelineState> playerStates = new ConcurrentHashMap<>();
    private static ActionConfig config;

    public static class PlayerTimelineState {
        public int currentTick = 0;
        public boolean running = false;
        public boolean paused = false;
        public boolean loop = false;
    }

    public static void initialize(ActionConfig cfg) {
        config = cfg;
    }

    public static void reloadConfig() {
        config = ActionConfig.getInstance();
    }

    public static void tick(MinecraftServer server) {
        if (config == null) return;
        Map<Integer, List<String>> schedule = config.timelineSchedule;
        if (schedule.isEmpty()) return;

        int maxSecond = schedule.keySet().stream().max(Integer::compare).orElse(0);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            PlayerTimelineState state = playerStates.get(uuid);
            if (state == null || !state.running || state.paused) continue;

            state.currentTick++;
            int second = state.currentTick / 20;

            List<String> actionIds = schedule.get(second);
            if (actionIds != null) {
                for (String actionId : actionIds) {
                    config.findById(actionId).ifPresent(def ->
                        ActionExecutor.execute(def, player, Map.of("timelineSecond", String.valueOf(second)))
                    );
                }
            }

            if (second >= maxSecond && state.currentTick % 20 == 0) {
                if (state.loop) {
                    state.currentTick = 0;
                } else {
                    state.running = false;
                    sendState(player, state, maxSecond);
                }
            }
        }
    }

    public static void start(ServerPlayerEntity player) {
        PlayerTimelineState state = getOrCreate(player);
        state.currentTick = 0;
        state.running = true;
        state.paused = false;
        sendState(player, state, getMaxSecond());
    }

    public static void stop(ServerPlayerEntity player) {
        PlayerTimelineState state = getOrCreate(player);
        state.running = false;
        state.paused = false;
        state.currentTick = 0;
        sendState(player, state, getMaxSecond());
    }

    public static void pause(ServerPlayerEntity player) {
        PlayerTimelineState state = getOrCreate(player);
        state.running = false;
        state.paused = true;
        sendState(player, state, getMaxSecond());
    }

    public static void resume(ServerPlayerEntity player) {
        PlayerTimelineState state = getOrCreate(player);
        state.running = true;
        state.paused = false;
        sendState(player, state, getMaxSecond());
    }

    public static void setLoop(ServerPlayerEntity player, boolean loop) {
        PlayerTimelineState state = getOrCreate(player);
        state.loop = loop;
        sendState(player, state, getMaxSecond());
    }

    public static void jumpTo(ServerPlayerEntity player, int second) {
        PlayerTimelineState state = getOrCreate(player);
        state.currentTick = Math.max(0, second * 20);
        sendState(player, state, getMaxSecond());
    }

    public static void addAction(ServerPlayerEntity player, int second, String actionId) {
        config.timelineSchedule.computeIfAbsent(second, k -> new ArrayList<>()).add(actionId);
        config.save();
        ActionEngine.reloadConfig();
        sendState(player, getOrCreate(player), getMaxSecond());
    }

    public static void removeAction(ServerPlayerEntity player, int second, String actionId) {
        List<String> ids = config.timelineSchedule.get(second);
        if (ids != null) {
            ids.remove(actionId);
            if (ids.isEmpty()) config.timelineSchedule.remove(second);
        }
        config.save();
        ActionEngine.reloadConfig();
        sendState(player, getOrCreate(player), getMaxSecond());
    }

    public static void moveUp(ServerPlayerEntity player, int second, String actionId) {
        List<String> ids = config.timelineSchedule.get(second);
        if (ids != null) {
            int idx = ids.indexOf(actionId);
            if (idx > 0) java.util.Collections.swap(ids, idx, idx - 1);
        }
        config.save();
        ActionEngine.reloadConfig();
        sendState(player, getOrCreate(player), getMaxSecond());
    }

    public static void moveDown(ServerPlayerEntity player, int second, String actionId) {
        List<String> ids = config.timelineSchedule.get(second);
        if (ids != null) {
            int idx = ids.indexOf(actionId);
            if (idx >= 0 && idx < ids.size() - 1) java.util.Collections.swap(ids, idx, idx + 1);
        }
        config.save();
        ActionEngine.reloadConfig();
        sendState(player, getOrCreate(player), getMaxSecond());
    }

    public static void removeSecond(ServerPlayerEntity player, int second) {
        config.timelineSchedule.remove(second);
        config.save();
        ActionEngine.reloadConfig();
        sendState(player, getOrCreate(player), getMaxSecond());
    }

    public static void cleanupPlayer(UUID uuid) {
        playerStates.remove(uuid);
    }

    public static PlayerTimelineState getOrCreate(ServerPlayerEntity player) {
        return playerStates.computeIfAbsent(player.getUuid(), k -> new PlayerTimelineState());
    }

    public static int getMaxSecond() {
        if (config == null || config.timelineSchedule.isEmpty()) return 0;
        return config.timelineSchedule.keySet().stream().max(Integer::compare).orElse(0);
    }

    private static void sendState(ServerPlayerEntity player, PlayerTimelineState state, int maxSecond) {
        try {
            ServerPlayNetworking.send(player, new TimelineStateS2CPacket(
                    state.currentTick / 20, state.running, state.paused, state.loop, maxSecond));
        } catch (Exception ignored) {}
    }
}

package com.kuilunfuzhe.monvhua.features.action;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ActionEngine {
    private static ActionConfig config;
    private static final Map<String, Map<UUID, Long>> timerLastFire = new ConcurrentHashMap<>();
    private static final Map<String, Map<UUID, Long>> delayPending = new ConcurrentHashMap<>();
    private static final Map<String, Map<UUID, Integer>> intervalExecCount = new ConcurrentHashMap<>();

    public static void initialize(ActionConfig cfg) {
        config = cfg;
        ActionTriggerHandler.register();
        registerTicks();
        registerConnectionEvents();
    }

    private static void registerTicks() {
        ServerTickEvents.END_SERVER_TICK.register(ActionEngine::tickTimers);
        ServerTickEvents.END_SERVER_TICK.register(ActionEngine::tickDelays);
    }

    private static void registerConnectionEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            long now = server.getOverworld().getTime();
            for (ActionConfig.ActionDef def : config.getEnabled()) {
                for (ActionConfig.TriggerDef trig : def.triggers) {
                    String type = trig.type.toUpperCase();
                    if ("TIMER_INTERVAL".equals(type)) {
                        timerLastFire.computeIfAbsent(def.id, k -> new ConcurrentHashMap<>())
                                .putIfAbsent(player.getUuid(), now);
                        intervalExecCount.computeIfAbsent(def.id, k -> new ConcurrentHashMap<>())
                                .putIfAbsent(player.getUuid(), 0);
                    } else if ("TIMER_DELAY".equals(type)) {
                        int delayTicks = getIntParam(trig.params, "delayTicks", 100);
                        delayPending.computeIfAbsent(def.id, k -> new ConcurrentHashMap<>())
                                .putIfAbsent(player.getUuid(), now + delayTicks);
                    }
                }
            }
            fireTriggersFor(TriggerType.PLAYER_JOIN, player, Map.of());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            cleanupPlayer(handler.getPlayer().getUuid());
        });
    }

    private static void tickTimers(MinecraftServer server) {
        long now = server.getOverworld().getTime();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            for (ActionConfig.ActionDef def : config.getEnabled()) {
                for (ActionConfig.TriggerDef trig : def.triggers) {
                    if (!"TIMER_INTERVAL".equalsIgnoreCase(trig.type)) continue;
                    int interval = getIntParam(trig.params, "intervalTicks", 200);
                    int max = getIntParam(trig.params, "maxExecutions", -1);

                    Map<UUID, Long> lastMap = timerLastFire.get(def.id);
                    if (lastMap == null) continue;
                    Long last = lastMap.get(uuid);
                    if (last == null) {
                        lastMap.put(uuid, now);
                        continue;
                    }
                    if (now - last < interval) continue;

                    Map<UUID, Integer> cntMap = intervalExecCount.get(def.id);
                    int count = cntMap != null ? cntMap.getOrDefault(uuid, 0) : 0;
                    if (max > 0 && count >= max) continue;

                    lastMap.put(uuid, now);
                    if (cntMap != null) cntMap.put(uuid, count + 1);

                    Map<String, String> vars = new LinkedHashMap<>();
                    vars.put("triggerTick", String.valueOf(now));
                    vars.put("executionCount", String.valueOf(count + 1));
                    ActionExecutor.execute(def, player, vars);
                }
            }
        }
    }

    private static void tickDelays(MinecraftServer server) {
        long now = server.getOverworld().getTime();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            for (ActionConfig.ActionDef def : config.getEnabled()) {
                for (ActionConfig.TriggerDef trig : def.triggers) {
                    if (!"TIMER_DELAY".equalsIgnoreCase(trig.type)) continue;
                    Map<UUID, Long> pendMap = delayPending.get(def.id);
                    if (pendMap == null) continue;
                    Long scheduled = pendMap.get(uuid);
                    if (scheduled == null || now < scheduled) continue;
                    pendMap.remove(uuid);
                    ActionExecutor.execute(def, player, Map.of("triggerTick", String.valueOf(now)));
                }
            }
        }
    }

    public static void fireTriggersFor(TriggerType type, ServerPlayerEntity player, Map<String, Object> context) {
        Map<String, String> vars = new LinkedHashMap<>();
        if (context != null) {
            for (Map.Entry<String, Object> e : context.entrySet()) {
                vars.put(e.getKey(), e.getValue() != null ? e.getValue().toString() : "");
            }
        }
        for (ActionConfig.ActionDef def : config.getEnabled()) {
            for (ActionConfig.TriggerDef trig : def.triggers) {
                if (matchesTrigger(type, trig, context)) {
                    ActionExecutor.execute(def, player, vars);
                    break; // one match per action is enough
                }
            }
        }
    }

    private static boolean matchesTrigger(TriggerType type, ActionConfig.TriggerDef trig, Map<String, Object> context) {
        String trigType = trig.type.toUpperCase();
        return switch (type) {
            case PLAYER_JOIN -> "PLAYER_JOIN".equals(trigType);
            case PLAYER_DEATH -> "PLAYER_DEATH".equals(trigType);
            case ATTACK -> "ATTACK".equals(trigType);
            case MANUAL -> "MANUAL".equals(trigType);
            case CHAT_KEYWORD -> {
                if (!"CHAT_KEYWORD".equals(trigType)) yield false;
                yield checkKeywordMatch(trig, context);
            }
            default -> false;
        };
    }

    @SuppressWarnings("unchecked")
    private static boolean checkKeywordMatch(ActionConfig.TriggerDef trig, Map<String, Object> context) {
        if (context == null) return false;
        String message = context.getOrDefault("message", "").toString().toLowerCase();
        Object kwObj = trig.params.get("keywords");
        if (!(kwObj instanceof List)) return false;
        List<String> keywords = (List<String>) kwObj;
        boolean caseSensitive = getBoolParam(trig.params, "caseSensitive", false);
        String matchMode = getStrParam(trig.params, "matchMode", "contains");
        String checkMsg = caseSensitive ? context.getOrDefault("message", "").toString() : message;
        for (String kw : keywords) {
            String checkKw = caseSensitive ? kw : kw.toLowerCase();
            if ("equals".equalsIgnoreCase(matchMode)) {
                if (checkMsg.equals(checkKw)) return true;
            } else {
                if (checkMsg.contains(checkKw)) return true;
            }
        }
        return false;
    }

    public static void triggerManually(String actionId, ServerPlayerEntity player) {
        config.findById(actionId).ifPresent(def ->
                ActionExecutor.execute(def, player, Map.of("trigger", "manual")));
    }

    public static void reloadConfig() {
        config = ActionConfig.getInstance();
        timerLastFire.clear();
        delayPending.clear();
        intervalExecCount.clear();
    }

    public static void cleanupPlayer(UUID uuid) {
        timerLastFire.values().forEach(m -> m.remove(uuid));
        delayPending.values().forEach(m -> m.remove(uuid));
        intervalExecCount.values().forEach(m -> m.remove(uuid));
    }

    public static ActionConfig getConfig() {
        return config;
    }

    // --- param helpers ---
    static int getIntParam(Map<String, Object> params, String key, int def) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.intValue();
        return def;
    }

    static boolean getBoolParam(Map<String, Object> params, String key, boolean def) {
        Object v = params.get(key);
        if (v instanceof Boolean b) return b;
        return def;
    }

    static String getStrParam(Map<String, Object> params, String key, String def) {
        Object v = params.get(key);
        return v != null ? v.toString() : def;
    }

    public enum TriggerType {
        CHAT_KEYWORD, ATTACK, TIMER_INTERVAL, TIMER_DELAY, PLAYER_JOIN, PLAYER_DEATH, MANUAL
    }
}

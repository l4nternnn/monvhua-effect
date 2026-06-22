package com.kuilunfuzhe.monvhua.client.imitate;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ImitateClientManager {

    private static final ConcurrentHashMap<UUID, String> imitateMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> imitateEndTime = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> switchCooldownEnd = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> soundWaveCooldownEnd = new ConcurrentHashMap<>();

    public static void setImitate(UUID uuid, String roleName, long endTime, long switchCooldown, long soundWaveCooldown) {
        if (roleName == null || roleName.isEmpty()) {
            imitateMap.remove(uuid);
            imitateEndTime.remove(uuid);
            AreaImitateClientManager.clearAreaImitate();
        } else {
            imitateMap.put(uuid, roleName);
            imitateEndTime.put(uuid, endTime);
        }
        switchCooldownEnd.put(uuid, switchCooldown);
        soundWaveCooldownEnd.put(uuid, soundWaveCooldown);
    }

    public static String getImitateName(UUID uuid) {
        return imitateMap.get(uuid);
    }

    public static boolean isImitating(UUID uuid) {
        return imitateMap.containsKey(uuid);
    }

    public static long getEndTime(UUID uuid) {
        return imitateEndTime.getOrDefault(uuid, 0L);
    }

    public static long getSwitchCooldownEnd(UUID uuid) {
        return switchCooldownEnd.getOrDefault(uuid, 0L);
    }

    public static long getSoundWaveCooldownEnd(UUID uuid) {
        return soundWaveCooldownEnd.getOrDefault(uuid, 0L);
    }

    public static int getRemainingTime(UUID uuid) {
        Long endTime = imitateEndTime.get(uuid);
        if (endTime == null || endTime == 0) return 0;
        long remaining = (endTime - System.currentTimeMillis()) / 1000;
        return remaining > 0 ? (int) remaining : 0;
    }

    public static int getSwitchCooldownRemaining(UUID uuid) {
        Long endTime = switchCooldownEnd.get(uuid);
        if (endTime == null || endTime == 0) return 0;
        long remaining = (endTime - System.currentTimeMillis()) / 1000;
        return remaining > 0 ? (int) remaining : 0;
    }

    public static int getSoundWaveCooldownRemaining(UUID uuid) {
        Long endTime = soundWaveCooldownEnd.get(uuid);
        if (endTime == null || endTime == 0) return 0;
        long remaining = (endTime - System.currentTimeMillis()) / 1000;
        return remaining > 0 ? (int) remaining : 0;
    }

    public static void initialize() {
    }

    public static void resetCooldowns(UUID uuid) {
        switchCooldownEnd.remove(uuid);
        soundWaveCooldownEnd.remove(uuid);
    }
}
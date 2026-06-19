package com.kuilunfuzhe.monvhua.network.imitate;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SilenceServerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Monvhua/SilenceServer");

    private static final Map<UUID, Long> silenceEndTime = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> casterMap = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> notifiedEnd = new ConcurrentHashMap<>();

    public static void startSilence(UUID targetUUID, UUID casterUUID, int durationSeconds) {
        long endTime = System.currentTimeMillis() + durationSeconds * 1000L;
        silenceEndTime.put(targetUUID, endTime);
        casterMap.put(targetUUID, casterUUID);
        notifiedEnd.remove(targetUUID);
        LOGGER.info("玩家 {} 开始静音，持续时间: {}秒，施法者: {}", targetUUID, durationSeconds, casterUUID);
    }

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();

        silenceEndTime.forEach((targetUUID, endTime) -> {
            if (now >= endTime) {
                ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetUUID);
                if (target != null && !notifiedEnd.containsKey(targetUUID)) {
                    target.sendMessage(Text.literal("§a静音效果已结束"), true);
                    LOGGER.info("玩家 {} 静音效果已结束", target.getName().getString());
                    notifiedEnd.put(targetUUID, true);
                }

                UUID casterUUID = casterMap.get(targetUUID);
                if (casterUUID != null) {
                    ServerPlayerEntity caster = server.getPlayerManager().getPlayer(casterUUID);
                    if (caster != null) {
                        caster.sendMessage(Text.literal("§a静音效果已结束，目标恢复正常"), true);
                        LOGGER.info("施法者 {} 收到静音结束提示", caster.getName().getString());
                    }
                }

                silenceEndTime.remove(targetUUID);
                casterMap.remove(targetUUID);
            }
        });
    }

    public static boolean isPlayerSilenced(UUID playerUUID) {
        Long endTime = silenceEndTime.get(playerUUID);
        if (endTime == null) return false;
        return System.currentTimeMillis() < endTime;
    }

    public static int getRemainingSeconds(UUID playerUUID) {
        Long endTime = silenceEndTime.get(playerUUID);
        if (endTime == null) return 0;
        long remaining = (endTime - System.currentTimeMillis()) / 1000;
        return remaining > 0 ? (int) remaining : 0;
    }

    public static void clearSilence(UUID playerUUID) {
        silenceEndTime.remove(playerUUID);
        casterMap.remove(playerUUID);
        notifiedEnd.remove(playerUUID);
        LOGGER.info("玩家 {} 静音效果已清除", playerUUID);
    }

    public static UUID getCasterUUID(UUID targetUUID) {
        return casterMap.get(targetUUID);
    }
}
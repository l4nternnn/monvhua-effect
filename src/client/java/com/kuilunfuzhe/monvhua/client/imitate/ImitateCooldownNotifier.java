package com.kuilunfuzhe.monvhua.client.imitate;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ImitateCooldownNotifier {

    private static final ConcurrentHashMap<UUID, Boolean> wasSwitchCooldownActive = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> wasSoundWaveCooldownActive = new ConcurrentHashMap<>();

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            UUID uuid = client.player.getUuid();

            checkSwitchCooldownEnd(client, uuid);
            checkSoundWaveCooldownEnd(client, uuid);
        });
    }

    private static void checkSwitchCooldownEnd(MinecraftClient client, UUID uuid) {
        int remaining = ImitateClientManager.getSwitchCooldownRemaining(uuid);
        boolean wasActive = wasSwitchCooldownActive.getOrDefault(uuid, false);
        boolean isActive = remaining > 0;

        if (wasActive && !isActive) {
            client.player.sendMessage(Text.literal("§a模仿魔法冷却结束").formatted(Formatting.GREEN), true);
        }

        wasSwitchCooldownActive.put(uuid, isActive);
    }

    private static void checkSoundWaveCooldownEnd(MinecraftClient client, UUID uuid) {
        int remaining = ImitateClientManager.getSoundWaveCooldownRemaining(uuid);
        boolean wasActive = wasSoundWaveCooldownActive.getOrDefault(uuid, false);
        boolean isActive = remaining > 0;

        if (wasActive && !isActive) {
            client.player.sendMessage(Text.literal("§b声波震荡冷却结束").formatted(Formatting.AQUA), true);
        }

        wasSoundWaveCooldownActive.put(uuid, isActive);
    }
}
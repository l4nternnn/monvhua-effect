package com.kuilunfuzhe.monvhua.features.cosmic_box;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CosmicBoxNetworking {
    private static final Map<UUID, Boolean> LAST_VISIBILITY = new HashMap<>();
    private static boolean serverReceiversRegistered;
    private static int syncTick;

    private CosmicBoxNetworking() {
    }

    public static void registerS2CPackets() {
        CosmicBoxTargetListS2CPacket.register();
        CosmicBoxBeamVisibilityS2CPacket.register();
    }

    public static void registerC2SPackets() {
        CosmicBoxSelectTargetC2SPacket.register();
    }

    public static void registerServerReceivers() {
        if (serverReceiversRegistered) {
            return;
        }

        serverReceiversRegistered = true;
        ServerPlayNetworking.registerGlobalReceiver(CosmicBoxSelectTargetC2SPacket.ID, (packet, context) -> {
            context.server().execute(() -> handleSelectTarget(packet, context.player()));
        });
        ServerTickEvents.END_SERVER_TICK.register(CosmicBoxNetworking::syncBeamVisibility);
    }

    public static boolean canUseCosmicBox(ServerPlayerEntity player) {
        return player.isCreative() || player.getCommandTags().contains("zhuizong");
    }

    private static void handleSelectTarget(CosmicBoxSelectTargetC2SPacket packet, ServerPlayerEntity player) {
        if (!canUseCosmicBox(player)) {
            player.sendMessage(Text.literal("只有创造模式或拥有 zhuizong 标签的玩家可以设置宇宙盒目标"), true);
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (player.squaredDistanceTo(packet.pos().toCenterPos()) > 64.0D) {
            player.sendMessage(Text.literal("距离宇宙盒太远"), true);
            return;
        }
        if (!(world.getBlockEntity(packet.pos()) instanceof CosmicBoxBlockEntity cosmicBox)) {
            return;
        }

        if (packet.targets().isEmpty()) {
            cosmicBox.setTargets(List.of());
            cosmicBox.setBeamActive(false);
            player.sendMessage(Text.literal("已清空宇宙盒目标"), true);
            return;
        }

        List<CosmicBoxBlockEntity.TargetRef> selected = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (CosmicBoxSelectTargetC2SPacket.SelectedTarget requested : packet.targets()) {
            if (!seen.add(requested.uuid())) {
                continue;
            }
            Entity target = world.getEntity(requested.uuid());
            if (target == null || !target.isAlive()) {
                continue;
            }
            String name = requested.name().isBlank() ? target.getName().getString() : requested.name();
            selected.add(new CosmicBoxBlockEntity.TargetRef(target.getUuid(), name));
        }

        if (selected.isEmpty()) {
            cosmicBox.setTargets(List.of());
            cosmicBox.setBeamActive(false);
            player.sendMessage(Text.literal("目标不在当前世界或已经消失"), true);
            return;
        }

        cosmicBox.setTargets(selected);
        player.sendMessage(Text.literal("宇宙盒目标已设置为 " + selected.size() + " 个"), true);
    }

    private static void syncBeamVisibility(MinecraftServer server) {
        syncTick++;
        if (syncTick < 20) {
            return;
        }
        syncTick = 0;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boolean visible = canUseCosmicBox(player);
            Boolean previous = LAST_VISIBILITY.put(player.getUuid(), visible);
            if (previous == null || previous != visible) {
                ServerPlayNetworking.send(player, new CosmicBoxBeamVisibilityS2CPacket(visible));
            }
        }
        LAST_VISIBILITY.keySet().removeIf(uuid -> server.getPlayerManager().getPlayer(uuid) == null);
    }
}

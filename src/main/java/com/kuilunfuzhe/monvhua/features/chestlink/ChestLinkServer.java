package com.kuilunfuzhe.monvhua.features.chestlink;

import com.kuilunfuzhe.monvhua.item.chestlink.ChestLinkItem;
import com.kuilunfuzhe.monvhua.network.chestlink.ChestLinkMappingsS2CPacket;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** Server-owned entrance lookup and client mapping synchronization. */
public final class ChestLinkServer {
    private static final org.slf4j.Logger LOGGER = com.kuilunfuzhe.monvhua.MonvhuaMod.LOGGER;

    private ChestLinkServer() {}

    public static void initialize() {
        UseBlockCallback.EVENT.register(ChestLinkServer::handleEmptyHandEntrance);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ChestLinkItem.migrateLegacyMappings(handler.player);
            sync(handler.player);
        }));
    }

    private static ActionResult handleEmptyHandEntrance(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (world.isClient() || player.isSneaking() || !(player instanceof ServerPlayerEntity serverPlayer)
                || !(world instanceof ServerWorld serverWorld)
                || !world.getBlockState(hit.getBlockPos()).isOf(Blocks.CHEST)) return ActionResult.PASS;
        ItemStack held = player.getStackInHand(hand);
        if (!held.isEmpty() || !player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty()) return ActionResult.PASS;
        ChestLinkStore.Endpoint entrance = new ChestLinkStore.Endpoint(world.getRegistryKey().getValue().toString(), hit.getBlockPos().toImmutable());
        ChestLinkStore.Mapping mapping = ChestLinkStore.get(serverPlayer.getServer()).find(entrance).orElse(null);
        if (mapping == null) return ActionResult.PASS;
        if (!(serverWorld.getBlockEntity(hit.getBlockPos()) instanceof ChestBlockEntity)) return ActionResult.PASS;
        LOGGER.info("[ChestLink] empty-hand entrance player={} entrance={} source={}", serverPlayer.getName().getString(), entrance.pos(), mapping.source().pos());
        return ChestLinkItem.openSource(serverPlayer, mapping.source());
    }

    public static void sync(ServerPlayerEntity player) {
        if (player == null || player.getServer() == null || !ServerPlayNetworking.canSend(player, ChestLinkMappingsS2CPacket.ID)) return;
        for (ChestLinkMappingsS2CPacket packet : packets(ChestLinkStore.get(player.getServer()))) ServerPlayNetworking.send(player, packet);
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) return;
        List<ChestLinkMappingsS2CPacket> packets = packets(ChestLinkStore.get(server));
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(player, ChestLinkMappingsS2CPacket.ID)) {
                for (ChestLinkMappingsS2CPacket packet : packets) ServerPlayNetworking.send(player, packet);
            }
        }
    }

    private static List<ChestLinkMappingsS2CPacket> packets(ChestLinkStore store) {
        ArrayList<ChestLinkMappingsS2CPacket.Entry> entries = new ArrayList<>();
        for (ChestLinkStore.Mapping mapping : store.all()) {
            entries.add(new ChestLinkMappingsS2CPacket.Entry(
                    mapping.entrance().dimension(), mapping.entrance().pos().getX(), mapping.entrance().pos().getY(), mapping.entrance().pos().getZ(),
                    mapping.source().dimension(), mapping.source().pos().getX(), mapping.source().pos().getY(), mapping.source().pos().getZ()));
        }
        if (entries.isEmpty()) return List.of(new ChestLinkMappingsS2CPacket(true, List.of()));
        ArrayList<ChestLinkMappingsS2CPacket> packets = new ArrayList<>();
        for (int start = 0; start < entries.size(); start += 128) {
            packets.add(new ChestLinkMappingsS2CPacket(start == 0, List.copyOf(entries.subList(start, Math.min(start + 128, entries.size())))));
        }
        return packets;
    }
}

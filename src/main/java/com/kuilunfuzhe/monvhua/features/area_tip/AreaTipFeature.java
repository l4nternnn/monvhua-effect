package com.kuilunfuzhe.monvhua.features.area_tip;

import com.kuilunfuzhe.monvhua.features.gravity.GravityAreaSpec;
import com.kuilunfuzhe.monvhua.item.area_tip.AreaTipItems;
import com.kuilunfuzhe.monvhua.item.config.AreaTipConfig;
import com.kuilunfuzhe.monvhua.network.area_tip.AreaTipPackets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AreaTipFeature {
    private static final Map<UUID, UUID> LAST_TRIGGERED_GROUP = new HashMap<>();

    private AreaTipFeature() {
    }

    public static void initialize() {
        ServerPlayNetworking.registerGlobalReceiver(AreaTipPackets.RequestConfigC2S.ID, (packet, context) ->
                ServerPlayNetworking.send(context.player(), new AreaTipPackets.ConfigS2C(AreaTipConfig.getInstance().toJson())));

        ServerPlayNetworking.registerGlobalReceiver(AreaTipPackets.UpdateConfigC2S.ID, (packet, context) ->
                context.server().execute(() -> updateConfig(context.player(), packet.json())));

        ServerPlayNetworking.registerGlobalReceiver(AreaTipPackets.RequestAreasC2S.ID, (packet, context) ->
                syncAreasTo(context.player()));

        ServerPlayNetworking.registerGlobalReceiver(AreaTipPackets.PlaceAreaC2S.ID, (packet, context) ->
                context.server().execute(() -> placeArea(context.player(), packet)));

        ServerPlayNetworking.registerGlobalReceiver(AreaTipPackets.PlaceBoundsC2S.ID, (packet, context) ->
                context.server().execute(() -> placeBounds(context.player(), packet)));

        ServerPlayNetworking.registerGlobalReceiver(AreaTipPackets.DeleteBoundsC2S.ID, (packet, context) ->
                context.server().execute(() -> deleteBounds(context.player(), packet)));

        ServerPlayNetworking.registerGlobalReceiver(AreaTipPackets.PlaceSelectionC2S.ID, (packet, context) ->
                context.server().execute(() -> placeSelection(context.player(), packet)));

        ServerPlayNetworking.registerGlobalReceiver(AreaTipPackets.DeleteSelectionC2S.ID, (packet, context) ->
                context.server().execute(() -> deleteSelection(context.player(), packet)));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncTo(handler.getPlayer()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 5 != 0) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                tickPlayerAreaTip(player);
            }
        });
    }

    public static void syncTo(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        ServerPlayNetworking.send(player, new AreaTipPackets.ConfigS2C(AreaTipConfig.getInstance().toJson()));
        syncAreasTo(player);
    }

    private static void updateConfig(ServerPlayerEntity player, String json) {
        if (player == null || !canUseAreaTip(player)) {
            return;
        }
        AreaTipConfig config = AreaTipConfig.fromJson(json);
        Set<UUID> validGroupIds = groupIds(config);
        AreaTipConfig.setInstance(config);
        LAST_TRIGGERED_GROUP.entrySet().removeIf(entry -> !validGroupIds.contains(entry.getValue()));
        for (ServerWorld world : player.getServer().getWorlds()) {
            if (AreaTipAreaStore.get(world).removeGroupsNotIn(validGroupIds) > 0) {
                broadcastFullSync(world);
            }
        }
        for (ServerPlayerEntity target : player.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(target, new AreaTipPackets.ConfigS2C(config.toJson()));
        }
        player.sendMessage(Text.literal("\u00a7aArea tip config updated"), true);
    }

    private static Set<UUID> groupIds(AreaTipConfig config) {
        Set<UUID> ids = new HashSet<>();
        for (AreaTipConfig.GroupConfig group : config.groups) {
            ids.add(group.uuid());
        }
        return ids;
    }

    private static void placeArea(ServerPlayerEntity player, AreaTipPackets.PlaceAreaC2S packet) {
        if (player == null || !(player.getWorld() instanceof ServerWorld world) || !isHoldingAreaTipStick(player)) {
            return;
        }
        GravityAreaSpec spec = new GravityAreaSpec(
                GravityAreaSpec.Shape.byId(packet.shape()),
                GravityAreaSpec.Half.byId(packet.half()),
                packet.sizeX(),
                packet.sizeY(),
                packet.sizeZ()
        );
        AreaTipAreaStore.StoredArea area = AreaTipAreaStore.get(world).addLatest(new AreaTipAreaStore.StoredArea(
                UUID.randomUUID(),
                packet.groupId(),
                packet.center(),
                spec.shape().ordinal(),
                spec.half().ordinal(),
                spec.sizeX(),
                spec.sizeY(),
                spec.sizeZ(),
                packet.color()
        ));
        broadcastFullSync(world);
        player.sendMessage(Text.literal("\u00a7aArea tip placed"), true);
    }

    private static void placeBounds(ServerPlayerEntity player, AreaTipPackets.PlaceBoundsC2S packet) {
        if (player == null || !(player.getWorld() instanceof ServerWorld world) || !canUseAreaTip(player)) {
            return;
        }
        BlockPos min = min(packet.min(), packet.max());
        BlockPos max = max(packet.min(), packet.max());
        BlockPos center = new BlockPos(
                (min.getX() + max.getX()) / 2,
                (min.getY() + max.getY()) / 2,
                (min.getZ() + max.getZ()) / 2
        );
        AreaTipAreaStore.StoredArea area = AreaTipAreaStore.get(world).addLatest(new AreaTipAreaStore.StoredArea(
                UUID.randomUUID(),
                packet.groupId(),
                center,
                GravityAreaSpec.Shape.BOX.ordinal(),
                GravityAreaSpec.Half.FULL.ordinal(),
                1,
                1,
                1,
                packet.color(),
                min,
                max
        ));
        broadcastFullSync(world);
        player.sendMessage(Text.literal("\u00a7aArea tip placed from Axiom selection"), true);
        sendGroupMessage(player, packet.groupId());
    }

    private static void placeSelection(ServerPlayerEntity player, AreaTipPackets.PlaceSelectionC2S packet) {
        if (player == null || !(player.getWorld() instanceof ServerWorld world) || !canUseAreaTip(player) || packet.blocks().isEmpty()) {
            return;
        }
        BlockPos min = minOf(packet.blocks());
        BlockPos max = maxOf(packet.blocks());
        BlockPos center = new BlockPos(
                (min.getX() + max.getX()) / 2,
                (min.getY() + max.getY()) / 2,
                (min.getZ() + max.getZ()) / 2
        );
        AreaTipAreaStore.StoredArea area = AreaTipAreaStore.get(world).addLatest(new AreaTipAreaStore.StoredArea(
                UUID.randomUUID(),
                packet.groupId(),
                center,
                GravityAreaSpec.Shape.BOX.ordinal(),
                GravityAreaSpec.Half.FULL.ordinal(),
                1,
                1,
                1,
                packet.color(),
                min,
                max,
                packet.blocks()
        ));
        broadcastFullSync(world);
        player.sendMessage(Text.literal("\u00a7aArea tip filled " + area.blocks().size() + " selected block(s)"), true);
        sendGroupMessage(player, packet.groupId());
    }

    private static void deleteBounds(ServerPlayerEntity player, AreaTipPackets.DeleteBoundsC2S packet) {
        if (player == null || !(player.getWorld() instanceof ServerWorld world) || !canUseAreaTip(player)) {
            return;
        }
        int removed = AreaTipAreaStore.get(world).removeIntersecting(packet.groupId(), packet.min(), packet.max());
        if (removed > 0) {
            broadcastFullSync(world);
            player.sendMessage(Text.literal("\u00a7eDeleted " + removed + " area tip range(s)"), true);
        } else {
            player.sendMessage(Text.literal("\u00a7cNo area tip ranges intersect the Axiom selection"), true);
        }
    }

    private static void deleteSelection(ServerPlayerEntity player, AreaTipPackets.DeleteSelectionC2S packet) {
        if (player == null || !(player.getWorld() instanceof ServerWorld world) || !canUseAreaTip(player) || packet.blocks().isEmpty()) {
            return;
        }
        int removed = AreaTipAreaStore.get(world).removeIntersectingBlocks(packet.groupId(), packet.blocks());
        if (removed > 0) {
            broadcastFullSync(world);
            player.sendMessage(Text.literal("\u00a7eDeleted " + removed + " area tip block(s)"), true);
        } else {
            player.sendMessage(Text.literal("\u00a7cNo area tip blocks intersect the Axiom selection"), true);
        }
    }

    private static void broadcastArea(ServerWorld world, AreaTipAreaStore.StoredArea area) {
        AreaTipPackets.AreaUpdateS2C update = new AreaTipPackets.AreaUpdateS2C(AreaTipPackets.AreaData.fromStored(area));
        for (ServerPlayerEntity target : world.getPlayers()) {
            ServerPlayNetworking.send(target, update);
        }
    }

    private static void broadcastFullSync(ServerWorld world) {
        List<AreaTipPackets.AreaData> areas = AreaTipAreaStore.get(world).toAreas().stream()
                .map(AreaTipPackets.AreaData::fromStored)
                .toList();
        AreaTipPackets.FullSyncS2C packet = new AreaTipPackets.FullSyncS2C(areas);
        for (ServerPlayerEntity target : world.getPlayers()) {
            ServerPlayNetworking.send(target, packet);
        }
    }

    private static BlockPos min(BlockPos first, BlockPos second) {
        return new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ())
        );
    }

    private static BlockPos max(BlockPos first, BlockPos second) {
        return new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ())
        );
    }

    private static BlockPos minOf(List<BlockPos> blocks) {
        int x = Integer.MAX_VALUE;
        int y = Integer.MAX_VALUE;
        int z = Integer.MAX_VALUE;
        for (BlockPos block : blocks) {
            x = Math.min(x, block.getX());
            y = Math.min(y, block.getY());
            z = Math.min(z, block.getZ());
        }
        return new BlockPos(x, y, z);
    }

    private static BlockPos maxOf(List<BlockPos> blocks) {
        int x = Integer.MIN_VALUE;
        int y = Integer.MIN_VALUE;
        int z = Integer.MIN_VALUE;
        for (BlockPos block : blocks) {
            x = Math.max(x, block.getX());
            y = Math.max(y, block.getY());
            z = Math.max(z, block.getZ());
        }
        return new BlockPos(x, y, z);
    }

    private static void syncAreasTo(ServerPlayerEntity player) {
        if (player == null || !(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        List<AreaTipPackets.AreaData> areas = AreaTipAreaStore.get(world).toAreas().stream()
                .map(AreaTipPackets.AreaData::fromStored)
                .toList();
        ServerPlayNetworking.send(player, new AreaTipPackets.FullSyncS2C(areas));
    }

    private static void tickPlayerAreaTip(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Optional<AreaTipAreaStore.StoredArea> area = AreaTipAreaStore.get(world).firstContaining(player.getBoundingBox());
        if (area.isEmpty()) {
            LAST_TRIGGERED_GROUP.remove(player.getUuid());
            return;
        }
        UUID groupId = area.get().groupId();
        UUID previous = LAST_TRIGGERED_GROUP.get(player.getUuid());
        if (groupId.equals(previous)) {
            return;
        }
        LAST_TRIGGERED_GROUP.put(player.getUuid(), groupId);
        String message = AreaTipConfig.getInstance().findGroup(groupId)
                .map(group -> group.message)
                .orElse("");
        if (!message.isBlank()) {
            player.sendMessage(parseLegacyText(message), false);
        }
    }

    private static void sendGroupMessage(ServerPlayerEntity player, UUID groupId) {
        String message = AreaTipConfig.getInstance().findGroup(groupId)
                .map(group -> group.message)
                .orElse("");
        if (!message.isBlank()) {
            player.sendMessage(parseLegacyText(message), false);
        }
    }

    private static Text parseLegacyText(String message) {
        MutableText result = Text.empty();
        Style style = Style.EMPTY;
        StringBuilder segment = new StringBuilder();
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '\u00a7' && i + 1 < message.length()) {
                Formatting formatting = Formatting.byCode(message.charAt(i + 1));
                if (formatting != null) {
                    appendSegment(result, segment, style);
                    if (formatting == Formatting.RESET) {
                        style = Style.EMPTY;
                    } else {
                        style = style.withFormatting(formatting);
                    }
                    i++;
                    continue;
                }
            }
            segment.append(c);
        }
        appendSegment(result, segment, style);
        return result;
    }

    private static void appendSegment(MutableText result, StringBuilder segment, Style style) {
        if (segment.isEmpty()) {
            return;
        }
        result.append(Text.literal(segment.toString()).setStyle(style));
        segment.setLength(0);
    }

    private static boolean canUseAreaTip(ServerPlayerEntity player) {
        return player.isCreative() || player.hasPermissionLevel(2) || isHoldingAreaTipStick(player);
    }

    private static boolean isHoldingAreaTipStick(PlayerEntity player) {
        return isAreaTipStick(player.getMainHandStack()) || isAreaTipStick(player.getOffHandStack());
    }

    private static boolean isAreaTipStick(ItemStack stack) {
        return stack.getItem() == AreaTipItems.AREA_TIP_STICK;
    }
}

package com.kuilunfuzhe.monvhua.features.area_tip;

import com.kuilunfuzhe.monvhua.features.gravity.GravityAreaSpec;
import com.kuilunfuzhe.monvhua.item.area_tip.AreaTipItems;
import com.kuilunfuzhe.monvhua.item.config.AreaTipConfig;
import com.kuilunfuzhe.monvhua.network.area_tip.AreaTipPackets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
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
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Queue;

public final class AreaTipFeature {
    private static final Map<UUID, UUID> LAST_TRIGGERED_GROUP = new HashMap<>();
    private static final Map<UUID, ResourceUploadState> RESOURCE_UPLOADS = new HashMap<>();
    private static final Map<UUID, Queue<ResourceDownloadJob>> RESOURCE_DOWNLOADS = new HashMap<>();
    private static final Map<UUID, SelectionPlacementState> SELECTION_PLACEMENTS = new HashMap<>();
    private static final Queue<SelectionMergeJob> SELECTION_MERGE_JOBS = new ArrayDeque<>();
    private static final int MAX_RESOURCE_BYTES = 64 * 1024 * 1024;
    private static final int RESOURCE_BYTES_PER_TICK = 3072;
    private static final int MAX_SELECTION_BATCH_CHUNKS = 4096;
    private static final int SELECTION_MERGE_CHUNKS_PER_TICK = 2;
    private static final int SELECTION_MERGE_AREAS_PER_TICK = 64;

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

        ServerPlayNetworking.registerGlobalReceiver(AreaTipPackets.PlaceSelectionChunkC2S.ID, (packet, context) ->
                context.server().execute(() -> placeSelectionChunk(context.player(), packet)));

        ServerPlayNetworking.registerGlobalReceiver(AreaTipPackets.DeleteSelectionC2S.ID, (packet, context) ->
                context.server().execute(() -> deleteSelection(context.player(), packet)));

        ServerPlayNetworking.registerGlobalReceiver(AreaTipPackets.ResourceUploadStartC2S.ID, (packet, context) ->
                context.server().execute(() -> startResourceUpload(context.player(), packet)));

        ServerPlayNetworking.registerGlobalReceiver(AreaTipPackets.ResourceUploadChunkC2S.ID, (packet, context) ->
                context.server().execute(() -> receiveResourceChunk(context.player(), packet)));

        ServerPlayNetworking.registerGlobalReceiver(AreaTipPackets.ResourceDeleteC2S.ID, (packet, context) ->
                context.server().execute(() -> deleteResource(context.player(), packet.filename())));

        ServerPlayNetworking.registerGlobalReceiver(AreaTipPackets.ResourceRequestC2S.ID, (packet, context) ->
                context.server().execute(() -> queueResourceSync(context.player(), packet.filename())));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncTo(handler.getPlayer()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickSelectionMergeJobs(server);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                tickResourceDownload(player);
                if (server.getTicks() % 5 == 0) {
                    tickPlayerAreaTip(player);
                }
            }
        });
    }

    public static void syncTo(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        ServerPlayNetworking.send(player, new AreaTipPackets.ConfigS2C(AreaTipConfig.getInstance().toJson()));
        queueConfigResources(player, AreaTipConfig.getInstance());
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
            queueConfigResources(target, config);
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
        AreaTipAreaStore.StoredArea area = addSelectionArea(world, packet.groupId(), packet.color(), packet.blocks());
        broadcastFullSync(world);
        player.sendMessage(Text.literal("\u00a7aArea tip filled " + area.blocks().size() + " selected block(s)"), true);
        sendGroupMessage(player, packet.groupId());
    }

    private static void placeSelectionChunk(ServerPlayerEntity player, AreaTipPackets.PlaceSelectionChunkC2S packet) {
        if (player == null || !(player.getWorld() instanceof ServerWorld world) || !canUseAreaTip(player)
                || packet.blocks().isEmpty() || packet.totalChunks() <= 0 || packet.totalChunks() > MAX_SELECTION_BATCH_CHUNKS
                || packet.chunkIndex() < 0 || packet.chunkIndex() >= packet.totalChunks()) {
            return;
        }
        SelectionPlacementState state = SELECTION_PLACEMENTS.computeIfAbsent(packet.batchId(), ignored ->
                new SelectionPlacementState(
                        player.getUuid(),
                        world,
                        packet.groupId(),
                        packet.min(),
                        packet.max(),
                        packet.color(),
                        packet.totalChunks(),
                        packet.totalBlocks()
                ));
        if (!state.accepts(player, world, packet)) {
            return;
        }
        state.addChunk(packet.chunkIndex(), packet.blocks());
        if (!state.isComplete()) {
            return;
        }
        SELECTION_PLACEMENTS.remove(packet.batchId());
        SELECTION_MERGE_JOBS.add(new SelectionMergeJob(
                player.getUuid(),
                world,
                packet.groupId(),
                packet.color(),
                state.orderedChunks(),
                packet.min(),
                packet.max(),
                packet.totalBlocks()
        ));
        player.sendMessage(Text.literal("\u00a7eQueued " + packet.totalBlocks()
                + " Axiom selected block(s) for async text area merge"), true);
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

    private static AreaTipAreaStore.StoredArea addSelectionArea(ServerWorld world, UUID groupId, int color, List<BlockPos> blocks) {
        BlockPos min = minOf(blocks);
        BlockPos max = maxOf(blocks);
        BlockPos center = new BlockPos(
                (min.getX() + max.getX()) / 2,
                (min.getY() + max.getY()) / 2,
                (min.getZ() + max.getZ()) / 2
        );
        return AreaTipAreaStore.get(world).addLatest(new AreaTipAreaStore.StoredArea(
                UUID.randomUUID(),
                groupId,
                center,
                GravityAreaSpec.Shape.BOX.ordinal(),
                GravityAreaSpec.Half.FULL.ordinal(),
                1,
                1,
                1,
                color,
                min,
                max,
                blocks
        ));
    }

    private static void tickSelectionMergeJobs(MinecraftServer server) {
        int processed = 0;
        while (processed < SELECTION_MERGE_CHUNKS_PER_TICK && !SELECTION_MERGE_JOBS.isEmpty()) {
            SelectionMergeJob job = SELECTION_MERGE_JOBS.peek();
            if (job == null) {
                SELECTION_MERGE_JOBS.poll();
                continue;
            }
            if (job.process(SELECTION_MERGE_CHUNKS_PER_TICK, SELECTION_MERGE_AREAS_PER_TICK)) {
                SELECTION_MERGE_JOBS.poll();
                broadcastFullSync(job.world());
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(job.playerId());
                if (player != null) {
                    player.sendMessage(Text.literal("\u00a7aArea tip filled " + job.addedBlocks()
                            + " selected block(s) across " + job.addedAreas() + " compressed area(s)"), true);
                    sendGroupMessage(player, job.groupId());
                }
            }
            processed++;
        }
    }

    private static void startResourceUpload(ServerPlayerEntity player, AreaTipPackets.ResourceUploadStartC2S packet) {
        if (player == null || !canUseAreaTip(player) || packet.totalBytes() <= 0 || packet.totalBytes() > MAX_RESOURCE_BYTES || packet.totalChunks() <= 0) {
            return;
        }
        try {
            Files.createDirectories(resourceDir());
            Path temp = resourceTempPath(player, packet.filename());
            Files.deleteIfExists(temp);
            RESOURCE_UPLOADS.put(player.getUuid(), new ResourceUploadState(packet.filename(), packet.totalBytes(), packet.totalChunks(), temp, 0, 0));
        } catch (IOException ignored) {
        }
    }

    private static void receiveResourceChunk(ServerPlayerEntity player, AreaTipPackets.ResourceUploadChunkC2S packet) {
        if (player == null || !canUseAreaTip(player)) {
            return;
        }
        ResourceUploadState state = RESOURCE_UPLOADS.get(player.getUuid());
        if (state == null || !state.filename().equals(packet.filename()) || packet.chunkIndex() != state.nextChunk()) {
            return;
        }
        byte[] bytes = packet.bytes();
        if (bytes.length == 0 || state.receivedBytes() + bytes.length > state.totalBytes()) {
            RESOURCE_UPLOADS.remove(player.getUuid());
            deleteQuietly(state.tempPath());
            return;
        }
        try {
            Files.createDirectories(state.tempPath().getParent());
            try (OutputStream output = Files.newOutputStream(state.tempPath(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
                output.write(bytes);
            }
            int nextChunk = state.nextChunk() + 1;
            int received = state.receivedBytes() + bytes.length;
            if (nextChunk >= state.totalChunks() && received == state.totalBytes()) {
                Path target = resolveResource(packet.filename());
                Files.createDirectories(target.getParent());
                Files.move(state.tempPath(), target, StandardCopyOption.REPLACE_EXISTING);
                RESOURCE_UPLOADS.remove(player.getUuid());
                for (ServerPlayerEntity targetPlayer : player.getServer().getPlayerManager().getPlayerList()) {
                    if (!targetPlayer.getUuid().equals(player.getUuid())) {
                        queueResourceSync(targetPlayer, packet.filename());
                    }
                }
                player.sendMessage(Text.literal("\u00a7aArea tip background synced: " + packet.filename()), true);
            } else {
                RESOURCE_UPLOADS.put(player.getUuid(), new ResourceUploadState(state.filename(), state.totalBytes(), state.totalChunks(), state.tempPath(), nextChunk, received));
            }
        } catch (IOException ignored) {
            RESOURCE_UPLOADS.remove(player.getUuid());
            deleteQuietly(state.tempPath());
        }
    }

    private static void deleteResource(ServerPlayerEntity player, String filename) {
        if (player == null || !canUseAreaTip(player) || filename == null || filename.isBlank()) {
            return;
        }
        deleteQuietly(resolveResource(filename));
        for (ServerPlayerEntity target : player.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(target, new AreaTipPackets.ResourceDeleteS2C(filename));
        }
    }

    private static void queueConfigResources(ServerPlayerEntity player, AreaTipConfig config) {
        if (player == null || config == null || config.groups == null) {
            return;
        }
        for (AreaTipConfig.GroupConfig group : config.groups) {
            if (group == null || group.hudTexts == null) {
                continue;
            }
            for (AreaTipConfig.HudTextEntry entry : group.hudTexts) {
                if (entry != null && entry.background != null && !entry.background.isBlank() && !entry.background.startsWith("gui/text_ui/")) {
                    queueResourceSync(player, entry.background);
                }
            }
        }
    }

    private static void queueResourceSync(ServerPlayerEntity player, String filename) {
        if (player == null || filename == null || filename.isBlank()) {
            return;
        }
        Path path = resolveResource(filename);
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length == 0 || bytes.length > MAX_RESOURCE_BYTES) {
                return;
            }
            int chunks = Math.max(1, (int) Math.ceil(bytes.length / (double) RESOURCE_BYTES_PER_TICK));
            Queue<ResourceDownloadJob> queue = RESOURCE_DOWNLOADS.computeIfAbsent(player.getUuid(), ignored -> new ArrayDeque<>());
            queue.removeIf(job -> job.filename().equals(safeResourceName(filename)));
            queue.add(new ResourceDownloadJob(safeResourceName(filename), bytes, chunks, -1));
        } catch (IOException ignored) {
        }
    }

    private static void tickResourceDownload(ServerPlayerEntity player) {
        Queue<ResourceDownloadJob> queue = RESOURCE_DOWNLOADS.get(player.getUuid());
        if (queue == null || queue.isEmpty()) {
            return;
        }
        ResourceDownloadJob job = queue.poll();
        if (job.chunkIndex() < 0) {
            ServerPlayNetworking.send(player, new AreaTipPackets.ResourceSyncStartS2C(job.filename(), job.bytes().length, job.totalChunks()));
            queue.add(new ResourceDownloadJob(job.filename(), job.bytes(), job.totalChunks(), 0));
            return;
        }
        int offset = job.chunkIndex() * RESOURCE_BYTES_PER_TICK;
        if (offset >= job.bytes().length) {
            return;
        }
        int end = Math.min(job.bytes().length, offset + RESOURCE_BYTES_PER_TICK);
        ServerPlayNetworking.send(player, new AreaTipPackets.ResourceSyncChunkS2C(
                job.filename(),
                job.chunkIndex(),
                job.totalChunks(),
                Arrays.copyOfRange(job.bytes(), offset, end)
        ));
        int next = job.chunkIndex() + 1;
        if (next < job.totalChunks()) {
            queue.add(new ResourceDownloadJob(job.filename(), job.bytes(), job.totalChunks(), next));
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

    private static Path resourceDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("monvhua/textures").normalize();
    }

    private static Path resolveResource(String filename) {
        Path root = resourceDir();
        Path path = root.resolve(safeResourceName(filename)).normalize();
        return path.startsWith(root) ? path : root.resolve("background.png");
    }

    private static Path resourceTempPath(ServerPlayerEntity player, String filename) {
        return resourceDir().resolve(".upload_" + player.getUuid() + "_" + safeResourceName(filename) + ".tmp").normalize();
    }

    private static String safeResourceName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "background.png";
        }
        String clean = filename.replace('\\', '/');
        int slash = clean.lastIndexOf('/');
        if (slash >= 0) {
            clean = clean.substring(slash + 1);
        }
        clean = clean.replaceAll("[^a-zA-Z0-9._-]+", "_");
        return clean.isBlank() ? "background.png" : clean;
    }

    private static void deleteQuietly(Path path) {
        try {
            if (path != null) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
        }
    }

    private record ResourceUploadState(String filename, int totalBytes, int totalChunks, Path tempPath, int nextChunk, int receivedBytes) {
    }

    private record ResourceDownloadJob(String filename, byte[] bytes, int totalChunks, int chunkIndex) {
    }

    private record SelectionBox(BlockPos min, BlockPos max, int volume) {
    }

    private record XRun(int minX, int maxX) {
    }

    private record XZRect(int minX, int maxX, int minZ, int maxZ) {
    }

    private record LayerRect(int y, XZRect rect) {
    }

    private record RowKey(int y, int z) {
    }

    private static final class SelectionPlacementState {
        private final UUID playerId;
        private final ServerWorld world;
        private final UUID groupId;
        private final BlockPos min;
        private final BlockPos max;
        private final int color;
        private final int totalChunks;
        private final int totalBlocks;
        private final Map<Integer, List<BlockPos>> chunks = new HashMap<>();

        private SelectionPlacementState(UUID playerId, ServerWorld world, UUID groupId, BlockPos min, BlockPos max,
                                        int color, int totalChunks, int totalBlocks) {
            this.playerId = playerId;
            this.world = world;
            this.groupId = groupId;
            this.min = min.toImmutable();
            this.max = max.toImmutable();
            this.color = color;
            this.totalChunks = totalChunks;
            this.totalBlocks = totalBlocks;
        }

        private boolean accepts(ServerPlayerEntity player, ServerWorld world, AreaTipPackets.PlaceSelectionChunkC2S packet) {
            return player.getUuid().equals(playerId)
                    && this.world == world
                    && groupId.equals(packet.groupId())
                    && min.equals(packet.min())
                    && max.equals(packet.max())
                    && color == packet.color()
                    && totalChunks == packet.totalChunks()
                    && totalBlocks == packet.totalBlocks();
        }

        private void addChunk(int index, List<BlockPos> blocks) {
            if (index < 0 || index >= totalChunks || blocks == null || blocks.isEmpty()) {
                return;
            }
            chunks.putIfAbsent(index, List.copyOf(blocks));
        }

        private boolean isComplete() {
            return chunks.size() == totalChunks;
        }

        private Queue<List<BlockPos>> orderedChunks() {
            Queue<List<BlockPos>> ordered = new ArrayDeque<>();
            for (int i = 0; i < totalChunks; i++) {
                List<BlockPos> chunk = chunks.get(i);
                if (chunk != null && !chunk.isEmpty()) {
                    ordered.add(chunk);
                }
            }
            return ordered;
        }
    }

    private static final class SelectionMergeJob {
        private final UUID playerId;
        private final ServerWorld world;
        private final UUID groupId;
        private final int color;
        private final Queue<List<BlockPos>> chunks;
        private final BlockPos min;
        private final BlockPos max;
        private final int totalBlocks;
        private final LinkedHashSet<BlockPos> selectedBlocks = new LinkedHashSet<>();
        private Queue<SelectionBox> pendingAreas;
        private int addedBlocks;
        private int addedAreas;

        private SelectionMergeJob(UUID playerId, ServerWorld world, UUID groupId, int color,
                                  Queue<List<BlockPos>> chunks, BlockPos min, BlockPos max, int totalBlocks) {
            this.playerId = playerId;
            this.world = world;
            this.groupId = groupId;
            this.color = color;
            this.chunks = new ArrayDeque<>(chunks);
            this.min = min.toImmutable();
            this.max = max.toImmutable();
            this.totalBlocks = totalBlocks;
        }

        private boolean process(int maxChunks, int maxAreas) {
            int processed = 0;
            while (processed < maxChunks && !chunks.isEmpty()) {
                List<BlockPos> chunk = chunks.poll();
                if (chunk != null && !chunk.isEmpty()) {
                    for (BlockPos block : chunk) {
                        if (block != null && block.getX() >= min.getX() && block.getX() <= max.getX()
                                && block.getY() >= min.getY() && block.getY() <= max.getY()
                                && block.getZ() >= min.getZ() && block.getZ() <= max.getZ()) {
                            selectedBlocks.add(block.toImmutable());
                        }
                    }
                }
                processed++;
            }
            if (!chunks.isEmpty()) {
                return false;
            }
            if (pendingAreas == null) {
                pendingAreas = new ArrayDeque<>(compressSelection(selectedBlocks));
                selectedBlocks.clear();
            }
            int addedThisTick = 0;
            while (addedThisTick < maxAreas && !pendingAreas.isEmpty()) {
                SelectionBox box = pendingAreas.poll();
                if (box != null && box.volume() > 0) {
                    addSelectionBox(world, groupId, color, box);
                    addedBlocks += box.volume();
                    addedAreas++;
                }
                addedThisTick++;
            }
            return pendingAreas.isEmpty();
        }

        private static List<SelectionBox> compressSelection(Collection<BlockPos> blocks) {
            if (blocks == null || blocks.isEmpty()) {
                return List.of();
            }
            Map<RowKey, List<Integer>> rows = new HashMap<>();
            for (BlockPos block : blocks) {
                rows.computeIfAbsent(new RowKey(block.getY(), block.getZ()), ignored -> new java.util.ArrayList<>()).add(block.getX());
            }

            Map<Integer, Map<XRun, List<Integer>>> runsByLayer = new HashMap<>();
            for (Map.Entry<RowKey, List<Integer>> entry : rows.entrySet()) {
                List<Integer> xs = entry.getValue();
                xs.sort(Integer::compareTo);
                List<XRun> runs = new java.util.ArrayList<>();
                int start = xs.get(0);
                int end = start;
                for (int i = 1; i < xs.size(); i++) {
                    int x = xs.get(i);
                    if (x <= end) {
                        continue;
                    }
                    if (x == end + 1) {
                        end = x;
                        continue;
                    }
                    runs.add(new XRun(start, end));
                    start = x;
                    end = x;
                }
                runs.add(new XRun(start, end));

                Map<XRun, List<Integer>> runsByZ = runsByLayer.computeIfAbsent(
                        entry.getKey().y(),
                        ignored -> new HashMap<>()
                );
                for (XRun run : runs) {
                    runsByZ.computeIfAbsent(run, ignored -> new java.util.ArrayList<>()).add(entry.getKey().z());
                }
            }

            Map<Integer, List<XZRect>> layerRects = new HashMap<>();
            for (Map.Entry<Integer, Map<XRun, List<Integer>>> layerEntry : runsByLayer.entrySet()) {
                for (Map.Entry<XRun, List<Integer>> runEntry : layerEntry.getValue().entrySet()) {
                    List<Integer> zs = runEntry.getValue();
                    zs.sort(Integer::compareTo);
                    int minZ = zs.get(0);
                    int maxZ = minZ;
                    for (int i = 1; i < zs.size(); i++) {
                        int z = zs.get(i);
                        if (z <= maxZ) {
                            continue;
                        }
                        if (z == maxZ + 1) {
                            maxZ = z;
                            continue;
                        }
                        layerRects.computeIfAbsent(layerEntry.getKey(), ignored -> new java.util.ArrayList<>())
                                .add(new XZRect(runEntry.getKey().minX(), runEntry.getKey().maxX(), minZ, maxZ));
                        minZ = z;
                        maxZ = z;
                    }
                    layerRects.computeIfAbsent(layerEntry.getKey(), ignored -> new java.util.ArrayList<>())
                            .add(new XZRect(runEntry.getKey().minX(), runEntry.getKey().maxX(), minZ, maxZ));
                }
            }

            List<LayerRect> layers = new java.util.ArrayList<>();
            for (Map.Entry<Integer, List<XZRect>> entry : layerRects.entrySet()) {
                for (XZRect rect : entry.getValue()) {
                    layers.add(new LayerRect(entry.getKey(), rect));
                }
            }
            layers.sort(Comparator
                    .comparingInt((LayerRect layer) -> layer.rect().minX())
                    .thenComparingInt(layer -> layer.rect().maxX())
                    .thenComparingInt(layer -> layer.rect().minZ())
                    .thenComparingInt(layer -> layer.rect().maxZ())
                    .thenComparingInt(LayerRect::y));

            List<SelectionBox> boxes = new java.util.ArrayList<>();
            XZRect currentRect = null;
            int minY = 0;
            int maxY = 0;
            for (LayerRect layer : layers) {
                if (currentRect != null && currentRect.equals(layer.rect()) && layer.y() == maxY + 1) {
                    maxY = layer.y();
                    continue;
                }
                if (currentRect != null) {
                    boxes.add(toSelectionBox(currentRect, minY, maxY));
                }
                currentRect = layer.rect();
                minY = layer.y();
                maxY = layer.y();
            }
            if (currentRect != null) {
                boxes.add(toSelectionBox(currentRect, minY, maxY));
            }
            return boxes;
        }

        private static SelectionBox toSelectionBox(XZRect rect, int minY, int maxY) {
            BlockPos min = new BlockPos(rect.minX(), minY, rect.minZ());
            BlockPos max = new BlockPos(rect.maxX(), maxY, rect.maxZ());
            long volume = (long) (rect.maxX() - rect.minX() + 1)
                    * (long) (maxY - minY + 1)
                    * (long) (rect.maxZ() - rect.minZ() + 1);
            return new SelectionBox(min, max, volume > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) volume);
        }

        private static void addSelectionBox(ServerWorld world, UUID groupId, int color, SelectionBox box) {
            BlockPos min = box.min();
            BlockPos max = box.max();
            BlockPos center = new BlockPos(
                    (min.getX() + max.getX()) / 2,
                    (min.getY() + max.getY()) / 2,
                    (min.getZ() + max.getZ()) / 2
            );
            AreaTipAreaStore.get(world).addLatest(new AreaTipAreaStore.StoredArea(
                    UUID.randomUUID(),
                    groupId,
                    center,
                    GravityAreaSpec.Shape.BOX.ordinal(),
                    GravityAreaSpec.Half.FULL.ordinal(),
                    1,
                    1,
                    1,
                    color,
                    min,
                    max
            ));
        }

        private UUID playerId() {
            return playerId;
        }

        private ServerWorld world() {
            return world;
        }

        private UUID groupId() {
            return groupId;
        }

        private int addedBlocks() {
            return Math.min(addedBlocks, totalBlocks);
        }

        private int addedAreas() {
            return addedAreas;
        }
    }
}

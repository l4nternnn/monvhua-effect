package com.kuilunfuzhe.monvhua.features.area_tip;

import com.kuilunfuzhe.monvhua.features.gravity.GravityAreaSpec;
import com.kuilunfuzhe.monvhua.item.area_tip.AreaTipItems;
import com.kuilunfuzhe.monvhua.item.config.AreaTipConfig;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.network.area_tip.AreaTipPackets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public final class AreaTipClient {
    private static final double PREVIEW_REACH = 96.0D;
    private static final long AXIOM_RENDER_KEEPALIVE_MS = 750L;
    private static final List<AreaView> AREAS = new ArrayList<>();
    private static boolean previewVisible;
    private static boolean lastUsePressed;
    private static boolean lastAttackPressed;
    private static UUID axiomRenderGroupId;
    private static long axiomRenderUntilMs;
    private static long areasRevision;
    private static long configRevision;

    private AreaTipClient() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(AreaTipClient::tick);
        ClientPlayNetworking.registerGlobalReceiver(AreaTipPackets.ConfigS2C.ID, (packet, context) ->
                context.client().execute(() -> {
                    AreaTipConfig config = AreaTipConfig.fromJson(packet.json());
                    AreaTipConfig.syncInstance(config);
                    configRevision++;
                    if (context.client().currentScreen instanceof AreaTipConfigScreen screen) {
                        screen.receiveConfig(config);
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(AreaTipPackets.FullSyncS2C.ID, (packet, context) ->
                context.client().execute(() -> {
                    AREAS.clear();
                    for (AreaTipPackets.AreaData area : packet.areas()) {
                        AREAS.add(AreaView.fromPacket(area));
                    }
                    areasRevision++;
                }));
        ClientPlayNetworking.registerGlobalReceiver(AreaTipPackets.AreaUpdateS2C.ID, (packet, context) ->
                context.client().execute(() -> {
                    AREAS.removeIf(area -> area.id().equals(packet.area().id()));
                    AREAS.add(AreaView.fromPacket(packet.area()));
                    areasRevision++;
                }));
    }

    public static boolean isHoldingAreaTipStick(MinecraftClient client) {
        return client.player != null && client.player.getMainHandStack().getItem() == AreaTipItems.AREA_TIP_STICK;
    }

    public static List<AreaView> areas() {
        return List.copyOf(AREAS);
    }

    public static List<AreaView> areasForGroup(UUID groupId) {
        if (groupId == null) {
            return List.of();
        }
        return AREAS.stream()
                .filter(area -> groupId.equals(area.groupId()))
                .toList();
    }

    public static long areasRevision() {
        return areasRevision;
    }

    public static long configRevision() {
        return configRevision;
    }

    public static void keepAxiomGroupVisible(UUID groupId) {
        axiomRenderGroupId = groupId;
        axiomRenderUntilMs = System.currentTimeMillis() + AXIOM_RENDER_KEEPALIVE_MS;
    }

    public static UUID axiomRenderGroupId() {
        return System.currentTimeMillis() <= axiomRenderUntilMs ? axiomRenderGroupId : null;
    }

    public static boolean previewVisible() {
        return previewVisible;
    }

    public static BlockPos previewCenter(MinecraftClient client) {
        return previewVisible ? targetBlock(client) : null;
    }

    public static AreaTipConfig.GroupConfig currentGroup() {
        return AreaTipConfig.getInstance().selectedGroup().orElse(null);
    }

    public static int colorFor(UUID groupId, int fallbackColor) {
        return AreaTipConfig.getInstance().findGroup(groupId)
                .map(group -> group.color)
                .orElse(fallbackColor);
    }

    public static void sendConfigUpdate(AreaTipConfig config) {
        configRevision++;
        SafeClientNetworking.send(new AreaTipPackets.UpdateConfigC2S(config.toJson()));
    }

    public static void requestSync() {
        SafeClientNetworking.send(new AreaTipPackets.RequestConfigC2S());
        SafeClientNetworking.send(new AreaTipPackets.RequestAreasC2S());
    }

    public static boolean placeCurrentGroupBounds(BlockPos min, BlockPos max) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || min == null || max == null) {
            return false;
        }
        AreaTipConfig.GroupConfig group = currentGroup();
        if (group == null) {
            client.player.sendMessage(Text.literal("\u00a7cNo area tip group configured"), true);
            return false;
        }
        SafeClientNetworking.send(new AreaTipPackets.PlaceBoundsC2S(group.uuid(), min, max, group.color));
        client.player.sendMessage(Text.literal("\u00a7ePlacing area tip from Axiom selection"), true);
        return true;
    }

    public static boolean placeCurrentGroupSelection(List<BlockPos> blocks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || blocks == null || blocks.isEmpty()) {
            return false;
        }
        AreaTipConfig.GroupConfig group = currentGroup();
        if (group == null) {
            client.player.sendMessage(Text.literal("\u00a7cNo area tip group configured"), true);
            return false;
        }
        SafeClientNetworking.send(new AreaTipPackets.PlaceSelectionC2S(group.uuid(), blocks, group.color));
        client.player.sendMessage(Text.literal("\u00a7eFilling " + blocks.size() + " Axiom selected block(s)"), true);
        return true;
    }

    public static boolean deleteCurrentGroupBounds(BlockPos min, BlockPos max) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || min == null || max == null) {
            return false;
        }
        AreaTipConfig.GroupConfig group = currentGroup();
        if (group == null) {
            client.player.sendMessage(Text.literal("\u00a7cNo area tip group configured"), true);
            return false;
        }
        SafeClientNetworking.send(new AreaTipPackets.DeleteBoundsC2S(group.uuid(), min, max));
        client.player.sendMessage(Text.literal("\u00a7eDeleting area tip ranges in Axiom selection"), true);
        return true;
    }

    public static boolean deleteCurrentGroupSelection(List<BlockPos> blocks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || blocks == null || blocks.isEmpty()) {
            return false;
        }
        AreaTipConfig.GroupConfig group = currentGroup();
        if (group == null) {
            client.player.sendMessage(Text.literal("\u00a7cNo area tip group configured"), true);
            return false;
        }
        SafeClientNetworking.send(new AreaTipPackets.DeleteSelectionC2S(group.uuid(), blocks));
        client.player.sendMessage(Text.literal("\u00a7eDeleting " + blocks.size() + " selected text block(s)"), true);
        return true;
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null || !isHoldingAreaTipStick(client)) {
            previewVisible = false;
            resetKeys();
            return;
        }
        if (client.currentScreen != null) {
            resetKeys();
            return;
        }

        boolean usePressed = client.options.useKey.isPressed();
        boolean attackPressed = client.options.attackKey.isPressed();
        if (usePressed && !lastUsePressed) {
            handleUse(client);
            client.options.useKey.setPressed(false);
        }
        if (attackPressed && !lastAttackPressed) {
            if (previewVisible) {
                previewVisible = false;
                client.player.sendMessage(Text.literal("\u00a7eArea tip preview cancelled"), true);
            }
            client.options.attackKey.setPressed(false);
        }
        lastUsePressed = usePressed;
        lastAttackPressed = attackPressed;
    }

    private static void handleUse(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        if (client.player.isSneaking()) {
            requestSync();
            client.setScreen(new AreaTipConfigScreen());
            return;
        }

        BlockPos center = targetBlock(client);
        if (center == null) {
            return;
        }
        AreaTipConfig.GroupConfig group = currentGroup();
        if (group == null) {
            client.player.sendMessage(Text.literal("\u00a7cNo area tip group configured"), true);
            return;
        }
        if (!previewVisible) {
            previewVisible = true;
            client.player.sendMessage(Text.literal("\u00a7eArea tip preview"), true);
            return;
        }
        GravityAreaSpec spec = group.spec();
        SafeClientNetworking.send(new AreaTipPackets.PlaceAreaC2S(
                group.uuid(),
                center,
                spec.shape().ordinal(),
                spec.half().ordinal(),
                spec.sizeX(),
                spec.sizeY(),
                spec.sizeZ(),
                group.color
        ));
    }

    private static BlockPos targetBlock(MinecraftClient client) {
        if (client.player == null) {
            return null;
        }
        HitResult hit = client.player.raycast(PREVIEW_REACH, 0.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos().toImmutable();
        }
        return null;
    }

    private static void resetKeys() {
        lastUsePressed = false;
        lastAttackPressed = false;
    }

    public static boolean isF2(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_F2;
    }

    public record AreaView(UUID id, UUID groupId, BlockPos center, GravityAreaSpec spec, int color,
                           BlockPos min, BlockPos max, List<BlockPos> blocks) {
        public AreaView {
            center = center == null ? BlockPos.ORIGIN : center.toImmutable();
            spec = spec == null ? new GravityAreaSpec(GravityAreaSpec.Shape.BOX, GravityAreaSpec.Half.FULL, 1, 1, 1) : spec;
            blocks = sanitizeBlocks(blocks);
            if (min != null && max != null) {
                BlockPos fixedMin = new BlockPos(
                        Math.min(min.getX(), max.getX()),
                        Math.min(min.getY(), max.getY()),
                        Math.min(min.getZ(), max.getZ())
                );
                BlockPos fixedMax = new BlockPos(
                        Math.max(min.getX(), max.getX()),
                        Math.max(min.getY(), max.getY()),
                        Math.max(min.getZ(), max.getZ())
                );
                min = fixedMin.toImmutable();
                max = fixedMax.toImmutable();
            } else {
                min = null;
                max = null;
            }
            if (blocks.isEmpty()) {
                blocks = min != null && max != null ? blocksInBounds(min, max) : spec.coveredBlocks(center);
            }
            if (!blocks.isEmpty() && (min == null || max == null)) {
                min = minOf(blocks);
                max = maxOf(blocks);
            }
        }

        private static AreaView fromPacket(AreaTipPackets.AreaData data) {
            return new AreaView(
                    data.id(),
                    data.groupId(),
                    data.center(),
                    new GravityAreaSpec(
                            GravityAreaSpec.Shape.byId(data.shape()),
                            GravityAreaSpec.Half.byId(data.half()),
                            data.sizeX(),
                            data.sizeY(),
                            data.sizeZ()
                    ),
                    data.color(),
                    data.min(),
                    data.max(),
                    data.blocks()
            );
        }

        public List<BlockPos> coveredBlocks() {
            return blocks;
        }

        private static List<BlockPos> blocksInBounds(BlockPos min, BlockPos max) {
            List<BlockPos> blocks = new ArrayList<>();
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        blocks.add(new BlockPos(x, y, z));
                        if (blocks.size() >= GravityAreaSpec.MAX_RENDER_BLOCKS) {
                            return blocks;
                        }
                    }
                }
            }
            return blocks;
        }

        public BlockPos minBlock() {
            if (min != null) {
                return min;
            }
            if (!blocks.isEmpty()) {
                return minOf(blocks);
            }
            GravityAreaSpec.Bounds bounds = spec.bounds(center);
            return new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
        }

        public BlockPos maxBlock() {
            if (max != null) {
                return max;
            }
            if (!blocks.isEmpty()) {
                return maxOf(blocks);
            }
            GravityAreaSpec.Bounds bounds = spec.bounds(center);
            return new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ());
        }

        public String boundsText() {
            BlockPos min = minBlock();
            BlockPos max = maxBlock();
            String prefix = blocks.isEmpty() ? "" : blocks.size() + " blocks  ";
            return prefix + min.getX() + "," + min.getY() + "," + min.getZ()
                    + " -> " + max.getX() + "," + max.getY() + "," + max.getZ();
        }

        private static List<BlockPos> sanitizeBlocks(List<BlockPos> blocks) {
            if (blocks == null || blocks.isEmpty()) {
                return List.of();
            }
            LinkedHashSet<BlockPos> sanitized = new LinkedHashSet<>();
            for (BlockPos block : blocks) {
                if (block == null) {
                    continue;
                }
                sanitized.add(block.toImmutable());
                if (sanitized.size() >= GravityAreaSpec.MAX_RENDER_BLOCKS) {
                    break;
                }
            }
            return List.copyOf(sanitized);
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
    }
}

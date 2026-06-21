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
import java.util.List;
import java.util.UUID;

public final class AreaTipClient {
    private static final double PREVIEW_REACH = 96.0D;
    private static final List<AreaView> AREAS = new ArrayList<>();
    private static boolean previewVisible;
    private static boolean lastUsePressed;
    private static boolean lastAttackPressed;

    private AreaTipClient() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(AreaTipClient::tick);
        ClientPlayNetworking.registerGlobalReceiver(AreaTipPackets.ConfigS2C.ID, (packet, context) ->
                context.client().execute(() -> {
                    AreaTipConfig config = AreaTipConfig.fromJson(packet.json());
                    AreaTipConfig.syncInstance(config);
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
                }));
        ClientPlayNetworking.registerGlobalReceiver(AreaTipPackets.AreaUpdateS2C.ID, (packet, context) ->
                context.client().execute(() -> {
                    AREAS.removeIf(area -> area.id().equals(packet.area().id()));
                    AREAS.add(AreaView.fromPacket(packet.area()));
                }));
    }

    public static boolean isHoldingAreaTipStick(MinecraftClient client) {
        return client.player != null && client.player.getMainHandStack().getItem() == AreaTipItems.AREA_TIP_STICK;
    }

    public static List<AreaView> areas() {
        return List.copyOf(AREAS);
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
                           BlockPos min, BlockPos max) {
        public AreaView {
            center = center.toImmutable();
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
                    data.max()
            );
        }

        public List<BlockPos> coveredBlocks() {
            if (min == null || max == null) {
                return spec.coveredBlocks(center);
            }
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
    }
}

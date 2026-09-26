package com.kuilunfuzhe.monvhua.features.chestlink;

import com.kuilunfuzhe.monvhua.item.chestlink.ChestLinkItems;
import com.kuilunfuzhe.monvhua.network.chestlink.ChestLinkMappingsS2CPacket;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import java.util.ArrayList;
import java.util.List;

public final class ChestLinkHighlightRenderer {
    private static final int RADIUS = 16;
    private static long lastScanTick = Long.MIN_VALUE;
    private static BlockPos lastCenter = BlockPos.ORIGIN;
    private static List<BlockPos> nearbyChests = List.of();
    private ChestLinkHighlightRenderer() {}
    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        ItemStack stack = client.player.getMainHandStack();
        if (!stack.isOf(ChestLinkItems.CHEST_LINK)) return;
        Vec3d camera = context.camera().getPos();
        VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getLines());
        ChestLinkMappingsS2CPacket.Entry aimedMapping = null;
        if (client.crosshairTarget instanceof BlockHitResult hit && client.world.getBlockState(hit.getBlockPos()).isOf(Blocks.CHEST)) {
            aimedMapping = ChestLinkClientState.findEntrance(client.world.getRegistryKey().getValue().toString(), hit.getBlockPos());
            if (aimedMapping != null) {
                drawBox(context, vertices, hit.getBlockPos(), camera, 1.0f, 0.75f, 0.1f);
                BlockPos source = new BlockPos(aimedMapping.sourceX(), aimedMapping.sourceY(), aimedMapping.sourceZ());
                if (aimedMapping.sourceDimension().equals(client.world.getRegistryKey().getValue().toString())
                        && client.world.isChunkLoaded(source.getX() >> 4, source.getZ() >> 4)
                        && client.world.getBlockState(source).isOf(Blocks.CHEST)) {
                    drawBox(context, vertices, source, camera, 0.75f, 0.2f, 1.0f);
                }
            }
        }
        if (!com.kuilunfuzhe.monvhua.item.chestlink.ChestLinkItem.isHighlightEnabled(stack)) return;
        BlockPos center = client.player.getBlockPos();
        long tick = client.world.getTime();
        if (!center.equals(lastCenter) || tick - lastScanTick >= 5L) {
            refresh(client, center, tick);
        }
        for (BlockPos pos : nearbyChests) {
            VertexRendering.drawBox(context.matrixStack(), vertices,
                    pos.getX() - camera.x + 0.03, pos.getY() - camera.y + 0.03, pos.getZ() - camera.z + 0.03,
                    pos.getX() - camera.x + 0.97, pos.getY() - camera.y + 0.97, pos.getZ() - camera.z + 0.97,
                    0.2f, 1.0f, 0.9f, 1.0f);
        }
        BlockPos bound = ChestLinkClientState.source();
        if (bound != null && ChestLinkClientState.sourceDimension().equals(client.world.getRegistryKey().getValue().toString())
                && client.world.isChunkLoaded(bound.getX() >> 4, bound.getZ() >> 4)) {
            VertexRendering.drawBox(context.matrixStack(), vertices,
                    bound.getX() - camera.x + 0.01, bound.getY() - camera.y + 0.01, bound.getZ() - camera.z + 0.01,
                    bound.getX() - camera.x + 0.99, bound.getY() - camera.y + 0.99, bound.getZ() - camera.z + 0.99,
                    1.0f, 0.8f, 0.1f, 1.0f);
        }
    }

    private static void drawBox(WorldRenderContext context, VertexConsumer vertices, BlockPos pos, Vec3d camera, float red, float green, float blue) {
        VertexRendering.drawBox(context.matrixStack(), vertices,
                pos.getX() - camera.x + 0.01, pos.getY() - camera.y + 0.01, pos.getZ() - camera.z + 0.01,
                pos.getX() - camera.x + 0.99, pos.getY() - camera.y + 0.99, pos.getZ() - camera.z + 0.99,
                red, green, blue, 1.0f);
    }

    private static void refresh(MinecraftClient client, BlockPos center, long tick) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = center.getX() - RADIUS; x <= center.getX() + RADIUS; x++) {
            for (int z = center.getZ() - RADIUS; z <= center.getZ() + RADIUS; z++) {
                if (!client.world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = Math.max(client.world.getBottomY(), center.getY() - RADIUS); y <= Math.min(client.world.getTopYInclusive(), center.getY() + RADIUS); y++) {
                    int dx = x - center.getX(), dy = y - center.getY(), dz = z - center.getZ();
                    if (dx * dx + dy * dy + dz * dz > RADIUS * RADIUS) continue;
                    pos.set(x, y, z);
                    if (client.world.getBlockState(pos).isOf(Blocks.CHEST)) found.add(pos.toImmutable());
                }
            }
        }
        nearbyChests = List.copyOf(found);
        lastCenter = center.toImmutable();
        lastScanTick = tick;
    }
}

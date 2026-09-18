package com.kuilunfuzhe.monvhua.features.swing;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockRenderType;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.util.math.random.Random;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashMap;

/** Dedicated renderer for captured swing blocks, independent of vanilla chunk meshes. */
public final class SwingStructureRenderer {
    private static final Map<SwingStructure, Map<BlockPos, CachedParts>> CACHE = new WeakHashMap<>();
    private static long modelBuilds;
    private static long lastRenderNanos;
    private record CachedParts(BlockStateModel model, List<BlockModelPart> parts) { }
    public static long modelBuilds() { return modelBuilds; }
    public static void initialize() {
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("monvhua-swing-render-stats")
                        .executes(context -> {
                            context.getSource().sendFeedback(net.minecraft.text.Text.literal("Swing structures=" + CACHE.size()
                                    + " modelBuilds=" + modelBuilds + " lastRenderUs=" + lastRenderNanos / 1000));
                            return 1;
                        })));
    }
    private SwingStructureRenderer() {
    }

    public static void render(SwingEntityRenderer.State state, MatrixStack matrices,
                              VertexConsumerProvider vertices, int fallbackLight) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (state.world == null) return;
        long started = System.nanoTime();
        SwingStructureSpace space = new SwingStructureSpace(state.structure, state.pivot, state.angle, state.z);
        SwingRenderWorld localWorld = new SwingRenderWorld(space, state.world);
        Map<BlockPos, CachedParts> models = CACHE.computeIfAbsent(state.structure, ignored -> new HashMap<>());
        matrices.push();
        matrices.multiply((state.z ? RotationAxis.POSITIVE_Z : RotationAxis.POSITIVE_X).rotation(state.angle));
        for (SwingBlock block : state.structure.blocks()) {
            matrices.push();
            matrices.translate(block.localPos().getX() - .5, block.localPos().getY() - .5,
                    block.localPos().getZ() - .5);
            var manager = client.getBlockRenderManager();
            if (block.state().getRenderType() == BlockRenderType.MODEL) {
                var model = manager.getModel(block.state());
                CachedParts cached = models.get(block.localPos());
                // Model identity changes on resource reload, releasing the old resource's parts.
                if (cached == null || cached.model() != model) {
                    cached = new CachedParts(model, List.copyOf(model.getParts(Random.create(block.state().getRenderingSeed(block.localPos())))));
                    models.put(block.localPos(), cached);
                    modelBuilds++;
                }
                manager.renderBlock(block.state(), block.localPos(), localWorld, matrices,
                        vertices.getBuffer(RenderLayers.getMovingBlockLayer(block.state())), true, cached.parts());
            } else {
                int light = WorldRenderer.getLightmapCoordinates(state.world, BlockPos.ofFloored(space.toWorld(Vec3d.of(block.localPos()))));
                manager.renderBlockAsEntity(block.state(), matrices, vertices, light, OverlayTexture.DEFAULT_UV);
            }
            matrices.pop();
        }
        matrices.pop();
        lastRenderNanos = System.nanoTime() - started;
    }
}

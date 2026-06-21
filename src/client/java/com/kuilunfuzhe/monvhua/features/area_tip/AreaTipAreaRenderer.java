package com.kuilunfuzhe.monvhua.features.area_tip;

import com.kuilunfuzhe.monvhua.features.gravity.GravityAreaSpec;
import com.kuilunfuzhe.monvhua.item.config.AreaTipConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AreaTipAreaRenderer {
    private static final int MAX_BLOCKS_PER_COLOR = 30000;

    private AreaTipAreaRenderer() {
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || !AreaTipClient.isHoldingAreaTipStick(client)) {
            return;
        }

        Map<Integer, Set<BlockPos>> blocksByColor = new HashMap<>();
        for (AreaTipClient.AreaView view : AreaTipClient.areas()) {
            int color = AreaTipClient.colorFor(view.groupId(), view.color());
            addBlocks(blocksByColor.computeIfAbsent(color, ignored -> new HashSet<>()), view.coveredBlocks());
        }

        BlockPos preview = AreaTipClient.previewCenter(client);
        AreaTipConfig.GroupConfig group = AreaTipClient.currentGroup();
        if (preview != null && group != null) {
            addBlocks(blocksByColor.computeIfAbsent(group.color, ignored -> new HashSet<>()), group.spec().coveredBlocks(preview));
        }

        if (blocksByColor.isEmpty()) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getLines());
        long time = client.world.getTime();
        for (Map.Entry<Integer, Set<BlockPos>> entry : blocksByColor.entrySet()) {
            Set<BlockPos> blocks = entry.getValue();
            Bounds bounds = Bounds.of(blocks);
            renderBlockSet(matrices, vertices, camera, blocks, bounds, entry.getKey(), time);
        }
    }

    private static void addBlocks(Set<BlockPos> target, List<BlockPos> source) {
        for (BlockPos pos : source) {
            if (target.size() >= MAX_BLOCKS_PER_COLOR) {
                return;
            }
            target.add(pos.toImmutable());
        }
    }

    private static void renderBlockSet(MatrixStack matrices, VertexConsumer vertices, Vec3d camera,
                                       Set<BlockPos> blocks, Bounds bounds, int color, long time) {
        for (BlockPos pos : blocks) {
            if (!isBoundary(pos, blocks)) {
                continue;
            }
            float minX = (float) (pos.getX() - camera.x);
            float minY = (float) (pos.getY() - camera.y);
            float minZ = (float) (pos.getZ() - camera.z);
            float maxX = minX + 1.0F;
            float maxY = minY + 1.0F;
            float maxZ = minZ + 1.0F;
            for (Direction direction : Direction.values()) {
                if (blocks.contains(pos.offset(direction))) {
                    continue;
                }
                drawFaceOuterEdges(matrices, vertices, pos, direction, blocks, bounds, color, time,
                        minX, minY, minZ, maxX, maxY, maxZ, camera);
            }
        }
    }

    private static boolean isBoundary(BlockPos pos, Set<BlockPos> blocks) {
        for (Direction direction : Direction.values()) {
            if (!blocks.contains(pos.offset(direction))) {
                return true;
            }
        }
        return false;
    }

    private static void drawFaceOuterEdges(MatrixStack matrices, VertexConsumer vertices,
                                           BlockPos pos, Direction face, Set<BlockPos> blocks,
                                           Bounds bounds, int color, long time,
                                           float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                                           Vec3d camera) {
        for (Direction side : Direction.values()) {
            if (side.getAxis() == face.getAxis()) {
                continue;
            }
            BlockPos sideNeighbor = pos.offset(side);
            if (blocks.contains(sideNeighbor) && !blocks.contains(sideNeighbor.offset(face))) {
                continue;
            }
            drawEdge(matrices, vertices, face, side, bounds, color, time,
                    minX, minY, minZ, maxX, maxY, maxZ, camera);
        }
    }

    private static void drawEdge(MatrixStack matrices, VertexConsumer vertices, Direction face, Direction side,
                                 Bounds bounds, int color, long time,
                                 float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                                 Vec3d camera) {
        float[] a = edgePoint(face, side, minX, minY, minZ, maxX, maxY, maxZ, false);
        float[] b = edgePoint(face, side, minX, minY, minZ, maxX, maxY, maxZ, true);
        float normalX = b[0] - a[0];
        float normalY = b[1] - a[1];
        float normalZ = b[2] - a[2];
        float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (length > 0.0F) {
            normalX /= length;
            normalY /= length;
            normalZ /= length;
        }

        int colorA = flowingColor(color, bounds, a[0] + camera.x, a[1] + camera.y, a[2] + camera.z, time);
        int colorB = flowingColor(color, bounds, b[0] + camera.x, b[1] + camera.y, b[2] + camera.z, time);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        vertices.vertex(matrix, a[0], a[1], a[2]).color(colorA).normal(normalX, normalY, normalZ);
        vertices.vertex(matrix, b[0], b[1], b[2]).color(colorB).normal(normalX, normalY, normalZ);
    }

    private static int flowingColor(int color, Bounds bounds, double x, double y, double z, long time) {
        double dx = bounds.maxX == bounds.minX ? 0.0D : (x - bounds.minX) / (bounds.maxX - bounds.minX);
        double dy = bounds.maxY == bounds.minY ? 0.0D : (bounds.maxY - y) / (bounds.maxY - bounds.minY);
        double dz = bounds.maxZ == bounds.minZ ? 0.0D : (z - bounds.minZ) / (bounds.maxZ - bounds.minZ);
        double diagonal = (dx + dy + dz) / 3.0D;
        double wave = 0.5D + 0.5D * Math.sin((diagonal - time * 0.035D) * Math.PI * 2.0D);
        int r = lerp(255, (color >>> 16) & 0xFF, wave);
        int g = lerp(255, (color >>> 8) & 0xFF, wave);
        int b = lerp(255, color & 0xFF, wave);
        return (220 << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    private static float[] edgePoint(Direction face, Direction side,
                                     float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                                     boolean maxAlongEdge) {
        Direction.Axis edgeAxis = remainingAxis(face.getAxis(), side.getAxis());
        float x = fixedOrEdge(Direction.Axis.X, face, side, edgeAxis, minX, maxX, maxAlongEdge);
        float y = fixedOrEdge(Direction.Axis.Y, face, side, edgeAxis, minY, maxY, maxAlongEdge);
        float z = fixedOrEdge(Direction.Axis.Z, face, side, edgeAxis, minZ, maxZ, maxAlongEdge);
        return new float[] {x, y, z};
    }

    private static float fixedOrEdge(Direction.Axis axis, Direction face, Direction side, Direction.Axis edgeAxis,
                                     float min, float max, boolean maxAlongEdge) {
        if (axis == edgeAxis) {
            return maxAlongEdge ? max : min;
        }
        if (axis == face.getAxis()) {
            return face.getDirection() == Direction.AxisDirection.POSITIVE ? max : min;
        }
        return side.getDirection() == Direction.AxisDirection.POSITIVE ? max : min;
    }

    private static Direction.Axis remainingAxis(Direction.Axis first, Direction.Axis second) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != first && axis != second) {
                return axis;
            }
        }
        return Direction.Axis.Y;
    }

    private record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        private static Bounds of(Set<BlockPos> blocks) {
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            double maxZ = -Double.MAX_VALUE;
            for (BlockPos pos : blocks) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX() + 1.0D);
                maxY = Math.max(maxY, pos.getY() + 1.0D);
                maxZ = Math.max(maxZ, pos.getZ() + 1.0D);
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}

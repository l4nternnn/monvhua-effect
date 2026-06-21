package com.kuilunfuzhe.monvhua.features.area_tip;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AreaTipAreaRenderer {
    private static final int MAX_BLOCKS_PER_COLOR = 30000;

    private static long cachedAreasRevision = Long.MIN_VALUE;
    private static long cachedConfigRevision = Long.MIN_VALUE;
    private static boolean cachedHoldingStick;
    private static UUID cachedAxiomGroupId;
    private static List<RenderGroup> cachedAreaGroups = List.of();
    private static PreviewKey cachedPreviewKey;
    private static RenderGroup cachedPreviewGroup;

    private AreaTipAreaRenderer() {
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }
        boolean holdingStick = AreaTipClient.isHoldingAreaTipStick(client);
        UUID axiomGroupId = AreaTipClient.axiomRenderGroupId();
        if (!holdingStick && axiomGroupId == null) {
            return;
        }

        List<RenderGroup> areaGroups = cachedAreaGroups(holdingStick, axiomGroupId);
        RenderGroup previewGroup = previewGroup(client, holdingStick);
        if (areaGroups.isEmpty() && previewGroup == null) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getLines());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        long time = client.world.getTime();
        for (RenderGroup group : areaGroups) {
            renderGroup(vertices, matrix, camera, group, time);
        }
        if (previewGroup != null) {
            renderGroup(vertices, matrix, camera, previewGroup, time);
        }
    }

    private static List<RenderGroup> cachedAreaGroups(boolean holdingStick, UUID axiomGroupId) {
        long areasRevision = AreaTipClient.areasRevision();
        long configRevision = AreaTipClient.configRevision();
        if (cachedAreasRevision == areasRevision
                && cachedConfigRevision == configRevision
                && cachedHoldingStick == holdingStick
                && java.util.Objects.equals(cachedAxiomGroupId, axiomGroupId)) {
            return cachedAreaGroups;
        }

        cachedAreasRevision = areasRevision;
        cachedConfigRevision = configRevision;
        cachedHoldingStick = holdingStick;
        cachedAxiomGroupId = axiomGroupId;

        Map<Integer, Set<BlockPos>> blocksByColor = new HashMap<>();
        for (AreaTipClient.AreaView view : AreaTipClient.areas()) {
            if (!holdingStick && (axiomGroupId == null || !axiomGroupId.equals(view.groupId()))) {
                continue;
            }
            int color = AreaTipClient.colorFor(view.groupId(), view.color());
            addBlocks(blocksByColor.computeIfAbsent(color, ignored -> new HashSet<>()), view.coveredBlocks());
        }
        cachedAreaGroups = buildGroups(blocksByColor);
        return cachedAreaGroups;
    }

    private static RenderGroup previewGroup(MinecraftClient client, boolean holdingStick) {
        if (!holdingStick) {
            cachedPreviewKey = null;
            cachedPreviewGroup = null;
            return null;
        }
        BlockPos preview = AreaTipClient.previewCenter(client);
        AreaTipConfig.GroupConfig group = AreaTipClient.currentGroup();
        if (preview == null || group == null) {
            cachedPreviewKey = null;
            cachedPreviewGroup = null;
            return null;
        }
        PreviewKey key = new PreviewKey(preview, group.color, group.spec());
        if (key.equals(cachedPreviewKey)) {
            return cachedPreviewGroup;
        }
        Set<BlockPos> blocks = new HashSet<>();
        addBlocks(blocks, key.spec().coveredBlocks(preview));
        cachedPreviewKey = key;
        cachedPreviewGroup = createGroup(key.color(), blocks);
        return cachedPreviewGroup;
    }

    private static List<RenderGroup> buildGroups(Map<Integer, Set<BlockPos>> blocksByColor) {
        if (blocksByColor.isEmpty()) {
            return List.of();
        }
        List<RenderGroup> groups = new ArrayList<>(blocksByColor.size());
        for (Map.Entry<Integer, Set<BlockPos>> entry : blocksByColor.entrySet()) {
            RenderGroup group = createGroup(entry.getKey(), entry.getValue());
            if (group != null) {
                groups.add(group);
            }
        }
        return List.copyOf(groups);
    }

    private static RenderGroup createGroup(int color, Set<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        Set<BlockPos> copiedBlocks = Set.copyOf(blocks);
        Bounds bounds = Bounds.of(copiedBlocks);
        List<LineSegment> edges = buildEdges(copiedBlocks);
        if (edges.isEmpty()) {
            return null;
        }
        return new RenderGroup(color, bounds, edges);
    }

    private static void addBlocks(Set<BlockPos> target, List<BlockPos> source) {
        for (BlockPos pos : source) {
            if (target.size() >= MAX_BLOCKS_PER_COLOR) {
                return;
            }
            target.add(pos.toImmutable());
        }
    }

    private static List<LineSegment> buildEdges(Set<BlockPos> blocks) {
        List<LineSegment> edges = new ArrayList<>();
        BlockPos.Mutable scratchA = new BlockPos.Mutable();
        BlockPos.Mutable scratchB = new BlockPos.Mutable();
        for (BlockPos pos : blocks) {
            if (!isBoundary(pos, blocks, scratchA)) {
                continue;
            }
            for (Direction face : Direction.values()) {
                if (containsOffset(blocks, pos, face, scratchA)) {
                    continue;
                }
                for (Direction side : Direction.values()) {
                    if (side.getAxis() == face.getAxis()) {
                        continue;
                    }
                    boolean sidePresent = containsOffset(blocks, pos, side, scratchA);
                    boolean sideFacePresent = sidePresent
                            && containsOffset(blocks, scratchA.getX(), scratchA.getY(), scratchA.getZ(), face, scratchB);
                    if (sidePresent && !sideFacePresent) {
                        continue;
                    }
                    edges.add(createEdge(pos, face, side));
                }
            }
        }
        return List.copyOf(edges);
    }

    private static boolean isBoundary(BlockPos pos, Set<BlockPos> blocks, BlockPos.Mutable scratch) {
        for (Direction direction : Direction.values()) {
            if (!containsOffset(blocks, pos, direction, scratch)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsOffset(Set<BlockPos> blocks, BlockPos pos, Direction direction, BlockPos.Mutable scratch) {
        return containsOffset(blocks, pos.getX(), pos.getY(), pos.getZ(), direction, scratch);
    }

    private static boolean containsOffset(Set<BlockPos> blocks, int x, int y, int z,
                                          Direction direction, BlockPos.Mutable scratch) {
        scratch.set(
                x + direction.getOffsetX(),
                y + direction.getOffsetY(),
                z + direction.getOffsetZ()
        );
        return blocks.contains(scratch);
    }

    private static LineSegment createEdge(BlockPos pos, Direction face, Direction side) {
        Direction.Axis edgeAxis = remainingAxis(face.getAxis(), side.getAxis());
        float minX = pos.getX();
        float minY = pos.getY();
        float minZ = pos.getZ();
        float maxX = minX + 1.0F;
        float maxY = minY + 1.0F;
        float maxZ = minZ + 1.0F;

        float x1 = edgeCoordinate(Direction.Axis.X, face, side, edgeAxis, minX, maxX, false);
        float y1 = edgeCoordinate(Direction.Axis.Y, face, side, edgeAxis, minY, maxY, false);
        float z1 = edgeCoordinate(Direction.Axis.Z, face, side, edgeAxis, minZ, maxZ, false);
        float x2 = edgeCoordinate(Direction.Axis.X, face, side, edgeAxis, minX, maxX, true);
        float y2 = edgeCoordinate(Direction.Axis.Y, face, side, edgeAxis, minY, maxY, true);
        float z2 = edgeCoordinate(Direction.Axis.Z, face, side, edgeAxis, minZ, maxZ, true);

        float normalX = x2 - x1;
        float normalY = y2 - y1;
        float normalZ = z2 - z1;
        float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (length > 0.0F) {
            normalX /= length;
            normalY /= length;
            normalZ /= length;
        }
        return new LineSegment(x1, y1, z1, x2, y2, z2, normalX, normalY, normalZ);
    }

    private static void renderGroup(VertexConsumer vertices, Matrix4f matrix, Vec3d camera, RenderGroup group, long time) {
        for (LineSegment edge : group.edges()) {
            float x1 = (float) (edge.x1() - camera.x);
            float y1 = (float) (edge.y1() - camera.y);
            float z1 = (float) (edge.z1() - camera.z);
            float x2 = (float) (edge.x2() - camera.x);
            float y2 = (float) (edge.y2() - camera.y);
            float z2 = (float) (edge.z2() - camera.z);
            int colorA = flowingColor(group.color(), group.bounds(), edge.x1(), edge.y1(), edge.z1(), time);
            int colorB = flowingColor(group.color(), group.bounds(), edge.x2(), edge.y2(), edge.z2(), time);
            vertices.vertex(matrix, x1, y1, z1).color(colorA).normal(edge.normalX(), edge.normalY(), edge.normalZ());
            vertices.vertex(matrix, x2, y2, z2).color(colorB).normal(edge.normalX(), edge.normalY(), edge.normalZ());
        }
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

    private static float edgeCoordinate(Direction.Axis axis, Direction face, Direction side, Direction.Axis edgeAxis,
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

    private record RenderGroup(int color, Bounds bounds, List<LineSegment> edges) {
    }

    private record LineSegment(float x1, float y1, float z1, float x2, float y2, float z2,
                               float normalX, float normalY, float normalZ) {
    }

    private record PreviewKey(BlockPos center, int color, com.kuilunfuzhe.monvhua.features.gravity.GravityAreaSpec spec) {
        private PreviewKey {
            center = center.toImmutable();
        }
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

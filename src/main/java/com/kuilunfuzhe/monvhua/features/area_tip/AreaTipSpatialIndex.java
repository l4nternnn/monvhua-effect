package com.kuilunfuzhe.monvhua.features.area_tip;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Immutable broad phase. One leaf per area, independent of its block volume. Server thread only. */
final class AreaTipSpatialIndex {
    private final Node root;

    AreaTipSpatialIndex(List<AreaTipAreaStore.StoredArea> areas) {
        List<Node> leaves = new ArrayList<>(areas.size());
        for (int i = 0; i < areas.size(); i++) {
            var area = areas.get(i);
            BlockPos min = area.minBlock();
            BlockPos max = area.maxBlock();
            // Legacy entries may carry explicit bounds narrower than their exact block set.
            for (BlockPos block : area.blocks()) {
                min = new BlockPos(Math.min(min.getX(), block.getX()), Math.min(min.getY(), block.getY()), Math.min(min.getZ(), block.getZ()));
                max = new BlockPos(Math.max(max.getX(), block.getX()), Math.max(max.getY(), block.getY()), Math.max(max.getZ(), block.getZ()));
            }
            // Inclusive bounds: preserve the old point tests even exactly on a block face.
            leaves.add(new Node(min.getX(), min.getY(), min.getZ(),
                    max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0, i, area, null, null));
        }
        root = build(leaves, 0, leaves.size());
    }

    Optional<AreaTipAreaStore.StoredArea> first(Box box, Predicate<AreaTipAreaStore.StoredArea> matches) {
        Node hit = first(root, box, matches, null);
        return hit == null ? Optional.empty() : Optional.of(hit.area);
    }

    private static Node first(Node node, Box box, Predicate<AreaTipAreaStore.StoredArea> matches, Node best) {
        if (node == null || (best != null && node.order >= best.order)
                || box.maxX < node.minX || box.minX > node.maxX
                || box.maxY < node.minY || box.minY > node.maxY
                || box.maxZ < node.minZ || box.minZ > node.maxZ) return best;
        if (node.area != null) return matches.test(node.area) ? node : best;
        Node earlier = node.left.order <= node.right.order ? node.left : node.right;
        Node later = earlier == node.left ? node.right : node.left;
        return first(later, box, matches, first(earlier, box, matches, best));
    }

    private static Node build(List<Node> nodes, int from, int to) {
        if (from == to) return null;
        if (to - from == 1) return nodes.get(from);
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int i = from; i < to; i++) {
            Node n = nodes.get(i);
            minX = Math.min(minX, n.minX); minY = Math.min(minY, n.minY); minZ = Math.min(minZ, n.minZ);
            maxX = Math.max(maxX, n.maxX); maxY = Math.max(maxY, n.maxY); maxZ = Math.max(maxZ, n.maxZ);
        }
        int axis = maxX - minX >= maxY - minY && maxX - minX >= maxZ - minZ ? 0
                : maxY - minY >= maxZ - minZ ? 1 : 2;
        nodes.subList(from, to).sort(Comparator.comparingDouble(n -> switch (axis) {
            case 0 -> n.minX + n.maxX;
            case 1 -> n.minY + n.maxY;
            default -> n.minZ + n.maxZ;
        }));
        int middle = (from + to) >>> 1;
        Node left = build(nodes, from, middle), right = build(nodes, middle, to);
        return new Node(minX, minY, minZ, maxX, maxY, maxZ,
                Math.min(left.order, right.order), null, left, right);
    }

    private record Node(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                        int order, AreaTipAreaStore.StoredArea area, Node left, Node right) {}
}

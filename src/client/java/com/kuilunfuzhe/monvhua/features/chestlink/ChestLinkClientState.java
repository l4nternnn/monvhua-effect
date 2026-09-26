package com.kuilunfuzhe.monvhua.features.chestlink;

import net.minecraft.util.math.BlockPos;
import com.kuilunfuzhe.monvhua.network.chestlink.ChestLinkMappingsS2CPacket;
import java.util.ArrayList;
import java.util.List;

public final class ChestLinkClientState {
    private static BlockPos source;
    private static String sourceDimension = "";
    private static List<ChestLinkMappingsS2CPacket.Entry> mappings = List.of();
    public static void update(boolean active, String dimension, int x, int y, int z) {
        source = active ? new BlockPos(x, y, z) : null;
        sourceDimension = active ? dimension : "";
    }
    public static BlockPos source() { return source; }
    public static String sourceDimension() { return sourceDimension; }
    public static void applyMappings(boolean replace, List<ChestLinkMappingsS2CPacket.Entry> entries) {
        if (replace) mappings = List.copyOf(entries);
        else {
            ArrayList<ChestLinkMappingsS2CPacket.Entry> merged = new ArrayList<>(mappings);
            merged.addAll(entries);
            mappings = List.copyOf(merged);
        }
    }
    public static List<ChestLinkMappingsS2CPacket.Entry> mappings() { return mappings; }
    public static ChestLinkMappingsS2CPacket.Entry findEntrance(String dimension, BlockPos pos) {
        for (ChestLinkMappingsS2CPacket.Entry entry : mappings) {
            if (entry.targetDimension().equals(dimension)
                    && entry.targetX() == pos.getX() && entry.targetY() == pos.getY() && entry.targetZ() == pos.getZ()) {
                return entry;
            }
        }
        return null;
    }
}

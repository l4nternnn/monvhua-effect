package com.kuilunfuzhe.monvhua.features.area_tip;

import com.kuilunfuzhe.monvhua.features.gravity.GravityAreaSpec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class AreaTipAreaStore extends PersistentState {
    public static final int MAX_STORED_BLOCKS = GravityAreaSpec.MAX_RENDER_BLOCKS;
    public static final Codec<StoredArea> STORED_AREA_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.CODEC.fieldOf("id").forGetter(StoredArea::id),
            Uuids.CODEC.fieldOf("groupId").forGetter(StoredArea::groupId),
            BlockPos.CODEC.fieldOf("center").forGetter(StoredArea::center),
            Codec.INT.fieldOf("shape").forGetter(StoredArea::shape),
            Codec.INT.fieldOf("half").forGetter(StoredArea::half),
            Codec.INT.fieldOf("sizeX").forGetter(StoredArea::sizeX),
            Codec.INT.fieldOf("sizeY").forGetter(StoredArea::sizeY),
            Codec.INT.fieldOf("sizeZ").forGetter(StoredArea::sizeZ),
            Codec.INT.fieldOf("color").forGetter(StoredArea::color),
            BlockPos.CODEC.optionalFieldOf("min").forGetter(area -> Optional.ofNullable(area.min())),
            BlockPos.CODEC.optionalFieldOf("max").forGetter(area -> Optional.ofNullable(area.max())),
            BlockPos.CODEC.listOf().optionalFieldOf("blocks").forGetter(area ->
                    area.blocks().isEmpty() ? Optional.empty() : Optional.of(area.blocks()))
    ).apply(instance, AreaTipAreaStore::storedAreaFromCodec));
    public static final Codec<AreaTipAreaStore> CODEC = STORED_AREA_CODEC.listOf()
            .xmap(AreaTipAreaStore::new, AreaTipAreaStore::toAreas);
    public static final PersistentStateType<AreaTipAreaStore> TYPE = new PersistentStateType<>(
            "monvhua_area_tips",
            AreaTipAreaStore::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<UUID, StoredArea> areas = new LinkedHashMap<>();
    private final transient Map<UUID, Set<BlockPos>> blockLookupCache = new HashMap<>();

    public AreaTipAreaStore() {
    }

    private AreaTipAreaStore(List<StoredArea> entries) {
        for (StoredArea entry : entries) {
            StoredArea area = entry.sanitized();
            areas.put(area.id(), area);
        }
    }

    public static AreaTipAreaStore get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE);
    }

    private static StoredArea storedAreaFromCodec(UUID id, UUID groupId, BlockPos center, int shape, int half,
                                                  int sizeX, int sizeY, int sizeZ, int color,
                                                  Optional<BlockPos> min, Optional<BlockPos> max,
                                                  Optional<List<BlockPos>> blocks) {
        return new StoredArea(id, groupId, center, shape, half, sizeX, sizeY, sizeZ, color,
                min.orElse(null), max.orElse(null), blocks.orElse(List.of()));
    }

    public StoredArea add(StoredArea area) {
        StoredArea sanitized = area.sanitized();
        areas.put(sanitized.id(), sanitized);
        blockLookupCache.remove(sanitized.id());
        markDirty();
        return sanitized;
    }

    public List<StoredArea> toAreas() {
        return new ArrayList<>(areas.values());
    }

    public int removeIntersecting(UUID groupId, BlockPos min, BlockPos max) {
        if (groupId == null || min == null || max == null) {
            return 0;
        }
        BlockPos fixedMin = min(min, max);
        BlockPos fixedMax = max(min, max);
        List<UUID> removed = new ArrayList<>();
        for (StoredArea area : areas.values()) {
            if (groupId.equals(area.groupId()) && area.intersects(fixedMin, fixedMax)) {
                removed.add(area.id());
            }
        }
        for (UUID id : removed) {
            areas.remove(id);
            blockLookupCache.remove(id);
        }
        if (!removed.isEmpty()) {
            markDirty();
        }
        return removed.size();
    }

    public int removeIntersectingBlocks(UUID groupId, List<BlockPos> selectedBlocks) {
        if (groupId == null || selectedBlocks == null || selectedBlocks.isEmpty()) {
            return 0;
        }
        List<BlockPos> sanitizedBlocks = sanitizeBlocks(selectedBlocks);
        Set<BlockPos> selected = new LinkedHashSet<>(sanitizedBlocks);
        BlockPos selectedMin = minOf(sanitizedBlocks);
        BlockPos selectedMax = maxOf(sanitizedBlocks);
        List<UUID> removedIds = new ArrayList<>();
        List<StoredArea> replacements = new ArrayList<>();
        int removedCount = 0;
        for (StoredArea area : new ArrayList<>(areas.values())) {
            if (!groupId.equals(area.groupId()) || !area.intersects(selectedMin, selectedMax)) {
                continue;
            }
            List<BlockPos> areaBlocks = area.coveredBlocks();
            if (areaBlocks.isEmpty()) {
                continue;
            }
            List<BlockPos> remaining = new ArrayList<>(areaBlocks.size());
            boolean changed = false;
            for (BlockPos block : areaBlocks) {
                if (selected.contains(block)) {
                    changed = true;
                    removedCount++;
                    continue;
                }
                remaining.add(block);
            }
            if (!changed) {
                continue;
            }
            removedIds.add(area.id());
            if (!remaining.isEmpty()) {
                replacements.add(new StoredArea(
                        area.id(),
                        area.groupId(),
                        area.center(),
                        area.shape(),
                        area.half(),
                        area.sizeX(),
                        area.sizeY(),
                        area.sizeZ(),
                        area.color(),
                        null,
                        null,
                        remaining
                ));
            }
        }
        for (UUID id : removedIds) {
            areas.remove(id);
            blockLookupCache.remove(id);
        }
        for (StoredArea replacement : replacements) {
            areas.put(replacement.id(), replacement);
            blockLookupCache.remove(replacement.id());
        }
        if (removedCount > 0) {
            markDirty();
        }
        return removedCount;
    }

    public Optional<StoredArea> firstContaining(Box box) {
        Vec3d center = box.getCenter();
        for (StoredArea area : areas.values()) {
            if (contains(area, center) || containsAnyCorner(area, box)) {
                return Optional.of(area);
            }
        }
        return Optional.empty();
    }

    private boolean containsAnyCorner(StoredArea area, Box box) {
        return contains(area, new Vec3d(box.minX, box.minY, box.minZ))
                || contains(area, new Vec3d(box.minX, box.minY, box.maxZ))
                || contains(area, new Vec3d(box.minX, box.maxY, box.minZ))
                || contains(area, new Vec3d(box.minX, box.maxY, box.maxZ))
                || contains(area, new Vec3d(box.maxX, box.minY, box.minZ))
                || contains(area, new Vec3d(box.maxX, box.minY, box.maxZ))
                || contains(area, new Vec3d(box.maxX, box.maxY, box.minZ))
                || contains(area, new Vec3d(box.maxX, box.maxY, box.maxZ));
    }

    private boolean contains(StoredArea area, Vec3d pos) {
        if (!area.hasBlockSet()) {
            return area.contains(pos);
        }
        return blockLookup(area).contains(BlockPos.ofFloored(pos));
    }

    private boolean intersectsAnyBlock(StoredArea area, Set<BlockPos> selectedBlocks) {
        if (!area.hasBlockSet()) {
            return area.intersectsAnyBlock(selectedBlocks);
        }
        Set<BlockPos> lookup = blockLookup(area);
        if (selectedBlocks.size() <= lookup.size()) {
            for (BlockPos block : selectedBlocks) {
                if (lookup.contains(block)) {
                    return true;
                }
            }
            return false;
        }
        for (BlockPos block : lookup) {
            if (selectedBlocks.contains(block)) {
                return true;
            }
        }
        return false;
    }

    private Set<BlockPos> blockLookup(StoredArea area) {
        return blockLookupCache.computeIfAbsent(area.id(), ignored -> new LinkedHashSet<>(area.blocks()));
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
            if (sanitized.size() >= MAX_STORED_BLOCKS) {
                break;
            }
        }
        return List.copyOf(sanitized);
    }

    private static BlockPos minOf(List<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return BlockPos.ORIGIN;
        }
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
        if (blocks == null || blocks.isEmpty()) {
            return BlockPos.ORIGIN;
        }
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

    public record StoredArea(UUID id, UUID groupId, BlockPos center, int shape, int half,
                             int sizeX, int sizeY, int sizeZ, int color,
                             BlockPos min, BlockPos max, List<BlockPos> blocks) {
        public StoredArea {
            id = id == null ? UUID.randomUUID() : id;
            groupId = groupId == null ? new UUID(0L, 0L) : groupId;
            center = center == null ? BlockPos.ORIGIN : center.toImmutable();
            color = 0xFF000000 | (color & 0xFFFFFF);
            blocks = sanitizeBlocks(blocks);
            if (!blocks.isEmpty() && (min == null || max == null)) {
                min = minOf(blocks);
                max = maxOf(blocks);
            }
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

        public StoredArea(UUID id, UUID groupId, BlockPos center, int shape, int half,
                          int sizeX, int sizeY, int sizeZ, int color) {
            this(id, groupId, center, shape, half, sizeX, sizeY, sizeZ, color, null, null);
        }

        public StoredArea(UUID id, UUID groupId, BlockPos center, int shape, int half,
                          int sizeX, int sizeY, int sizeZ, int color, BlockPos min, BlockPos max) {
            this(id, groupId, center, shape, half, sizeX, sizeY, sizeZ, color, min, max, List.of());
        }

        public GravityAreaSpec spec() {
            return new GravityAreaSpec(
                    GravityAreaSpec.Shape.byId(shape),
                    GravityAreaSpec.Half.byId(half),
                    sizeX,
                    sizeY,
                    sizeZ
            );
        }

        public boolean contains(Vec3d pos) {
            if (hasBlockSet()) {
                return containsBlock(BlockPos.ofFloored(pos));
            }
            if (hasExactBounds()) {
                return pos.x >= min.getX() && pos.x <= max.getX() + 1.0D
                        && pos.y >= min.getY() && pos.y <= max.getY() + 1.0D
                        && pos.z >= min.getZ() && pos.z <= max.getZ() + 1.0D;
            }
            return spec().contains(center, pos);
        }

        public boolean hasExactBounds() {
            return min != null && max != null;
        }

        public boolean hasBlockSet() {
            return !blocks.isEmpty();
        }

        public BlockPos minBlock() {
            if (hasExactBounds()) {
                return min;
            }
            GravityAreaSpec.Bounds bounds = spec().bounds(center);
            return new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
        }

        public BlockPos maxBlock() {
            if (hasExactBounds()) {
                return max;
            }
            GravityAreaSpec.Bounds bounds = spec().bounds(center);
            return new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ());
        }

        public boolean intersects(BlockPos otherMin, BlockPos otherMax) {
            BlockPos thisMin = minBlock();
            BlockPos thisMax = maxBlock();
            return thisMin.getX() <= otherMax.getX() && thisMax.getX() >= otherMin.getX()
                    && thisMin.getY() <= otherMax.getY() && thisMax.getY() >= otherMin.getY()
                    && thisMin.getZ() <= otherMax.getZ() && thisMax.getZ() >= otherMin.getZ();
        }

        public boolean intersectsAnyBlock(Set<BlockPos> selectedBlocks) {
            if (selectedBlocks == null || selectedBlocks.isEmpty()) {
                return false;
            }
            if (hasBlockSet()) {
                for (BlockPos block : blocks) {
                    if (selectedBlocks.contains(block)) {
                        return true;
                    }
                }
                return false;
            }
            for (BlockPos block : selectedBlocks) {
                if (containsBlock(block)) {
                    return true;
                }
            }
            return false;
        }

        private boolean containsBlock(BlockPos block) {
            if (hasBlockSet()) {
                return blocks.contains(block);
            }
            if (hasExactBounds()) {
                return block.getX() >= min.getX() && block.getX() <= max.getX()
                        && block.getY() >= min.getY() && block.getY() <= max.getY()
                        && block.getZ() >= min.getZ() && block.getZ() <= max.getZ();
            }
            return spec().containsBlock(center, block);
        }

        public List<BlockPos> coveredBlocks() {
            if (hasBlockSet()) {
                return blocks;
            }
            if (!hasExactBounds()) {
                return spec().coveredBlocks(center);
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

        private StoredArea sanitized() {
            GravityAreaSpec spec = spec();
            return new StoredArea(id, groupId, center, spec.shape().ordinal(), spec.half().ordinal(),
                    spec.sizeX(), spec.sizeY(), spec.sizeZ(), color, min, max, blocks);
        }
    }
}

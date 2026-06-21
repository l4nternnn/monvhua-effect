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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AreaTipAreaStore extends PersistentState {
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
            BlockPos.CODEC.optionalFieldOf("max").forGetter(area -> Optional.ofNullable(area.max()))
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
                                                  Optional<BlockPos> min, Optional<BlockPos> max) {
        return new StoredArea(id, groupId, center, shape, half, sizeX, sizeY, sizeZ, color,
                min.orElse(null), max.orElse(null));
    }

    public StoredArea add(StoredArea area) {
        StoredArea sanitized = area.sanitized();
        areas.put(sanitized.id(), sanitized);
        markDirty();
        return sanitized;
    }

    public List<StoredArea> toAreas() {
        return new ArrayList<>(areas.values());
    }

    public Optional<StoredArea> firstContaining(Box box) {
        Vec3d center = box.getCenter();
        for (StoredArea area : areas.values()) {
            if (area.contains(center) || containsAnyCorner(area, box)) {
                return Optional.of(area);
            }
        }
        return Optional.empty();
    }

    private static boolean containsAnyCorner(StoredArea area, Box box) {
        return area.contains(new Vec3d(box.minX, box.minY, box.minZ))
                || area.contains(new Vec3d(box.minX, box.minY, box.maxZ))
                || area.contains(new Vec3d(box.minX, box.maxY, box.minZ))
                || area.contains(new Vec3d(box.minX, box.maxY, box.maxZ))
                || area.contains(new Vec3d(box.maxX, box.minY, box.minZ))
                || area.contains(new Vec3d(box.maxX, box.minY, box.maxZ))
                || area.contains(new Vec3d(box.maxX, box.maxY, box.minZ))
                || area.contains(new Vec3d(box.maxX, box.maxY, box.maxZ));
    }

    public record StoredArea(UUID id, UUID groupId, BlockPos center, int shape, int half,
                             int sizeX, int sizeY, int sizeZ, int color, BlockPos min, BlockPos max) {
        public StoredArea {
            id = id == null ? UUID.randomUUID() : id;
            groupId = groupId == null ? new UUID(0L, 0L) : groupId;
            center = center == null ? BlockPos.ORIGIN : center.toImmutable();
            color = 0xFF000000 | (color & 0xFFFFFF);
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

        public List<BlockPos> coveredBlocks() {
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
                    spec.sizeX(), spec.sizeY(), spec.sizeZ(), color, min, max);
        }
    }
}

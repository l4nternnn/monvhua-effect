package com.kuilunfuzhe.monvhua.network.area_tip;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.area_tip.AreaTipAreaStore;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public final class AreaTipPackets {
    private static final int MAX_JSON_LENGTH = 1_000_000;
    private static final int MAX_AREA_UPDATES = 4096;
    private static final int MAX_REGION_BLOCKS = 24000;
    private static final int MAX_RESOURCE_NAME_LENGTH = 160;
    private static final int MAX_RESOURCE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_RESOURCE_CHUNK_BYTES = 16 * 1024;

    private AreaTipPackets() {
    }

    public static void registerS2C() {
        ConfigS2C.register();
        FullSyncS2C.register();
        AreaUpdateS2C.register();
        ResourceSyncStartS2C.register();
        ResourceSyncChunkS2C.register();
        ResourceDeleteS2C.register();
    }

    public static void registerC2S() {
        RequestConfigC2S.register();
        UpdateConfigC2S.register();
        RequestAreasC2S.register();
        PlaceAreaC2S.register();
        PlaceBoundsC2S.register();
        DeleteBoundsC2S.register();
        PlaceSelectionC2S.register();
        DeleteSelectionC2S.register();
        ResourceUploadStartC2S.register();
        ResourceUploadChunkC2S.register();
        ResourceDeleteC2S.register();
        ResourceRequestC2S.register();
    }

    public record AreaData(UUID id, UUID groupId, BlockPos center, int shape, int half,
                           int sizeX, int sizeY, int sizeZ, int color,
                           BlockPos min, BlockPos max, List<BlockPos> blocks) {
        public AreaData {
            id = id == null ? UUID.randomUUID() : id;
            groupId = groupId == null ? new UUID(0L, 0L) : groupId;
            center = center == null ? BlockPos.ORIGIN : center.toImmutable();
            color = 0xFF000000 | (color & 0xFFFFFF);
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
        }

        public AreaData(UUID id, UUID groupId, BlockPos center, int shape, int half,
                        int sizeX, int sizeY, int sizeZ, int color) {
            this(id, groupId, center, shape, half, sizeX, sizeY, sizeZ, color, null, null, List.of());
        }

        public AreaData(UUID id, UUID groupId, BlockPos center, int shape, int half,
                        int sizeX, int sizeY, int sizeZ, int color, BlockPos min, BlockPos max) {
            this(id, groupId, center, shape, half, sizeX, sizeY, sizeZ, color, min, max, List.of());
        }

        public static AreaData fromStored(AreaTipAreaStore.StoredArea area) {
            return new AreaData(area.id(), area.groupId(), area.center(), area.shape(), area.half(),
                    area.sizeX(), area.sizeY(), area.sizeZ(), area.color(), area.min(), area.max(), area.blocks());
        }

        public AreaTipAreaStore.StoredArea toStored() {
            return new AreaTipAreaStore.StoredArea(id, groupId, center, shape, half, sizeX, sizeY, sizeZ, color, min, max, blocks);
        }

        private static AreaData read(RegistryByteBuf buf) {
            UUID id = buf.readUuid();
            UUID groupId = buf.readUuid();
            BlockPos center = buf.readBlockPos();
            int shape = buf.readVarInt();
            int half = buf.readVarInt();
            int sizeX = buf.readVarInt();
            int sizeY = buf.readVarInt();
            int sizeZ = buf.readVarInt();
            int color = buf.readInt();
            boolean hasBounds = buf.readBoolean();
            BlockPos min = hasBounds ? buf.readBlockPos() : null;
            BlockPos max = hasBounds ? buf.readBlockPos() : null;
            List<BlockPos> blocks = readBlocks(buf);
            return new AreaData(id, groupId, center, shape, half, sizeX, sizeY, sizeZ, color, min, max, blocks);
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(id);
            buf.writeUuid(groupId);
            buf.writeBlockPos(center);
            buf.writeVarInt(shape);
            buf.writeVarInt(half);
            buf.writeVarInt(sizeX);
            buf.writeVarInt(sizeY);
            buf.writeVarInt(sizeZ);
            buf.writeInt(color);
            boolean hasBounds = min != null && max != null;
            buf.writeBoolean(hasBounds);
            if (hasBounds) {
                buf.writeBlockPos(min);
                buf.writeBlockPos(max);
            }
            writeBlocks(buf, blocks);
        }
    }

    public record ConfigS2C(String json) implements CustomPayload {
        public static final Id<ConfigS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "area_tip_config"));
        public static final PacketCodec<RegistryByteBuf, ConfigS2C> CODEC = PacketCodec.of(ConfigS2C::write, ConfigS2C::new);
        private static boolean registered = false;

        private ConfigS2C(RegistryByteBuf buf) {
            this(buf.readString(MAX_JSON_LENGTH));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(json, MAX_JSON_LENGTH);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FullSyncS2C(List<AreaData> areas) implements CustomPayload {
        public static final Id<FullSyncS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "area_tip_full_sync"));
        public static final PacketCodec<RegistryByteBuf, FullSyncS2C> CODEC = PacketCodec.of(FullSyncS2C::write, FullSyncS2C::new);
        private static boolean registered = false;

        public FullSyncS2C {
            areas = List.copyOf(areas.size() > MAX_AREA_UPDATES ? areas.subList(0, MAX_AREA_UPDATES) : areas);
        }

        private FullSyncS2C(RegistryByteBuf buf) {
            this(readAreas(buf));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(Math.min(areas.size(), MAX_AREA_UPDATES));
            for (int i = 0; i < Math.min(areas.size(), MAX_AREA_UPDATES); i++) {
                areas.get(i).write(buf);
            }
        }

        private static List<AreaData> readAreas(RegistryByteBuf buf) {
            int count = Math.min(buf.readVarInt(), MAX_AREA_UPDATES);
            List<AreaData> areas = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                areas.add(AreaData.read(buf));
            }
            return areas;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record AreaUpdateS2C(AreaData area) implements CustomPayload {
        public static final Id<AreaUpdateS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "area_tip_update"));
        public static final PacketCodec<RegistryByteBuf, AreaUpdateS2C> CODEC = PacketCodec.of(AreaUpdateS2C::write, AreaUpdateS2C::new);
        private static boolean registered = false;

        private AreaUpdateS2C(RegistryByteBuf buf) {
            this(AreaData.read(buf));
        }

        private void write(RegistryByteBuf buf) {
            area.write(buf);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record RequestConfigC2S() implements CustomPayload {
        public static final Id<RequestConfigC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "request_area_tip_config"));
        public static final PacketCodec<RegistryByteBuf, RequestConfigC2S> CODEC = PacketCodec.unit(new RequestConfigC2S());
        private static boolean registered = false;

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record UpdateConfigC2S(String json) implements CustomPayload {
        public static final Id<UpdateConfigC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "update_area_tip_config"));
        public static final PacketCodec<RegistryByteBuf, UpdateConfigC2S> CODEC = PacketCodec.of(UpdateConfigC2S::write, UpdateConfigC2S::new);
        private static boolean registered = false;

        private UpdateConfigC2S(RegistryByteBuf buf) {
            this(buf.readString(MAX_JSON_LENGTH));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(json, MAX_JSON_LENGTH);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record RequestAreasC2S() implements CustomPayload {
        public static final Id<RequestAreasC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "request_area_tips"));
        public static final PacketCodec<RegistryByteBuf, RequestAreasC2S> CODEC = PacketCodec.unit(new RequestAreasC2S());
        private static boolean registered = false;

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PlaceAreaC2S(UUID groupId, BlockPos center, int shape, int half,
                               int sizeX, int sizeY, int sizeZ, int color) implements CustomPayload {
        public static final Id<PlaceAreaC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "place_area_tip"));
        public static final PacketCodec<RegistryByteBuf, PlaceAreaC2S> CODEC = PacketCodec.of(PlaceAreaC2S::write, PlaceAreaC2S::new);
        private static boolean registered = false;

        private PlaceAreaC2S(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(groupId);
            buf.writeBlockPos(center);
            buf.writeVarInt(shape);
            buf.writeVarInt(half);
            buf.writeVarInt(sizeX);
            buf.writeVarInt(sizeY);
            buf.writeVarInt(sizeZ);
            buf.writeInt(color);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PlaceBoundsC2S(UUID groupId, BlockPos min, BlockPos max, int color) implements CustomPayload {
        public static final Id<PlaceBoundsC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "place_area_tip_bounds"));
        public static final PacketCodec<RegistryByteBuf, PlaceBoundsC2S> CODEC = PacketCodec.of(PlaceBoundsC2S::write, PlaceBoundsC2S::new);
        private static boolean registered = false;

        public PlaceBoundsC2S {
            groupId = groupId == null ? new UUID(0L, 0L) : groupId;
            min = min == null ? BlockPos.ORIGIN : min.toImmutable();
            max = max == null ? min : max.toImmutable();
            color = 0xFF000000 | (color & 0xFFFFFF);
        }

        private PlaceBoundsC2S(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readBlockPos(), buf.readBlockPos(), buf.readInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(groupId);
            buf.writeBlockPos(min);
            buf.writeBlockPos(max);
            buf.writeInt(color);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record DeleteBoundsC2S(UUID groupId, BlockPos min, BlockPos max) implements CustomPayload {
        public static final Id<DeleteBoundsC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "delete_area_tip_bounds"));
        public static final PacketCodec<RegistryByteBuf, DeleteBoundsC2S> CODEC = PacketCodec.of(DeleteBoundsC2S::write, DeleteBoundsC2S::new);
        private static boolean registered = false;

        public DeleteBoundsC2S {
            groupId = groupId == null ? new UUID(0L, 0L) : groupId;
            min = min == null ? BlockPos.ORIGIN : min.toImmutable();
            max = max == null ? min : max.toImmutable();
        }

        private DeleteBoundsC2S(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readBlockPos(), buf.readBlockPos());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(groupId);
            buf.writeBlockPos(min);
            buf.writeBlockPos(max);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PlaceSelectionC2S(UUID groupId, List<BlockPos> blocks, int color) implements CustomPayload {
        public static final Id<PlaceSelectionC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "place_area_tip_selection"));
        public static final PacketCodec<RegistryByteBuf, PlaceSelectionC2S> CODEC = PacketCodec.of(PlaceSelectionC2S::write, PlaceSelectionC2S::new);
        private static boolean registered = false;

        public PlaceSelectionC2S {
            groupId = groupId == null ? new UUID(0L, 0L) : groupId;
            blocks = sanitizeBlocks(blocks);
            color = 0xFF000000 | (color & 0xFFFFFF);
        }

        private PlaceSelectionC2S(RegistryByteBuf buf) {
            this(buf.readUuid(), readBlocks(buf), buf.readInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(groupId);
            writeBlocks(buf, blocks);
            buf.writeInt(color);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record DeleteSelectionC2S(UUID groupId, List<BlockPos> blocks) implements CustomPayload {
        public static final Id<DeleteSelectionC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "delete_area_tip_selection"));
        public static final PacketCodec<RegistryByteBuf, DeleteSelectionC2S> CODEC = PacketCodec.of(DeleteSelectionC2S::write, DeleteSelectionC2S::new);
        private static boolean registered = false;

        public DeleteSelectionC2S {
            groupId = groupId == null ? new UUID(0L, 0L) : groupId;
            blocks = sanitizeBlocks(blocks);
        }

        private DeleteSelectionC2S(RegistryByteBuf buf) {
            this(buf.readUuid(), readBlocks(buf));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(groupId);
            writeBlocks(buf, blocks);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ResourceUploadStartC2S(String filename, int totalBytes, int totalChunks) implements CustomPayload {
        public static final Id<ResourceUploadStartC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "area_tip_resource_upload_start"));
        public static final PacketCodec<RegistryByteBuf, ResourceUploadStartC2S> CODEC = PacketCodec.of(ResourceUploadStartC2S::write, ResourceUploadStartC2S::new);
        private static boolean registered = false;

        public ResourceUploadStartC2S {
            filename = sanitizeResourceName(filename);
            totalBytes = Math.clamp(totalBytes, 0, MAX_RESOURCE_BYTES);
            totalChunks = Math.clamp(totalChunks, 0, 100000);
        }

        private ResourceUploadStartC2S(RegistryByteBuf buf) {
            this(buf.readString(MAX_RESOURCE_NAME_LENGTH), buf.readVarInt(), buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(filename, MAX_RESOURCE_NAME_LENGTH);
            buf.writeVarInt(totalBytes);
            buf.writeVarInt(totalChunks);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ResourceUploadChunkC2S(String filename, int chunkIndex, int totalChunks, byte[] bytes) implements CustomPayload {
        public static final Id<ResourceUploadChunkC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "area_tip_resource_upload_chunk"));
        public static final PacketCodec<RegistryByteBuf, ResourceUploadChunkC2S> CODEC = PacketCodec.of(ResourceUploadChunkC2S::write, ResourceUploadChunkC2S::new);
        private static boolean registered = false;

        public ResourceUploadChunkC2S {
            filename = sanitizeResourceName(filename);
            chunkIndex = Math.max(0, chunkIndex);
            totalChunks = Math.clamp(totalChunks, 0, 100000);
            bytes = bytes == null ? new byte[0] : bytes.clone();
            if (bytes.length > MAX_RESOURCE_CHUNK_BYTES) {
                byte[] limited = new byte[MAX_RESOURCE_CHUNK_BYTES];
                System.arraycopy(bytes, 0, limited, 0, MAX_RESOURCE_CHUNK_BYTES);
                bytes = limited;
            }
        }

        private ResourceUploadChunkC2S(RegistryByteBuf buf) {
            this(buf.readString(MAX_RESOURCE_NAME_LENGTH), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(MAX_RESOURCE_CHUNK_BYTES));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(filename, MAX_RESOURCE_NAME_LENGTH);
            buf.writeVarInt(chunkIndex);
            buf.writeVarInt(totalChunks);
            buf.writeByteArray(bytes);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ResourceDeleteC2S(String filename) implements CustomPayload {
        public static final Id<ResourceDeleteC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "area_tip_resource_delete"));
        public static final PacketCodec<RegistryByteBuf, ResourceDeleteC2S> CODEC = PacketCodec.of(ResourceDeleteC2S::write, ResourceDeleteC2S::new);
        private static boolean registered = false;

        public ResourceDeleteC2S {
            filename = sanitizeResourceName(filename);
        }

        private ResourceDeleteC2S(RegistryByteBuf buf) {
            this(buf.readString(MAX_RESOURCE_NAME_LENGTH));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(filename, MAX_RESOURCE_NAME_LENGTH);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ResourceRequestC2S(String filename) implements CustomPayload {
        public static final Id<ResourceRequestC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "area_tip_resource_request"));
        public static final PacketCodec<RegistryByteBuf, ResourceRequestC2S> CODEC = PacketCodec.of(ResourceRequestC2S::write, ResourceRequestC2S::new);
        private static boolean registered = false;

        public ResourceRequestC2S {
            filename = sanitizeResourceName(filename);
        }

        private ResourceRequestC2S(RegistryByteBuf buf) {
            this(buf.readString(MAX_RESOURCE_NAME_LENGTH));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(filename, MAX_RESOURCE_NAME_LENGTH);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ResourceSyncStartS2C(String filename, int totalBytes, int totalChunks) implements CustomPayload {
        public static final Id<ResourceSyncStartS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "area_tip_resource_sync_start"));
        public static final PacketCodec<RegistryByteBuf, ResourceSyncStartS2C> CODEC = PacketCodec.of(ResourceSyncStartS2C::write, ResourceSyncStartS2C::new);
        private static boolean registered = false;

        public ResourceSyncStartS2C {
            filename = sanitizeResourceName(filename);
            totalBytes = Math.clamp(totalBytes, 0, MAX_RESOURCE_BYTES);
            totalChunks = Math.clamp(totalChunks, 0, 100000);
        }

        private ResourceSyncStartS2C(RegistryByteBuf buf) {
            this(buf.readString(MAX_RESOURCE_NAME_LENGTH), buf.readVarInt(), buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(filename, MAX_RESOURCE_NAME_LENGTH);
            buf.writeVarInt(totalBytes);
            buf.writeVarInt(totalChunks);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ResourceSyncChunkS2C(String filename, int chunkIndex, int totalChunks, byte[] bytes) implements CustomPayload {
        public static final Id<ResourceSyncChunkS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "area_tip_resource_sync_chunk"));
        public static final PacketCodec<RegistryByteBuf, ResourceSyncChunkS2C> CODEC = PacketCodec.of(ResourceSyncChunkS2C::write, ResourceSyncChunkS2C::new);
        private static boolean registered = false;

        public ResourceSyncChunkS2C {
            filename = sanitizeResourceName(filename);
            chunkIndex = Math.max(0, chunkIndex);
            totalChunks = Math.clamp(totalChunks, 0, 100000);
            bytes = bytes == null ? new byte[0] : bytes.clone();
            if (bytes.length > MAX_RESOURCE_CHUNK_BYTES) {
                byte[] limited = new byte[MAX_RESOURCE_CHUNK_BYTES];
                System.arraycopy(bytes, 0, limited, 0, MAX_RESOURCE_CHUNK_BYTES);
                bytes = limited;
            }
        }

        private ResourceSyncChunkS2C(RegistryByteBuf buf) {
            this(buf.readString(MAX_RESOURCE_NAME_LENGTH), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(MAX_RESOURCE_CHUNK_BYTES));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(filename, MAX_RESOURCE_NAME_LENGTH);
            buf.writeVarInt(chunkIndex);
            buf.writeVarInt(totalChunks);
            buf.writeByteArray(bytes);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ResourceDeleteS2C(String filename) implements CustomPayload {
        public static final Id<ResourceDeleteS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "area_tip_resource_delete_sync"));
        public static final PacketCodec<RegistryByteBuf, ResourceDeleteS2C> CODEC = PacketCodec.of(ResourceDeleteS2C::write, ResourceDeleteS2C::new);
        private static boolean registered = false;

        public ResourceDeleteS2C {
            filename = sanitizeResourceName(filename);
        }

        private ResourceDeleteS2C(RegistryByteBuf buf) {
            this(buf.readString(MAX_RESOURCE_NAME_LENGTH));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(filename, MAX_RESOURCE_NAME_LENGTH);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static List<BlockPos> readBlocks(RegistryByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_REGION_BLOCKS);
        List<BlockPos> blocks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            blocks.add(buf.readBlockPos());
        }
        return sanitizeBlocks(blocks);
    }

    private static void writeBlocks(RegistryByteBuf buf, List<BlockPos> blocks) {
        List<BlockPos> sanitized = sanitizeBlocks(blocks);
        buf.writeVarInt(sanitized.size());
        for (BlockPos block : sanitized) {
            buf.writeBlockPos(block);
        }
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
            if (sanitized.size() >= MAX_REGION_BLOCKS) {
                break;
            }
        }
        return List.copyOf(sanitized);
    }

    private static String sanitizeResourceName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "background.png";
        }
        String clean = filename.replace('\\', '/');
        int slash = clean.lastIndexOf('/');
        if (slash >= 0) {
            clean = clean.substring(slash + 1);
        }
        clean = clean.replaceAll("[^a-zA-Z0-9._-]+", "_");
        if (clean.isBlank()) {
            return "background.png";
        }
        return clean.length() > MAX_RESOURCE_NAME_LENGTH ? clean.substring(0, MAX_RESOURCE_NAME_LENGTH) : clean;
    }
}

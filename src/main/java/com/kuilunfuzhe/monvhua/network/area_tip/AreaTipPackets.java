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
import java.util.List;
import java.util.UUID;

public final class AreaTipPackets {
    private static final int MAX_JSON_LENGTH = 1_000_000;
    private static final int MAX_AREA_UPDATES = 4096;

    private AreaTipPackets() {
    }

    public static void registerS2C() {
        ConfigS2C.register();
        FullSyncS2C.register();
        AreaUpdateS2C.register();
    }

    public static void registerC2S() {
        RequestConfigC2S.register();
        UpdateConfigC2S.register();
        RequestAreasC2S.register();
        PlaceAreaC2S.register();
        PlaceBoundsC2S.register();
    }

    public record AreaData(UUID id, UUID groupId, BlockPos center, int shape, int half,
                           int sizeX, int sizeY, int sizeZ, int color, BlockPos min, BlockPos max) {
        public AreaData {
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

        public AreaData(UUID id, UUID groupId, BlockPos center, int shape, int half,
                        int sizeX, int sizeY, int sizeZ, int color) {
            this(id, groupId, center, shape, half, sizeX, sizeY, sizeZ, color, null, null);
        }

        public static AreaData fromStored(AreaTipAreaStore.StoredArea area) {
            return new AreaData(area.id(), area.groupId(), area.center(), area.shape(), area.half(),
                    area.sizeX(), area.sizeY(), area.sizeZ(), area.color(), area.min(), area.max());
        }

        public AreaTipAreaStore.StoredArea toStored() {
            return new AreaTipAreaStore.StoredArea(id, groupId, center, shape, half, sizeX, sizeY, sizeZ, color, min, max);
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
            return new AreaData(id, groupId, center, shape, half, sizeX, sizeY, sizeZ, color, min, max);
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
}

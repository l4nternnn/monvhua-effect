package com.kuilunfuzhe.monvhua.network.gravity;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public final class GravityPackets {
    private GravityPackets() {
    }

    public static void registerC2S() {
        AdjustGravityC2S.register();
        SelectBlocksC2S.register();
        DebugAreaActionC2S.register();
        RequestConfigC2S.register();
        UpdateConfigC2S.register();
    }

    public static void registerS2C() {
        AreaGravityS2C.register();
        ClearAreaGravityS2C.register();
        EntityGravityS2C.register();
        ConfigS2C.register();
        EnergyS2C.register();
        ExtractPoseS2C.register();
    }

    public record AdjustGravityC2S(int entityId, double gravity) implements CustomPayload {
        public static final Id<AdjustGravityC2S> ID = new Id<>(Identifier.of("monvhua", "adjust_gravity"));
        public static final PacketCodec<RegistryByteBuf, AdjustGravityC2S> CODEC = PacketCodec.of(AdjustGravityC2S::write, AdjustGravityC2S::new);
        private static boolean registered = false;

        private AdjustGravityC2S(RegistryByteBuf buf) {
            this(buf.readInt(), buf.readDouble());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeInt(entityId);
            buf.writeDouble(gravity);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record SelectBlocksC2S(BlockPos center) implements CustomPayload {
        public static final Id<SelectBlocksC2S> ID = new Id<>(Identifier.of("monvhua", "select_gravity_blocks"));
        public static final PacketCodec<RegistryByteBuf, SelectBlocksC2S> CODEC = PacketCodec.of(SelectBlocksC2S::write, SelectBlocksC2S::new);
        private static boolean registered = false;

        private SelectBlocksC2S(RegistryByteBuf buf) {
            this(buf.readBlockPos());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(center);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record DebugAreaActionC2S(int action, BlockPos center, int shape, int half, int sizeX, int sizeY, int sizeZ, int ticks, double gravity) implements CustomPayload {
        public static final Id<DebugAreaActionC2S> ID = new Id<>(Identifier.of("monvhua", "debug_area_action"));
        public static final PacketCodec<RegistryByteBuf, DebugAreaActionC2S> CODEC = PacketCodec.of(DebugAreaActionC2S::write, DebugAreaActionC2S::new);
        private static boolean registered = false;

        private DebugAreaActionC2S(RegistryByteBuf buf) {
            this(buf.readInt(), buf.readBlockPos(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readDouble());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeInt(action);
            buf.writeBlockPos(center);
            buf.writeInt(shape);
            buf.writeInt(half);
            buf.writeInt(sizeX);
            buf.writeInt(sizeY);
            buf.writeInt(sizeZ);
            buf.writeInt(ticks);
            buf.writeDouble(gravity);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record AreaGravityS2C(UUID id, BlockPos center, int shape, int half, int sizeX, int sizeY, int sizeZ, int ticks, double gravity) implements CustomPayload {
        public static final Id<AreaGravityS2C> ID = new Id<>(Identifier.of("monvhua", "area_gravity"));
        public static final PacketCodec<RegistryByteBuf, AreaGravityS2C> CODEC = PacketCodec.of(AreaGravityS2C::write, AreaGravityS2C::new);
        private static boolean registered = false;

        private AreaGravityS2C(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readBlockPos(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readDouble());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(id);
            buf.writeBlockPos(center);
            buf.writeInt(shape);
            buf.writeInt(half);
            buf.writeInt(sizeX);
            buf.writeInt(sizeY);
            buf.writeInt(sizeZ);
            buf.writeInt(ticks);
            buf.writeDouble(gravity);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record ClearAreaGravityS2C(UUID id, BlockPos center, int extent, boolean all) implements CustomPayload {
        public static final Id<ClearAreaGravityS2C> ID = new Id<>(Identifier.of("monvhua", "clear_area_gravity"));
        public static final PacketCodec<RegistryByteBuf, ClearAreaGravityS2C> CODEC = PacketCodec.of(ClearAreaGravityS2C::write, ClearAreaGravityS2C::new);
        private static boolean registered = false;

        private ClearAreaGravityS2C(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readBlockPos(), buf.readInt(), buf.readBoolean());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(id);
            buf.writeBlockPos(center);
            buf.writeInt(extent);
            buf.writeBoolean(all);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record EntityGravityS2C(int entityId, int ticks, double force, double directionX, double directionY, double directionZ) implements CustomPayload {
        public static final Id<EntityGravityS2C> ID = new Id<>(Identifier.of("monvhua", "entity_gravity_effect"));
        public static final PacketCodec<RegistryByteBuf, EntityGravityS2C> CODEC = PacketCodec.of(EntityGravityS2C::write, EntityGravityS2C::new);
        private static boolean registered = false;

        private EntityGravityS2C(RegistryByteBuf buf) {
            this(buf.readInt(), buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeInt(entityId);
            buf.writeInt(ticks);
            buf.writeDouble(force);
            buf.writeDouble(directionX);
            buf.writeDouble(directionY);
            buf.writeDouble(directionZ);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record ConfigS2C(String json) implements CustomPayload {
        public static final Id<ConfigS2C> ID = new Id<>(Identifier.of("monvhua", "gravity_config"));
        public static final PacketCodec<RegistryByteBuf, ConfigS2C> CODEC = PacketCodec.of(ConfigS2C::write, ConfigS2C::new);
        private static boolean registered = false;

        private ConfigS2C(RegistryByteBuf buf) {
            this(buf.readString());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(json);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record EnergyS2C(double energy, double maxEnergy) implements CustomPayload {
        public static final Id<EnergyS2C> ID = new Id<>(Identifier.of("monvhua", "gravity_energy"));
        public static final PacketCodec<RegistryByteBuf, EnergyS2C> CODEC = PacketCodec.of(EnergyS2C::write, EnergyS2C::new);
        private static boolean registered = false;

        private EnergyS2C(RegistryByteBuf buf) {
            this(buf.readDouble(), buf.readDouble());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeDouble(energy);
            buf.writeDouble(maxEnergy);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record ExtractPoseS2C(int entityId, int ticks) implements CustomPayload {
        public static final Id<ExtractPoseS2C> ID = new Id<>(Identifier.of("monvhua", "gravity_extract_pose"));
        public static final PacketCodec<RegistryByteBuf, ExtractPoseS2C> CODEC = PacketCodec.of(ExtractPoseS2C::write, ExtractPoseS2C::new);
        private static boolean registered = false;

        private ExtractPoseS2C(RegistryByteBuf buf) {
            this(buf.readInt(), buf.readInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeInt(entityId);
            buf.writeInt(ticks);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record RequestConfigC2S() implements CustomPayload {
        public static final Id<RequestConfigC2S> ID = new Id<>(Identifier.of("monvhua", "request_gravity_config"));
        public static final PacketCodec<RegistryByteBuf, RequestConfigC2S> CODEC = PacketCodec.unit(new RequestConfigC2S());
        private static boolean registered = false;

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record UpdateConfigC2S(String json) implements CustomPayload {
        public static final Id<UpdateConfigC2S> ID = new Id<>(Identifier.of("monvhua", "update_gravity_config"));
        public static final PacketCodec<RegistryByteBuf, UpdateConfigC2S> CODEC = PacketCodec.of(UpdateConfigC2S::write, UpdateConfigC2S::new);
        private static boolean registered = false;

        private UpdateConfigC2S(RegistryByteBuf buf) {
            this(buf.readString());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(json);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }
    }
}

package com.kuilunfuzhe.monvhua.network.gravity;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class GravityPackets {
    private GravityPackets() {
    }

    public static void registerC2S() {
        AdjustGravityC2S.register();
    }

    public static void registerS2C() {
        AreaGravityS2C.register();
        ClearAreaGravityS2C.register();
        EntityGravityS2C.register();
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

    public record AreaGravityS2C(BlockPos center, int radius, int ticks, double gravity) implements CustomPayload {
        public static final Id<AreaGravityS2C> ID = new Id<>(Identifier.of("monvhua", "area_gravity"));
        public static final PacketCodec<RegistryByteBuf, AreaGravityS2C> CODEC = PacketCodec.of(AreaGravityS2C::write, AreaGravityS2C::new);
        private static boolean registered = false;

        private AreaGravityS2C(RegistryByteBuf buf) {
            this(buf.readBlockPos(), buf.readInt(), buf.readInt(), buf.readDouble());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(center);
            buf.writeInt(radius);
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

    public record ClearAreaGravityS2C(BlockPos center, int radius, boolean all) implements CustomPayload {
        public static final Id<ClearAreaGravityS2C> ID = new Id<>(Identifier.of("monvhua", "clear_area_gravity"));
        public static final PacketCodec<RegistryByteBuf, ClearAreaGravityS2C> CODEC = PacketCodec.of(ClearAreaGravityS2C::write, ClearAreaGravityS2C::new);
        private static boolean registered = false;

        private ClearAreaGravityS2C(RegistryByteBuf buf) {
            this(buf.readBlockPos(), buf.readInt(), buf.readBoolean());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(center);
            buf.writeInt(radius);
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

    public record EntityGravityS2C(int entityId, int ticks, double gravity) implements CustomPayload {
        public static final Id<EntityGravityS2C> ID = new Id<>(Identifier.of("monvhua", "entity_gravity_effect"));
        public static final PacketCodec<RegistryByteBuf, EntityGravityS2C> CODEC = PacketCodec.of(EntityGravityS2C::write, EntityGravityS2C::new);
        private static boolean registered = false;

        private EntityGravityS2C(RegistryByteBuf buf) {
            this(buf.readInt(), buf.readInt(), buf.readDouble());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeInt(entityId);
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
}

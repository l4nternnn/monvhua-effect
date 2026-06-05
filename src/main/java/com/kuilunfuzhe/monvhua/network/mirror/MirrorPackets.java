package com.kuilunfuzhe.monvhua.network.mirror;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class MirrorPackets {
    private MirrorPackets() {
    }

    public static void registerC2S() {
        ToggleC2S.register();
        ChargeC2S.register();
        ConfigUpdateC2S.register();
        RequestConfigC2S.register();
    }

    public static void registerS2C() {
        StateS2C.register();
        ConfigS2C.register();
        ChargeSyncS2C.register();
    }

    public record ChargeC2S(boolean start) implements CustomPayload {
        public static final Id<ChargeC2S> ID = new Id<>(Identifier.of("monvhua", "mirror_charge"));
        public static final PacketCodec<RegistryByteBuf, ChargeC2S> CODEC = PacketCodec.tuple(
                PacketCodecs.BOOLEAN, ChargeC2S::start,
                ChargeC2S::new
        );
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

    public record ChargeSyncS2C(int currentTicks, int maxTicks) implements CustomPayload {
        public static final Id<ChargeSyncS2C> ID = new Id<>(Identifier.of("monvhua", "mirror_charge_sync"));
        public static final PacketCodec<RegistryByteBuf, ChargeSyncS2C> CODEC = PacketCodec.of(
                ChargeSyncS2C::write, ChargeSyncS2C::new
        );
        private static boolean registered = false;

        private ChargeSyncS2C(RegistryByteBuf buf) {
            this(buf.readInt(), buf.readInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeInt(currentTicks);
            buf.writeInt(maxTicks);
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
        public static final Id<ConfigS2C> ID = new Id<>(Identifier.of("monvhua", "mirror_config_sync"));
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

    public record ConfigUpdateC2S(String json) implements CustomPayload {
        public static final Id<ConfigUpdateC2S> ID = new Id<>(Identifier.of("monvhua", "mirror_config_update"));
        public static final PacketCodec<RegistryByteBuf, ConfigUpdateC2S> CODEC = PacketCodec.of(ConfigUpdateC2S::write, ConfigUpdateC2S::new);
        private static boolean registered = false;

        private ConfigUpdateC2S(RegistryByteBuf buf) {
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

    public record StateS2C(
            boolean slot1Active,
            double hsX1,
            double hsY1,
            double hsZ1,
            double mapX1,
            double mapY1,
            double mapZ1,
            double radius1,
            boolean slot2Active,
            double hsX2,
            double hsY2,
            double hsZ2,
            double mapX2,
            double mapY2,
            double mapZ2,
            double radius2,
            boolean viewportActive
    ) implements CustomPayload {
        public static final Id<StateS2C> ID = new Id<>(Identifier.of("monvhua", "mirror_state"));
        public static final PacketCodec<PacketByteBuf, StateS2C> CODEC = PacketCodec.of(
                (StateS2C value, PacketByteBuf buf) -> {
                    buf.writeBoolean(value.slot1Active);
                    buf.writeDouble(value.hsX1);
                    buf.writeDouble(value.hsY1);
                    buf.writeDouble(value.hsZ1);
                    buf.writeDouble(value.mapX1);
                    buf.writeDouble(value.mapY1);
                    buf.writeDouble(value.mapZ1);
                    buf.writeDouble(value.radius1);
                    buf.writeBoolean(value.slot2Active);
                    buf.writeDouble(value.hsX2);
                    buf.writeDouble(value.hsY2);
                    buf.writeDouble(value.hsZ2);
                    buf.writeDouble(value.mapX2);
                    buf.writeDouble(value.mapY2);
                    buf.writeDouble(value.mapZ2);
                    buf.writeDouble(value.radius2);
                    buf.writeBoolean(value.viewportActive);
                },
                (PacketByteBuf buf) -> {
                    boolean s1a = buf.readBoolean();
                    double hsx1 = buf.readDouble(), hsy1 = buf.readDouble(), hsz1 = buf.readDouble();
                    double mpx1 = buf.readDouble(), mpy1 = buf.readDouble(), mpz1 = buf.readDouble();
                    double r1 = buf.readDouble();
                    boolean s2a = buf.readBoolean();
                    double hsx2 = buf.readDouble(), hsy2 = buf.readDouble(), hsz2 = buf.readDouble();
                    double mpx2 = buf.readDouble(), mpy2 = buf.readDouble(), mpz2 = buf.readDouble();
                    double r2 = buf.readDouble();
                    boolean va = buf.readBoolean();
                    return new StateS2C(
                            s1a, hsx1, hsy1, hsz1, mpx1, mpy1, mpz1, r1,
                            s2a, hsx2, hsy2, hsz2, mpx2, mpy2, mpz2, r2, va
                    );
                }
        );
        private static boolean registered = false;

        public Vec3d getHsPos1() {
            return slot1Active ? new Vec3d(hsX1, hsY1, hsZ1) : null;
        }

        public Vec3d getMapPos1() {
            return slot1Active ? new Vec3d(mapX1, mapY1, mapZ1) : null;
        }

        public Vec3d getHsPos2() {
            return slot2Active ? new Vec3d(hsX2, hsY2, hsZ2) : null;
        }

        public Vec3d getMapPos2() {
            return slot2Active ? new Vec3d(mapX2, mapY2, mapZ2) : null;
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

    public record ToggleC2S() implements CustomPayload {
        public static final Id<ToggleC2S> ID = new Id<>(Identifier.of("monvhua", "mirror_toggle"));
        public static final PacketCodec<PacketByteBuf, ToggleC2S> CODEC = PacketCodec.unit(new ToggleC2S());
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

    public record RequestConfigC2S() implements CustomPayload {
        public static final Id<RequestConfigC2S> ID = new Id<>(Identifier.of("monvhua", "request_mirror_config"));
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
}

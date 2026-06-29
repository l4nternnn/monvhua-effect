package com.kuilunfuzhe.monvhua.network.injured_and_bleeding;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class InjuredBleedingPackets {
    public static final int MAX_DROPS = 48;

    private InjuredBleedingPackets() {
    }

    public static void registerS2C() {
        BloodEffectS2C.register();
        ConfigS2C.register();
    }

    public static void registerC2S() {
        RequestConfigC2S.register();
        UpdateConfigC2S.register();
    }

    public record BloodDropData(double endX, double endY, double endZ,
                                float normalX, float normalY, float normalZ,
                                float rotationDegrees, int delayTicks, int flightTicks, int shapeSeed) {
        private BloodDropData(RegistryByteBuf buf) {
            this(
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readInt()
            );
        }

        private void write(RegistryByteBuf buf) {
            buf.writeDouble(endX);
            buf.writeDouble(endY);
            buf.writeDouble(endZ);
            buf.writeFloat(normalX);
            buf.writeFloat(normalY);
            buf.writeFloat(normalZ);
            buf.writeFloat(rotationDegrees);
            buf.writeVarInt(delayTicks);
            buf.writeVarInt(flightTicks);
            buf.writeInt(shapeSeed);
        }

        public Vec3d end() {
            return new Vec3d(endX, endY, endZ);
        }

        public Vec3d normal() {
            return new Vec3d(normalX, normalY, normalZ);
        }
    }

    public record BloodEffectS2C(int entityId, double originX, double originY, double originZ,
                                 double emitterOffsetX, double emitterOffsetY, double emitterOffsetZ,
                                 int scale, int fadeTicks, List<BloodDropData> drops) implements CustomPayload {
        public static final Id<BloodEffectS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "injured_bleeding_effect"));
        public static final PacketCodec<RegistryByteBuf, BloodEffectS2C> CODEC = PacketCodec.of(BloodEffectS2C::write, BloodEffectS2C::new);
        private static boolean registered = false;

        public BloodEffectS2C {
            drops = List.copyOf(drops.size() > MAX_DROPS ? drops.subList(0, MAX_DROPS) : drops);
        }

        private BloodEffectS2C(RegistryByteBuf buf) {
            this(
                    buf.readVarInt(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    readDrops(buf)
            );
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeDouble(originX);
            buf.writeDouble(originY);
            buf.writeDouble(originZ);
            buf.writeDouble(emitterOffsetX);
            buf.writeDouble(emitterOffsetY);
            buf.writeDouble(emitterOffsetZ);
            buf.writeVarInt(scale);
            buf.writeVarInt(fadeTicks);
            buf.writeVarInt(Math.min(drops.size(), MAX_DROPS));
            for (int i = 0; i < Math.min(drops.size(), MAX_DROPS); i++) {
                drops.get(i).write(buf);
            }
        }

        private static List<BloodDropData> readDrops(RegistryByteBuf buf) {
            int count = Math.min(Math.max(0, buf.readVarInt()), MAX_DROPS);
            List<BloodDropData> drops = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                drops.add(new BloodDropData(buf));
            }
            return drops;
        }

        public Vec3d origin() {
            return new Vec3d(originX, originY, originZ);
        }

        public Vec3d emitterOffset() {
            return new Vec3d(emitterOffsetX, emitterOffsetY, emitterOffsetZ);
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

    public record ConfigS2C(String json) implements CustomPayload {
        public static final Id<ConfigS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "injured_bleeding_config"));
        public static final PacketCodec<RegistryByteBuf, ConfigS2C> CODEC = PacketCodec.of(ConfigS2C::write, ConfigS2C::new);
        private static boolean registered = false;

        private ConfigS2C(RegistryByteBuf buf) {
            this(buf.readString());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(json);
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
        public static final Id<RequestConfigC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "request_injured_bleeding_config"));
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
        public static final Id<UpdateConfigC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "update_injured_bleeding_config"));
        public static final PacketCodec<RegistryByteBuf, UpdateConfigC2S> CODEC = PacketCodec.of(UpdateConfigC2S::write, UpdateConfigC2S::new);
        private static boolean registered = false;

        private UpdateConfigC2S(RegistryByteBuf buf) {
            this(buf.readString());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(json);
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

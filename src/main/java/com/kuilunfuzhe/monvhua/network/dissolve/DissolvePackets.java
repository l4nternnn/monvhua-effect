package com.kuilunfuzhe.monvhua.network.dissolve;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.dissolve.DissolveFeature;
import com.kuilunfuzhe.monvhua.features.dissolve.DissolveProfile;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class DissolvePackets {
    private DissolvePackets() {
    }

    public static void registerS2C() {
        StartS2C.register();
        StopS2C.register();
        FinishS2C.register();
    }

    public record StartS2C(UUID targetUuid, int entityId, int elapsedTicks, long seed,
                           DissolveProfile profile) implements CustomPayload {
        public static final Id<StartS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "dissolve_start"));
        public static final PacketCodec<RegistryByteBuf, StartS2C> CODEC = PacketCodec.of(StartS2C::write, StartS2C::new);
        private static boolean registered;

        public StartS2C {
            profile = profile == null ? DissolveProfile.DEFAULT : profile;
            elapsedTicks = Math.max(0, elapsedTicks);
        }

        private StartS2C(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readVarInt(), buf.readVarInt(), buf.readLong(), DissolveProfile.read(buf));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(targetUuid);
            buf.writeVarInt(entityId);
            buf.writeVarInt(elapsedTicks);
            buf.writeLong(seed);
            profile.write(buf);
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

    public record StopS2C(UUID targetUuid, int reasonId) implements CustomPayload {
        public static final Id<StopS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "dissolve_stop"));
        public static final PacketCodec<RegistryByteBuf, StopS2C> CODEC = PacketCodec.of(StopS2C::write, StopS2C::new);
        private static boolean registered;

        public StopS2C(UUID targetUuid, DissolveFeature.StopReason reason) {
            this(targetUuid, (reason == null ? DissolveFeature.StopReason.CANCELLED : reason).id());
        }

        private StopS2C(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(targetUuid);
            buf.writeVarInt(reasonId);
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

    public record FinishS2C(UUID targetUuid, int entityId) implements CustomPayload {
        public static final Id<FinishS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "dissolve_finish"));
        public static final PacketCodec<RegistryByteBuf, FinishS2C> CODEC = PacketCodec.of(FinishS2C::write, FinishS2C::new);
        private static boolean registered;

        private FinishS2C(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(targetUuid);
            buf.writeVarInt(entityId);
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

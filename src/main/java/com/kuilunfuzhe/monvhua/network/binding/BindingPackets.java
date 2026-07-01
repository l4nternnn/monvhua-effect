package com.kuilunfuzhe.monvhua.network.binding;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class BindingPackets {
    private BindingPackets() {
    }

    public static void registerC2S() {
        AdjustLengthC2S.register();
        StruggleC2S.register();
    }

    public static void registerS2C() {
        StateS2C.register();
    }

    public record AdjustLengthC2S(int direction) implements CustomPayload {
        public static final Id<AdjustLengthC2S> ID = new Id<>(Identifier.of("monvhua", "binding_adjust_length"));
        public static final PacketCodec<RegistryByteBuf, AdjustLengthC2S> CODEC = PacketCodec.of(AdjustLengthC2S::write, AdjustLengthC2S::new);
        private static boolean registered = false;

        private AdjustLengthC2S(RegistryByteBuf buf) {
            this(buf.readInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeInt(direction);
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

    public record StruggleC2S() implements CustomPayload {
        public static final Id<StruggleC2S> ID = new Id<>(Identifier.of("monvhua", "binding_struggle"));
        public static final PacketCodec<RegistryByteBuf, StruggleC2S> CODEC = PacketCodec.unit(new StruggleC2S());
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

    public record StateS2C(UUID holderUuid, UUID targetUuid, int holderId, int targetId, double length, boolean active) implements CustomPayload {
        public static final Id<StateS2C> ID = new Id<>(Identifier.of("monvhua", "binding_state"));
        public static final PacketCodec<RegistryByteBuf, StateS2C> CODEC = PacketCodec.of(StateS2C::write, StateS2C::new);
        private static boolean registered = false;

        private StateS2C(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readUuid(), buf.readInt(), buf.readInt(), buf.readDouble(), buf.readBoolean());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(holderUuid);
            buf.writeUuid(targetUuid);
            buf.writeInt(holderId);
            buf.writeInt(targetId);
            buf.writeDouble(length);
            buf.writeBoolean(active);
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

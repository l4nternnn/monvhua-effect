package com.kuilunfuzhe.monvhua.network.gravity;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class GravityPackets {
    private GravityPackets() {
    }

    public static void registerC2S() {
        AdjustGravityC2S.register();
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
}

package com.kuilunfuzhe.monvhua.network.secret;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class SecretPackets {
    private SecretPackets() {}

    public static void registerS2C() {
        ConfigS2C.register();
    }

    public static void registerC2S() {
        RequestConfigC2S.register();
        UpdateConfigC2S.register();
    }

    public record RequestConfigC2S() implements CustomPayload {
        public static final Id<RequestConfigC2S> ID = new Id<>(Identifier.of("monvhua", "request_secret_config"));
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

    public record ConfigS2C(String json) implements CustomPayload {
        public static final Id<ConfigS2C> ID = new Id<>(Identifier.of("monvhua", "secret_config"));
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

    public record UpdateConfigC2S(String json) implements CustomPayload {
        public static final Id<UpdateConfigC2S> ID = new Id<>(Identifier.of("monvhua", "update_secret_config"));
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
package com.kuilunfuzhe.monvhua.network.general_stage;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class GeneralStagePackets {
    private GeneralStagePackets() {
    }

    public static void registerC2S() {
        RequestGlobalConfigC2S.register();
        UpdateGlobalConfigC2S.register();
    }

    public static void registerS2C() {
        GlobalConfigS2C.register();
        PlayerStageS2C.register();
    }

    public record GlobalConfigS2C(String json) implements CustomPayload {
        public static final Id<GlobalConfigS2C> ID = new Id<>(Identifier.of("monvhua", "global_config"));
        public static final PacketCodec<PacketByteBuf, GlobalConfigS2C> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, GlobalConfigS2C::json, GlobalConfigS2C::new);
        private static boolean registered = false;

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

        public record StageConfig(int dailyLimit, int maxMarks, int minScore, int maxScore, int watchRequiredTicks, int markExpireSeconds,
                                  int parrotDailyLimit, int maxActiveParrots,
                                  double uiDrainRate, double watchDrainRate, double regenRate) {
        }
    }

    public record PlayerStageS2C(int stage) implements CustomPayload {
        public static final Id<PlayerStageS2C> ID = new Id<>(Identifier.of("monvhua", "player_stage"));
        public static final PacketCodec<PacketByteBuf, PlayerStageS2C> CODEC =
                PacketCodec.tuple(PacketCodecs.INTEGER, PlayerStageS2C::stage, PlayerStageS2C::new);
        private static boolean registered = false;

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

    public record RequestGlobalConfigC2S() implements CustomPayload {
        public static final Id<RequestGlobalConfigC2S> ID = new Id<>(Identifier.of("monvhua", "request_global_config"));
        public static final PacketCodec<RegistryByteBuf, RequestGlobalConfigC2S> CODEC =
                PacketCodec.unit(new RequestGlobalConfigC2S());
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

    public record UpdateGlobalConfigC2S(int stage, int dailyLimit, int maxMarks, int minScore, int maxScore,
                                        int watchRequiredTicks, int markExpireSeconds, int parrotDailyLimit, int maxActiveParrots,
                                        double uiDrainRate, double watchDrainRate, double regenRate) implements CustomPayload {
        public static final Id<UpdateGlobalConfigC2S> ID = new Id<>(Identifier.of("monvhua", "update_global_config"));
        public static final PacketCodec<RegistryByteBuf, UpdateGlobalConfigC2S> CODEC = PacketCodec.of(
                (packet, buf) -> {
                    buf.writeInt(packet.stage);
                    buf.writeInt(packet.dailyLimit);
                    buf.writeInt(packet.maxMarks);
                    buf.writeInt(packet.minScore);
                    buf.writeInt(packet.maxScore);
                    buf.writeInt(packet.watchRequiredTicks);
                    buf.writeInt(packet.markExpireSeconds);
                    buf.writeInt(packet.parrotDailyLimit);
                    buf.writeInt(packet.maxActiveParrots);
                    buf.writeDouble(packet.uiDrainRate);
                    buf.writeDouble(packet.watchDrainRate);
                    buf.writeDouble(packet.regenRate);
                },
                buf -> new UpdateGlobalConfigC2S(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble())
        );
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
}

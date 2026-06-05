package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public final class EvilEyesPackets {
    private EvilEyesPackets() {
    }

    public static void registerC2S() {
        MarkEntityC2S.register();
        UnmarkEntityC2S.register();
        SelectView.register();
        ExitViewC2S.register();
        RequestGlobalConfigC2S.register();
        UpdateGlobalConfigC2S.register();
        PlaceParrotC2S.register();
        AnchorDestroyC2S.register();
    }

    public static void registerS2C() {
        GlobalConfigS2C.register();
        OpenUIS2C.register();
        EntityMarkedS2C.register();
        SelectView.register();
        ForceExitViewS2C.register();
        AnchorParticleS2C.register();
        PlayerStageS2C.register();
        ExplosionParticleS2C.register();
    }

    public record AnchorDestroyC2S(UUID standId) implements CustomPayload {
        public static final Id<AnchorDestroyC2S> ID = new Id<>(Identifier.of("monvhua", "anchor_destroy"));
        public static final PacketCodec<RegistryByteBuf, AnchorDestroyC2S> CODEC = PacketCodec.of(
                (packet, buf) -> buf.writeUuid(packet.standId),
                buf -> new AnchorDestroyC2S(buf.readUuid())
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

    public record AnchorParticleS2C(UUID standId, Vec3d pos, int type) implements CustomPayload {
        public static final Id<AnchorParticleS2C> ID = new Id<>(Identifier.of("monvhua", "anchor_particle"));
        public static final PacketCodec<RegistryByteBuf, AnchorParticleS2C> CODEC = PacketCodec.of(
                (packet, buf) -> {
                    buf.writeUuid(packet.standId);
                    buf.writeDouble(packet.pos.x);
                    buf.writeDouble(packet.pos.y);
                    buf.writeDouble(packet.pos.z);
                    buf.writeInt(packet.type);
                },
                buf -> new AnchorParticleS2C(buf.readUuid(), new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readInt())
        );
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

    public record EntityMarkedS2C(UUID entityUuid, String entityName) implements CustomPayload {
        public static final Id<EntityMarkedS2C> ID = new Id<>(Identifier.of("monvhua", "entity_marked"));
        public static final PacketCodec<RegistryByteBuf, EntityMarkedS2C> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString), EntityMarkedS2C::entityUuid,
                PacketCodecs.STRING, EntityMarkedS2C::entityName,
                EntityMarkedS2C::new
        );
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

    public record ExitViewC2S() implements CustomPayload {
        public static final Id<ExitViewC2S> ID = new Id<>(Identifier.of("monvhua", "exit_view"));
        public static final PacketCodec<RegistryByteBuf, ExitViewC2S> CODEC = PacketCodec.unit(new ExitViewC2S());
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

    public record ExplosionParticleS2C(Vec3d pos) implements CustomPayload {
        public static final Id<ExplosionParticleS2C> ID = new Id<>(Identifier.of("monvhua", "explosion_particle"));
        public static final PacketCodec<RegistryByteBuf, ExplosionParticleS2C> CODEC = PacketCodec.of(
                (packet, buf) -> {
                    buf.writeDouble(packet.pos.x);
                    buf.writeDouble(packet.pos.y);
                    buf.writeDouble(packet.pos.z);
                },
                buf -> new ExplosionParticleS2C(new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()))
        );
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

    public record ForceExitViewS2C() implements CustomPayload {
        public static final Id<ForceExitViewS2C> ID = new Id<>(Identifier.of("monvhua", "force_exit_view"));
        public static final PacketCodec<RegistryByteBuf, ForceExitViewS2C> CODEC = PacketCodec.unit(new ForceExitViewS2C());
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

        public record StageConfig(int dailyLimit, int maxMarks, int minScore, int maxScore, int watchRequiredTicks, int parrotDailyLimit, int maxActiveParrots) {
        }
    }

    public record MarkEntityC2S(int entityId) implements CustomPayload {
        public static final Id<MarkEntityC2S> ID = new Id<>(Identifier.of("monvhua", "mark_entity"));
        public static final PacketCodec<RegistryByteBuf, MarkEntityC2S> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER, MarkEntityC2S::entityId,
                MarkEntityC2S::new
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

    public record OpenUIS2C() implements CustomPayload {
        public static final Id<OpenUIS2C> ID = new Id<>(Identifier.of("monvhua", "open_ui"));
        public static final PacketCodec<RegistryByteBuf, OpenUIS2C> CODEC = PacketCodec.unit(new OpenUIS2C());
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

    public record OpenUIPayload() implements CustomPayload {
        public static final CustomPayload.Id<OpenUIPayload> ID = new CustomPayload.Id<>(Identifier.of("monvhua", "open_ui"));
        public static final PacketCodec<RegistryByteBuf, OpenUIPayload> CODEC = PacketCodec.unit(new OpenUIPayload());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PlaceParrotC2S(Vec3d pos) implements CustomPayload {
        public static final Id<PlaceParrotC2S> ID = new Id<>(Identifier.of("monvhua", "place_parrot"));
        public static final PacketCodec<RegistryByteBuf, PlaceParrotC2S> CODEC = PacketCodec.of(
                (packet, buf) -> {
                    buf.writeDouble(packet.pos.x);
                    buf.writeDouble(packet.pos.y);
                    buf.writeDouble(packet.pos.z);
                },
                buf -> new PlaceParrotC2S(new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()))
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
        public static final PacketCodec<RegistryByteBuf, RequestGlobalConfigC2S> CODEC = PacketCodec.unit(new RequestGlobalConfigC2S());
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

    public record RequestViewC2S(UUID entityUuid) implements CustomPayload {
        public static final Id<RequestViewC2S> ID = new Id<>(Identifier.of("monvhua", "request_view"));
        public static final PacketCodec<RegistryByteBuf, RequestViewC2S> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString), RequestViewC2S::entityUuid,
                RequestViewC2S::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record SelectView(UUID entityUuid) implements CustomPayload {
        public static final Id<SelectView> ID = new Id<>(Identifier.of("monvhua", "select_view"));
        public static final PacketCodec<PacketByteBuf, SelectView> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString), SelectView::entityUuid,
                SelectView::new
        );
        private static boolean registered = false;

        public static void register() {
            if (!registered) {
                registered = true;
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record SetCameraEntityS2C(UUID cameraEntityUuid) implements CustomPayload {
        public static final Id<SetCameraEntityS2C> ID = new Id<>(Identifier.of("monvhua", "set_camera"));
        public static final PacketCodec<RegistryByteBuf, SetCameraEntityS2C> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString), SetCameraEntityS2C::cameraEntityUuid,
                SetCameraEntityS2C::new
        );
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

    public record UnmarkEntityC2S(UUID entityUuid) implements CustomPayload {
        public static final Id<UnmarkEntityC2S> ID = new Id<>(Identifier.of("monvhua", "unmark_entity"));
        public static final PacketCodec<RegistryByteBuf, UnmarkEntityC2S> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString), UnmarkEntityC2S::entityUuid,
                UnmarkEntityC2S::new
        );
        private static boolean registered = false;

        public static void register() {
            if (!registered) {
                registered = true;
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record UpdateGlobalConfigC2S(int stage, int dailyLimit, int maxMarks, int minScore, int maxScore, int watchRequiredTicks, int parrotDailyLimit, int maxActiveParrots) implements CustomPayload {
        public static final Id<UpdateGlobalConfigC2S> ID = new Id<>(Identifier.of("monvhua", "update_global_config"));
        public static final PacketCodec<RegistryByteBuf, UpdateGlobalConfigC2S> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER, UpdateGlobalConfigC2S::stage,
                PacketCodecs.INTEGER, UpdateGlobalConfigC2S::dailyLimit,
                PacketCodecs.INTEGER, UpdateGlobalConfigC2S::maxMarks,
                PacketCodecs.INTEGER, UpdateGlobalConfigC2S::minScore,
                PacketCodecs.INTEGER, UpdateGlobalConfigC2S::maxScore,
                PacketCodecs.INTEGER, UpdateGlobalConfigC2S::watchRequiredTicks,
                PacketCodecs.INTEGER, UpdateGlobalConfigC2S::parrotDailyLimit,
                PacketCodecs.INTEGER, UpdateGlobalConfigC2S::maxActiveParrots,
                UpdateGlobalConfigC2S::new
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

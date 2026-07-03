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
        ClairvoyanceUiStateC2S.register();
        ExitViewC2S.register();
        PlaceParrotC2S.register();
        AnchorDestroyC2S.register();
    }

    public static void registerS2C() {
        ClairvoyanceEnergyS2C.register();
        OpenUIS2C.register();
        ViewModeS2C.register();
        EntityMarkedS2C.register();
        SelectView.register();
        ForceExitViewS2C.register();
        AnchorParticleS2C.register();
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

    public record EntityMarkedS2C(UUID entityUuid, String entityName, String entityTag) implements CustomPayload {
        public static final Id<EntityMarkedS2C> ID = new Id<>(Identifier.of("monvhua", "entity_marked"));
        public static final PacketCodec<RegistryByteBuf, EntityMarkedS2C> CODEC = PacketCodec.of(EntityMarkedS2C::write, EntityMarkedS2C::new);
        private static boolean registered = false;

        public EntityMarkedS2C(UUID entityUuid, String entityName) {
            this(entityUuid, entityName, "");
        }

        private EntityMarkedS2C(RegistryByteBuf buf) {
            this(UUID.fromString(buf.readString()), buf.readString(), buf.readString());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(entityUuid.toString());
            buf.writeString(entityName == null ? "" : entityName);
            buf.writeString(entityTag == null ? "" : entityTag);
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

    public record ClairvoyanceUiStateC2S(boolean open, int previewCount, boolean hovered, boolean expanded) implements CustomPayload {
        public static final Id<ClairvoyanceUiStateC2S> ID = new Id<>(Identifier.of("monvhua", "clairvoyance_ui_state"));
        public static final PacketCodec<RegistryByteBuf, ClairvoyanceUiStateC2S> CODEC = PacketCodec.of(
                (packet, buf) -> {
                    buf.writeBoolean(packet.open);
                    buf.writeInt(packet.previewCount);
                    buf.writeBoolean(packet.hovered);
                    buf.writeBoolean(packet.expanded);
                },
                buf -> new ClairvoyanceUiStateC2S(buf.readBoolean(), buf.readInt(), buf.readBoolean(), buf.readBoolean())
        );
        private static boolean registered = false;

        public ClairvoyanceUiStateC2S(boolean open, int previewCount, boolean expanded) {
            this(open, previewCount, false, expanded);
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

    public record ClairvoyanceEnergyS2C(double currentEnergy, double maxEnergy) implements CustomPayload {
        public static final Id<ClairvoyanceEnergyS2C> ID = new Id<>(Identifier.of("monvhua", "clairvoyance_energy"));
        public static final PacketCodec<PacketByteBuf, ClairvoyanceEnergyS2C> CODEC =
                PacketCodec.tuple(PacketCodecs.DOUBLE, ClairvoyanceEnergyS2C::currentEnergy,
                        PacketCodecs.DOUBLE, ClairvoyanceEnergyS2C::maxEnergy,
                        ClairvoyanceEnergyS2C::new);
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

    public record ViewModeS2C(String mode) implements CustomPayload {
        public static final Id<ViewModeS2C> ID = new Id<>(Identifier.of("monvhua", "clairvoyance_view_mode"));
        public static final PacketCodec<PacketByteBuf, ViewModeS2C> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, ViewModeS2C::mode, ViewModeS2C::new);
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
}

package com.kuilunfuzhe.monvhua.network.activity;

import com.kuilunfuzhe.monvhua.features.activity.UiActivityBubbleSize;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class UiActivityPackets {
    private UiActivityPackets() {
    }

    public static void registerC2S() {
        StateC2S.register();
    }

    public static void registerS2C() {
        StateS2C.register();
        BubbleSizeS2C.register();
    }

    public enum Activity {
        NONE(0),
        CHAT(1),
        INVENTORY(2);

        private final int id;

        Activity(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static Activity fromId(int id) {
            return switch (id) {
                case 1 -> CHAT;
                case 2 -> INVENTORY;
                default -> NONE;
            };
        }
    }

    public record StateC2S(Activity activity, int contentId) implements CustomPayload {
        public static final Id<StateC2S> ID = new Id<>(Identifier.of("monvhua", "ui_activity_state_c2s"));
        public static final PacketCodec<RegistryByteBuf, StateC2S> CODEC =
                PacketCodec.of(StateC2S::write, StateC2S::new);
        private static boolean registered;

        public StateC2S {
            activity = activity == null ? Activity.NONE : activity;
            contentId = Math.max(0, contentId);
        }

        private StateC2S(RegistryByteBuf buf) {
            this(Activity.fromId(buf.readUnsignedByte()), buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeByte(activity.id());
            buf.writeVarInt(contentId);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        private static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record StateS2C(UUID playerUuid, Activity activity, long changedAtGameTime, int contentId)
            implements CustomPayload {
        public static final Id<StateS2C> ID = new Id<>(Identifier.of("monvhua", "ui_activity_state_s2c"));
        public static final PacketCodec<RegistryByteBuf, StateS2C> CODEC =
                PacketCodec.of(StateS2C::write, StateS2C::new);
        private static boolean registered;

        public StateS2C {
            activity = activity == null ? Activity.NONE : activity;
            contentId = Math.max(0, contentId);
        }

        private StateS2C(RegistryByteBuf buf) {
            this(buf.readUuid(), Activity.fromId(buf.readUnsignedByte()), buf.readLong(), buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(playerUuid);
            buf.writeByte(activity.id());
            buf.writeLong(changedAtGameTime);
            buf.writeVarInt(contentId);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        private static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record BubbleSizeS2C(float multiplier) implements CustomPayload {
        public static final Id<BubbleSizeS2C> ID = new Id<>(Identifier.of("monvhua", "ui_activity_bubble_size_s2c"));
        public static final PacketCodec<RegistryByteBuf, BubbleSizeS2C> CODEC =
                PacketCodec.of(BubbleSizeS2C::write, BubbleSizeS2C::new);
        private static boolean registered;

        public BubbleSizeS2C {
            multiplier = UiActivityBubbleSize.sanitize(multiplier);
        }

        private BubbleSizeS2C(RegistryByteBuf buf) {
            this(buf.readFloat());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeFloat(multiplier);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        private static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }
    }
}

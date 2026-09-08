package com.kuilunfuzhe.monvhua.network.activity;

import com.kuilunfuzhe.monvhua.features.activity.UiActivityBubbleSize;
import com.kuilunfuzhe.monvhua.features.activity.UiActivityBubbleStyle;
import com.kuilunfuzhe.monvhua.features.activity.UiActivityBubbleAvatarCatalog;
import com.kuilunfuzhe.monvhua.features.activity.UiActivityBubbleAvatarLayout;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UiActivityPackets {
    private UiActivityPackets() {
    }

    public static void registerC2S() {
        StateC2S.register();
        AvatarLayoutRequestC2S.register();
        AvatarLayoutUpdateC2S.register();
        BubbleStyleUpdateC2S.register();
    }

    public static void registerS2C() {
        StateS2C.register();
        BubbleSizeS2C.register();
        BubbleStyleS2C.register();
        AvatarS2C.register();
        AvatarLayoutStateS2C.register();
    }

    public enum Activity {
        NONE(0),
        CHAT(1),
        INVENTORY(2),
        WRITING(3),
        TRANSIENT(4),
        CONTAINER_SCENE(5);

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
                case 3 -> WRITING;
                case 4 -> TRANSIENT;
                case 5 -> CONTAINER_SCENE;
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

    public record StateS2C(UUID playerUuid, Activity activity, long shownAtGameTime,
                           long effectStartedAtGameTime, int contentId)
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
            this(buf.readUuid(), Activity.fromId(buf.readUnsignedByte()), buf.readLong(), buf.readLong(), buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(playerUuid);
            buf.writeByte(activity.id());
            buf.writeLong(shownAtGameTime);
            buf.writeLong(effectStartedAtGameTime);
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

    public record BubbleStyleS2C(int styleId) implements CustomPayload {
        public static final Id<BubbleStyleS2C> ID = new Id<>(
                Identifier.of("monvhua", "ui_activity_bubble_style_s2c"));
        public static final PacketCodec<RegistryByteBuf, BubbleStyleS2C> CODEC =
                PacketCodec.of(BubbleStyleS2C::write, BubbleStyleS2C::new);
        private static boolean registered;

        public BubbleStyleS2C {
            styleId = UiActivityBubbleStyle.fromId(styleId).ordinal();
        }

        private BubbleStyleS2C(RegistryByteBuf buf) {
            this(buf.readUnsignedByte());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeByte(styleId);
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

    public record AvatarS2C(UUID playerUuid, int avatarId) implements CustomPayload {
        public static final Id<AvatarS2C> ID = new Id<>(Identifier.of("monvhua", "ui_activity_avatar_s2c"));
        public static final PacketCodec<RegistryByteBuf, AvatarS2C> CODEC =
                PacketCodec.of(AvatarS2C::write, AvatarS2C::new);
        private static boolean registered;

        public AvatarS2C {
            avatarId = UiActivityBubbleAvatarCatalog.key(avatarId).isEmpty()
                    ? UiActivityBubbleAvatarCatalog.NONE : avatarId;
        }

        private AvatarS2C(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(playerUuid);
            buf.writeVarInt(avatarId);
        }

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }

        private static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record AvatarLayoutRequestC2S() implements CustomPayload {
        public static final Id<AvatarLayoutRequestC2S> ID = new Id<>(
                Identifier.of("monvhua", "ui_activity_avatar_layout_request_c2s"));
        public static final PacketCodec<RegistryByteBuf, AvatarLayoutRequestC2S> CODEC =
                PacketCodec.of((packet, buf) -> {}, buf -> new AvatarLayoutRequestC2S());
        private static boolean registered;

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }

        private static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record AvatarLayoutUpdateC2S(int avatarId, float centerX, float centerY, float scale)
            implements CustomPayload {
        public static final Id<AvatarLayoutUpdateC2S> ID = new Id<>(
                Identifier.of("monvhua", "ui_activity_avatar_layout_update_c2s"));
        public static final PacketCodec<RegistryByteBuf, AvatarLayoutUpdateC2S> CODEC =
                PacketCodec.of(AvatarLayoutUpdateC2S::write, AvatarLayoutUpdateC2S::new);
        private static boolean registered;

        public AvatarLayoutUpdateC2S {
            centerX = finite(centerX);
            centerY = finite(centerY);
            scale = finite(scale);
        }

        private AvatarLayoutUpdateC2S(RegistryByteBuf buf) {
            this(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(avatarId);
            buf.writeFloat(centerX);
            buf.writeFloat(centerY);
            buf.writeFloat(scale);
        }

        private static float finite(float value) { return Float.isFinite(value) ? value : 0.0F; }

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }

        private static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record AvatarLayoutStateS2C(Map<Integer, UiActivityBubbleAvatarLayout> layouts)
            implements CustomPayload {
        public static final Id<AvatarLayoutStateS2C> ID = new Id<>(
                Identifier.of("monvhua", "ui_activity_avatar_layout_state_s2c"));
        public static final PacketCodec<RegistryByteBuf, AvatarLayoutStateS2C> CODEC =
                PacketCodec.of(AvatarLayoutStateS2C::write, AvatarLayoutStateS2C::new);
        private static boolean registered;

        public AvatarLayoutStateS2C {
            Map<Integer, UiActivityBubbleAvatarLayout> copy = new LinkedHashMap<>();
            if (layouts != null) {
                layouts.forEach((id, layout) -> {
                    if (layout != null && UiActivityBubbleAvatarCatalog.key(id) != null
                            && !UiActivityBubbleAvatarCatalog.key(id).isEmpty()) {
                        copy.put(id, UiActivityBubbleAvatarLayout.sanitize(
                                layout.centerX(), layout.centerY(), layout.scale(), id));
                    }
                });
            }
            layouts = Map.copyOf(copy);
        }

        private AvatarLayoutStateS2C(RegistryByteBuf buf) {
            this(readLayouts(buf));
        }

        private void write(RegistryByteBuf buf) {
            // The catalog is data-driven; keep the cap well above the normal
            // list size so newly added avatars are not silently omitted.
            int count = Math.min(layouts.size(), 128);
            buf.writeVarInt(count);
            int written = 0;
            for (Map.Entry<Integer, UiActivityBubbleAvatarLayout> entry : layouts.entrySet()) {
                if (written++ >= count) break;
                buf.writeVarInt(entry.getKey());
                UiActivityBubbleAvatarLayout layout = entry.getValue();
                buf.writeFloat(layout.centerX());
                buf.writeFloat(layout.centerY());
                buf.writeFloat(layout.scale());
            }
        }

        private static Map<Integer, UiActivityBubbleAvatarLayout> readLayouts(RegistryByteBuf buf) {
            int count = Math.min(buf.readVarInt(), 128);
            Map<Integer, UiActivityBubbleAvatarLayout> result = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                int id = buf.readVarInt();
                float x = buf.readFloat();
                float y = buf.readFloat();
                float scale = buf.readFloat();
                if (!UiActivityBubbleAvatarCatalog.key(id).isEmpty()) {
                    result.put(id, UiActivityBubbleAvatarLayout.sanitize(x, y, scale, id));
                }
            }
            return result;
        }

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }

        private static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record BubbleStyleUpdateC2S(int styleId) implements CustomPayload {
        public static final Id<BubbleStyleUpdateC2S> ID = new Id<>(
                Identifier.of("monvhua", "ui_activity_bubble_style_update_c2s"));
        public static final PacketCodec<RegistryByteBuf, BubbleStyleUpdateC2S> CODEC =
                PacketCodec.of(BubbleStyleUpdateC2S::write, BubbleStyleUpdateC2S::new);
        private static boolean registered;

        public BubbleStyleUpdateC2S {
            styleId = UiActivityBubbleStyle.fromId(styleId).ordinal();
        }

        private BubbleStyleUpdateC2S(RegistryByteBuf buf) {
            this(buf.readUnsignedByte());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeByte(styleId);
        }

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }

        private static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }
    }
}

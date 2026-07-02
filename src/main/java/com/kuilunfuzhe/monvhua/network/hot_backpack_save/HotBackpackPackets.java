package com.kuilunfuzhe.monvhua.network.hot_backpack_save;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class HotBackpackPackets {
    public static final int MAX_JSON_LENGTH = 8 * 1024 * 1024;

    private HotBackpackPackets() {
    }

    public static void registerS2C() {
        StateS2C.register();
    }

    public static void registerC2S() {
        RequestStateC2S.register();
        SaveSpecialPlayersC2S.register();
        ApplySnapshotC2S.register();
        ApplySnapshotToSelfC2S.register();
        UndoApplyC2S.register();
        EditPreviewSlotC2S.register();
        EditOwnSlotC2S.register();
    }

    public record StateS2C(String json) implements CustomPayload {
        public static final Id<StateS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "hot_backpack_state"));
        public static final PacketCodec<RegistryByteBuf, StateS2C> CODEC = PacketCodec.of(StateS2C::write, StateS2C::new);
        private static boolean registered;

        private StateS2C(RegistryByteBuf buf) {
            this(buf.readString(MAX_JSON_LENGTH));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(json, MAX_JSON_LENGTH);
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

    public record RequestStateC2S() implements CustomPayload {
        public static final Id<RequestStateC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "request_hot_backpack_state"));
        public static final PacketCodec<RegistryByteBuf, RequestStateC2S> CODEC = PacketCodec.unit(new RequestStateC2S());
        private static boolean registered;

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

    public record SaveSpecialPlayersC2S() implements CustomPayload {
        public static final Id<SaveSpecialPlayersC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "save_hot_backpack_special_players"));
        public static final PacketCodec<RegistryByteBuf, SaveSpecialPlayersC2S> CODEC = PacketCodec.unit(new SaveSpecialPlayersC2S());
        private static boolean registered;

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

    public record ApplySnapshotC2S(UUID sourceUuid, long timestamp, UUID targetUuid) implements CustomPayload {
        public static final Id<ApplySnapshotC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "apply_hot_backpack_snapshot"));
        public static final PacketCodec<RegistryByteBuf, ApplySnapshotC2S> CODEC = PacketCodec.of(ApplySnapshotC2S::write, ApplySnapshotC2S::new);
        private static boolean registered;

        private ApplySnapshotC2S(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readLong(), buf.readUuid());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(sourceUuid);
            buf.writeLong(timestamp);
            buf.writeUuid(targetUuid);
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

    public record ApplySnapshotToSelfC2S(UUID sourceUuid, long timestamp) implements CustomPayload {
        public static final Id<ApplySnapshotToSelfC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "apply_hot_backpack_snapshot_self"));
        public static final PacketCodec<RegistryByteBuf, ApplySnapshotToSelfC2S> CODEC = PacketCodec.of(ApplySnapshotToSelfC2S::write, ApplySnapshotToSelfC2S::new);
        private static boolean registered;

        private ApplySnapshotToSelfC2S(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readLong());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(sourceUuid);
            buf.writeLong(timestamp);
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

    public record UndoApplyC2S(UUID targetUuid) implements CustomPayload {
        public static final Id<UndoApplyC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "undo_hot_backpack_apply"));
        public static final PacketCodec<RegistryByteBuf, UndoApplyC2S> CODEC = PacketCodec.of(UndoApplyC2S::write, UndoApplyC2S::new);
        private static boolean registered;

        private UndoApplyC2S(RegistryByteBuf buf) {
            this(buf.readUuid());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(targetUuid);
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

    public record EditPreviewSlotC2S(UUID sourceUuid, long timestamp, int slot, String itemNbtJson) implements CustomPayload {
        public static final Id<EditPreviewSlotC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "edit_hot_backpack_preview_slot"));
        public static final PacketCodec<RegistryByteBuf, EditPreviewSlotC2S> CODEC = PacketCodec.of(EditPreviewSlotC2S::write, EditPreviewSlotC2S::new);
        private static boolean registered;

        private EditPreviewSlotC2S(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readLong(), buf.readVarInt(), buf.readString(MAX_JSON_LENGTH));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(sourceUuid);
            buf.writeLong(timestamp);
            buf.writeVarInt(slot);
            buf.writeString(itemNbtJson == null ? "" : itemNbtJson, MAX_JSON_LENGTH);
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

    public record EditOwnSlotC2S(int slot, String itemNbtJson) implements CustomPayload {
        public static final Id<EditOwnSlotC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "edit_hot_backpack_own_slot"));
        public static final PacketCodec<RegistryByteBuf, EditOwnSlotC2S> CODEC = PacketCodec.of(EditOwnSlotC2S::write, EditOwnSlotC2S::new);
        private static boolean registered;

        private EditOwnSlotC2S(RegistryByteBuf buf) {
            this(buf.readVarInt(), buf.readString(MAX_JSON_LENGTH));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(slot);
            buf.writeString(itemNbtJson == null ? "" : itemNbtJson, MAX_JSON_LENGTH);
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

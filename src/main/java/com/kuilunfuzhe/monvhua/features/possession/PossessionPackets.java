package com.kuilunfuzhe.monvhua.features.possession;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;
import net.minecraft.util.Uuids;
import net.minecraft.util.PlayerInput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PossessionPackets {
    private PossessionPackets() {
    }

    public static void registerC2S() {
        StopC2S.register();
        InputC2S.register();
        SelectSlotC2S.register();
        ActionC2S.register();
    }

    public static void registerS2C() {
        StateS2C.register();
        VisualStateS2C.register();
        SwingS2C.register();
        HotbarS2C.register();
        InventoryS2C.register();
    }

    public record StopC2S() implements CustomPayload {
        public static final Id<StopC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "possession_stop"));
        public static final PacketCodec<PacketByteBuf, StopC2S> CODEC = PacketCodec.unit(new StopC2S());
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

    public record InputC2S(int sequence, PlayerInput input, float yaw, float pitch) implements CustomPayload {
        public static final Id<InputC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "possession_input"));
        public static final PacketCodec<PacketByteBuf, InputC2S> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER, InputC2S::sequence,
                PlayerInput.PACKET_CODEC, InputC2S::input,
                PacketCodecs.FLOAT, InputC2S::yaw,
                PacketCodecs.FLOAT, InputC2S::pitch,
                InputC2S::new
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

    public record SelectSlotC2S(int slot) implements CustomPayload {
        public static final Id<SelectSlotC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "possession_select_slot"));
        public static final PacketCodec<PacketByteBuf, SelectSlotC2S> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER, SelectSlotC2S::slot,
                SelectSlotC2S::new
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

    public record ActionC2S(int action) implements CustomPayload {
        public static final int ATTACK = 0;
        public static final int USE = 1;
        public static final int RELEASE_USE = 2;
        public static final int BREAKING = 3;
        public static final int BREAK_ABORT = 4;
        public static final int SWAP_OFFHAND = 5;
        public static final int DROP_ITEM = 6;
        public static final int DROP_STACK = 7;

        public static final Id<ActionC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "possession_action"));
        public static final PacketCodec<PacketByteBuf, ActionC2S> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER, ActionC2S::action,
                ActionC2S::new
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

    public record StateS2C(boolean active, int targetEntityId, UUID targetUuid, int lockedWandSlot) implements CustomPayload {
        public static final Id<StateS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "possession_state"));
        public static final PacketCodec<PacketByteBuf, StateS2C> CODEC = PacketCodec.tuple(
                PacketCodecs.BOOLEAN, StateS2C::active,
                PacketCodecs.INTEGER, StateS2C::targetEntityId,
                Uuids.PACKET_CODEC, StateS2C::targetUuid,
                PacketCodecs.INTEGER, StateS2C::lockedWandSlot,
                StateS2C::new
        );
        private static boolean registered = false;

        public static StateS2C inactive() {
            return new StateS2C(false, -1, new UUID(0L, 0L), -1);
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

    public record VisualStateS2C(boolean sprinting, boolean sneaking, boolean using, Hand activeHand) implements CustomPayload {
        public static final Id<VisualStateS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "possession_visual_state"));
        public static final PacketCodec<PacketByteBuf, VisualStateS2C> CODEC = PacketCodec.ofStatic(
                VisualStateS2C::write,
                VisualStateS2C::read
        );
        private static boolean registered = false;

        private static void write(PacketByteBuf buf, VisualStateS2C packet) {
            buf.writeBoolean(packet.sprinting());
            buf.writeBoolean(packet.sneaking());
            buf.writeBoolean(packet.using());
            buf.writeVarInt(packet.activeHand().ordinal());
        }

        private static VisualStateS2C read(PacketByteBuf buf) {
            boolean sprinting = buf.readBoolean();
            boolean sneaking = buf.readBoolean();
            boolean using = buf.readBoolean();
            Hand hand = buf.readVarInt() == Hand.OFF_HAND.ordinal() ? Hand.OFF_HAND : Hand.MAIN_HAND;
            return new VisualStateS2C(sprinting, sneaking, using, hand);
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

    public record SwingS2C(Hand hand) implements CustomPayload {
        public static final Id<SwingS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "possession_swing"));
        public static final PacketCodec<PacketByteBuf, SwingS2C> CODEC = PacketCodec.ofStatic(
                SwingS2C::write,
                SwingS2C::read
        );
        private static boolean registered = false;

        private static void write(PacketByteBuf buf, SwingS2C packet) {
            buf.writeVarInt(packet.hand().ordinal());
        }

        private static SwingS2C read(PacketByteBuf buf) {
            Hand hand = buf.readVarInt() == Hand.OFF_HAND.ordinal() ? Hand.OFF_HAND : Hand.MAIN_HAND;
            return new SwingS2C(hand);
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

    public record HotbarS2C(int selectedSlot, List<ItemStack> hotbar) implements CustomPayload {
        public static final Id<HotbarS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "possession_hotbar"));
        public static final PacketCodec<RegistryByteBuf, HotbarS2C> CODEC = PacketCodec.ofStatic(HotbarS2C::write, HotbarS2C::read);
        private static boolean registered = false;

        private static void write(RegistryByteBuf buf, HotbarS2C packet) {
            buf.writeVarInt(packet.selectedSlot());
            int size = Math.min(9, packet.hotbar().size());
            buf.writeVarInt(size);
            for (int i = 0; i < size; i++) {
                ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, packet.hotbar().get(i));
            }
        }

        private static HotbarS2C read(RegistryByteBuf buf) {
            int selectedSlot = buf.readVarInt();
            int size = Math.max(0, Math.min(9, buf.readVarInt()));
            List<ItemStack> hotbar = new ArrayList<>(9);
            for (int i = 0; i < size; i++) {
                hotbar.add(ItemStack.OPTIONAL_PACKET_CODEC.decode(buf));
            }
            while (hotbar.size() < 9) {
                hotbar.add(ItemStack.EMPTY);
            }
            return new HotbarS2C(selectedSlot, hotbar);
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

    public record InventoryS2C(int selectedSlot, List<ItemStack> stacks) implements CustomPayload {
        public static final int MAX_SLOTS = 43;
        public static final Id<InventoryS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "possession_inventory"));
        public static final PacketCodec<RegistryByteBuf, InventoryS2C> CODEC = PacketCodec.ofStatic(InventoryS2C::write, InventoryS2C::read);
        private static boolean registered = false;

        private static void write(RegistryByteBuf buf, InventoryS2C packet) {
            buf.writeVarInt(packet.selectedSlot());
            int size = Math.min(MAX_SLOTS, packet.stacks().size());
            buf.writeVarInt(size);
            for (int i = 0; i < size; i++) {
                ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, packet.stacks().get(i));
            }
        }

        private static InventoryS2C read(RegistryByteBuf buf) {
            int selectedSlot = buf.readVarInt();
            int size = Math.max(0, Math.min(MAX_SLOTS, buf.readVarInt()));
            List<ItemStack> stacks = new ArrayList<>(MAX_SLOTS);
            for (int i = 0; i < size; i++) {
                stacks.add(ItemStack.OPTIONAL_PACKET_CODEC.decode(buf));
            }
            while (stacks.size() < MAX_SLOTS) {
                stacks.add(ItemStack.EMPTY);
            }
            return new InventoryS2C(selectedSlot, stacks);
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

}

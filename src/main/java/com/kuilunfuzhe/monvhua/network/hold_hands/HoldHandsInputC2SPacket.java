package com.kuilunfuzhe.monvhua.network.hold_hands;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Untrusted, sequence-checked input samples used only by the hold-pair solver. */
public record HoldHandsInputC2SPacket(long clientTick, int sequence,
                                      boolean forward, boolean backward, boolean left, boolean right,
                                      boolean jump, boolean sneak, boolean sprint, float yaw)
        implements CustomPayload {
    public static final CustomPayload.Id<HoldHandsInputC2SPacket> ID =
            new CustomPayload.Id<>(Identifier.of("monvhua", "hold_hands_input"));

    public static final PacketCodec<RegistryByteBuf, HoldHandsInputC2SPacket> CODEC =
            PacketCodec.of(HoldHandsInputC2SPacket::write, HoldHandsInputC2SPacket::new);

    private static boolean registered;

    private HoldHandsInputC2SPacket(RegistryByteBuf buf) {
        this(buf.readLong(), buf.readInt(), buf.readBoolean(), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readBoolean(), buf.readFloat());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeLong(clientTick);
        buf.writeInt(sequence);
        buf.writeBoolean(forward);
        buf.writeBoolean(backward);
        buf.writeBoolean(left);
        buf.writeBoolean(right);
        buf.writeBoolean(jump);
        buf.writeBoolean(sneak);
        buf.writeBoolean(sprint);
        buf.writeFloat(yaw);
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

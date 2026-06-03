package com.kuilunfuzhe.monvhua.network.action;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ActionPoseS2CPacket(int entityId, float[] poseValues, int durationTicks) implements CustomPayload {
    public static final Id<ActionPoseS2CPacket> ID = new Id<>(Identifier.of("monvhua", "action_pose"));
    public static final PacketCodec<RegistryByteBuf, ActionPoseS2CPacket> CODEC = PacketCodec.of(
            ActionPoseS2CPacket::write, ActionPoseS2CPacket::new);

    public ActionPoseS2CPacket {
        poseValues = normalize(poseValues);
    }

    private ActionPoseS2CPacket(RegistryByteBuf buf) {
        this(buf.readVarInt(), readPose(buf), buf.readVarInt());
    }

    private static float[] readPose(RegistryByteBuf buf) {
        float[] values = new float[18];
        for (int i = 0; i < values.length; i++) {
            values[i] = buf.readFloat();
        }
        return values;
    }

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(entityId);
        for (float value : poseValues) {
            buf.writeFloat(value);
        }
        buf.writeVarInt(durationTicks);
    }

    private static float[] normalize(float[] values) {
        float[] normalized = new float[18];
        if (values != null) {
            System.arraycopy(values, 0, normalized, 0, Math.min(values.length, normalized.length));
        }
        return normalized;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static boolean registered = false;
    public static void register() {
        if (!registered) {
            PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }
}

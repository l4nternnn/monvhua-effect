package com.kuilunfuzhe.monvhua.network.bodypose;

import com.kuilunfuzhe.monvhua.config.BodyPoseDefaultsConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record UpdateBodyPoseDefaultsC2SPacket(boolean slimModel, String poseMode) implements CustomPayload {
    public static final Id<UpdateBodyPoseDefaultsC2SPacket> ID =
            new Id<>(Identifier.of("monvhua", "update_body_pose_defaults"));
    public static final PacketCodec<RegistryByteBuf, UpdateBodyPoseDefaultsC2SPacket> CODEC =
            PacketCodec.of(UpdateBodyPoseDefaultsC2SPacket::write, UpdateBodyPoseDefaultsC2SPacket::new);

    private static boolean registered = false;

    public UpdateBodyPoseDefaultsC2SPacket {
        poseMode = BodyPoseDefaultsConfig.normalizePoseMode(poseMode);
    }

    private UpdateBodyPoseDefaultsC2SPacket(RegistryByteBuf buf) {
        this(buf.readBoolean(), buf.readString());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeBoolean(this.slimModel);
        buf.writeString(this.poseMode);
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

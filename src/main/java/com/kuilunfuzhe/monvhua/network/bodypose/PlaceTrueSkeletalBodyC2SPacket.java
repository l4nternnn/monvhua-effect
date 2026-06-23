package com.kuilunfuzhe.monvhua.network.bodypose;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PlaceTrueSkeletalBodyC2SPacket(String skinName, boolean slimModel, boolean playerSkin, String playerName,
                                             List<BonePose> bones,
                                             float offsetX, float offsetY, float offsetZ,
                                             float rotationPitch, float rotationYaw, float rotationRoll,
                                             float modelScale,
                                             boolean fixedBase, double baseX, double baseY, double baseZ,
                                             boolean backpackEnabled) implements CustomPayload {
    public static final Id<PlaceTrueSkeletalBodyC2SPacket> ID = new Id<>(Identifier.of("monvhua", "place_true_skeletal_body"));
    public static final PacketCodec<RegistryByteBuf, PlaceTrueSkeletalBodyC2SPacket> CODEC =
            PacketCodec.of(PlaceTrueSkeletalBodyC2SPacket::write, PlaceTrueSkeletalBodyC2SPacket::new);
    private static final int MAX_BONES = 64;
    private static boolean registered = false;

    public PlaceTrueSkeletalBodyC2SPacket(String skinName, boolean slimModel, boolean playerSkin, String playerName,
                                          List<BonePose> bones,
                                          float offsetX, float offsetY, float offsetZ,
                                          float rotationPitch, float rotationYaw, float rotationRoll,
                                          float modelScale,
                                          boolean backpackEnabled) {
        this(skinName, slimModel, playerSkin, playerName, bones,
                offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, modelScale,
                false, 0.0D, 0.0D, 0.0D, backpackEnabled);
    }

    public PlaceTrueSkeletalBodyC2SPacket(String skinName, boolean slimModel, boolean playerSkin, String playerName,
                                          List<BonePose> bones,
                                          float offsetX, float offsetY, float offsetZ,
                                          float rotationPitch, float rotationYaw, float rotationRoll,
                                          float modelScale,
                                          double baseX, double baseY, double baseZ,
                                          boolean backpackEnabled) {
        this(skinName, slimModel, playerSkin, playerName, bones,
                offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, modelScale,
                true, baseX, baseY, baseZ, backpackEnabled);
    }

    public PlaceTrueSkeletalBodyC2SPacket {
        playerName = playerName == null ? "" : playerName;
        bones = List.copyOf(bones == null ? List.of() : bones.subList(0, Math.min(bones.size(), MAX_BONES)));
        modelScale = modelScale <= 0.0F ? 1.0F : modelScale;
    }

    private PlaceTrueSkeletalBodyC2SPacket(RegistryByteBuf buf) {
        this(buf.readString(), buf.readBoolean(), buf.readBoolean(), buf.readString(),
                readBones(buf),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readBoolean());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(this.skinName);
        buf.writeBoolean(this.slimModel);
        buf.writeBoolean(this.playerSkin);
        buf.writeString(this.playerName);
        buf.writeVarInt(Math.min(this.bones.size(), MAX_BONES));
        for (int i = 0; i < Math.min(this.bones.size(), MAX_BONES); i++) {
            BonePose pose = this.bones.get(i);
            buf.writeString(pose.name());
            buf.writeFloat(pose.pitch());
            buf.writeFloat(pose.yaw());
            buf.writeFloat(pose.roll());
            buf.writeFloat(pose.offsetX());
            buf.writeFloat(pose.offsetY());
            buf.writeFloat(pose.offsetZ());
            buf.writeFloat(pose.scale());
            buf.writeBoolean(pose.visible());
        }
        buf.writeFloat(this.offsetX);
        buf.writeFloat(this.offsetY);
        buf.writeFloat(this.offsetZ);
        buf.writeFloat(this.rotationPitch);
        buf.writeFloat(this.rotationYaw);
        buf.writeFloat(this.rotationRoll);
        buf.writeFloat(this.modelScale);
        buf.writeBoolean(this.fixedBase);
        buf.writeDouble(this.baseX);
        buf.writeDouble(this.baseY);
        buf.writeDouble(this.baseZ);
        buf.writeBoolean(this.backpackEnabled);
    }

    private static List<BonePose> readBones(RegistryByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_BONES);
        List<BonePose> bones = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            bones.add(new BonePose(buf.readString(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));
        }
        return bones;
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

    public record BonePose(String name, float pitch, float yaw, float roll, float offsetX, float offsetY, float offsetZ, float scale, boolean visible) {
        public BonePose(String name, float pitch, float yaw, float roll, float scale) {
            this(name, pitch, yaw, roll, 0.0F, 0.0F, 0.0F, scale, true);
        }

        public BonePose(String name, float pitch, float yaw, float roll, float offsetX, float offsetY, float offsetZ, float scale) {
            this(name, pitch, yaw, roll, offsetX, offsetY, offsetZ, scale, true);
        }

        public BonePose {
            name = name == null ? "" : name;
            scale = scale <= 0.0F ? 1.0F : scale;
        }
    }
}

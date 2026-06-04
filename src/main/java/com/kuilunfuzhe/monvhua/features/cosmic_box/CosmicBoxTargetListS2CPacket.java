package com.kuilunfuzhe.monvhua.features.cosmic_box;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record CosmicBoxTargetListS2CPacket(BlockPos pos, List<TargetEntry> targets) implements CustomPayload {
    public static final Id<CosmicBoxTargetListS2CPacket> ID =
            new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "cosmic_box_targets"));
    public static final PacketCodec<RegistryByteBuf, CosmicBoxTargetListS2CPacket> CODEC =
            PacketCodec.of(CosmicBoxTargetListS2CPacket::write, CosmicBoxTargetListS2CPacket::new);

    private CosmicBoxTargetListS2CPacket(RegistryByteBuf buf) {
        this(buf.readBlockPos(), readTargets(buf));
    }

    private void write(RegistryByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(targets.size());
        for (TargetEntry target : targets) {
            buf.writeUuid(target.uuid());
            buf.writeString(target.name());
            buf.writeString(target.kind());
            buf.writeDouble(target.distance());
        }
    }

    private static List<TargetEntry> readTargets(RegistryByteBuf buf) {
        int count = buf.readVarInt();
        List<TargetEntry> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID uuid = buf.readUuid();
            String name = buf.readString();
            String kind = buf.readString();
            double distance = buf.readDouble();
            targets.add(new TargetEntry(uuid, name, kind, distance));
        }
        return targets;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public record TargetEntry(UUID uuid, String name, String kind, double distance) {
    }

    private static boolean registered;

    public static void register() {
        if (!registered) {
            PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }
}

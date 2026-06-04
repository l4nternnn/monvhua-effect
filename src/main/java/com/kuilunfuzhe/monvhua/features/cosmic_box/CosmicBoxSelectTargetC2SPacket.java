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

public record CosmicBoxSelectTargetC2SPacket(BlockPos pos, List<SelectedTarget> targets) implements CustomPayload {
    public static final Id<CosmicBoxSelectTargetC2SPacket> ID =
            new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "cosmic_box_select_target"));
    public static final PacketCodec<RegistryByteBuf, CosmicBoxSelectTargetC2SPacket> CODEC =
            PacketCodec.of(CosmicBoxSelectTargetC2SPacket::write, CosmicBoxSelectTargetC2SPacket::new);

    private CosmicBoxSelectTargetC2SPacket(RegistryByteBuf buf) {
        this(buf.readBlockPos(), readTargets(buf));
    }

    private void write(RegistryByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(targets.size());
        for (SelectedTarget target : targets) {
            buf.writeUuid(target.uuid());
            buf.writeString(target.name());
        }
    }

    private static List<SelectedTarget> readTargets(RegistryByteBuf buf) {
        int count = buf.readVarInt();
        List<SelectedTarget> targets = new ArrayList<>(Math.min(count, 128));
        for (int i = 0; i < count; i++) {
            UUID uuid = buf.readUuid();
            String name = buf.readString();
            if (targets.size() < 128) {
                targets.add(new SelectedTarget(uuid, name));
            }
        }
        return targets;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public record SelectedTarget(UUID uuid, String name) {
    }

    private static boolean registered;

    public static void register() {
        if (!registered) {
            PayloadTypeRegistry.playC2S().register(ID, CODEC);
            registered = true;
        }
    }
}

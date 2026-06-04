package com.kuilunfuzhe.monvhua.features.cosmic_box;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CosmicBoxBeamVisibilityS2CPacket(boolean visible) implements CustomPayload {
    public static final Id<CosmicBoxBeamVisibilityS2CPacket> ID =
            new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "cosmic_box_beam_visibility"));
    public static final PacketCodec<RegistryByteBuf, CosmicBoxBeamVisibilityS2CPacket> CODEC =
            PacketCodec.of(CosmicBoxBeamVisibilityS2CPacket::write, CosmicBoxBeamVisibilityS2CPacket::new);

    private CosmicBoxBeamVisibilityS2CPacket(RegistryByteBuf buf) {
        this(buf.readBoolean());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeBoolean(visible);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static boolean registered;

    public static void register() {
        if (!registered) {
            PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }
}

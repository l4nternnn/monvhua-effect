package com.kuilunfuzhe.monvhua.network.commandpanel;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Display-only snapshot of the recipient, never a panel configuration upload. */
public record PanelStatusS2C(long sessionId, int requestSeq, String roleTag, boolean hasTarget, String tags, String username, boolean hasScore, int score,
        float health, float maximum, float absorption, boolean dead, String heartType,
        boolean hardcore, boolean regenerating) implements CustomPayload {
    private static boolean registered;
    public static final Id<PanelStatusS2C> ID = new Id<>(Identifier.of("monvhua", "panel_status"));
    public static final PacketCodec<RegistryByteBuf, PanelStatusS2C> CODEC = PacketCodec.of(
        (p, b) -> {
            b.writeVarLong(p.sessionId); b.writeVarInt(p.requestSeq); b.writeString(p.roleTag, 64); b.writeBoolean(p.hasTarget); b.writeString(p.tags, 8192); b.writeString(p.username, 64);
            b.writeBoolean(p.hasScore); b.writeInt(p.score);
            b.writeFloat(p.health); b.writeFloat(p.maximum); b.writeFloat(p.absorption);
            b.writeBoolean(p.dead); b.writeString(p.heartType, 32);
            b.writeBoolean(p.hardcore); b.writeBoolean(p.regenerating);
        }, b -> new PanelStatusS2C(b.readVarLong(), b.readVarInt(), b.readString(64), b.readBoolean(), b.readString(8192), b.readString(64), b.readBoolean(),
            b.readInt(), b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean(),
            b.readString(32), b.readBoolean(), b.readBoolean()));
    @Override public Id<? extends CustomPayload> getId() { return ID; }

    public static void register() {
        if (!registered) {
            PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }
}

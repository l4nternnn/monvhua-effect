package com.kuilunfuzhe.monvhua.network.glitch;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.glitch.GlitchPlane;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GlitchPlanePackets {
    private GlitchPlanePackets() {}
    public static void registerS2C() { FullSyncS2C.register(); }
    public record FullSyncS2C(List<GlitchPlane> planes) implements CustomPayload {
        public static final Id<FullSyncS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "glitch_plane_sync"));
        public static final PacketCodec<RegistryByteBuf, FullSyncS2C> CODEC = PacketCodec.of(FullSyncS2C::write, FullSyncS2C::new);
        private static boolean registered;
        public FullSyncS2C { planes = List.copyOf(planes == null ? List.of() : planes); }
        private FullSyncS2C(RegistryByteBuf buf) { this(readPlanes(buf)); }
        private void write(RegistryByteBuf buf) { buf.writeVarInt(planes.size()); for (GlitchPlane p : planes) writePlane(buf, p); }
        @Override public Id<? extends CustomPayload> getId() { return ID; }
        public static void register() { if (!registered) { PayloadTypeRegistry.playS2C().register(ID, CODEC); registered = true; } }
    }
    private static List<GlitchPlane> readPlanes(RegistryByteBuf buf) { int n = Math.min(buf.readVarInt(), 64); List<GlitchPlane> result = new ArrayList<>(n); for (int i=0;i<n;i++) result.add(readPlane(buf)); return result; }
    private static GlitchPlane readPlane(RegistryByteBuf b) { return new GlitchPlane(b.readUuid(), b.readString(48), GlitchPlane.Axis.values()[Math.clamp(b.readByte(), 0, 2)], BlockPos.fromLong(b.readLong()), BlockPos.fromLong(b.readLong()), b.readBoolean(), b.readLong(), Math.clamp(b.readFloat(), 0F, 1F), Math.clamp(b.readVarInt(), 1, 20)); }
    private static void writePlane(RegistryByteBuf b, GlitchPlane p) { b.writeUuid(p.id()); b.writeString(p.name(), 48); b.writeByte(p.normal().ordinal()); b.writeLong(p.min().asLong()); b.writeLong(p.max().asLong()); b.writeBoolean(p.enabled()); b.writeLong(p.seed()); b.writeFloat(p.density()); b.writeVarInt(p.refreshTicks()); }
}

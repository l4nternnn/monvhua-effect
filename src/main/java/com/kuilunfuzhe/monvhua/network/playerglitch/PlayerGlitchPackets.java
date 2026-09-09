package com.kuilunfuzhe.monvhua.network.playerglitch;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class PlayerGlitchPackets {
    private PlayerGlitchPackets() {}
    public static void registerS2C() { StateS2C.register(); }
    public record StateS2C(UUID target, boolean enabled, long seed, int maxFragments, float maxOffset, int revision) implements CustomPayload {
        public static final Id<StateS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "player_glitch_state"));
        public static final PacketCodec<RegistryByteBuf, StateS2C> CODEC = PacketCodec.of(StateS2C::write, StateS2C::new);
        private static boolean registered;
        public StateS2C {
            if (target == null) target = new UUID(0L, 0L);
            maxFragments = Math.clamp(maxFragments, 1, 256);
            maxOffset = Math.clamp(maxOffset, 0.0F, 0.12F);
        }
        private StateS2C(RegistryByteBuf b) { this(b.readUuid(), b.readBoolean(), b.readLong(), b.readVarInt(), b.readFloat(), b.readVarInt()); }
        private void write(RegistryByteBuf b) { b.writeUuid(target); b.writeBoolean(enabled); b.writeLong(seed); b.writeVarInt(maxFragments); b.writeFloat(maxOffset); b.writeVarInt(revision); }
        @Override public Id<? extends CustomPayload> getId() { return ID; }
        public static void register() { if (!registered) { PayloadTypeRegistry.playS2C().register(ID, CODEC); registered = true; } }
    }
}

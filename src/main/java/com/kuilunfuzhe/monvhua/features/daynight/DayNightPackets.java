package com.kuilunfuzhe.monvhua.features.daynight;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class DayNightPackets {
    private static DayNightConfig read(RegistryByteBuf b) {
        return new DayNightConfig(b.readBoolean(), b.readInt(), b.readInt(), b.readInt(), b.readInt());
    }
    private static void write(RegistryByteBuf b, DayNightConfig c) {
        b.writeBoolean(c.enabled()); b.writeInt(c.dawn()); b.writeInt(c.day()); b.writeInt(c.dusk()); b.writeInt(c.night());
    }
    // action: 0=request, 1=apply config, 2=explicitly resume daylight gamerule.
    public record Request(int action, long revision, DayNightConfig config) implements CustomPayload {
        public static final Id<Request> ID = new Id<>(Identifier.of("monvhua", "daynight_request"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.of(Request::write, Request::new);
        private Request(RegistryByteBuf b) { this(b.readInt(), b.readLong(), DayNightPackets.read(b)); }
        private void write(RegistryByteBuf b) { b.writeInt(action); b.writeLong(revision); DayNightPackets.write(b, config); }
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record State(long revision, DayNightConfig config, boolean editable, boolean daylight, String message) implements CustomPayload {
        public static final Id<State> ID = new Id<>(Identifier.of("monvhua", "daynight_state"));
        public static final PacketCodec<RegistryByteBuf, State> CODEC = PacketCodec.of(State::write, State::new);
        private State(RegistryByteBuf b) { this(b.readLong(), DayNightPackets.read(b), b.readBoolean(), b.readBoolean(), b.readString(512)); }
        private void write(RegistryByteBuf b) {
            b.writeLong(revision); DayNightPackets.write(b, config); b.writeBoolean(editable); b.writeBoolean(daylight); b.writeString(message, 512);
        }
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    public static void register() {
        PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(State.ID, State.CODEC);
    }
}

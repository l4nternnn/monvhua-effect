package com.kuilunfuzhe.monvhua.features.daynight;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.ArrayList;

public final class DayNightPackets {
    private static DayNightConfig read(RegistryByteBuf b) {
        boolean enabled=b.readBoolean();int count=b.readVarInt();
        if(count<1||count>32) throw new IllegalArgumentException("Invalid segment count");
        ArrayList<DayNightSegment> list=new ArrayList<>();
        for(int i=0;i<count;i++) list.add(new DayNightSegment(b.readString(64),b.readString(64),
                b.readInt(),b.readInt(),b.readInt(),b.readBoolean()?b.readInt():null));
        return new DayNightConfig(enabled,list);
    }
    private static void write(RegistryByteBuf b,DayNightConfig c) {
        b.writeBoolean(c.enabled());b.writeVarInt(c.segments().size());
        for(DayNightSegment s:c.segments()) {
            b.writeString(s.id(),64);b.writeString(s.name(),64);b.writeInt(s.start());b.writeInt(s.end());b.writeInt(s.seconds());
            b.writeBoolean(s.color()!=null);if(s.color()!=null) b.writeInt(s.color());
        }
    }
    // 0=read; 1=apply+save; 2=resume daylight; 3=save draft only; 4=recheck adapter.
    public record Request(int action,long revision,DayNightConfig config) implements CustomPayload {
        public static final Id<Request> ID=new Id<>(Identifier.of("monvhua","daynight_request_v3"));
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.of(Request::write,Request::new);
        private Request(RegistryByteBuf b) { this(b.readInt(),b.readLong(),DayNightPackets.read(b)); }
        private void write(RegistryByteBuf b) { b.writeInt(action);b.writeLong(revision);DayNightPackets.write(b,config); }
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record State(long revision,DayNightConfig config,DayNightConfig draft,boolean editable,boolean daylight,
                        boolean bridgeReady,boolean paused,int result,String message,String diagnostic) implements CustomPayload {
        public static final Id<State> ID=new Id<>(Identifier.of("monvhua","daynight_state_v3"));
        public static final PacketCodec<RegistryByteBuf,State> CODEC=PacketCodec.of(State::write,State::new);
        private State(RegistryByteBuf b) { this(b.readLong(),DayNightPackets.read(b),DayNightPackets.read(b),
                b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readInt(),b.readString(512),b.readString(512)); }
        private void write(RegistryByteBuf b) {
            b.writeLong(revision);DayNightPackets.write(b,config);DayNightPackets.write(b,draft);
            b.writeBoolean(editable);b.writeBoolean(daylight);b.writeBoolean(bridgeReady);b.writeBoolean(paused);
            b.writeInt(result);b.writeString(message,512);b.writeString(diagnostic,512);
        }
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    public static void register() {
        PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);
        PayloadTypeRegistry.playS2C().register(State.ID,State.CODEC);
    }
}

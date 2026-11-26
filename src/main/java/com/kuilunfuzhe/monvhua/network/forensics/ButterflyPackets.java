package com.kuilunfuzhe.monvhua.network.forensics;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Full nearby snapshot with last five actually swept tick positions; no identities or hidden source data. */
public final class ButterflyPackets {
    private ButterflyPackets(){}
    public record Flight(int id,int seed,int remaining,List<Vec3d> trail){
        public Flight {trail=List.copyOf(trail);if(id<1||remaining<0||remaining>832||trail.size()!=5
                ||trail.stream().anyMatch(p->!com.kuilunfuzhe.monvhua.features.forensics.butterfly.ButterflyClearance.finite(p)))
            throw new IllegalArgumentException("Invalid butterfly flight");}
    }
    public record Sync(String dimension,long tick,double randomRadius,List<Flight> flights) implements CustomPayload {
        public Sync {flights=List.copyOf(flights);if(flights.size()>48||!Double.isFinite(randomRadius)||randomRadius<0||randomRadius>.25)
            throw new IllegalArgumentException("Invalid butterfly snapshot");}
        // Extended TTL can exceed old clients' 640-tick validation limit.
        public static final Id<Sync> ID=new Id<>(Identifier.of("monvhua","blood_butterflies_v2"));
        public static final PacketCodec<RegistryByteBuf,Sync> CODEC=PacketCodec.of(Sync::write,Sync::new);
        private Sync(RegistryByteBuf b){this(b.readString(128),b.readLong(),b.readDouble(),read(b));}
        private static List<Flight> read(RegistryByteBuf b){
            int count=b.readVarInt();if(count<0||count>48)throw new IllegalArgumentException("Too many butterflies");
            var list=new ArrayList<Flight>();
            for(int i=0;i<count;i++){
                int id=b.readVarInt(),seed=b.readInt(),remaining=b.readVarInt();var trail=new ArrayList<Vec3d>();
                for(int j=0;j<5;j++)trail.add(new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()));
                list.add(new Flight(id,seed,remaining,trail));
            }
            return list;
        }
        private void write(RegistryByteBuf b){
            b.writeString(dimension,128);b.writeLong(tick);b.writeDouble(randomRadius);b.writeVarInt(flights.size());
            for(var f:flights){b.writeVarInt(f.id);b.writeInt(f.seed);b.writeVarInt(f.remaining);
                for(var p:f.trail){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}}
        }
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    public static void register(){PayloadTypeRegistry.playS2C().register(Sync.ID,Sync.CODEC);}
}

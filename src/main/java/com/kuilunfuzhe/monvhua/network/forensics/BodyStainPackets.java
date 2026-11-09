package com.kuilunfuzhe.monvhua.network.forensics;
import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.forensics.blood.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.*;
/** Public contamination levels only; no blood origin or life ID is sent. */
public final class BodyStainPackets {
    public record Sync(String key,int hands,int torso) implements CustomPayload {
        public static final Id<Sync> ID=new Id<>(Identifier.of("monvhua","body_stains_v1"));
        public static final PacketCodec<RegistryByteBuf,Sync> CODEC=PacketCodec.of((p,b)->{b.writeString(p.key,64);b.writeByte(p.hands);b.writeByte(p.torso);},b->new Sync(b.readString(64),b.readUnsignedByte(),b.readUnsignedByte()));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    private static final Set<ItemDisplayEntity> CORPSES=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<UUID,Map<String,Sync>> SENT=new HashMap<>();
    public static void track(ItemDisplayEntity d){if(Forensics.hasBinding(d.getItemStack()))CORPSES.add(d);}
    public static void initialize(){
        PayloadTypeRegistry.playS2C().register(Sync.ID,Sync.CODEC);
        ServerEntityEvents.ENTITY_LOAD.register((e,w)->{if(e instanceof ItemDisplayEntity d&&Forensics.hasBinding(d.getItemStack()))CORPSES.add(d);});
        ServerEntityEvents.ENTITY_UNLOAD.register((e,w)->{if(e instanceof ItemDisplayEntity d)CORPSES.remove(d);});
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->SENT.remove(h.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{CORPSES.clear();SENT.clear();});
        ServerTickEvents.END_SERVER_TICK.register(s->{
            if(s.getTicks()%10!=0)return;
            var store=BloodTransferStore.get(s);
            Map<UUID,Map<String,Sync>> wanted=new HashMap<>();
            for(var player:s.getPlayerManager().getPlayerList()){
                String life=ForensicStore.get(s).life(player.getUuid()).getString("id","");
                var packet=levels(store,"p/"+player.getUuid(),life);
                var viewers=new HashSet<>(PlayerLookup.tracking(player));viewers.add(player);
                for(var v:viewers)wanted.computeIfAbsent(v.getUuid(),id->new HashMap<>()).put(packet.key,packet);
            }
            for(var d:List.copyOf(CORPSES)){
                if(d.isRemoved())continue;
                var binding=Forensics.binding(d.getItemStack());String id=binding.getString("id","");
                String life=ForensicStore.get(s).corpse(id).getString("id","");
                var packet=levels(store,"c/"+id,life);
                for(var v:PlayerLookup.tracking(d))wanted.computeIfAbsent(v.getUuid(),key->new HashMap<>()).put(packet.key,packet);
            }
            for(var v:s.getPlayerManager().getPlayerList()){
                if(!ServerPlayNetworking.canSend(v,Sync.ID))continue;
                var previous=SENT.getOrDefault(v.getUuid(),Map.of());var next=wanted.getOrDefault(v.getUuid(),Map.of());
                for(var packet:next.values())if(!packet.equals(previous.get(packet.key)))ServerPlayNetworking.send(v,packet);
                for(String key:previous.keySet())if(!next.containsKey(key))ServerPlayNetworking.send(v,new Sync(key,0,0));
                SENT.put(v.getUuid(),next);
            }
        });
    }
    private static Sync levels(BloodTransferStore s,String key,String life){return new Sync(key,BloodTransferService.enabled()?s.visible(life+"/hands"):0,BloodTransferService.enabled()?s.visible(life+"/torso"):0);}
}

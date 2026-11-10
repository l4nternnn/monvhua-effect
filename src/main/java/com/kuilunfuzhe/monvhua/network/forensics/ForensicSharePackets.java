package com.kuilunfuzhe.monvhua.network.forensics;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Notice sent after a Showcase share was successfully opened and disclosed. */
public final class ForensicSharePackets {
    public record Notice(String corpse,String part,String evidenceId) implements CustomPayload {
        public static final Id<Notice> ID=new Id<>(Identifier.of("monvhua","forensic_share_notice"));
        public static final PacketCodec<RegistryByteBuf,Notice> CODEC=PacketCodec.of((p,b)->{b.writeString(p.corpse,64);b.writeString(p.part,32);b.writeString(p.evidenceId,64);},b->new Notice(b.readString(64),b.readString(32),b.readString(64)));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    private ForensicSharePackets(){}
    public static void register(){PayloadTypeRegistry.playS2C().register(Notice.ID,Notice.CODEC);}
    public static void send(net.minecraft.server.network.ServerPlayerEntity player,String corpse,String part,String evidence){
        if(ServerPlayNetworking.canSend(player,Notice.ID))ServerPlayNetworking.send(player,new Notice(corpse,part,evidence));
    }
}

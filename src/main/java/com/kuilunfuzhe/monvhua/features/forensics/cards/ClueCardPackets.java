package com.kuilunfuzhe.monvhua.features.forensics.cards;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.List;

public final class ClueCardPackets {
    public static final Gson JSON=new Gson();
    public record Request(String op,String id,String other,int revision,ClueCardStore.Fields fields) implements CustomPayload {
        public static final Id<Request> ID=new Id<>(Identifier.of("monvhua","clue_card_request"));
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.of((p,b)->b.writeString(JSON.toJson(p),4096),b->JSON.fromJson(b.readString(4096),Request.class));
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Detail(String id,int width,int height,int[] pixels,String body){}
    public record State(String session,boolean recording,List<ClueCardStore.Summary> cards,String latestA,String latestB,String selected,Detail detail,String notice,boolean open,boolean confirmCapture,String responseOp,boolean success) implements CustomPayload {
        public static final Id<State> ID=new Id<>(Identifier.of("monvhua","clue_card_state"));
        public static final PacketCodec<RegistryByteBuf,State> CODEC=PacketCodec.of((p,b)->b.writeString(JSON.toJson(p),524288),b->JSON.fromJson(b.readString(524288),State.class));
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    private ClueCardPackets(){}
    public static void register(){PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);PayloadTypeRegistry.playS2C().register(State.ID,State.CODEC);}
}

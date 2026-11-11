package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.util.*;

public final class ClueCardClient {
    static ClueCardPackets.State state;
    static final Map<String,ClueCardPackets.Detail> DETAILS=new HashMap<>();
    private ClueCardClient(){}
    public static void initialize(){
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{state=null;DETAILS.clear();ClueCardBodyPreview.clear();});
        ClientPlayNetworking.registerGlobalReceiver(ClueCardPackets.State.ID,(packet,context)->context.client().execute(()->{
            if(state!=null&&!state.session().equals(packet.session())){DETAILS.clear();ClueCardBodyPreview.clear();}state=packet;
            if(packet.detail()!=null)DETAILS.put(packet.detail().id(),packet.detail());
            var client=context.client();
            if(packet.open()){
                if(packet.confirmCapture())client.setScreen(new ClueCardWorkshopScreen(new ClueCardPersonalScreen(""),"",true));
                else if("board".equals(packet.responseOp()))client.setScreen(new ClueCardScreen());
                else if("craft".equals(packet.responseOp()))client.setScreen(new ClueCardWorkshopScreen(new ClueCardPersonalScreen(""),"",false));
                else client.setScreen(new ClueCardPersonalScreen(packet.selected()));
            }
            else if(client.currentScreen instanceof ClueCardScreen screen)screen.receive(packet);
            else if(client.currentScreen instanceof ClueCardPersonalScreen screen)screen.receive(packet);
            else if(client.currentScreen instanceof ClueCardWorkshopScreen screen)screen.receive(packet);
            else if(client.currentScreen instanceof ClueCardEditorScreen editor)editor.receive(packet);
            if(!packet.notice().isEmpty()&&client.player!=null)client.player.sendMessage(Text.literal(packet.notice()),false);
        }));
    }
    static ClueCardStore.Summary find(String id){return state==null?null:state.cards().stream().filter(c->c.id().equals(id)).findFirst().orElse(null);}
    static void request(String op,String id,String other,int revision,ClueCardStore.Fields fields){
        if(ClientPlayNetworking.canSend(ClueCardPackets.Request.ID))ClientPlayNetworking.send(new ClueCardPackets.Request(op,id,other,revision,fields));
    }
    static void request(String op,String id){request(op,id,"",0,null);}
    static void open(){request("list","");MinecraftClient.getInstance().setScreen(new ClueCardPersonalScreen(""));}
    static void openBoard(){request("list","");MinecraftClient.getInstance().setScreen(new ClueCardScreen());}
}

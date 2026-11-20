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
        ClueCaseManagerClient.initialize();
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{state=null;DETAILS.clear();ClueCardBodyPreview.clear();ClueCardEnvironmentPreview.clear();ClueCardItemPreview.clear();});
        ClientPlayNetworking.registerGlobalReceiver(ClueCardPackets.State.ID,(packet,context)->context.client().execute(()->{
            var previous=state;
            if(state!=null&&!state.session().equals(packet.session())){DETAILS.clear();ClueCardBodyPreview.clear();ClueCardEnvironmentPreview.clear();ClueCardItemPreview.clear();}state=packet;
            if(packet.detail()!=null)DETAILS.put(packet.detail().id(),packet.detail());
            var client=context.client();
            if(previous!=null&&previous.session().equals(packet.session())&&!packet.history().isEmpty()
                    &&(previous.history().isEmpty()||packet.history().getLast().number()>previous.history().getLast().number())){
                var comparison=packet.history().getLast();
                com.kuilunfuzhe.monvhua.features.forensics.EvidenceWorldPreview.showComparison(comparison.a(),comparison.b());
            }
            if(packet.open()){
                if(packet.confirmCapture())client.setScreen(new ClueCardWorkshopScreen(new ClueCardPersonalScreen(""),"",true));
                else if("board".equals(packet.responseOp()))client.setScreen(new ClueCardScreen());
                else if("craft".equals(packet.responseOp()))client.setScreen(new ClueCardWorkshopScreen(new ClueCardPersonalScreen(""),"",false));
                else client.setScreen(new ClueCardPersonalScreen(packet.selected()));
            }
            else if(client.currentScreen instanceof ClueCardScreen screen)screen.receive(packet);
            else if(client.currentScreen instanceof ClueCardPersonalScreen screen)screen.receive(packet);
            else if(client.currentScreen instanceof ClueCardWorkshopScreen screen)screen.receive(packet);
            else if(client.currentScreen instanceof ClueCardItemWorkshopScreen screen)screen.receive(packet);
            else if(client.currentScreen instanceof ClueCardEditorScreen editor)editor.receive(packet);
            if(!packet.notice().isEmpty()&&client.player!=null)client.player.sendMessage(Text.literal(packet.notice()),false);
        }));
    }
    static String eventText(ClueCardStore.Summary card){return (card.preciseTime()==null||card.preciseTime().isEmpty()?"":card.preciseTime()+"\n")+card.fields().event();}
    static ClueCardStore.Summary find(String id){return state==null?null:state.cards().stream().filter(c->c.id().equals(id)).findFirst().orElse(null);}
    static void request(String op,String id,String other,int revision,ClueCardStore.Fields fields){
        if(ClientPlayNetworking.canSend(ClueCardPackets.Request.ID))ClientPlayNetworking.send(new ClueCardPackets.Request(op,id,other,revision,fields));
    }
    static void request(String op,String id){request(op,id,"",0,null);}
    static void open(){request("list","");MinecraftClient.getInstance().setScreen(new ClueCardPersonalScreen(""));}
    static void openBoard(){request("list","");MinecraftClient.getInstance().setScreen(new ClueCardScreen());}
    static void openBoard(String selected){request("list","");MinecraftClient.getInstance().setScreen(new ClueCardScreen(selected));}
}

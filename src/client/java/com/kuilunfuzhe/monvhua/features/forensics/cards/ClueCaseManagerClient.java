package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

final class ClueCaseManagerClient {
    private ClueCaseManagerClient(){}
    static void initialize(){ClientPlayNetworking.registerGlobalReceiver(ClueCaseAdmin.State.ID,(packet,context)->context.client().execute(()->{
        var client=context.client();if(packet.open())client.setScreen(new ClueCaseManagerScreen(packet));
        else if(client.currentScreen instanceof ClueCaseManagerScreen screen)screen.receive(packet);
    }));}
    static void request(ClueCaseAdmin.Request request){if(ClientPlayNetworking.canSend(ClueCaseAdmin.Request.ID))ClientPlayNetworking.send(request);}
}

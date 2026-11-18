package com.kuilunfuzhe.monvhua.features.forensics;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.*;

/** Vanilla chat retains its normal send/click behaviour and returns to the evidence host. */
public final class ClueChat {
    private static Screen parent;
    private ClueChat(){}
    public static void initialize(){
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->parent=null);
    }
    /** Resolve the destination before vanilla clears the screen, avoiding a world-only frame. */
    public static Screen destination(Screen next){
        var c=MinecraftClient.getInstance();
        if(parent==null||!(c.currentScreen instanceof ChatScreen))return next;
        var previous=parent;parent=null;
        return next==null&&c.player!=null&&c.world!=null?previous:next;
    }
    public static boolean open(Screen source,int key,int scan){
        var c=MinecraftClient.getInstance();
        if(!c.options.chatKey.matchesKey(key,scan)&&!c.options.commandKey.matchesKey(key,scan))return false;
        parent=source;c.setScreen(new ChatScreen(c.options.commandKey.matchesKey(key,scan)?"/":""){
            @Override public void renderBackground(net.minecraft.client.gui.DrawContext context,int x,int y,float delta){source.renderBackground(context,-1,-1,delta);}
            @Override public void render(net.minecraft.client.gui.DrawContext context,int x,int y,float delta){
                source.render(context,-1,-1,delta);super.render(context,x,y,delta);
            }
        });return true;
    }
}

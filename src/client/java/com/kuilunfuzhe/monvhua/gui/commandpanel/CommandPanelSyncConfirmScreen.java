package com.kuilunfuzhe.monvhua.gui.commandpanel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
public final class CommandPanelSyncConfirmScreen extends Screen {
    public CommandPanelSyncConfirmScreen(){super(Text.literal("同步命令面板"));}
    @Override protected void init(){
        CommandPanelSyncManager.Pending p=CommandPanelSyncManager.pending();
        int y=height/2+10;
        addDrawableChild(ButtonWidget.builder(Text.literal("同步配置"),b->CommandPanelSyncManager.accept()).dimensions(width/2-105,y,100,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("保留我的配置"),b->CommandPanelSyncManager.reject()).dimensions(width/2+5,y,100,20).build());
    }
    @Override public void render(DrawContext c,int mx,int my,float d){c.fill(0,0,width,height,0xCC101014); CommandPanelSyncManager.Pending p=CommandPanelSyncManager.pending(); if(p!=null)c.drawCenteredTextWithShadow(textRenderer,Text.literal(p.source()+" 请求同步命令面板"),width/2,height/2-25,0xFFFFFFFF); super.render(c,mx,my,d);}
}

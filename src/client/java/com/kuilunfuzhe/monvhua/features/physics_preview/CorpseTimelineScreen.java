package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseHistoryController;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import java.util.UUID;
import org.lwjgl.glfw.GLFW;

/** An inspection overlay: never advances native state or commits a historical pose. */
public final class CorpseTimelineScreen extends Screen {
    private final CorpseHistoryController.Entry entry;
    private final MinecraftServer server;
    private final UUID player;
    private boolean dragging,released;
    public CorpseTimelineScreen(CorpseHistoryController.Entry entry,MinecraftServer server,UUID player){
        super(Text.literal("尸体姿态时间轴"));this.entry=entry;this.server=server;this.player=player;
    }
    private int left(){return Math.max(16,width/2-Math.min(240,(width-32)/2));}
    private int right(){return width-left();}
    private int barY(){return height-70;}
    @Override protected void init(){
        addDrawableChild(ButtonWidget.builder(Text.literal("返回最新"),b->entry.selectedFrame=-1).dimensions(width/2-105,height-30,100,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"),b->close()).dimensions(width/2+5,height-30,100,20).build());
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void renderBackground(DrawContext c,int x,int y,float delta){} // no blur/dim/world pause
    @Override public void tick(){if(!entry.valid||client==null||client.getServer()!=server||client.player==null||!client.player.isAlive())close();}
    private boolean available(){return entry.valid&&entry.confirmed()&&entry.first()>=0&&entry.last()>entry.first();}
    private void select(double x){if(available())entry.selectedFrame=entry.nearest(entry.first()+Math.clamp((x-left())/(right()-left()),0,1)*(entry.last()-entry.first()));}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0&&x>=left()-6&&x<=right()+6&&Math.abs(y-barY())<=10){dragging=true;select(x);return true;}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragging&&button==0){select(x);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0)dragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(available()){
            int current=entry.selectedFrame<0?entry.last():entry.selectedFrame;
            if(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_RIGHT){entry.selectedFrame=entry.adjacent(current,key==GLFW.GLFW_KEY_LEFT?-1:1);return true;}
            if(key==GLFW.GLFW_KEY_HOME){entry.selectedFrame=entry.first();return true;}
            if(key==GLFW.GLFW_KEY_END){entry.selectedFrame=-1;return true;}
        }
        return super.keyPressed(key,scan,mods);
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){
        int first=entry.first(),last=entry.last(),step=entry.selectedFrame<0?last:entry.nearest(entry.selectedFrame);
        String status=!entry.confirmed()?"等待物理帧边界冻结…":first<0?"无过程记录；仅显示原尸体姿态":String.format(java.util.Locale.ROOT,"12 帧/秒 · %.2f s · 物理帧 %d · %d–%d · %s",step/60.0,step,first,last,entry.active?"已冻结":"已稳定");
        c.drawCenteredTextWithShadow(textRenderer,Text.literal(status),width/2,barY()-25,0xFFFFFFFF);
        c.fill(left(),barY()-2,right(),barY()+3,0xFF78909C);
        int knob=left()+(last>first?(int)((right()-left())*(step-first)/(double)(last-first)):0);
        c.fill(knob-4,barY()-8,knob+5,barY()+9,available()?0xFFFFC857:0xFFAAAAAA);
        c.drawCenteredTextWithShadow(textRenderer,Text.literal("历史查看；继续将返回最新模拟帧 · "+(entry.manual?"关闭后保持手动冻结":"关闭后恢复")),width/2,barY()+17,0xFFFFFFFF);
        super.render(c,mx,my,delta);
    }
    @Override public void removed(){if(!released){released=true;server.execute(()->CorpseHistoryController.closeTimeline(entry.id,player));}}
}

package com.kuilunfuzhe.monvhua.features.physics_preview;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.UUID;

/** Remote inspection overlay. The server validates selection and owns the pause lock. */
public final class RemoteCorpseTimelineScreen extends Screen {
    private final UUID id;
    private boolean dragging,released;
    private Long pending;
    public RemoteCorpseTimelineScreen(UUID id){super(Text.literal("尸体姿态时间轴"));this.id=id;}
    private int left(){return Math.max(16,width/2-Math.min(240,(width-32)/2));}
    private int right(){return width-left();}
    private int barY(){return height-70;}
    private RemoteCorpsePhysics.Timeline state(){var s=RemoteCorpsePhysics.timeline();return s!=null&&s.id().equals(id)?s:null;}
    @Override protected void init(){
        addDrawableChild(ButtonWidget.builder(Text.literal("返回最新"),b->pending=-1L).dimensions(width/2-105,height-30,100,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"),b->close()).dimensions(width/2+5,height-30,100,20).build());
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void renderBackground(DrawContext c,int x,int y,float delta){}
    @Override public void tick(){
        if(state()==null||client.player==null||!client.player.isAlive()){close();return;}
        if(pending!=null){RemoteCorpsePhysics.timelineAction("select",id,pending);pending=null;}
    }
    private void select(double x){var s=state();if(s==null||!s.confirmed()||s.steps().length<2)return;var steps=s.steps();double target=steps[0]+Math.clamp((x-left())/(right()-left()),0,1)*(steps[steps.length-1]-steps[0]);long nearest=steps[0];for(long step:steps)if(Math.abs(step-target)<Math.abs(nearest-target))nearest=step;pending=nearest;}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0&&x>=left()-6&&x<=right()+6&&Math.abs(y-barY())<=10){dragging=true;select(x);return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragging&&button==0){select(x);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0)dragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        var s=state();if(s!=null&&s.steps().length>0){var steps=s.steps();long current=pending==null?s.selected():pending;if(current<0)current=steps[steps.length-1];
            if(key==GLFW.GLFW_KEY_HOME){pending=steps[0];return true;}
            if(key==GLFW.GLFW_KEY_END){pending=-1L;return true;}
            if(key==GLFW.GLFW_KEY_LEFT){long value=steps[0];for(long step:steps)if(step<current)value=step;pending=value;return true;}
            if(key==GLFW.GLFW_KEY_RIGHT){long value=steps[steps.length-1];for(long step:steps)if(step>current){value=step;break;}pending=value;return true;}
        }return super.keyPressed(key,scan,mods);
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){
        var s=state();if(s==null)return;var steps=s.steps();long first=steps.length==0?0:steps[0],last=steps.length==0?0:steps[steps.length-1],selected=pending==null?s.selected():pending;if(selected<0)selected=last;
        String label=!s.confirmed()?"等待服务器在物理帧边界冻结…":steps.length==0?"无过程记录；仅显示原尸体姿态":String.format(java.util.Locale.ROOT,"12 帧/秒 · %.2f s · 物理帧 %d · %d–%d",selected/60.0,selected,first,last);
        c.drawCenteredTextWithShadow(textRenderer,label,width/2,barY()-25,0xFFFFFFFF);c.fill(left(),barY()-2,right(),barY()+3,0xFF78909C);
        int knob=left()+(last>first?(int)((right()-left())*(selected-first)/(double)(last-first)):0);c.fill(knob-4,barY()-8,knob+5,barY()+9,0xFFFFC857);
        c.drawCenteredTextWithShadow(textRenderer,s.manual()?"关闭后保持手动冻结":"关闭后恢复最新模拟帧",width/2,barY()+17,0xFFFFFFFF);super.render(c,mx,my,delta);
    }
    @Override public void removed(){if(!released){released=true;RemoteCorpsePhysics.timelineAction("close",id,-1);}}
}

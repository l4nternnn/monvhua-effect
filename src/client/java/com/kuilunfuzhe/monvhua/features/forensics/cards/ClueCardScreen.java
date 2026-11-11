package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.*;

/** Public presentation and A/B comparison are one surface, separate from the bag/workshop. */
public final class ClueCardScreen extends Screen {
    private boolean manual,initialized;
    private String a="",b="",active="",latest="";
    private int leftPage,rightPage,rows,sideW,cardW,leftX,rightX,rightCardX;
    private int filter; // 0 all readable, 1 owned, 2 public
    private String notice="",group="";
    public ClueCardScreen(){super(Text.literal("公共比较板"));}
    @Override public boolean shouldPause(){return false;}
    @Override protected void init(){
        if(!initialized){initialized=true;useLatest();}
        rebuild();
    }
    void receive(ClueCardPackets.State packet){notice=packet.notice();String key=packet.latestA()+packet.latestB();if(!key.equals(latest)){latest=key;if(!manual)useLatest();}rebuild();}
    private ButtonWidget button(String text,int x,int y,int w,Runnable click){return addDrawableChild(ButtonWidget.builder(Text.literal(text),ignored->click.run()).dimensions(x,y,Math.max(18,w),20).build());}
    private List<ClueCardStore.Summary> candidates(){if(ClueCardClient.state==null)return List.of();return ClueCardClient.state.cards().stream().filter(c->filter==0||(filter==1?c.owned():c.locked())).filter(c->group.isEmpty()||c.group().equals(group)).toList();}
    private void rebuild(){
        clearChildren();
        sideW=Math.max(55,(width-32)*16/100);cardW=Math.max(60,(width-40-sideW*2)/2);leftX=12+sideW;rightCardX=leftX+cardW+8;rightX=rightCardX+cardW+8;
        button("个人线索",8,8,110,ClueCardClient::open);
        button("刷新",124,8,50,()->ClueCardClient.request("list",""));
        button(switch(filter){case 1->"自身持有";case 2->"已展示";default->"全部可用";},180,8,76,()->{filter=(filter+1)%3;leftPage=rightPage=0;rebuild();});
        button("关闭",width-52,8,44,this::close);
        rows=Math.max(1,(height-140)/26);var entries=candidates();int maxPage=Math.max(0,(entries.size()-1)/rows);leftPage=Math.min(leftPage,maxPage);rightPage=Math.min(rightPage,maxPage);
        for(int row=0;row<rows;row++){
            int li=leftPage*rows+row,ri=rightPage*rows+row;
            if(li<entries.size()){var c=entries.get(li);button(shortTitle(c),8,42+row*26,sideW-4,()->select(c.id(),true));}
            if(ri<entries.size()){var c=entries.get(ri);button(shortTitle(c),rightX,42+row*26,sideW-4,()->select(c.id(),false));}
        }
        int pageY=height-88;button("<",8,pageY,25,()->{leftPage=Math.max(0,leftPage-1);rebuild();});button(">",sideW-20,pageY,25,()->{leftPage=Math.min(maxPage,leftPage+1);rebuild();});
        button("<",rightX,pageY,25,()->{rightPage=Math.max(0,rightPage-1);rebuild();});button(">",width-32,pageY,25,()->{rightPage=Math.min(maxPage,rightPage+1);rebuild();});
        int actionsY=height-62;int actionW=(width-34)/4;
        button("出示 A",8,actionsY,actionW,()->confirm("出示后该版本将锁定，并向本次实验全体参与者开放。",()->ClueCardClient.request("publish",a))).active=ClueCardClient.find(a)!=null;
        button("出示 B",14+actionW,actionsY,actionW,()->confirm("出示后该版本将锁定，并向本次实验全体参与者开放。",()->ClueCardClient.request("publish",b))).active=ClueCardClient.find(b)!=null;
        button("展示比较",20+actionW*2,actionsY,actionW,()->confirm("将公开左右两张卡，并锁定尚未展示的版本。",()->ClueCardClient.request("compare",a,b,0,null))).active=ClueCardClient.find(a)!=null&&ClueCardClient.find(b)!=null&&!a.equals(b);
        button("最新比较",26+actionW*3,actionsY,actionW,()->{manual=false;useLatest();rebuild();});
        button("详情 A",leftX+4,height-88,cardW-12,()->{if(ClueCardClient.find(a)!=null)client.setScreen(new ClueCardReadScreen(this,a));});
        button("详情 B",rightCardX+4,height-88,cardW-12,()->{if(ClueCardClient.find(b)!=null)client.setScreen(new ClueCardReadScreen(this,b));});
    }
    private void confirm(String message,Runnable action){client.setScreen(new ConfirmScreen(ok->{client.setScreen(this);if(ok)action.run();},Text.literal("确认呈供"),Text.literal(message)));}
    private String shortTitle(ClueCardStore.Summary c){String label=(c.locked()?"已示 ":"私有 ")+c.fields().title();return textRenderer.trimToWidth(label,sideW-10);}
    private void select(String id,boolean left){if(left)a=id;else b=id;active=id;manual=true;ClueCardClient.request("view",id);rebuild();}
    private void useLatest(){var state=ClueCardClient.state;if(state==null)return;a=state.latestA();b=state.latestB();if(a.isEmpty()){a=state.cards().stream().filter(ClueCardStore.Summary::locked).reduce((x,y)->y).map(ClueCardStore.Summary::id).orElse("");}active=a;if(!a.isEmpty())ClueCardClient.request("view",a);if(!b.isEmpty())ClueCardClient.request("view",b);}
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        context.fill(0,0,width,height,0xee111820);
        drawCard(context,a,leftX+2,39,cardW-4,height-136);drawCard(context,b,rightCardX+2,39,cardW-4,height-136);
        super.render(context,mouseX,mouseY,delta);
        String status=notice.isEmpty()?"左右选卡仅影响本人；出示操作才会公开并锁定。":notice;
        context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(status,width-16),8,height-30,0xffffd488);
        context.drawTextWithShadow(textRenderer,Text.literal("实验预览：方块色块快照，不是完整现场摄影；不支持拼图。"),8,height-16,0xff9caab8);
    }
    private void drawCard(DrawContext c,String id,int x,int y,int w,int h){
        c.fill(x,y,x+w,y+h,active.equals(id)&&!id.isEmpty()?0xff334459:0xff25303e);var summary=ClueCardClient.find(id);
        if(summary==null){c.drawTextWithShadow(textRenderer,"从两侧选择卡片",x+5,y+8,0xffa9b4be);return;}
        int yy=y+6;c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(summary.fields().title(),w-10),x+5,yy,0xffffffff);yy+=13;
        line(c,"时间："+empty(summary.fields().time()),x+5,yy,w-10);yy+=12;line(c,"地点："+empty(summary.fields().place()),x+5,yy,w-10);yy+=12;
        if(yy>=y+h-8)return;int previewH=Math.min(Math.max(12,Math.min(72,h/3)),y+h-8-yy);drawSnapshot(c,ClueCardClient.DETAILS.get(id),x+5,yy,w-10,previewH);yy+=previewH+5;
        if(yy>=y+h-4)return;
        c.enableScissor(x+4,yy,x+w-4,y+h-4);
        for(var part:textRenderer.wrapLines(Text.literal("事件："+empty(summary.fields().event())),w-10)){c.drawTextWithShadow(textRenderer,part,x+5,yy,0xffe6ecf2);yy+=11;}
        if(!summary.fields().inference().isEmpty())for(var part:textRenderer.wrapLines(Text.literal("判断："+summary.fields().inference()),w-10)){c.drawTextWithShadow(textRenderer,part,x+5,yy,0xffffd488);yy+=11;}
        line(c,summary.locked()?"出示者："+summary.presenter():"尚未展示",x+5,yy+2,w-10);c.disableScissor();
    }
    private static String empty(String s){return s.isEmpty()?"未填写":s;}
    private void line(DrawContext c,String text,int x,int y,int w){c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(text,w),x,y,0xffcad5df);}
    static void drawSnapshot(DrawContext c,ClueCardPackets.Detail detail,int x,int y,int w,int h){
        c.fill(x,y,x+w,y+h,0xff111923);if(detail!=null&&!detail.body().isEmpty()){ClueCardBodyPreview.render(c,detail,x,y,w,h);return;}if(detail==null||detail.width()!=64||detail.height()!=48||detail.pixels().length!=3072)return;
        int[] pixels=detail.pixels();for(int row=0;row<48;row++){int from=0;while(from<64){int color=pixels[row*64+from],to=from+1;while(to<64&&pixels[row*64+to]==color)to++;if(color!=0)c.fill(x+from*w/64,y+row*h/48,x+to*w/64,y+(row+1)*h/48,color);from=to;}}
    }
}

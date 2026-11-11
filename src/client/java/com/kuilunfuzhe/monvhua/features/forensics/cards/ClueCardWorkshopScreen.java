package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.List;

/** Dedicated manufacture screen. A failed operation always leaves this screen and its source intact. */
final class ClueCardWorkshopScreen extends Screen {
    private final Screen parent;
    private boolean capture;
    private String source,notice="",pending="";
    private int page,rows,listWidth;
    ClueCardWorkshopScreen(Screen parent,String source,boolean capture){super(Text.literal(capture?"制作现场记录卡":"卡片制作台"));this.parent=parent;this.source=source;this.capture=capture;}
    @Override public boolean shouldPause(){return false;}
    private ButtonWidget button(String text,int x,int y,int w,Runnable action){return addDrawableChild(ButtonWidget.builder(Text.literal(text),b->action.run()).dimensions(x,y,w,20).build());}
    private List<ClueCardStore.Summary> sources(){return ClueCardClient.state==null?List.of():ClueCardClient.state.cards().stream().filter(ClueCardStore.Summary::owned).toList();}
    @Override protected void init(){rebuild();}
    void receive(ClueCardPackets.State state){
        if(!state.notice().isEmpty())notice=state.notice();
        if(!pending.isEmpty()&&pending.equals(state.responseOp())){
            pending="";
            if(state.success()&&!state.selected().isEmpty()){
                source=state.selected();capture=false;rebuild();var card=ClueCardClient.find(source);
                if(card!=null)client.setScreen(new ClueCardEditorScreen(this,card));return;
            }
        }
        rebuild();
    }
    private void submit(String op){if(!pending.isEmpty())return;pending=op;notice="正在处理，请稍候……";ClueCardClient.request(op,source);rebuild();}
    private void rebuild(){
        clearChildren();listWidth=Math.max(90,width/3);rows=Math.max(1,(height-112)/24);
        button("返回个人线索",8,8,104,this::close);
        if(capture){
            button(pending.isEmpty()?"确认制作 / 重试":"处理中",width/2-108,height-62,138,()->submit("capture")).active=pending.isEmpty();
            button("重新选点",width/2+36,height-62,80,()->{ClueCardClient.request("cancel","");client.setScreen(null);}).active=pending.isEmpty();return;
        }
        var sources=sources();int max=Math.max(0,(sources.size()-1)/rows);page=Math.min(page,max);
        for(int i=0;i<rows;i++){int index=page*rows+i;if(index<sources.size()){var card=sources.get(index);button(textRenderer.trimToWidth(card.fields().title(),listWidth-20),8,40+i*24,listWidth-12,()->{source=card.id();ClueCardClient.request("view",source);rebuild();}).active=pending.isEmpty();}}
        button("上一页",8,height-62,(listWidth-18)/2,()->{page=Math.max(0,page-1);rebuild();});button("下一页",listWidth/2+3,height-62,(listWidth-18)/2,()->{page=Math.min(max,page+1);rebuild();});
        var selected=ClueCardClient.find(source);button(pending.isEmpty()?"制作副本（消耗一张空白卡）":"正在制作……",listWidth+12,height-62,width-listWidth-24,()->submit("copy")).active=pending.isEmpty()&&selected!=null&&selected.owned()&&ClueCardClient.state!=null&&ClueCardClient.state.recording();
    }
    @Override public void close(){if(!pending.isEmpty())return;if(capture)ClueCardClient.request("cancel","");client.setScreen(parent==null?new ClueCardPersonalScreen(""):parent);}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        c.fill(0,0,width,height,0xff141f2b);
        if(capture){int y=height/2-42;c.drawCenteredTextWithShadow(textRenderer,Text.literal("记录点已固定，无需保持潜行或原准星方向"),width/2,y,0xffeef5fb);
            c.drawCenteredTextWithShadow(textRenderer,Text.literal("失败不会消耗卡片，保留当前界面并可重试"),width/2,y+18,0xffffd488);
            c.drawCenteredTextWithShadow(textRenderer,Text.literal("当前记录：直径三格的可见方块表面"),width/2,y+36,0xffb7c9db);
        }else{var selected=ClueCardClient.find(source);int x=listWidth+12,w=width-x-12;
            c.drawTextWithShadow(textRenderer,Text.literal("来源卡槽"),x,42,0xffd6e6f3);
            if(selected==null)c.drawTextWithShadow(textRenderer,"从左侧选择来源卡",x,60,0xffa9bbcc);
            else{c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(selected.fields().title(),w),x,60,0xffffffff);ClueCardScreen.drawSnapshot(c,ClueCardClient.DETAILS.get(source),x,78,w,Math.max(16,height-164));}
        }
        super.render(c,mx,my,delta);c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(notice,width-16),8,height-30,0xffffcf88);
    }
}

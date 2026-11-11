package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.*;

/** Read-only paged text; a long statement must not silently disappear behind a small card. */
final class ClueCardReadScreen extends Screen {
    private final Screen parent;private final String id;private int page;
    ClueCardReadScreen(Screen parent,String id){super(Text.literal("记录详情"));this.parent=parent;this.id=id;}
    @Override public boolean shouldPause(){return false;}
    @Override protected void init(){
        addDrawableChild(ButtonWidget.builder(Text.literal("上一页"),b->page=Math.max(0,page-1)).dimensions(12,height-30,68,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("下一页"),b->page++).dimensions(86,height-30,68,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"),b->close()).dimensions(width-80,height-30,68,20).build());
    }
    @Override public void close(){client.setScreen(parent);}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        c.fill(0,0,width,height,0xff111821);var card=ClueCardClient.find(id);
        if(card!=null){int left=width/2+8;ClueCardScreen.drawSnapshot(c,ClueCardClient.DETAILS.get(id),12,30,width/2-28,height-80);
            String text=card.fields().title()+"\n大致时间："+card.fields().time()+"\n地点："+card.fields().place()+"\n事件描述："+card.fields().event()+"\n补充判断："+card.fields().inference()+"\n"+(card.locked()?"出示者："+card.presenter():"尚未展示");
            var lines=textRenderer.wrapLines(Text.literal(text),width-left-12);int count=Math.max(1,(height-78)/12);page=Math.min(page,Math.max(0,(lines.size()-1)/count));
            for(int i=0;i<count&&page*count+i<lines.size();i++)c.drawTextWithShadow(textRenderer,lines.get(page*count+i),left,30+i*12,0xffe0e9ef);
        }
        super.render(c,mx,my,delta);
    }
}

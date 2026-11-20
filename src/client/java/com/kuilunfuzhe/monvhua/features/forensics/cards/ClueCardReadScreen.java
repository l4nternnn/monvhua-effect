package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Expanded single folio. Returning preserves its parent's pair, filters and history position. */
final class ClueCardReadScreen extends ClueFolioScreen {
    private final Screen parent;private final String id;private int page;private boolean model;
    ClueCardReadScreen(Screen parent,String id){super("展开记录");this.parent=parent;this.id=id;}
    @Override protected void init(){layout(570,370);int bw=(fw-38)/4;button("‹ 前页",fx+10,fy+fh-40,bw,()->page=Math.max(0,page-1)).active=!model;button("后页 ›",fx+16+bw,fy+fh-40,bw,()->page++).active=!model;button(model?"阅读正文":"查看预览",fx+22+2*bw,fy+fh-40,bw,()->{model=!model;clearAndInit();});if(ClueCardItemPreview.isItem(id))button("物品详情",fx+fw-90,fy+6,76,()->ClueCardItemPreview.open(this,id));button("返回",fx+28+3*bw,fy+fh-40,bw,this::close);}
    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0&&model&&ClueCardItemPreview.isItem(id)&&mx>=fx+14&&mx<fx+fw-14&&my>=fy+54&&my<fy+fh-54){ClueCardItemPreview.open(this,id);return true;}return super.mouseClicked(mx,my,button);}
    @Override public void close(){client.setScreen(parent);}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        background(c,"单份记录 · 观察内容与个人判断分别保留");var card=ClueCardClient.find(id);
        if(card!=null){
            if(model)ClueCardScreen.drawSnapshot(c,ClueCardClient.DETAILS.get(id),fx+14,fy+54,fw-28,fh-108);
            else{String text=card.fields().title()+"\n大致时间："+card.fields().time()+"\n地点："+card.fields().place()+"\n\n事件描述："+ClueCardClient.eventText(card)+"\n\n补充判断："+card.fields().inference()+"\n\n"+(card.locked()?"出示者："+card.presenter():"个人草稿 · 尚未出示");
                var lines=textRenderer.wrapLines(Text.literal(text),fw-36);int count=Math.max(1,(fh-110)/RULE_STEP);page=Math.min(page,Math.max(0,(lines.size()-1)/count));rules(c,fx+18,fy+54,fw-36,Math.min(count,lines.size()-page*count),RULE_STEP);for(int i=0;i<count&&page*count+i<lines.size();i++)c.drawText(textRenderer,lines.get(page*count+i),fx+18,fy+54+i*RULE_STEP,INK,false);}
        }
        super.render(c,mx,my,delta);if(model)ClueCardItemPreview.hover(c,id,mx,my,fx+14,fy+54,fw-28,fh-108);status(c,model?"预览仅展示记录时已获取的信息":"第 "+(page+1)+" 页");
    }
}

package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/** Read-only item details; long ordinary tooltips are paginated for screen and world parity. */
final class ClueCardItemScreen extends ClueFolioScreen {
    private final Screen parent;private final String id;private int page;
    ClueCardItemScreen(Screen parent,String id){super("物品详情");this.parent=parent;this.id=id;}
    @Override protected void init(){layout(530,340);button("返回",fx+fw-66,fy+6,54,this::close);button("上一页",fx+fw-132,fy+fh-40,56,()->page=Math.max(0,page-1));button("下一页",fx+fw-70,fy+fh-40,56,()->page++);}
    @Override public void close(){client.setScreen(parent);}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        background(c,"记录时的物品状态 · 仅供查阅");int size=Math.min(100,fw/3),x=fx+size+30,w=fw-size-44,count=Math.max(1,(fh-114)/14);
        ClueCardScreen.drawSnapshot(c,ClueCardClient.DETAILS.get(id),fx+14,fy+60,size,size);
        var lines=ClueCardItemPreview.lines(id,w);page=Math.min(page,Math.max(0,(lines.size()-1)/count));
        c.fill(x-4,fy+52,x+w+4,fy+fh-52,0xff302a22);for(int i=0;i<count&&page*count+i<lines.size();i++)c.drawText(textRenderer,lines.get(page*count+i),x,fy+56+i*14,0xffeee0bd,false);
        super.render(c,mx,my,delta);status(c,"记录数量："+ClueCardItemPreview.stack(ClueCardClient.DETAILS.get(id)).getCount()+" · 第 "+(page+1)+" 页");
    }
}

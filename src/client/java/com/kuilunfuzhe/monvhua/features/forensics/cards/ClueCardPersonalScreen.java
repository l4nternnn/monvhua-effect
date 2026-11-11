package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.List;

/** A personal catalogue with one selected record. No public comparison or manufacturing controls. */
final class ClueCardPersonalScreen extends Screen {
    private String selected,notice="";
    private int page,rows,listWidth;
    ClueCardPersonalScreen(String selected){super(Text.literal("个人线索"));this.selected=selected;}
    @Override public boolean shouldPause(){return false;}
    private ButtonWidget button(String text,int x,int y,int w,Runnable action){return addDrawableChild(ButtonWidget.builder(Text.literal(text),b->action.run()).dimensions(x,y,w,20).build());}
    private List<ClueCardStore.Summary> cards(){return ClueCardClient.state==null?List.of():ClueCardClient.state.cards().stream().filter(c->c.owned()||c.collected()).toList();}
    @Override protected void init(){rebuild();}
    void receive(ClueCardPackets.State state){notice=state.notice();rebuild();}
    private void rebuild(){
        clearChildren();listWidth=Math.max(90,width/3);rows=Math.max(1,(height-120)/24);var cards=cards();int max=Math.max(0,(cards.size()-1)/rows);page=Math.min(page,max);
        button("刷新",8,8,50,()->ClueCardClient.request("list",""));
        button("导入伤口",64,8,76,()->ClueCardClient.request("import_wounds",""));
        button("公共比较板",146,8,90,ClueCardClient::openBoard);
        button("关闭",width-52,8,44,this::close);
        for(int i=0;i<rows;i++){int index=page*rows+i;if(index<cards.size()){var card=cards.get(index);button(textRenderer.trimToWidth(card.fields().title(),listWidth-20),8,40+i*24,listWidth-12,()->{selected=card.id();ClueCardClient.request("view",selected);rebuild();});}}
        button("上一页",8,height-76,(listWidth-18)/2,()->{page=Math.max(0,page-1);rebuild();});button("下一页",listWidth/2+3,height-76,(listWidth-18)/2,()->{page=Math.min(max,page+1);rebuild();});
        var card=ClueCardClient.find(selected);int actionWidth=(width-36)/4;
        button("查看详情",8,height-50,actionWidth,()->client.setScreen(new ClueCardReadScreen(this,selected))).active=card!=null;
        button("编辑草稿",14+actionWidth,height-50,actionWidth,()->client.setScreen(new ClueCardEditorScreen(this,card))).active=card!=null&&card.owned()&&!card.locked();
        button("打开制作台",20+actionWidth*2,height-50,actionWidth,()->client.setScreen(new ClueCardWorkshopScreen(this,selected,false)));
        button("取出卡片",26+actionWidth*3,height-50,actionWidth,()->ClueCardClient.request("withdraw",selected)).active=card!=null&&card.owned()&&card.inBag();
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){
        c.fill(0,0,width,height,0xff111821);var card=ClueCardClient.find(selected);int x=listWidth+8,w=width-x-12;
        c.fill(x,38,x+w,height-62,0xff253342);
        if(card==null)c.drawTextWithShadow(textRenderer,"选择一张自有或已收录的线索",x+6,46,0xffc8d6e4);
        else{
            c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(card.fields().title(),w-12),x+6,44,0xffffffff);
            c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth("时间："+card.fields().time()+"  地点："+card.fields().place(),w-12),x+6,58,0xffcad7e3);
            int previewH=Math.max(16,Math.min(94,height-155));ClueCardScreen.drawSnapshot(c,ClueCardClient.DETAILS.get(selected),x+6,74,w-12,previewH);
            c.drawTextWithShadow(textRenderer,card.owned()?(card.locked()?"本人记录 · 已锁定":"本人记录 · 未展示草稿"):"他人呈供 · 已收录",x+6,80+previewH,0xffffd488);
        }
        super.render(c,mx,my,delta);c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(notice,width-16),8,height-20,0xffffd488);
    }
}

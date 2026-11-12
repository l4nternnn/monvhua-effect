package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;

/** Inventory selection is virtual: no item is transferred to a client-owned slot. */
final class ClueCardItemWorkshopScreen extends ClueFolioScreen {
    private final Screen parent;private int selected=-1,gx,gy,step,lw;private String pending="",notice="";private boolean ready;
    ClueCardItemWorkshopScreen(Screen parent){super("物品记录台");this.parent=parent;}
    @Override protected void init(){rebuild();}
    private void rebuild(){
        clearChildren();layout(610,380);lw=Math.max(184,fw/2);step=Math.min(25,(lw-20)/9);gx=fx+12;gy=fy+68;
        button("返回",fx+fw-66,fy+6,54,this::close).active=pending.isEmpty();
        button("重新读取",fx+lw+12,fy+fh-66,fw-lw-26,()->select(selected)).active=selected>=0&&pending.isEmpty();
        button("记录 · 耗一张空白卡",fx+lw+12,fy+fh-42,fw-lw-26,()->{pending="capture_item";ClueCardClient.request(pending,"");rebuild();}).active=ready&&pending.isEmpty()&&ClueCardClient.state!=null&&ClueCardClient.state.recording();
    }
    private void select(int slot){if(!pending.isEmpty()||slot<0)return;selected=slot;ready=false;pending="item_preview";ClueCardClient.request(pending,Integer.toString(slot));rebuild();}
    void receive(ClueCardPackets.State state){
        if(!state.notice().isEmpty())notice=state.notice();if(!pending.equals(state.responseOp()))return;
        String op=pending;pending="";if(op.equals("item_preview"))ready=state.success();
        if(op.equals("capture_item")&&!state.success())ready=false;
        if(op.equals("capture_item")&&state.success()){ready=false;var card=ClueCardClient.find(state.selected());if(card!=null){client.setScreen(new ClueCardEditorScreen(this,card));return;}}
        rebuild();
    }
    @Override public void close(){if(!pending.isEmpty())return;ClueCardClient.request("cancel_item","");client.setScreen(parent);}
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0&&pending.isEmpty())for(int slot=0;slot<=40;slot++){int row=slot/9,col=slot%9,x=gx+col*step,y=gy+row*step;if(mx>=x&&mx<x+step&&my>=y&&my<y+step){select(slot);return true;}}
        return super.mouseClicked(mx,my,button);
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){
        background(c,"一、选择背包物品  ›  二、确认记录  ›  三、填写描述");
        line(c,"背包 / 快捷栏 / 装备 / 副手",gx,gy-15,lw-20,MUTED);ItemStack hovered=ItemStack.EMPTY;
        if(client.player!=null)for(int slot=0;slot<=40;slot++){int x=gx+slot%9*step,y=gy+slot/9*step;var stack=client.player.getInventory().getStack(slot);c.fill(x,y,x+step-1,y+step-1,slot==selected?0xffd6c69c:0xffa89572);c.drawBorder(x,y,step-1,step-1,slot==selected?RED:BRASS);c.drawItem(stack,x+(step-16)/2,y+(step-16)/2);if(step>=20)c.drawStackOverlay(textRenderer,stack,x+(step-16)/2,y+(step-16)/2);if(mx>=x&&mx<x+step&&my>=y&&my<y+step)hovered=stack;}
        int x=fx+lw+12,w=fw-lw-26;ClueCardScreen.drawSnapshot(c,ready?ClueCardClient.DETAILS.get("__item"):null,x,fy+60,w,Math.max(20,fh-145));
        line(c,"只消耗空白卡，原物品保留",gx,fy+fh-66,lw-20,INK);line(c,"地点默认为记录位置，可另行填写",gx,fy+fh-48,lw-20,MUTED);
        super.render(c,mx,my,delta);status(c,notice);if(!hovered.isEmpty())c.drawItemTooltip(textRenderer,hovered,mx,my);
    }
}

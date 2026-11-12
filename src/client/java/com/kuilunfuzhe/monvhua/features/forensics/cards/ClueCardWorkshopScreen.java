package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;

/** Source selection and paid manufacture, followed by a separate draft editor. */
final class ClueCardWorkshopScreen extends ClueFolioScreen {
    private final Screen parent;private boolean capture;private String source,notice="",pending="";private int page,rows,lw;
    ClueCardWorkshopScreen(Screen parent,String source,boolean capture){super(capture?"现场记录":"线索制卡台");this.parent=parent;this.source=source;this.capture=capture;}
    private List<ClueCardStore.Summary> sources(){return ClueCardClient.state==null?List.of():ClueCardClient.state.cards().stream().filter(ClueCardStore.Summary::owned).toList();}
    @Override protected void init(){rebuild();}
    void receive(ClueCardPackets.State state){if(!state.notice().isEmpty())notice=state.notice();if(!pending.isEmpty()&&pending.equals(state.responseOp())){pending="";if(state.success()&&!state.selected().isEmpty()){source=state.selected();capture=false;rebuild();var card=ClueCardClient.find(source);if(card!=null)client.setScreen(new ClueCardEditorScreen(this,card));return;}}rebuild();}
    private void submit(String op){if(!pending.isEmpty())return;pending=op;notice="正在处理……";ClueCardClient.request(op,source);rebuild();}
    private void rebuild(){
        clearChildren();layout(590,370);lw=Math.max(110,fw*35/100);rows=Math.max(1,(fh-174)/23);
        button("返回卷宗",fx+fw-88,fy+6,76,this::close).active=pending.isEmpty();
        if(capture){button(pending.isEmpty()?"确认记录 · 消耗一张":"正在记录",fx+fw-238,fy+fh-42,140,()->submit("capture")).active=pending.isEmpty();button("重新选点",fx+fw-90,fy+fh-42,78,()->{ClueCardClient.request("cancel","");client.setScreen(null);}).active=pending.isEmpty();return;}
        button("物品记录",fx+10,fy+fh-94,lw-18,()->client.setScreen(new ClueCardItemWorkshopScreen(this))).active=pending.isEmpty();
        var entries=sources();int max=Math.max(0,(entries.size()-1)/rows);page=Math.min(page,max);
        for(int i=0;i<rows&&page*rows+i<entries.size();i++){var card=entries.get(page*rows+i);button((card.id().equals(source)?"· ":"")+card.fields().title(),fx+10,fy+65+i*23,lw-18,()->{source=card.id();ClueCardClient.request("view",source);rebuild();}).active=pending.isEmpty();}
        button("‹",fx+10,fy+fh-54,26,()->{page=Math.max(0,page-1);rebuild();});button("›",fx+42,fy+fh-54,26,()->{page=Math.min(max,page+1);rebuild();});
        var selected=ClueCardClient.find(source);
        button(pending.isEmpty()?(selected!=null&&!selected.group().isEmpty()?"制作伤情副本 · 不耗卡":"制作副本 · 消耗一张"):"正在制作",fx+lw+14,fy+fh-42,fw-lw-26,()->submit("copy")).active=pending.isEmpty()&&selected!=null&&selected.owned()&&ClueCardClient.state!=null&&ClueCardClient.state.recording();
    }
    private int blanks(){if(client==null||client.player==null||ClueCardClient.state==null)return 0;int count=0;var inventory=client.player.getInventory();for(int i=0;i<inventory.size();i++){var stack=inventory.getStack(i);if(stack.isOf(ClueCardItems.BLANK)&&ClueCardClient.state.session().equals(ClueCardItems.data(stack,"clue_session")))count+=stack.getCount();}return count;}
    @Override public void close(){if(!pending.isEmpty())return;if(capture)ClueCardClient.request("cancel","");client.setScreen(parent==null?new ClueCardPersonalScreen(""):parent);}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        background(c,capture?"一、选定位置  ›  二、记录现场  ›  三、填写说明":"一、选择原卡  ›  二、制作副本  ›  三、填写说明");
        if(capture){int ph=Math.max(24,fh-144);ClueCardScreen.drawSnapshot(c,ClueCardClient.DETAILS.get("__capture"),fx+14,fy+55,fw-28,ph);line(c,"场景与视角已固定；确认不会重新采集",fx+14,fy+fh-83,fw-28,INK);}
        else{int x=fx+lw+14,w=fw-lw-28;c.fill(fx+lw,fy+50,fx+lw+1,fy+fh-28,BRASS);line(c,"原卡目录",fx+12,fy+51,lw-24,INK);var selected=ClueCardClient.find(source);
            line(c,selected==null?"请选择原卡":selected.fields().title(),x,fy+54,w,INK);ClueCardScreen.drawSnapshot(c,ClueCardClient.DETAILS.get(source),x,fy+72,w,Math.max(16,fh-159));line(c,"原卡予以保留，副本可另行填写",x,fy+fh-74,w,MUTED);}
        line(c,"空白卡："+blanks()+" 张",fx+12,fy+fh-68,lw-20,MUTED);super.render(c,mx,my,delta);status(c,notice.isEmpty()?"同一遗体的伤情副本不另行消耗空白卡":notice);
    }
}

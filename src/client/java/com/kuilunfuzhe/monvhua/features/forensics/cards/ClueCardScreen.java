package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import java.util.*;

/** Public presentation and comparison share one board; all navigation stays viewer-local. */
public final class ClueCardScreen extends ClueFolioScreen {
    private String a="",b="",notice="",seed;
    private boolean manual,initialized,loaded;
    private int leftPage,rightPage,rows,side,cardW,ax,bx,historyIndex=-1;
    private int leftFilter,rightFilter;
    public ClueCardScreen(){this("");}
    ClueCardScreen(String seed){super("线索比较板");this.seed=seed;}
    @Override protected void init(){if(!initialized){initialized=true;useLatest();if(!seed.isEmpty()){a=seed;manual=true;fetch(a);}}rebuild();}
    void receive(ClueCardPackets.State packet){if(!packet.notice().isEmpty())notice=packet.notice();if(!loaded)useLatest();rebuild();}
    private void fetch(String id){if(!id.isEmpty())ClueCardClient.request("view",id);}
    private List<ClueCardStore.Summary> candidates(int filter){if(ClueCardClient.state==null)return List.of();return ClueCardClient.state.cards().stream().filter(c->filter==0||(filter==1?c.owned():c.locked())).toList();}
    private String filterLabel(int filter){return switch(filter){case 1->"自身持有";case 2->"已出示";default->"全部可用";};}
    private void choose(String id,boolean left){if(left)a=id;else b=id;manual=true;fetch(id);rebuild();}
    private void useLatest(){var state=ClueCardClient.state;if(state==null)return;loaded=true;a=state.latestA();b=state.latestB();if(a.isEmpty())a=state.cards().stream().filter(ClueCardStore.Summary::locked).reduce((x,y)->y).map(ClueCardStore.Summary::id).orElse("");historyIndex=state.history().size()-1;fetch(a);fetch(b);}
    private void history(int delta){var state=ClueCardClient.state;if(state==null||state.history().isEmpty())return;historyIndex=Math.max(0,Math.min(state.history().size()-1,historyIndex+delta));var entry=state.history().get(historyIndex);a=entry.a();b=entry.b();manual=true;notice="比较第 "+entry.number()+" 次 / "+entry.presenter();fetch(a);fetch(b);rebuild();}
    private void confirm(String message,Runnable action){client.setScreen(new ClueCardConfirmScreen(this,message,action));}
    private void rebuild(){
        clearChildren();layout(790,410);side=Math.max(48,fw*15/100);cardW=(fw-2*side-38)/2;ax=fx+side+14;bx=ax+cardW+10;rows=Math.max(1,(fh-156)/23);
        button("个人卷宗",fx+fw-140,fy+6,76,ClueCardClient::open);button("关闭",fx+fw-58,fy+6,48,this::close);
        button(filterLabel(leftFilter),fx+8,fy+49,side,()->{leftFilter=(leftFilter+1)%3;leftPage=0;rebuild();});
        button(filterLabel(rightFilter),fx+fw-side-8,fy+49,side,()->{rightFilter=(rightFilter+1)%3;rightPage=0;rebuild();});
        list(true);list(false);
        button("展开 A",ax,fy+fh-76,cardW,()->{if(ClueCardClient.find(a)!=null)client.setScreen(new ClueCardReadScreen(this,a));});
        button("展开 B",bx,fy+fh-76,cardW,()->{if(ClueCardClient.find(b)!=null)client.setScreen(new ClueCardReadScreen(this,b));});
        int bw=(fw-38)/4,y=fy+fh-48;
        button("出示 A",fx+10,y,bw,()->confirm("出示卡 A 后版本锁定，并向本次参与者开放。",()->ClueCardClient.request("publish",a))).active=ClueCardClient.find(a)!=null;
        button("出示 B",fx+16+bw,y,bw,()->confirm("出示卡 B 后版本锁定，并向本次参与者开放。",()->ClueCardClient.request("publish",b))).active=ClueCardClient.find(b)!=null;
        button("展示比较",fx+22+2*bw,y,bw,()->confirm("将公开左右两份记录，并锁定尚未展示的版本。",()->ClueCardClient.request("compare",a,b,0,null))).active=!a.equals(b)&&ClueCardClient.find(a)!=null&&ClueCardClient.find(b)!=null;
        button("最新比较",fx+28+3*bw,y,bw,()->{manual=false;useLatest();rebuild();});
        button("‹ 历史",ax,fy+49,(cardW*2+10)/2-3,()->history(-1));button("历史 ›",ax+(cardW*2+10)/2+3,fy+49,(cardW*2+10)/2-3,()->history(1));
    }
    private void list(boolean left){int filter=left?leftFilter:rightFilter;var entries=candidates(filter);int max=Math.max(0,(entries.size()-1)/rows);int page=Math.min(left?leftPage:rightPage,max);if(left)leftPage=page;else rightPage=page;int x=left?fx+8:fx+fw-side-8;
        for(int i=0;i<rows&&page*rows+i<entries.size();i++){var c=entries.get(page*rows+i);button(c.fields().title(),x,fy+74+i*23,side,()->choose(c.id(),left));}
        button("‹",x,fy+fh-76,(side-4)/2,()->{if(left)leftPage=Math.max(0,leftPage-1);else rightPage=Math.max(0,rightPage-1);rebuild();});button("›",x+(side+4)/2,fy+fh-76,(side-4)/2,()->{if(left)leftPage=Math.min(max,leftPage+1);else rightPage=Math.min(max,rightPage+1);rebuild();});
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){background(c,manual?"个人比较 · 选卡不会自动公开":"最新公开比较 · 各自独立浏览");drawCard(c,a,ax,fy+74,cardW,fh-158);drawCard(c,b,bx,fy+74,cardW,fh-158);super.render(c,mx,my,delta);status(c,notice);hoverItem(c,a,ax,mx,my);hoverItem(c,b,bx,mx,my);}
    private int previewHeight(){return Math.max(0,Math.min(64,fh-238));}
    private void hoverItem(DrawContext c,String id,int x,int mx,int my){ClueCardItemPreview.hover(c,id,mx,my,x+6,fy+121,cardW-12,previewHeight());}
    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0)for(boolean left:new boolean[]{true,false}){String id=left?a:b;int x=(left?ax:bx)+6;if(ClueCardItemPreview.isItem(id)&&mx>=x&&mx<x+cardW-12&&my>=fy+121&&my<fy+121+previewHeight()){ClueCardItemPreview.open(this,id);return true;}}return super.mouseClicked(mx,my,button);}
    private void drawCard(DrawContext c,String id,int x,int y,int w,int h){paper(c,x,y,w,h);var card=ClueCardClient.find(id);if(card==null){line(c,"选择记录",x+6,y+8,w-12,MUTED);return;}
        rules(c,x+6,y+6,w-12,3,14);line(c,card.fields().title(),x+6,y+6,w-12,INK);line(c,"时："+card.fields().time(),x+6,y+20,w-12,MUTED);line(c,"地："+card.fields().place(),x+6,y+34,w-12,MUTED);
        int ph=Math.max(0,Math.min(64,h-80));if(ph>8)drawSnapshot(c,ClueCardClient.DETAILS.get(id),x+6,y+47,w-12,ph);
        int ty=y+52+ph;var lines=textRenderer.wrapLines(Text.literal(ClueCardClient.eventText(card)),w-12);rules(c,x+6,ty,w-12,Math.min(lines.size(),Math.max(0,(y+h-16-ty)/11)),11);for(int i=0;i<Math.max(0,(y+h-16-ty)/11)&&i<lines.size();i++)c.drawText(textRenderer,lines.get(i),x+6,ty+i*11,INK,false);
        line(c,card.locked()?"出示："+card.presenter():"个人记录 · 尚未出示",x+6,y+h-12,w-12,RED);
    }
    static void drawSnapshot(DrawContext c,ClueCardPackets.Detail detail,int x,int y,int w,int h){
        if(w<1||h<1)return;c.fill(x,y,x+w,y+h,0xff554736);if(ClueCardItemPreview.render(c,detail,x,y,w,h))return;if(detail!=null&&ClueCardEnvironmentPreview.render(c,detail,x,y,w,h))return;if(detail!=null&&!detail.body().isEmpty()){ClueCardBodyPreview.render(c,detail,x,y,w,h);return;}if(detail==null||detail.width()!=64||detail.height()!=48||detail.pixels().length!=3072)return;
        int fittedW=Math.min(w,h*64/48),fittedH=fittedW*48/64;x+=(w-fittedW)/2;y+=(h-fittedH)/2;w=fittedW;h=fittedH;
        int[] pixels=detail.pixels();for(int row=0;row<48;row++){int from=0;while(from<64){int color=pixels[row*64+from],to=from+1;while(to<64&&pixels[row*64+to]==color)to++;if(color!=0)c.fill(x+from*w/64,y+row*h/48,x+to*w/64,y+(row+1)*h/48,color);from=to;}}
    }
}

package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.*;

/** Viewer-local navigation on the existing private 320x180 world anchor. */
public final class ClueCardWorldPanel {
    private final List<ButtonWidget> buttons=new ArrayList<>();
    private final Runnable back;
    private ClueCardPackets.State seen;
    private String a="",b="",armed="",expanded="",notice="";
    private int leftPage,rightPage,leftFilter,rightFilter,historyIndex=-1,textPage;
    private boolean itemInfo;
    private boolean manual,model,loaded;
    public ClueCardWorldPanel(Runnable back){this.back=back;ClueCardClient.request("list","");}
    private ButtonWidget button(String label,int x,int y,int w,Runnable action){var button=new ClueFolioScreen.SealButton(label,x,y,w,action);buttons.add(button);return button;}
    private List<ClueCardStore.Summary> cards(int filter){return ClueCardClient.state==null?List.of():ClueCardClient.state.cards().stream().filter(c->filter==0||(filter==1?c.owned():c.published())).toList();}
    private String filterLabel(int filter){return filter==1?"自有":filter==2?"已出示":"全部";}
    private void publish(String id){String key="publish:"+id;if(!key.equals(armed)){armed=key;rebuild();return;}ClueCardClient.request("publish",id);armed="";rebuild();}
    private void fetch(String id){if(!id.isEmpty())ClueCardClient.request("view",id);}
    private void select(String id,boolean left){if(left)a=id;else b=id;manual=true;armed="";fetch(id);rebuild();}
    private void latest(){var state=ClueCardClient.state;if(state==null)return;loaded=true;a=state.latestA();b=state.latestB();historyIndex=state.history().size()-1;manual=false;armed="";notice="";fetch(a);fetch(b);}
    /** Called by the network bridge when a new public comparison arrives. */
    public void showComparison(String left,String right){
        if(left==null||right==null||left.isEmpty()||right.isEmpty())return;
        a=left;b=right;loaded=true;manual=false;armed="";expanded="";itemInfo=false;textPage=0;notice="已跳转到最新公开比较";
        var state=ClueCardClient.state;seen=state;historyIndex=state==null?-1:state.history().size()-1;fetch(a);fetch(b);rebuild();
    }
    private void history(int delta){var state=ClueCardClient.state;if(state==null||state.history().isEmpty())return;
        historyIndex=Math.max(0,Math.min(state.history().size()-1,historyIndex+delta));var entry=state.history().get(historyIndex);
        a=entry.a();b=entry.b();manual=true;armed="";notice="比较第 "+entry.number()+" 次 · "+entry.presenter();fetch(a);fetch(b);rebuild();
    }
    private void rebuild(){
        buttons.clear();
        if(!expanded.isEmpty()){
            button("返回比较",5,4,68,()->{expanded="";rebuild();});
            button(model?"阅读正文":"记录预览",79,4,60,()->{model=!model;itemInfo=false;rebuild();});
            if(ClueCardItemPreview.isItem(expanded))button("物品信息",143,4,60,()->{model=false;itemInfo=true;textPage=0;rebuild();});
            button("上一页",207,4,50,()->{textPage=Math.max(0,textPage-1);rebuild();}).active=!model&&textPage>0;
            button("下一页",263,4,50,()->{textPage=Math.min(textPages()-1,textPage+1);rebuild();}).active=!model&&textPage<textPages()-1;
            return;
        }
        button("遗体",4,3,38,back);button("最新",46,3,38,()->{latest();rebuild();});
        button("‹ 历史",88,3,48,()->history(-1));button("历史 ›",140,3,48,()->history(1));
        button(armed.isEmpty()?"展示比较":"确认公开",192,3,66,()->{
            String key=a+":"+b;if(!key.equals(armed)){armed=key;rebuild();return;}
            ClueCardClient.request("compare",a,b,0,null);armed="";rebuild();
        }).active=!a.equals(b)&&ClueCardClient.find(a)!=null&&ClueCardClient.find(b)!=null;
        button("卷宗",262,3,54,ClueCardClient::open);
        var left=cards(leftFilter);var right=cards(rightFilter);int lm=Math.max(0,(left.size()-1)/4),rm=Math.max(0,(right.size()-1)/4);leftPage=Math.min(leftPage,lm);rightPage=Math.min(rightPage,rm);
        button(filterLabel(leftFilter),4,25,49,()->{leftFilter=(leftFilter+1)%3;leftPage=0;rebuild();});
        button(filterLabel(rightFilter),267,25,49,()->{rightFilter=(rightFilter+1)%3;rightPage=0;rebuild();});
        for(int i=0;i<4;i++){int l=leftPage*4+i,r=rightPage*4+i;
            if(l<left.size()){var card=left.get(l);button(shortTitle(card),4,47+i*22,49,()->select(card.id(),true));}
            if(r<right.size()){var card=right.get(r);button(shortTitle(card),267,47+i*22,49,()->select(card.id(),false));}
        }
        button("‹",4,140,22,()->{leftPage=Math.max(0,leftPage-1);rebuild();});button("›",30,140,23,()->{leftPage=Math.min(lm,leftPage+1);rebuild();});
        button("‹",267,140,22,()->{rightPage=Math.max(0,rightPage-1);rebuild();});button("›",293,140,23,()->{rightPage=Math.min(rm,rightPage+1);rebuild();});
        button("查阅 A",58,140,44,()->detail(a)).active=ClueCardClient.find(a)!=null;
        button("出示 A",106,140,46,()->publish(a)).active=ClueCardClient.find(a)!=null;
        button("查阅 B",164,140,44,()->detail(b)).active=ClueCardClient.find(b)!=null;
        button("出示 B",212,140,46,()->publish(b)).active=ClueCardClient.find(b)!=null;
    }
    private String shortTitle(ClueCardStore.Summary c){return (c.published()?"公 ":"私 ")+c.fields().title();}
    private void detail(String id){if(ClueCardClient.find(id)!=null){expanded=id;armed="";textPage=0;model=false;itemInfo=false;fetch(id);rebuild();}}
    private List<net.minecraft.text.OrderedText> textLines(){var card=ClueCardClient.find(expanded);if(card==null)return List.of();
        if(itemInfo)return ClueCardItemPreview.lines(expanded,206);
        return MinecraftClient.getInstance().textRenderer.wrapLines(Text.literal("事件描述："+card.fields().event()+"\n\n补充判断："+card.fields().inference()),294);
    }
    private int textPages(){return Math.max(1,(textLines().size()+6)/7);}
    private String itemAt(double x,double y){if(!expanded.isEmpty())return model&&ClueCardItemPreview.isItem(expanded)&&x>=10&&x<310&&y>=60&&y<157?expanded:"";if(y<69||y>=105)return "";if(x>=62&&x<148&&ClueCardItemPreview.isItem(a))return a;if(x>=168&&x<254&&ClueCardItemPreview.isItem(b))return b;return "";}
    public String hover(double x,double y){if(!itemAt(x,y).isEmpty())return "查看记录时的物品信息";for(var button:buttons)if(button.active&&button.isMouseOver(x,y))return button.getMessage().getString();return "";}
    public boolean click(double x,double y){String item=itemAt(x,y);if(!item.isEmpty()){detail(item);itemInfo=true;rebuild();return true;}for(var button:List.copyOf(buttons))if(button.active&&button.isMouseOver(x,y)){button.onPress();return true;}return false;}
    public void render(DrawContext c,int mx,int my,float delta){
        var current=ClueCardClient.state;if(current!=seen){if(current!=null&&seen!=null&&!current.session().equals(seen.session())){loaded=false;manual=false;expanded=armed="";leftPage=rightPage=textPage=0;}
            boolean changed=current!=null&&seen!=null&&!current.history().isEmpty()&&(seen.history().isEmpty()||current.history().getLast().number()>seen.history().getLast().number());seen=current;
            if(current!=null&&!current.notice().isEmpty())notice=current.notice();if(current!=null&&!loaded)latest();else if(changed)showComparison(current.latestA(),current.latestB());rebuild();}
        ClueFolioScreen.paper(c,2,2,316,176);
        if(expanded.isEmpty()){drawCard(c,a,58);drawCard(c,b,164);}else drawExpanded(c);
        for(var button:buttons){button.render(c,mx,my,delta);if(button.active&&button.isMouseOver(mx,my)){c.fill(button.getX(),button.getY(),button.getX()+button.getWidth(),button.getY()+button.getHeight(),0x206d3326);c.drawBorder(button.getX(),button.getY(),button.getWidth(),button.getHeight(),ClueFolioScreen.RED);}}
        String label=!armed.isEmpty()?(armed.startsWith("publish:")?"再次点击对应出示按钮，公开并锁定该卡":"再次确认将公开两卡，并锁定未出示版本"):!expanded.isEmpty()?(itemInfo?"记录时的物品状态 · 信息 "+(textPage+1)+" / "+textPages():model?"固定记录预览":"正文 "+(textPage+1)+" / "+textPages()):!notice.isEmpty()?notice:manual?"个人比较 · 选择不会自动公开":"最新公开比较 · 各自独立浏览";
        line(c,label,6,166,308,ClueFolioScreen.RED);
        if(!hover(mx,my).isEmpty())c.drawBorder(mx-4,my-4,9,9,ClueFolioScreen.RED);
    }
    private void line(DrawContext c,String value,int x,int y,int width,int color){var font=MinecraftClient.getInstance().textRenderer;c.drawText(font,font.trimToWidth(value,width),x,y,color,false);}
    private void drawExpanded(DrawContext c){var card=ClueCardClient.find(expanded);if(card==null)return;
        line(c,card.fields().title(),10,30,300,ClueFolioScreen.INK);
        line(c,"时间："+card.fields().time()+"    地点："+card.fields().place(),10,44,300,ClueFolioScreen.MUTED);
        if(model)ClueCardScreen.drawSnapshot(c,ClueCardClient.DETAILS.get(expanded),10,60,300,97);
        else {int x=itemInfo?104:12,w=itemInfo?206:294;if(itemInfo)ClueCardScreen.drawSnapshot(c,ClueCardClient.DETAILS.get(expanded),10,64,84,84);if(itemInfo)c.fill(x-2,60,x+w,157,0xff302a22);var lines=textLines();textPage=Math.min(textPage,textPages()-1);ClueFolioScreen.rules(c,x,64,w,Math.min(7,lines.size()-textPage*7),13);for(int i=0;i<7&&textPage*7+i<lines.size();i++)c.drawText(MinecraftClient.getInstance().textRenderer,lines.get(textPage*7+i),x,64+i*13,itemInfo?0xffeee0bd:ClueFolioScreen.INK,false);}
    }
    private void drawCard(DrawContext c,String id,int x){var text=MinecraftClient.getInstance().textRenderer;var card=ClueCardClient.find(id);ClueFolioScreen.paper(c,x,27,94,109);
        if(card==null){line(c,"选择记录",x+4,34,86,ClueFolioScreen.MUTED);return;}
        String[] lines={card.fields().title(),"时间 "+card.fields().time(),"地点 "+card.fields().place()};int y=31;
        ClueFolioScreen.rules(c,x+4,31,86,3,12);for(String line:lines){line(c,line,x+4,y,86,ClueFolioScreen.INK);y+=12;}
        ClueCardScreen.drawSnapshot(c,ClueCardClient.DETAILS.get(id),x+4,69,86,36);
        var wrapped=text.wrapLines(Text.literal(card.fields().event()),86);for(int i=0;i<Math.min(2,wrapped.size());i++)c.drawText(text,wrapped.get(i),x+4,109+i*11,ClueFolioScreen.INK,false);
    }
}

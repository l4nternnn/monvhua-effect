package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.*;

/** Compact 320x180 board, rendered on the existing private world anchor. */
public final class ClueCardWorldPanel {
    private final List<ButtonWidget> buttons=new ArrayList<>();
    private final Runnable back;
    private ClueCardPackets.State seen;
    private String a="",b="",armed="";
    private int leftPage,rightPage;
    private boolean manual;
    public ClueCardWorldPanel(Runnable back){this.back=back;ClueCardClient.request("list","");}
    private void button(String label,int x,int y,int w,Runnable action){buttons.add(ButtonWidget.builder(Text.literal(label),ignored->action.run()).dimensions(x,y,w,18).build());}
    private List<ClueCardStore.Summary> cards(){return ClueCardClient.state==null?List.of():ClueCardClient.state.cards();}
    private void select(String id,boolean left){if(left)a=id;else b=id;manual=true;armed="";ClueCardClient.request("view",id);rebuild();}
    private void latest(){var state=ClueCardClient.state;if(state==null)return;a=state.latestA();b=state.latestB();manual=false;armed="";if(!a.isEmpty())ClueCardClient.request("view",a);if(!b.isEmpty())ClueCardClient.request("view",b);}
    private void rebuild(){
        buttons.clear();button("遗体",4,3,40,back);button("最新比较",48,3,62,()->{latest();rebuild();});
        button(armed.isEmpty()?"展示比较":"确认公开",114,3,66,()->{
            if(a.isEmpty()||b.isEmpty()||a.equals(b))return;
            String key=a+":"+b;if(!key.equals(armed)){armed=key;rebuild();return;}
            ClueCardClient.request("compare",a,b,0,null);armed="";rebuild();
        });
        button("刷新",184,3,40,()->ClueCardClient.request("list",""));
        button("个人线索",228,3,86,ClueCardClient::open);
        var cards=cards();int max=Math.max(0,(cards.size()-1)/4);leftPage=Math.min(leftPage,max);rightPage=Math.min(rightPage,max);
        for(int i=0;i<4;i++){int l=leftPage*4+i,r=rightPage*4+i;
            if(l<cards.size()){var card=cards.get(l);button(shortTitle(card),4,31+i*26,49,()->select(card.id(),true));}
            if(r<cards.size()){var card=cards.get(r);button(shortTitle(card),267,31+i*26,49,()->select(card.id(),false));}
        }
        button("<",4,140,22,()->{leftPage=Math.max(0,leftPage-1);rebuild();});button(">",30,140,23,()->{leftPage=Math.min(max,leftPage+1);rebuild();});
        button("<",267,140,22,()->{rightPage=Math.max(0,rightPage-1);rebuild();});button(">",293,140,23,()->{rightPage=Math.min(max,rightPage+1);rebuild();});
        button("查看 A",58,140,94,()->detail(a));button("查看 B",164,140,94,()->detail(b));
    }
    private String shortTitle(ClueCardStore.Summary c){return MinecraftClient.getInstance().textRenderer.trimToWidth((c.locked()?"示 ":"私 ")+c.fields().title(),43);}
    private void detail(String id){var card=ClueCardClient.find(id);if(card!=null){ClueCardClient.request("view",id);MinecraftClient.getInstance().setScreen(new ClueCardReadScreen(null,id));}}
    public String hover(double x,double y){for(var button:buttons)if(button.active&&button.isMouseOver(x,y))return button.getMessage().getString();return "";}
    public boolean click(double x,double y){for(var button:List.copyOf(buttons))if(button.active&&button.isMouseOver(x,y)){button.onPress();return true;}return false;}
    public void render(DrawContext c,int mx,int my,float delta){
        var current=ClueCardClient.state;if(current!=seen){boolean changed=seen==null||current!=null&&(!current.latestA().equals(seen.latestA())||!current.latestB().equals(seen.latestB()));seen=current;if(changed&&!manual)latest();rebuild();}
        c.fill(0,0,320,180,0xff111821);drawCard(c,a,58);drawCard(c,b,164);
        for(var button:buttons)button.render(c,mx,my,delta);
        String label=!armed.isEmpty()?"再次确认将公开左右两卡，未展示版本会锁定":current==null||current.session().isEmpty()?"尚未加入实验：/mvclue experiment start":manual?"个人比较 · 点击最新比较返回公共默认内容":"共享最新比较 · 选卡和预览仅影响本人";
        c.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,Text.literal(label),5,166,0xffffd488);
        if(!hover(mx,my).isEmpty())c.drawBorder(mx-4,my-4,9,9,0xffffd65a);
    }
    private void drawCard(DrawContext c,String id,int x){var text=MinecraftClient.getInstance().textRenderer;var card=ClueCardClient.find(id);c.fill(x,27,x+94,136,0xff293847);
        if(card==null){c.drawTextWithShadow(text,"选择记录",x+4,34,0xffb5c5d5);return;}
        String[] lines={card.fields().title(),"时间 "+card.fields().time(),"地点 "+card.fields().place()};int y=31;
        for(String line:lines){c.drawTextWithShadow(text,text.trimToWidth(line,86),x+4,y,0xffedf2f5);y+=12;}
        ClueCardScreen.drawSnapshot(c,ClueCardClient.DETAILS.get(id),x+4,69,86,36);y=109;
        var wrapped=text.wrapLines(Text.literal(card.fields().event()),86);for(int i=0;i<Math.min(2,wrapped.size());i++)c.drawTextWithShadow(text,wrapped.get(i),x+4,y+i*11,0xffd5e1ec);
    }
}

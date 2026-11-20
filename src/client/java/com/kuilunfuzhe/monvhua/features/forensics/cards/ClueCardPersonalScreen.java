package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.gui.DrawContext;
import java.util.*;

/** Book catalogue: category -> corpse -> part -> individual observation. */
final class ClueCardPersonalScreen extends ClueFolioScreen {
    private String selected,notice="",category="全部",group="",part="";
    private int page,rows,lw;
    private boolean model=true;
    private static String partName(String id){return switch(id){case "head"->"头部";case "torso"->"躯干";case "left_arm"->"左臂";case "right_arm"->"右臂";case "left_leg"->"左腿";case "right_leg"->"右腿";default->"未知部位";};}
    ClueCardPersonalScreen(String selected){super("调查卷宗");this.selected=selected;}
    @Override protected void init(){rebuild();}
    void receive(ClueCardPackets.State state){if(!state.notice().isEmpty())notice=state.notice();rebuild();}
    private List<ClueCardStore.Summary> accessible(){return ClueCardClient.state==null?List.of():ClueCardClient.state.cards().stream().filter(c->c.owned()||c.collected()).toList();}
    private List<ClueCardStore.Summary> cards(){return accessible().stream().filter(c->switch(category){case "现场"->"scene".equals(c.kind());case "物品"->"item".equals(c.kind());case "遗体"->!c.group().isEmpty();case "收录"->!c.owned();default->true;}).filter(c->group.isEmpty()||c.group().equals(group)).filter(c->part.isEmpty()||(c.part().isEmpty()?"unknown":c.part()).equals(part)).toList();}
    private void choose(String id){selected=id;ClueCardClient.request("view",id);rebuild();}
    private void rebuild(){
        clearChildren();layout(640,400);lw=Math.max(150,fw*36/100);rows=Math.max(1,(fh-142)/23);
        button("制卡台",fx+fw-130,fy+6,58,()->client.setScreen(new ClueCardWorkshopScreen(this,selected,false)));
        button("关闭",fx+fw-66,fy+6,54,this::close);
        String[] cats={"全部","现场","物品","遗体","收录"};for(int i=0;i<cats.length;i++){String cat=cats[i];button((cat.equals(category)?"·":"")+cat,fx+10+i*((lw-20)/5),fy+48,(lw-20)/5-2,()->{category=cat;group=part="";page=0;rebuild();});}
        var entries=cards();int max=Math.max(0,(entries.size()-1)/rows);page=Math.min(page,max);
        if(category.equals("遗体")&&group.isEmpty()){
            var groups=accessible().stream().filter(c->!c.group().isEmpty()).collect(java.util.stream.Collectors.toMap(ClueCardStore.Summary::group,ClueCardStore.Summary::groupLabel,(a,b)->a,LinkedHashMap::new));
            var ids=new ArrayList<>(groups.keySet());max=Math.max(0,(ids.size()-1)/rows);page=Math.min(page,max);
            for(int i=0;i<rows&&page*rows+i<ids.size();i++){String id=ids.get(page*rows+i);button("› "+groups.get(id)+" ["+accessible().stream().filter(c->c.group().equals(id)).count()+"]",fx+10,fy+92+i*23,lw-20,()->{group=id;page=0;rebuild();});}
        }else if(category.equals("遗体")&&!group.isEmpty()&&part.isEmpty()){
            var parts=entries.stream().map(c->c.part().isEmpty()?"unknown":c.part()).distinct().toList();
            max=Math.max(0,(parts.size()-1)/rows);page=Math.min(page,max);
            for(int i=0;i<rows&&page*rows+i<parts.size();i++){String p=parts.get(page*rows+i);button("› "+partName(p)+" ["+entries.stream().filter(c->(c.part().isEmpty()?"unknown":c.part()).equals(p)).count()+"]",fx+10,fy+92+i*23,lw-20,()->{part=p;page=0;rebuild();});}
        }else for(int i=0;i<rows&&page*rows+i<entries.size();i++){var card=entries.get(page*rows+i);button((Objects.equals(selected,card.id())?"· ":"")+displayTitle(card),fx+10,fy+92+i*23,lw-20,()->choose(card.id()));}
        if(!group.isEmpty())button("‹ 上级",fx+10,fy+70,58,()->{if(!part.isEmpty())part="";else group="";page=0;rebuild();});
        int maxPage=max;button("‹",fx+10,fy+fh-48,24,()->{page=Math.max(0,page-1);rebuild();});button("›",fx+39,fy+fh-48,24,()->{page=Math.min(maxPage,page+1);rebuild();});

        int rx=fx+lw+12,rw=fw-lw-24;var card=ClueCardClient.find(selected);
        button(model?"阅读正文":"查看预览",rx,fy+48,76,()->{model=!model;rebuild();});
        button("展开",rx+82,fy+48,48,()->{if(card!=null)client.setScreen(new ClueCardReadScreen(this,selected));}).active=card!=null;
        if(ClueCardItemPreview.isItem(selected))button("物品详情",rx+136,fy+48,Math.max(50,rw-136),()->ClueCardItemPreview.open(this,selected));
        int bw=(rw-12)/3;
        button("编辑",rx,fy+fh-48,bw,()->client.setScreen(new ClueCardEditorScreen(this,card))).active=card!=null&&card.owned()&&!card.locked();
        String publishId=selected;
        button("出示",rx+bw+6,fy+fh-48,bw,()->client.setScreen(new ClueCardConfirmScreen(this,"出示线索","确认出示","出示后该版本将公开并定稿","确认直接出示“"+displayTitle(card)+"”？",()->ClueCardClient.request("publish",publishId)))).active=card!=null;
        button("取出",rx+(bw+6)*2,fy+fh-48,bw,()->ClueCardClient.request("withdraw",selected)).active=card!=null&&card.owned()&&card.inBag();
    }
    private String displayTitle(ClueCardStore.Summary card){if(card==null)return "";String title=card.fields().title();if(title.isEmpty())title="未命名线索";if(card.owned()&&!card.published())title="⌈私⌋"+title;if(card.copy())title+="⌈副⌋";return title;}
    private int blanks(){if(client.player==null||ClueCardClient.state==null)return 0;int count=0;for(int i=0;i<client.player.getInventory().size();i++){var stack=client.player.getInventory().getStack(i);if(stack.isOf(ClueCardItems.BLANK)&&ClueCardClient.state.session().equals(ClueCardItems.data(stack,"clue_session")))count+=stack.getCount();}return count;}
    @Override public boolean mouseClicked(double mx,double my,int button){int x=fx+lw+12,y=fy+122,w=fw-lw-24,h=Math.max(16,fh-180);if(button==0&&model&&ClueCardItemPreview.isItem(selected)&&mx>=x&&mx<x+w&&my>=y&&my<y+h){ClueCardItemPreview.open(this,selected);return true;}return super.mouseClicked(mx,my,button);}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        background(c,"已收录 "+accessible().size()+" 条记录 · 剩余空白卡 "+blanks()+" 张");c.fill(fx+lw,fy+46,fx+lw+1,fy+fh-24,BRASS);
        line(c,group.isEmpty()?"卷宗目录 · "+accessible().size()+" 条":("遗体资料 / "+(part.isEmpty()?"部位":partName(part))),fx+12,fy+77,lw-24,MUTED);
        var card=ClueCardClient.find(selected);int x=fx+lw+12,w=fw-lw-24,y=fy+76;
        if(card==null)line(c,"从左页选择记录",x,y,w,MUTED);
        else{
            rules(c,x,y,w,3,RULE_STEP);line(c,displayTitle(card),x,y,w,INK);line(c,"时间："+card.fields().time(),x,y+14,w,MUTED);line(c,"地点："+card.fields().place(),x,y+28,w,MUTED);
            if(model)ClueCardScreen.drawSnapshot(c,ClueCardClient.DETAILS.get(selected),x,y+46,w,Math.max(16,fh-180));
            else {var lines=textRenderer.wrapLines(net.minecraft.text.Text.literal("事件描述："+ClueCardClient.eventText(card)+"\n补充判断："+card.fields().inference()),w);int count=Math.max(1,(fh-185)/RULE_STEP);rules(c,x,y+46,w,Math.min(count,lines.size()),RULE_STEP);for(int i=0;i<Math.min(count,lines.size());i++)c.drawText(textRenderer,lines.get(i),x,y+46+i*RULE_STEP,INK,false);}
        }
        line(c,"伤情 "+accessible().stream().filter(cardEntry->!cardEntry.group().isEmpty()).count()+" 条",fx+75,fy+fh-43,lw-85,MUTED);super.render(c,mx,my,delta);status(c,notice.isEmpty()?"遗体伤情自动收录 · 每具遗体仅消耗一张空白卡":notice);if(model)ClueCardItemPreview.hover(c,selected,mx,my,x,y+46,w,Math.max(16,fh-180));
    }
}

package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.*;

/** List left, selected case and actions right. Distribution has its own tab to preserve legibility. */
final class ClueCaseManagerScreen extends ClueFolioScreen {
    private ClueCaseAdmin.State state;private String selected="",notice="",idDraft="",playersDraft="",bagsDraft="",cardsDraft="",previousDraft="";
    private TextFieldWidget idField,playersField,bagsField,cardsField,previousField;
    private int page,lw,rows;private boolean distribution,membership,pending;
    ClueCaseManagerScreen(ClueCaseAdmin.State state){super("案件管理册");this.state=state;bagsDraft=""+state.bags();cardsDraft=""+state.cards();}
    private void remember(){if(idField!=null)idDraft=idField.getText();if(playersField!=null)playersDraft=playersField.getText();if(bagsField!=null)bagsDraft=bagsField.getText();if(cardsField!=null)cardsDraft=cardsField.getText();if(previousField!=null)previousDraft=previousField.getText();}
    void receive(ClueCaseAdmin.State next){remember();state=next;pending=false;notice=next.notice();if(!selected.isEmpty()&&entry()==null)selected="";rebuild();}
    private ClueCardStore.CaseEntry entry(){return state.cases().stream().filter(e->e.id().equals(selected)).findFirst().orElse(null);}
    @Override protected void init(){rebuild();}
    private TextFieldWidget field(String value,int x,int y,int w,int max){var f=new TextFieldWidget(textRenderer,x,y,Math.max(30,w),18,Text.empty());f.setMaxLength(max);f.setText(value);f.setEditable(!pending);addDrawableChild(f);return f;}
    private void select(String id){remember();selected=id;idDraft=id;if(idField!=null)idField.setText(id);notice="";rebuild();}
    private void submit(String op){
        if(pending)return;remember();int bags=state.bags(),cards=state.cards();
        if(distribution&&!op.equals("list"))try{bags=Integer.parseInt(bagsDraft);cards=Integer.parseInt(cardsDraft);if(bags<1||bags>64||cards<1||cards>64)throw new NumberFormatException();}catch(NumberFormatException e){notice="数量须为 1 至 64 的整数";return;}
        String id=op.equals("add")||op.equals("open")||op.equals("close")?idDraft.strip():selected;
        pending=true;ClueCaseManagerClient.request(new ClueCaseAdmin.Request(op,id,playersDraft.strip(),bags,cards,state.revision(),previousDraft.strip()));rebuild();
    }
    private void remove(){remember();String id=selected;if(id.isEmpty())return;client.setScreen(new ClueCardConfirmScreen(this,"删除案件","确认删除","删除后无法恢复", "删除案件 "+id+" 及其全部线索卡和比较记录？此操作不可恢复。",()->{selected=id;submit("delete");}));}
    private void rebuild(){
        remember();idField=playersField=bagsField=cardsField=previousField=null;clearChildren();layout(740,410);lw=Math.max(108,fw*30/100);rows=Math.max(1,(fh-114)/23);int x=fx+lw+14,w=fw-lw-28;
        button("刷新",fx+fw-122,fy+6,52,()->submit("list")).active=!pending;button("关闭",fx+fw-64,fy+6,52,this::close).active=!pending;
        int max=Math.max(0,(state.cases().size()-1)/rows);page=Math.min(page,max);
        for(int i=0;i<rows&&page*rows+i<state.cases().size();i++){var e=state.cases().get(page*rows+i);button((e.id().equals(selected)?"· ":"")+(e.open()?"[开] ":"[关] ")+e.id(),fx+10,fy+60+i*23,lw-20,()->select(e.id())).active=!pending;}
        button("‹",fx+10,fy+fh-43,24,()->{page=Math.max(0,page-1);rebuild();}).active=!pending;button("›",fx+40,fy+fh-43,24,()->{page=Math.min(max,page+1);rebuild();}).active=!pending;
        int tabW=(w-12)/3;
        button(!distribution&&!membership?"· 案件":"案件",x,fy+48,tabW,()->{remember();distribution=membership=false;rebuild();}).active=!pending;
        button(membership?"· 参与者":"参与者",x+tabW+6,fy+48,tabW,()->{remember();membership=true;distribution=false;rebuild();}).active=!pending;
        button(distribution?"· 发放":"发放",x+2*(tabW+6),fy+48,tabW,()->{remember();distribution=true;membership=false;rebuild();}).active=!pending;
        if(membership){
            previousField=field(previousDraft,x+60,fy+78,w-60,40);playersField=field(playersDraft,x+60,fy+106,w-60,256);
            button("添加参与者",x,fy+137,(w-6)/2,()->submit("members")).active=!pending&&entry()!=null;
            button("接续原身份",x+(w+6)/2,fy+137,(w-6)/2,()->{remember();client.setScreen(new ClueCardConfirmScreen(this,"接续参与者身份","确认接续","将原参与者的线索和权限转交给指定玩家","将案件 "+selected+" 中的 "+previousDraft+" 接续给 "+playersDraft+"？",()->submit("rebind")));}).active=!pending&&entry()!=null;
        }else if(!distribution){
            idField=field(idDraft,x+48,fy+78,w-48,40);playersField=field(playersDraft,x+48,fy+106,w-48,256);
            int bw=(w-18)/4,y=fy+137;var e=entry();
            button("添加",x,y,bw,()->submit("add")).active=!pending;
            button("开启",x+bw+6,y,bw,()->submit("open")).active=!pending;
            button("关闭",x+2*(bw+6),y,bw,()->submit("close")).active=!pending&&state.cases().stream().anyMatch(ClueCardStore.CaseEntry::open);
            button("删除",x+3*(bw+6),y,bw,this::remove).active=!pending&&e!=null;
        }else{
            playersField=field(playersDraft,x+48,fy+78,w-48,256);bagsField=field(bagsDraft,x+80,fy+106,w-80,2);cardsField=field(cardsDraft,x+80,fy+134,w-80,2);
            int bw=(w-12)/3;boolean open=state.cases().stream().anyMatch(ClueCardStore.CaseEntry::open);
            button("保存默认",x,fy+166,bw,()->submit("defaults")).active=!pending;
            button("发放卡袋",x+bw+6,fy+166,bw,()->submit("give_bags")).active=!pending&&open;
            button("发放空白卡",x+2*(bw+6),fy+166,bw,()->submit("give_cards")).active=!pending&&open;
        }
    }
    @Override public void close(){if(!pending)super.close();}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        background(c,"案件独立保存 · 开启不自动发放物资");int x=fx+lw+14,w=fw-lw-28;
        line(c,"案件目录",fx+12,fy+48,lw-24,INK);c.fill(fx+lw,fy+48,fx+lw+1,fy+fh-25,BRASS);
        if(membership){line(c,"原参与者",x,fy+83,58,INK);line(c,"新玩家",x,fy+111,58,INK);line(c,"添加无需填写原参与者；接续须指定一名玩家",x,fy+164,w,MUTED);var e=entry();if(e!=null)line(c,String.join("、",e.players()),x,fy+181,w,INK);}
        else if(distribution){line(c,"接收者",x,fy+83,46,INK);line(c,"卡袋 / 每人",x,fy+111,78,INK);line(c,"空白卡 / 每人",x,fy+139,78,INK);}
        else{line(c,"编号",x,fy+83,46,INK);line(c,"参与者",x,fy+111,46,INK);var e=entry();
            line(c,"编号留空：开启自动命名 case_1–9；关闭当前案件",x,fy+164,w,INK);
            line(c,e==null?"参与者支持玩家名、@a、@a[tag=...]":String.join("、",e.players()),x,fy+181,w,MUTED);
        }
        super.render(c,mx,my,delta);status(c,pending?"正在处理……":!notice.isEmpty()?notice:membership?"身份改变时接续原身份；新增玩家使用添加参与者":distribution?"接收者支持玩家名或选择器；数量为每人 1–64 个":"开启已有案件不改写参与者；切换将关闭上一案件的记录");
    }
}

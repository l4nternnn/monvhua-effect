package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;

final class ClueCardEditorScreen extends ClueFolioScreen {
    private final Screen parent;private ClueCardStore.Summary card;
    private TextFieldWidget titleField,timeField,placeField;private EditBoxWidget body;
    private boolean pending,inferenceMode;private String eventDraft,inferenceDraft,notice="";
    ClueCardEditorScreen(Screen parent,ClueCardStore.Summary card){super("誊写记录");this.parent=parent==null?new ClueCardPersonalScreen(card.id()):parent;this.card=card;eventDraft=card.fields().event();inferenceDraft=card.fields().inference();}
    private TextFieldWidget field(String value,int x,int y,int w,int max){var f=new TextFieldWidget(textRenderer,x,y,w,18,Text.empty());f.setMaxLength(max);f.setText(value);f.setEditableColor(0xfff0dfb8);f.setUneditableColor(MUTED);return addDrawableChild(f);}
    private void rememberBody(){if(body!=null){if(inferenceMode)inferenceDraft=body.getText();else eventDraft=body.getText();}}
    @Override protected void init(){
        rememberBody();String title=titleField==null?card.fields().title():titleField.getText(),time=timeField==null?card.fields().time():timeField.getText(),place=placeField==null?card.fields().place():placeField.getText();
        layout(610,390);int split=fw*34/100,x=fx+split+18,w=fw-split-32;
        titleField=field(title,x+32,fy+52,w-32,32);int half=(w-8)/2;
        timeField=field(time,x+28,fy+79,half-28,32);placeField=field(place,x+half+36,fy+79,w-half-36,48);
        body=EditBoxWidget.builder().x(x).y(fy+127).placeholder(Text.literal(inferenceMode?"补充对现象的解释（可选）":"描述发生了什么或观察到了什么")).build(textRenderer,w,Math.max(24,fh-181),Text.literal(inferenceMode?"补充判断":"事件描述"));body.setMaxLength(256);body.setText(inferenceMode?inferenceDraft:eventDraft);addDrawableChild(body);
        button(inferenceMode?"事件描述":"补充判断",x,fy+104,76,()->{rememberBody();inferenceMode=!inferenceMode;body=null;clearAndInit();}).active=!pending;
        button(pending?"保存中":"保存修改",fx+fw-160,fy+fh-40,76,()->{if(pending)return;rememberBody();pending=true;ClueCardClient.request("save",card.id(),"",card.revision(),new ClueCardStore.Fields(titleField.getText(),timeField.getText(),placeField.getText(),eventDraft,inferenceDraft));clearAndInit();}).active=!pending&&!card.locked();
        button("返回",fx+fw-78,fy+fh-40,66,this::close).active=!pending;
        titleField.setEditable(!pending&&!card.locked());timeField.setEditable(!pending&&!card.locked());placeField.setEditable(!pending&&!card.locked());
    }
    void receive(ClueCardPackets.State state){if(!pending||!"save".equals(state.responseOp()))return;notice=state.notice();pending=false;if(state.success()&&state.selected().equals(card.id()))client.setScreen(parent);else{var current=ClueCardClient.find(card.id());if(current!=null)card=current;clearAndInit();}}
    @Override public void close(){if(!pending)client.setScreen(parent);}
    @Override public void render(DrawContext c,int mx,int my,float delta){background(c,"记录内容已保存 · 首次出示前可修改文字说明");int split=fw*34/100,x=fx+split+18,w=fw-split-32;c.fill(fx+split+5,fy+48,fx+split+6,fy+fh-46,BRASS);
        ClueCardScreen.drawSnapshot(c,ClueCardClient.DETAILS.get(card.id()),fx+12,fy+54,split-18,Math.max(24,fh-130));
        line(c,"记录预览",fx+12,fy+fh-66,split-18,MUTED);line(c,"标题",x,fy+57,30,INK);line(c,"时间",x,fy+84,27,INK);line(c,"地点",x+(w-8)/2+8,fy+84,27,INK);
        super.render(c,mx,my,delta);status(c,notice.isEmpty()?"保存修改不消耗空白卡":notice);
    }
}

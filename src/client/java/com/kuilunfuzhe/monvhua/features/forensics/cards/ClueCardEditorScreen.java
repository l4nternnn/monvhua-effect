package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;

final class ClueCardEditorScreen extends Screen {
    private final Screen parent;
    private ClueCardStore.Summary card;
    private TextFieldWidget titleField,timeField,placeField;
    private EditBoxWidget eventField,inferenceField;
    private boolean pending;
    private boolean inferenceMode;
    private String eventDraft,inferenceDraft;
    private String notice="";
    private ButtonWidget saveButton;
    ClueCardEditorScreen(Screen parent,ClueCardStore.Summary card){super(Text.literal("编辑线索卡"));this.parent=parent==null?new ClueCardPersonalScreen(card.id()):parent;this.card=card;eventDraft=card.fields().event();inferenceDraft=card.fields().inference();}
    @Override public boolean shouldPause(){return false;}
    private TextFieldWidget field(String value,int x,int y,int w,int max){var f=new TextFieldWidget(textRenderer,x,y,w,18,Text.empty());f.setMaxLength(max);f.setText(value);addDrawableChild(f);return f;}
    @Override protected void init(){
        String title=titleField==null?card.fields().title():titleField.getText(),time=timeField==null?card.fields().time():timeField.getText(),place=placeField==null?card.fields().place():placeField.getText();
        if(eventField!=null){if(inferenceMode)inferenceDraft=eventField.getText();else eventDraft=eventField.getText();}
        int w=Math.max(100,width*3/5-24);int bodyH=Math.max(20,height-152);
        titleField=field(title,56,25,w-48,32);timeField=field(time,56,50,w-48,32);placeField=field(place,56,75,w-48,48);
        eventField=EditBoxWidget.builder().x(12).y(108).placeholder(Text.literal(inferenceMode?"可选：对现象的解释":"描述发生的事或观察到的现象")).build(textRenderer,w,bodyH,Text.literal(inferenceMode?"补充判断":"事件描述"));eventField.setMaxLength(256);eventField.setText(inferenceMode?inferenceDraft:eventDraft);addDrawableChild(eventField);
        addDrawableChild(ButtonWidget.builder(Text.literal(inferenceMode?"编辑事件描述":"编辑补充判断"),button->{if(inferenceMode)inferenceDraft=eventField.getText();else eventDraft=eventField.getText();inferenceMode=!inferenceMode;eventField=null;clearAndInit();}).dimensions(width*3/5+8,height-64,width*2/5-20,20).build());
        saveButton=addDrawableChild(ButtonWidget.builder(Text.literal("保存"),button->{pending=true;button.active=false;if(inferenceMode)inferenceDraft=eventField.getText();else eventDraft=eventField.getText();ClueCardClient.request("save",card.id(),"",card.revision(),new ClueCardStore.Fields(titleField.getText(),timeField.getText(),placeField.getText(),eventDraft,inferenceDraft));}).dimensions(width-152,height-30,68,20).build());
        saveButton.active=!pending&&!card.locked();
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"),button->close()).dimensions(width-78,height-30,66,20).build());
    }
    void receive(ClueCardPackets.State state){
        if(!pending||!"save".equals(state.responseOp()))return;
        notice=state.notice();pending=false;
        if(state.success()&&state.selected().equals(card.id()))client.setScreen(parent);
        else {var latest=ClueCardClient.find(card.id());if(latest!=null)card=latest;clearAndInit();}
    }
    @Override public void close(){if(!pending)client.setScreen(parent);}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        c.fill(0,0,width,height,0xff141d28);c.drawTextWithShadow(textRenderer,"标题",12,29,0xffffffff);c.drawTextWithShadow(textRenderer,"时间",12,54,0xffffffff);c.drawTextWithShadow(textRenderer,"地点",12,79,0xffffffff);
        c.drawTextWithShadow(textRenderer,inferenceMode?"补充判断":"事件描述",12,96,0xffd0dae3);
        int previewX=width*3/5+8;ClueCardScreen.drawSnapshot(c,ClueCardClient.DETAILS.get(card.id()),previewX,30,width-previewX-12,Math.min(130,height-108));
        c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(notice,width-16),8,height-16,0xffffd488);
        super.render(c,mx,my,delta);
    }
}

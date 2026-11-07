package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.viewport.*;
import com.kuilunfuzhe.monvhua.mixin.BodyPoseDrawContextAccessor;
import com.kuilunfuzhe.monvhua.network.forensics.ForensicPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import org.joml.Matrix3x2f;
import java.util.*;

/** Collected evidence only: a dedicated orbit viewport and wound-linked floating cards. */
public final class EvidenceSummaryScreen extends Screen {
    private static final String[] PARTS={"head","right_arm","right_leg","left_leg","left_arm","torso"};
    private static final String[] LABELS={"头部","右臂","右腿","左腿","左臂","躯干"};
    private static int sequence;
    private int requestId;
    private String part="torso",corpse="",message="正在加载已收集线索……";
    private NbtCompound snapshot=new NbtCompound();
    private NbtList cases=new NbtList();
    private List<NbtCompound> wounds=List.of(),visible=List.of();
    private EvidenceScene scene;
    private int selected, detailScroll, maxDetailScroll;
    private CardHit detailHit;
    private float yaw=180,pitch=25;
    private final List<CardHit> cardHits=new ArrayList<>();
    public EvidenceSummaryScreen(){super(Text.literal("案件线索总结"));}
    public static void receive(ForensicPackets.SummaryResult result){
        if(MinecraftClient.getInstance().currentScreen instanceof EvidenceSummaryScreen screen && result.requestId()==screen.requestId)screen.accept(result.snapshot());
    }
    private void accept(NbtCompound data){
        if(data.contains("message")){message=data.getString("message","");return;}
        snapshot=data.copy();cases=data.getListOrEmpty("cases").copy();corpse=data.getString("id","");
        wounds=new ArrayList<>();for(var element:data.getListOrEmpty("wounds"))if(element instanceof NbtCompound wound)wounds.add(wound.copy());
        scene=corpse.isEmpty()?null:new EvidenceScene(data.getCompoundOrEmpty("observation"),data.getListOrEmpty("wounds"));
        message=scene==null?"暂无观察快照，请先探查遗体部位":"";select(part);
    }
    private void request(String id){
        requestId=++sequence;scene=null;wounds=List.of();visible=List.of();snapshot=new NbtCompound();cardHits.clear();message="正在加载已收集线索……";
        if(ClientPlayNetworking.canSend(ForensicPackets.SummaryRequest.ID))ClientPlayNetworking.send(new ForensicPackets.SummaryRequest(id,part,requestId));
        else message="服务器未启用线索总结";
    }
    @Override protected void init(){
        int x=previewLeft(),w=width-x-12,button=Math.max(18,Math.min(24,w/5)),center=x+w/2,base=height-42;
        addDrawableChild(ButtonWidget.builder(Text.literal("←"),b->yaw=(yaw-15+360)%360).dimensions(center-button*2,base,button,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("↑"),b->pitch=Math.min(75,pitch+15)).dimensions(center-button,base,button,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("↓"),b->pitch=Math.max(-75,pitch-15)).dimensions(center,base,button,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("→"),b->yaw=(yaw+15)%360).dimensions(center+button,base,button,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("重置"),b->{yaw=180;pitch=25;}).dimensions(center-24,height-21,48,18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"),b->close()).dimensions(width-52,5,46,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("上一案"),b->changeCase(-1)).dimensions(8,31,48,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("下一案"),b->changeCase(1)).dimensions(60,31,48,20).build());
        if(snapshot.isEmpty())request(corpse);else accept(snapshot);
    }
    private void changeCase(int delta){
        if(cases.isEmpty())return;int current=0;
        for(int i=0;i<cases.size();i++)if(cases.getCompound(i).orElseThrow().getString("id","").equals(corpse))current=i;
        yaw=180;pitch=25;request(cases.getCompound(Math.floorMod(current+delta,cases.size())).orElseThrow().getString("id",""));
    }
    private void select(String value){
        part=value;detailScroll=0;visible=wounds.stream().filter(w->w.getString("part","").equals(part)).toList();selected=0;
        if(scene!=null)scene.requestFocus(visible.isEmpty()?"":visible.getFirst().getString("id",""));
    }
    @Override public boolean shouldPause(){return false;}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0){
            for(CardHit card:cardHits)if(x>=card.x&&x<card.x+card.w&&y>=card.y&&y<card.y+card.h){if(selected!=card.index)detailScroll=0;selected=card.index;scene.requestFocus(visible.get(selected).getString("id",""));return true;}
            int r=wheelRadius(),sector=SummaryWheelGeometry.sector(x-wheelCenterX(),y-wheelCenterY(),r*.34,r);
            if(sector>=0){select(PARTS[sector]);return true;}
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(detailHit!=null&&x>=detailHit.x&&x<detailHit.x+detailHit.w&&y>=detailHit.y&&y<detailHit.y+detailHit.h){
            detailScroll=Math.max(0,Math.min(maxDetailScroll,detailScroll-(int)(vertical*12)));return true;
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        context.fill(0,0,width,height,0xF0111821);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,10,0xFFF1E7D6);
        String name=snapshot.getCompoundOrEmpty("observation").getString("name","");
        context.drawTextWithShadow(textRenderer,Text.literal(name.isEmpty()?"请选择已探查案件":name+" · "+corpse.substring(0,Math.min(8,corpse.length()))),116,36,0xFFE6C18A);
        drawWheel(context,mouseX,mouseY);cardHits.clear();detailHit=null;
        int left=previewLeft(),top=64,right=width-12,bottom=height-48;
        context.fill(left,top,right,bottom,0xFF24333D);
        EvidenceScene.Anchor anchor=null;
        if(scene!=null && bottom>top){
            var prepared=scene.prepare();var frame=new EvidenceViewportFrame(left,top,right-left,bottom-top,prepared.bounds(),yaw,pitch);
            Matrix3x2f pose=new Matrix3x2f(context.getMatrices());
            var clip=new ScreenRect(left,top,right-left,bottom-top).transformEachVertex(pose);
            ((BodyPoseDrawContextAccessor)context).monvhua$getPoseGuiState().addSpecialElement(new EvidenceViewportRenderState(left,top,right,bottom,pose,clip,frame,prepared.render()));
            if(!visible.isEmpty())anchor=prepared.anchor(visible.get(selected),frame);
            if(scene.fallback())context.drawTextWithShadow(textRenderer,Text.literal("标准姿态示意"),left+4,top+4,0xFFFFC788);
        }
        drawFloatingCards(context,anchor);
        if(!message.isEmpty())drawWrapped(context,message,cardLeft(),78,Math.max(70,previewLeft()-cardLeft()-8),0xFFBDC9D2);
        super.render(context,mouseX,mouseY,delta);
    }
    private int wheelRadius(){return Math.max(25,Math.min(78,Math.min(width/9,(height-85)/3)));}
    private int wheelCenterX(){return width/8;}
    private int wheelCenterY(){return 62+(height-80)/2;}
    private int cardLeft(){return width/4+8;}
    private int previewLeft(){return width*65/100;}
    private void drawWheel(DrawContext context, int mx, int my) {
        int r = wheelRadius(), cx = wheelCenterX(), cy = wheelCenterY();
        int hover = SummaryWheelGeometry.sector(mx - cx, my - cy, r * .34, r);
        for (int yy = -r; yy <= r; yy++) {
            int start = -r, previous = -2;
            for (int xx = -r; xx <= r + 1; xx++) {
                int sector = xx > r ? -2 : SummaryWheelGeometry.sector(xx, yy, r * .34, r);
                if (sector != previous) {
                    if (previous >= 0) context.fill(cx + start, cy + yy, cx + xx, cy + yy + 1,
                            previous == hover ? 0xEE8A6744 : previous == indexOf(part) ? 0xEE73583E : 0xE02B3545);
                    start = xx; previous = sector;
                }
            }
        }
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("线索"), cx, cy - 5, 0xFFE6C18A);
        for (int i = 0; i < LABELS.length; i++) {
            double angle = -Math.PI / 2 + i * SummaryWheelGeometry.STEP;
            int x = cx + (int) (Math.cos(angle) * r * .70), y = cy + (int) (Math.sin(angle) * r * .70);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(LABELS[i]), x, y - 5, 0xFFF1E7D6);
        }
    }

    private void drawFloatingCards(DrawContext context,EvidenceScene.Anchor anchor){
        int left=cardLeft(),w=Math.max(60,previewLeft()-left-10);
        context.drawTextWithShadow(textRenderer,Text.literal(LABELS[indexOf(part)]+" · 已收集线索"),left,58,0xFFE6C18A);
        if(scene==null)return;
        if(visible.isEmpty()){drawWrapped(context,"该部位暂无已收集伤口记录",left,80,w,0xFFBFCBD2);return;}
        for(int i=0;i<visible.size();i++){
            int x=left+i*(w/visible.size()),bw=w/visible.size()-3;
            context.fill(x,76,x+bw,96,i==selected?0xFF79553F:0xFF344553);
            context.drawTextWithShadow(textRenderer,Text.literal(i==0?"最高":"记录"+(i+1)),x+3,82,0xFFF1E7D6);
            cardHits.add(new CardHit(x,76,bw,20,i));
        }
        String detail=describe(visible.get(selected));
        if(anchor!=null&&!anchor.message().isEmpty())detail+="\n"+anchor.message();
        var wrapped=textRenderer.wrapLines(Text.literal(detail),Math.max(30,w-16));
        int h=Math.min(Math.max(44,wrapped.size()*12+26),Math.max(44,height-116));
        int desired=anchor!=null&&anchor.point()!=null?(int)anchor.point().y()-h/2:(height-h)/2;
        int y=Math.max(104,Math.min(height-h-8,desired));
        context.fill(left,y,left+w,y+h,0xF0334653);
        context.drawTextWithShadow(textRenderer,Text.literal(selected==0?"最高伤害":"伤害记录 "+(selected+1)),left+8,y+7,0xFFFFD49B);
        maxDetailScroll=Math.max(0,wrapped.size()*12-(h-28));detailScroll=Math.min(maxDetailScroll,detailScroll);
        context.enableScissor(left,y+21,left+w,y+h-4);
        for(int i=0;i<wrapped.size();i++)context.drawTextWithShadow(textRenderer,wrapped.get(i),left+8,y+23+i*12-detailScroll,0xFFE2E7EE);
        context.disableScissor();
        detailHit=new CardHit(left,y,w,h,selected);cardHits.add(detailHit);
        if(anchor!=null&&anchor.point()!=null){
            int endX=(int)anchor.point().x(),endY=(int)anchor.point().y(),startX=left+w,startY=y+h/2,elbow=previewLeft()+6;
            line(context,startX,startY,elbow,startY);line(context,elbow,startY,elbow,endY);line(context,elbow,endY,endX,endY);
            context.fill(endX-2,endY-2,endX+3,endY+3,0xFF64C8F0);
        }
    }
    private void drawWrapped(DrawContext context,String value,int x,int y,int w,int color){
        for(var line:textRenderer.wrapLines(Text.literal(value),w)){context.drawTextWithShadow(textRenderer,line,x,y,color);y+=12;}
    }
    private static void line(DrawContext context,int x1,int y1,int x2,int y2){context.fill(Math.min(x1,x2),Math.min(y1,y2),Math.max(x1,x2)+1,Math.max(y1,y2)+1,0xFF64C8F0);}
    private String describe(NbtCompound wound){
        String kinds=String.join("、",Arrays.stream(wound.getString("kinds","unknown").split("\\+")).map(Forensics::kindLabel).toList());
        String severity=switch(wound.getString("severity","moderate")){case "mild"->"轻度";case "severe"->"重度";default->"中度";};
        String direction=switch(wound.getString("direction","")){case "front"->"前侧";case "back"->"后侧";case "left"->"左侧";case "right"->"右侧";default->"不明";};
        return kinds+" · "+severity+"\n来向："+direction+(wound.getString("precision","").equals("approximate")?"\n部位与方向为估算":"\n位置缺少可靠记录")+(wound.getBoolean("death_trigger",false)?"\n疑似主要致命损伤":"");
    }
    private int indexOf(String value){for(int i=0;i<PARTS.length;i++)if(PARTS[i].equals(value))return i;return 5;}
    private record CardHit(int x,int y,int w,int h,int index){}
}

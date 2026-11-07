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
    private float yaw=180,pitch=25,zoom=1;
    private org.joml.Quaternionf orientation=EvidenceViewportFrame.orientation(180,25);
    private org.joml.Quaternionf overviewOrientation=new org.joml.Quaternionf(orientation),focusFrom,focusTo;
    private org.joml.Vector3f target;
    private boolean dragging, focusing, rolling, panning;
    private ButtonWidget rollButton;
    private double rollStartX;
    private float rollAngle;
    private org.joml.Quaternionf rollStartOrientation;
    private long focusStarted;
    private float startYaw,startPitch,startZoom,endYaw,endPitch,endZoom;
    private org.joml.Vector3f startTarget,endTarget;
    private float overviewYaw=180,overviewPitch=25,overviewZoom=1;
    private String focusMessage="";
    private void manual(){focusing=false;focusMessage="";}
    private void rotate(float x,float y){manual();orientation=EvidenceViewportFrame.drag(orientation,x*2,-y*2);}
    private void overview(){manual();dragging=false;rolling=false;panning=false;target=null;yaw=overviewYaw;pitch=overviewPitch;zoom=overviewZoom;orientation.set(overviewOrientation);}
    private boolean inViewport(double x,double y){return x>=previewLeft()&&x<width-12&&y>=64&&y<height-48;}
    private void focusSelected(){
        if(scene==null||visible.isEmpty())return;
        var prepared=scene.prepare();var wound=visible.get(selected);var point=prepared.target(wound);
        if(point==null){manual();focusMessage="该记录没有可靠位置，无法自动聚焦";return;}
        if(target==null){overviewYaw=yaw;overviewPitch=pitch;overviewZoom=zoom;overviewOrientation.set(orientation);}
        var normal=point.normal();float baseYaw=(float)Math.toDegrees(Math.atan2(normal.x,normal.z));
        float basePitch=(float)Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,normal.y))));
        float diagonal=(float)Math.sqrt(prepared.bounds().getLengthX()*prepared.bounds().getLengthX()+prepared.bounds().getLengthY()*prepared.bounds().getLengthY()+prepared.bounds().getLengthZ()*prepared.bounds().getLengthZ());
        float focusZoom=Math.max(.035f,Math.min(.65f,point.extent()/Math.max(.1f,diagonal)));
        boolean found=false;
        for(float dy:new float[]{0,25,-25,50,-50}){
            for(float dp:new float[]{0,20,-20}){
                float cy=baseYaw+dy,cp=Math.max(-85,Math.min(85,basePitch+dp));
                var frame=new EvidenceViewportFrame(previewLeft(),64,width-previewLeft()-12,height-112,prepared.bounds(),cy,cp,point.position(),focusZoom);
                if(prepared.anchor(wound,frame).point()!=null){endYaw=cy;endPitch=cp;found=true;break;}
            }
            if(found)break;
        }
        if(!found){manual();focusMessage="无法找到无遮挡视角，可切换仅遗体后重试";return;}
        focusFrom=new org.joml.Quaternionf(orientation);focusTo=EvidenceViewportFrame.orientation(endYaw,endPitch);
        startYaw=yaw;startPitch=pitch;startZoom=zoom;endZoom=focusZoom;
        var b=prepared.bounds();startTarget=target==null?new org.joml.Vector3f((float)((b.minX+b.maxX)/2),(float)((b.minY+b.maxY)/2),(float)((b.minZ+b.maxZ)/2)):new org.joml.Vector3f(target);
        endTarget=new org.joml.Vector3f(point.position());focusStarted=System.nanoTime();focusing=true;dragging=false;panning=false;rolling=false;focusMessage="";
    }
    private void advanceFocus(){
        if(!focusing)return;
        float t=Math.min(1,(System.nanoTime()-focusStarted)/700000000f);float ease=t*t*(3-2*t);
        float turn=((endYaw-startYaw)%360+540)%360-180;
        orientation.set(focusFrom).slerp(focusTo,ease).normalize();
        yaw=startYaw+turn*ease;pitch=startPitch+(endPitch-startPitch)*ease;
        zoom=startZoom+(endZoom-startZoom)*ease;target=new org.joml.Vector3f(startTarget).lerp(endTarget,ease);
        if(t>=1)focusing=false;
    }
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
        rolling=false;dragging=false;panning=false;
        int x=previewLeft(),w=width-x-12,button=Math.max(18,Math.min(24,w/5)),center=x+w/2,base=height-42;
        addDrawableChild(ButtonWidget.builder(Text.literal("←"),b->rotate(-15,0)).dimensions(center-button*2,base,button,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("↑"),b->rotate(0,15)).dimensions(center-button,base,button,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("↓"),b->rotate(0,-15)).dimensions(center,base,button,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("→"),b->rotate(15,0)).dimensions(center+button,base,button,20).build());
        rollButton=addDrawableChild(ButtonWidget.builder(Text.literal("⟳"),b->{}).dimensions(center+12,height-21,26,18)
                .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("平面旋转：按住左键，向左逆时针、向右顺时针"))).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("总览"),b->overview()).dimensions(center-40,height-21,48,18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("现场/遗体"),b->{if(scene!=null){overview();scene.toggleEnvironment();}}).dimensions(8,height-24,78,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"),b->close()).dimensions(width-52,5,46,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("上一案"),b->changeCase(-1)).dimensions(8,31,48,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("下一案"),b->changeCase(1)).dimensions(60,31,48,20).build());
        if(snapshot.isEmpty())request(corpse);else accept(snapshot);
    }
    private void changeCase(int delta){
        if(cases.isEmpty())return;int current=0;
        for(int i=0;i<cases.size();i++)if(cases.getCompound(i).orElseThrow().getString("id","").equals(corpse))current=i;
        overviewYaw=180;overviewPitch=25;overviewZoom=1;overviewOrientation.set(EvidenceViewportFrame.orientation(180,25));overview();request(cases.getCompound(Math.floorMod(current+delta,cases.size())).orElseThrow().getString("id",""));
    }
    private void select(String value){
        overview();part=value;detailScroll=0;visible=wounds.stream().filter(w->w.getString("part","").equals(part)).toList();selected=0;
        if(scene!=null)scene.requestFocus(visible.isEmpty()?"":visible.getFirst().getString("id",""));
    }
    @Override public boolean shouldPause(){return false;}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==1&&scene!=null&&inViewport(x,y)&&!dragging&&!rolling){
            if(target==null){overviewYaw=yaw;overviewPitch=pitch;overviewZoom=zoom;overviewOrientation.set(orientation);}
            manual();panning=true;return true;
        }
        if(panning)return true;
        if(button==0){
            if(scene!=null&&rollButton!=null&&rollButton.isMouseOver(x,y)){
                manual();dragging=false;rolling=true;rollStartX=x;rollAngle=0;
                rollStartOrientation=new org.joml.Quaternionf(orientation);return true;
            }
            for(CardHit card:cardHits)if(x>=card.x&&x<card.x+card.w&&y>=card.y&&y<card.y+card.h){if(selected!=card.index)detailScroll=0;selected=card.index;scene.requestFocus(visible.get(selected).getString("id",""));focusSelected();return true;}
            if(scene!=null&&inViewport(x,y)){manual();dragging=true;return true;}
            int r=wheelRadius(),sector=SummaryWheelGeometry.sector(x-wheelCenterX(),y-wheelCenterY(),r*.34,r);
            if(sector>=0){select(PARTS[sector]);return true;}
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(panning&&button==1&&scene!=null){
            manual();
            var prepared=scene.prepare();
            var frame=new EvidenceViewportFrame(previewLeft(),64,width-previewLeft()-12,height-112,prepared.bounds(),orientation,target,zoom);
            target=frame.pannedTarget((float)dx,(float)dy);return true;
        }
        if(rolling&&button==0){
            rollAngle=(float)(x-rollStartX);orientation=EvidenceViewportFrame.roll(rollStartOrientation,rollAngle);return true;
        }
        if(dragging&&button==0){manual();orientation=EvidenceViewportFrame.drag(orientation,(float)dx,(float)dy);return true;}
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        if(button==1&&panning){panning=false;return true;}
        if(button==0&&rolling){rolling=false;return true;}
        if(button==0&&dragging){dragging=false;return true;}return super.mouseReleased(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(rolling)return true;
        if(detailHit!=null&&x>=detailHit.x&&x<detailHit.x+detailHit.w&&y>=detailHit.y&&y<detailHit.y+detailHit.h){
            detailScroll=Math.max(0,Math.min(maxDetailScroll,detailScroll-(int)(vertical*12)));return true;
        }
        if(scene!=null&&inViewport(x,y)){manual();zoom=Math.max(.025f,Math.min(4,zoom*(float)Math.exp(-vertical*.12)));return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        advanceFocus();
        context.fill(0,0,width,height,0xF0111821);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,10,0xFFF1E7D6);
        String name=snapshot.getCompoundOrEmpty("observation").getString("name","");
        context.drawTextWithShadow(textRenderer,Text.literal(name.isEmpty()?"请选择已探查案件":name+" · "+corpse.substring(0,Math.min(8,corpse.length()))),116,36,0xFFE6C18A);
        drawWheel(context,mouseX,mouseY);cardHits.clear();detailHit=null;
        int left=previewLeft(),top=64,right=width-12,bottom=height-48;
        context.fill(left,top,right,bottom,0xFF24333D);
        EvidenceScene.Anchor anchor=null;
        if(scene!=null && bottom>top){
            var prepared=scene.prepare();var frame=new EvidenceViewportFrame(left,top,right-left,bottom-top,prepared.bounds(),orientation,target,zoom);
            Matrix3x2f pose=new Matrix3x2f(context.getMatrices());
            var clip=new ScreenRect(left,top,right-left,bottom-top).transformEachVertex(pose);
            ((BodyPoseDrawContextAccessor)context).monvhua$getPoseGuiState().addSpecialElement(new EvidenceViewportRenderState(left,top,right,bottom,pose,clip,frame,prepared.render()));
            if(!visible.isEmpty())anchor=prepared.anchor(visible.get(selected),frame);
            if(scene.showsEnvironment())context.drawTextWithShadow(textRenderer,Text.literal("现场快照 · 直径3格"),left+4,bottom-13,0xFFBFCBD2);
            if(scene.fallback())context.drawTextWithShadow(textRenderer,Text.literal("标准姿态示意"),left+4,top+4,0xFFFFC788);
        }
        drawFloatingCards(context,anchor);
        if(!message.isEmpty())drawWrapped(context,message,cardLeft(),78,Math.max(70,previewLeft()-cardLeft()-8),0xFFBDC9D2);
        super.render(context,mouseX,mouseY,delta);
        if(rolling)drawRollBar(context);

    }
    private void drawRollBar(DrawContext context){
        int left=previewLeft()+8,right=width-20,cy=height-68,center=(left+right)/2;
        context.fill(left-5,cy-24,right+5,cy+12,0xF014202A);
        context.drawCenteredTextWithShadow(textRenderer,Text.literal("逆时针 ←  "+Math.round(rollAngle)+"°  → 顺时针"),center,cy-19,0xFFF1E7D6);
        context.fill(left,cy-3,right,cy+4,0xFF71828C);
        context.fill(left+1,cy-2,right-1,cy+3,0xFF192630);
        context.fill(center,cy-5,center+1,cy+6,0xFFBCC9D1);
        int knob=center+(int)(Math.max(-90,Math.min(90,rollAngle))/90*(right-left)/2);
        for(int y=-5;y<=5;y++){
            int half=(int)Math.sqrt(25-y*y);context.fill(knob-half,cy+y,knob+half+1,cy+y+1,0xFFEF4444);
        }
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
        if(!focusMessage.isEmpty())detail+="\n"+focusMessage;
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

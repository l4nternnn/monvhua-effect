package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.viewport.*;
import com.kuilunfuzhe.monvhua.mixin.BodyPoseDrawContextAccessor;
import com.kuilunfuzhe.monvhua.network.forensics.ForensicPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import org.joml.Matrix3x2f;
import java.util.*;

/** Collected evidence only: a dedicated orbit viewport and wound-linked floating cards. */
public final class EvidencePanel extends net.minecraft.client.gui.AbstractParentElement implements net.minecraft.client.gui.Drawable {
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
    private boolean inViewport(double x,double y){return x>=previewLeft()&&x<previewRight()&&y>=previewTop()&&y<previewBottom();}
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
                var frame=new EvidenceViewportFrame(previewLeft(),previewTop(),previewRight()-previewLeft(),previewBottom()-previewTop(),prepared.bounds(),cy,cp,point.position(),focusZoom);
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
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final net.minecraft.client.font.TextRenderer textRenderer = client.textRenderer;
    private final Text title = Text.literal("案件线索总结");
    private final List<ButtonWidget> buttons = new ArrayList<>();
    private final boolean world;
    private final Runnable onClose;
    private int width, height;
    public EvidencePanel(boolean world, Runnable onClose) { this.world=world; this.onClose=onClose; }
    public void resize(int width,int height) { this.width=width;this.height=height;buttons.clear();init(); }
    private ButtonWidget addDrawableChild(ButtonWidget button) { buttons.add(button);return button; }
    @Override public List<ButtonWidget> children() { return buttons; }
    public void dispose() { EvidenceSessionRouter.remove(this); }
    private void close() { onClose.run(); }
    private long clickUntil;
    private int clickX,clickY;
    public boolean worldClick(double x,double y) {
        if(!world||worldTarget(x,y)==null)return false;
        boolean handled=mouseClicked(x,y,0);mouseReleased(x,y,0);
        if(handled){clickX=(int)x;clickY=(int)y;clickUntil=System.nanoTime()+250_000_000L;}
        return handled;
    }
    private record WorldTarget(int x,int y,int w,int h,String label){}
    private WorldTarget worldTarget(double x,double y){
        for(var button:buttons)if(button.visible&&button.active&&button.isMouseOver(x,y))
            return new WorldTarget(button.getX(),button.getY(),button.getWidth(),button.getHeight(),button.getMessage().getString());
        for(var card:cardHits)if(x>=card.x&&x<card.x+card.w&&y>=card.y&&y<card.y+card.h)
            return new WorldTarget(card.x,card.y,card.w,card.h,"聚焦伤口记录 "+(card.index+1));
        int r=wheelRadius(),sector=SummaryWheelGeometry.sector(x-wheelCenterX(),y-wheelCenterY(),r*.34,r);
        if(sector>=0)return new WorldTarget(0,0,0,0,"查看"+LABELS[sector]);
        return null;
    }
    public String worldHoverLabel(double x,double y){var target=worldTarget(x,y);return target==null?"":target.label;}
    private void drawWorldFeedback(DrawContext context,int x,int y){
        if(x<0||y<0||x>=width||y>=height)return;
        var target=worldTarget(x,y);int color=target==null?0xFFCAD6E0:0xFFFFD65A;
        // Highest layer: same target ordering as clicks, including floating cards.
        context.createNewRootLayer();
        if(target!=null&&target.w>0){
            context.fill(target.x,target.y,target.x+target.w,target.y+target.h,0x305FE4FF);
            context.drawBorder(target.x,target.y,target.w,target.h,color);
        }
        context.fill(x-5,y-1,x+6,y+2,0xFF101820);
        context.fill(x-1,y-5,x+2,y+6,0xFF101820);
        context.fill(x-4,y,x+5,y+1,color);context.fill(x,y-4,x+1,y+5,color);
        if(System.nanoTime()<clickUntil)context.drawBorder(clickX-8,clickY-8,17,17,0xFF6DFF9B);
    }

    void accept(NbtCompound data){
        if(data.contains("message")){message=data.getString("message","");return;}
        snapshot=data.copy();cases=data.getListOrEmpty("cases").copy();corpse=data.getString("id","");
        wounds=new ArrayList<>();for(var element:data.getListOrEmpty("wounds"))if(element instanceof NbtCompound wound)wounds.add(wound.copy());
        scene=corpse.isEmpty()?null:new EvidenceScene(data.getCompoundOrEmpty("observation"),data.getListOrEmpty("wounds"));
        message=scene==null?"暂无观察快照，请先探查遗体部位":"";select(part);
    }
    private void request(String id){
        requestId=++sequence;EvidenceSessionRouter.expect(this,requestId);scene=null;wounds=List.of();visible=List.of();snapshot=new NbtCompound();cardHits.clear();message="正在加载已收集线索……";
        if(ClientPlayNetworking.canSend(ForensicPackets.SummaryRequest.ID))ClientPlayNetworking.send(new ForensicPackets.SummaryRequest(id,part,requestId));
        else message="服务器未启用线索总结";
    }
    private void init(){
        rolling=false;dragging=false;panning=false;
        if(world){
            initWorldButtons();
            if(snapshot.isEmpty())request(corpse);else accept(snapshot);
            return;
        }
        int x=previewLeft(),w=width-x-12,button=Math.max(18,Math.min(24,w/5)),center=x+w/2,base=height-42;
        addDrawableChild(ButtonWidget.builder(Text.literal("←"),b->rotate(-15,0)).dimensions(center-button*2,base,button,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("↑"),b->rotate(0,15)).dimensions(center-button,base,button,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("↓"),b->rotate(0,-15)).dimensions(center,base,button,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("→"),b->rotate(15,0)).dimensions(center+button,base,button,20).build());
        rollButton=addDrawableChild(ButtonWidget.builder(Text.literal("⟳"),b->{if(world){manual();orientation=EvidenceViewportFrame.roll(orientation,15);}}).dimensions(center+12,height-21,26,18)
                .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(world?"顺时针旋转15°":"平面旋转：按住左键，向左逆时针、向右顺时针"))).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("总览"),b->overview()).dimensions(center-40,height-21,48,18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("现场/遗体"),b->{if(scene!=null){overview();scene.toggleEnvironment();}}).dimensions(8,height-24,78,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"),b->close()).dimensions(width-52,5,46,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("上一案"),b->changeCase(-1)).dimensions(8,31,48,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("下一案"),b->changeCase(1)).dimensions(60,31,48,20).build());
        if(snapshot.isEmpty())request(corpse);else accept(snapshot);
    }
    /** Compact 320x180 world layout. Buttons deliberately have large physical hit areas. */
    private void initWorldButtons(){
        worldButton("←",6,4,26,20,()->changeCase(-1));
        worldButton("→",36,4,26,20,()->changeCase(1));
        worldButton("关闭",278,4,36,20,this::close);
        worldButton("现场/遗体",6,134,88,22,()->{if(scene!=null){overview();scene.toggleEnvironment();}});
        worldButton("上翻",102,154,51,22,()->detailScroll=Math.max(0,detailScroll-36));
        worldButton("下翻",157,154,53,22,()->detailScroll=Math.min(maxDetailScroll,detailScroll+36));
        String[] arrows={"←","↑","↓","→"};
        for(int i=0;i<4;i++){
            final int action=i;
            worldButton(arrows[i],218+i*24,130,22,20,()->{
                switch(action){case 0->rotate(-15,0);case 1->rotate(0,15);case 2->rotate(0,-15);case 3->rotate(15,0);}
            });
        }
        worldButton("总览",218,154,42,22,this::overview);
        worldButton("⟲",264,154,23,22,()->{manual();orientation=EvidenceViewportFrame.roll(orientation,-15);});
        worldButton("⟳",291,154,23,22,()->{manual();orientation=EvidenceViewportFrame.roll(orientation,15);});
    }
    private void worldButton(String text,int x,int y,int w,int h,Runnable action){
        addDrawableChild(ButtonWidget.builder(Text.literal(text),b->action.run()).dimensions(x,y,w,h).build());
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
    @Override public boolean mouseClicked(double x,double y,int button){
        if(!world&&button==1&&scene!=null&&inViewport(x,y)&&!dragging&&!rolling){
            if(target==null){overviewYaw=yaw;overviewPitch=pitch;overviewZoom=zoom;overviewOrientation.set(orientation);}
            manual();panning=true;return true;
        }
        if(panning)return true;
        if(button==0){
            if(!world&&scene!=null&&rollButton!=null&&rollButton.isMouseOver(x,y)){
                manual();dragging=false;rolling=true;rollStartX=x;rollAngle=0;
                rollStartOrientation=new org.joml.Quaternionf(orientation);return true;
            }
            for(var buttonWidget:buttons)if(buttonWidget.isMouseOver(x,y))return super.mouseClicked(x,y,button);
            for(CardHit card:cardHits)if(x>=card.x&&x<card.x+card.w&&y>=card.y&&y<card.y+card.h){if(selected!=card.index)detailScroll=0;selected=card.index;scene.requestFocus(visible.get(selected).getString("id",""));focusSelected();return true;}
            if(!world&&scene!=null&&inViewport(x,y)){manual();dragging=true;return true;}
            int r=wheelRadius(),sector=SummaryWheelGeometry.sector(x-wheelCenterX(),y-wheelCenterY(),r*.34,r);
            if(sector>=0){select(PARTS[sector]);return true;}
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(panning&&button==1&&scene!=null){
            manual();
            var prepared=scene.prepare();
            var frame=new EvidenceViewportFrame(previewLeft(),previewTop(),previewRight()-previewLeft(),previewBottom()-previewTop(),prepared.bounds(),orientation,target,zoom);
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
        if(world)return false;
        if(rolling)return true;
        if(detailHit!=null&&x>=detailHit.x&&x<detailHit.x+detailHit.w&&y>=detailHit.y&&y<detailHit.y+detailHit.h){
            detailScroll=Math.max(0,Math.min(maxDetailScroll,detailScroll-(int)(vertical*12)));return true;
        }
        if(scene!=null&&inViewport(x,y)){manual();zoom=Math.max(.025f,Math.min(4,zoom*(float)Math.exp(-vertical*.12)));return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        advanceFocus();
        context.fill(0,0,width,height,world?0xFF111821:0xF0111821);
        String name=snapshot.getCompoundOrEmpty("observation").getString("name","");
        if(world){
            context.fill(0,0,width,28,0xFF202D39);
            context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(name.isEmpty()?"线索 · 请选择案件":"线索 · "+name,202),68,10,0xFFFFD49B);
            context.drawCenteredTextWithShadow(textRenderer,"检查部位",50,30,0xFFE6C18A);
            context.drawCenteredTextWithShadow(textRenderer,scene==null?"待探查":scene.showsEnvironment()?"现场快照":"仅看遗体",50,164,0xFFE2E7EE);
        }else{
            context.drawCenteredTextWithShadow(textRenderer,title,width/2,10,0xFFF1E7D6);
            context.drawTextWithShadow(textRenderer,Text.literal(name.isEmpty()?"请选择已探查案件":name+" · "+corpse.substring(0,Math.min(8,corpse.length()))),116,36,0xFFE6C18A);
        }
        drawWheel(context,mouseX,mouseY);cardHits.clear();detailHit=null;
        int left=previewLeft(),top=previewTop(),right=previewRight(),bottom=previewBottom();
        context.fill(left,top,right,bottom,0xFF24333D);
        EvidenceScene.Anchor anchor=null;
        if(scene!=null && bottom>top){
            var prepared=scene.prepare();var frame=new EvidenceViewportFrame(left,top,right-left,bottom-top,prepared.bounds(),orientation,target,zoom);
            Matrix3x2f pose=new Matrix3x2f(context.getMatrices());
            var clip=new ScreenRect(left,top,right-left,bottom-top).transformEachVertex(pose);
            ((BodyPoseDrawContextAccessor)context).monvhua$getPoseGuiState().addSpecialElement(new EvidenceViewportRenderState(left,top,right,bottom,pose,clip,frame,prepared.render()));
            if(!visible.isEmpty())anchor=prepared.anchor(visible.get(selected),frame);
            if(!world&&scene.showsEnvironment())context.drawTextWithShadow(textRenderer,Text.literal("现场快照 · 直径3格"),left+4,bottom-13,0xFFBFCBD2);
            if(scene.fallback())context.drawTextWithShadow(textRenderer,Text.literal(world?"标准示意":"标准姿态示意"),left+4,top+4,0xFFFFC788);
        }
        if(world)drawWorldCards(context,anchor);else drawFloatingCards(context,anchor);
        if(!world&&!message.isEmpty())drawWrapped(context,message,cardLeft(),78,Math.max(70,previewLeft()-cardLeft()-8),0xFFBDC9D2);
        for(var button:buttons)button.render(context,mouseX,mouseY,delta);
        if(rolling)drawRollBar(context);
        if(world)drawWorldFeedback(context,mouseX,mouseY);

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
    private int wheelRadius(){if(world)return 44;return Math.max(25,Math.min(78,Math.min(width/9,(height-85)/3)));}
    private int wheelCenterX(){return world?50:width/8;}
    private int wheelCenterY(){return world?86:62+(height-80)/2;}
    private int cardLeft(){return world?102:width/4+8;}
    private int previewLeft(){return world?218:width*65/100;}
    private int previewTop(){return world?32:64;}
    private int previewRight(){return world?314:width-12;}
    private int previewBottom(){return world?126:height-48;}
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

    private void drawWorldCards(DrawContext context,EvidenceScene.Anchor anchor){
        int left=102,w=108;
        context.drawTextWithShadow(textRenderer,LABELS[indexOf(part)]+" · 线索",left,32,0xFFFFD49B);
        if(scene==null||visible.isEmpty()){
            maxDetailScroll=0;detailScroll=0;
            context.fill(left,48,left+w,148,0xFF263743);
            String status=!message.isEmpty()?message:"该部位暂无已收集线索";
            context.enableScissor(left+6,54,left+w-6,142);
            drawWrapped(context,status,left+6,58,w-12,0xFFE2E7EE);
            context.disableScissor();return;
        }
        // At most three records today; tabs and body remain stable while the model rotates.
        for(int i=0;i<visible.size();i++){
            int x=left+i*(w/visible.size()),bw=w/visible.size()-3;
            context.fill(x,48,x+bw,68,i==selected?0xFF916537:0xFF344553);
            context.drawCenteredTextWithShadow(textRenderer,i==0?"最高":"记录"+(i+1),x+bw/2,54,0xFFFFF0DA);
            cardHits.add(new CardHit(x,48,bw,20,i));
        }
        String detail=describe(visible.get(selected));
        if(!focusMessage.isEmpty())detail+="\n"+focusMessage;
        if(anchor!=null&&!anchor.message().isEmpty())detail+="\n"+anchor.message();
        var lines=textRenderer.wrapLines(Text.literal(detail),w-12);
        context.fill(left,72,left+w,148,0xFF304653);
        int textHeight=66;
        maxDetailScroll=Math.max(0,lines.size()*12-textHeight);detailScroll=Math.min(maxDetailScroll,detailScroll);
        context.enableScissor(left+6,77,left+w-6,143);
        for(int i=0;i<lines.size();i++)context.drawTextWithShadow(textRenderer,lines.get(i),left+6,78+i*12-detailScroll,0xFFF1F3F5);
        context.disableScissor();
        detailHit=new CardHit(left,72,w,76,selected);cardHits.add(detailHit);
        if(maxDetailScroll>0){
            int thumb=12,travel=76-thumb;
            int y=72+(int)(travel*(double)detailScroll/maxDetailScroll);
            context.fill(left+w-2,y,left+w,y+thumb,0xFFFFD49B);
        }
        if(anchor!=null&&anchor.point()!=null){
            int x=(int)anchor.point().x(),y=(int)anchor.point().y();
            line(context,210,110,214,110);line(context,214,110,214,y);line(context,214,y,x,y);
            context.fill(x-2,y-2,x+3,y+3,0xFF64C8F0);
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

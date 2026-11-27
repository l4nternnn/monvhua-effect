package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.gui.DrawContext;
import java.util.*;

/** Book catalogue: category -> corpse -> part -> individual observation. */
final class ClueCardPersonalScreen extends ClueFolioScreen {
    private String selected,notice="",category="全部",group="",part="";
    private int page,rows,lw;
    private double detailScroll;
    private int detailHeight,thumbX,thumbY,thumbW,thumbH;
    private boolean thumbVisible;
    private ClueSnapshotTexture thumbnail;
    private ClueCardPackets.Detail thumbnailDetail;
    private boolean thumbnailFailed;
    private record DetailLine(net.minecraft.text.OrderedText text,int x,int y,int color) {}
    private final List<DetailLine> detailLines=new ArrayList<>();
    private static String partName(String id){return switch(id){case "head"->"头部";case "torso"->"躯干";case "left_arm"->"左臂";case "right_arm"->"右臂";case "left_leg"->"左腿";case "right_leg"->"右腿";default->"未知部位";};}
    ClueCardPersonalScreen(String selected){super("调查卷宗");this.selected=selected;}
    @Override protected void init(){rebuild();}
    @Override public void removed(){releaseThumbnail();}
    private void releaseThumbnail(){if(thumbnail!=null)thumbnail.close();thumbnail=null;thumbnailDetail=null;thumbnailFailed=false;}
    @Override protected int titleWidth(){return fw-262;}
    void receive(ClueCardPackets.State state){if(!state.notice().isEmpty())notice=state.notice();rebuild();}
    private List<ClueCardStore.Summary> accessible(){return ClueCardClient.state==null?List.of():ClueCardClient.state.cards().stream().filter(c->c.owned()||c.collected()).toList();}
    private List<ClueCardStore.Summary> cards(){return accessible().stream().filter(c->switch(category){case "现场"->"scene".equals(c.kind());case "物品"->"item".equals(c.kind());case "遗体"->!c.group().isEmpty();case "收录"->!c.owned();default->true;}).filter(c->group.isEmpty()||c.group().equals(group)).filter(c->part.isEmpty()||(c.part().isEmpty()?"unknown":c.part()).equals(part)).toList();}
    private void choose(String id){detailScroll=0;selected=id;ClueCardClient.request("view",id);rebuild();}
    private void rebuild(){
        clearChildren();layout(640,400);lw=Math.max(150,fw*36/100);rows=Math.max(1,(fh-142)/23);
        button("比较板",fx+fw-238,fy+6,48,ClueCardClient::openBoard);
        button("遗体面板",fx+fw-184,fy+6,48,()->{
            var chosen=ClueCardClient.find(selected);
            String corpse=!group.isEmpty()?group:chosen==null?"":chosen.group();
            client.setScreen(new com.kuilunfuzhe.monvhua.features.forensics.EvidenceSummaryScreen(corpse));
        });
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
        button("展开",rx,fy+48,48,()->{if(card!=null)client.setScreen(new ClueCardReadScreen(this,selected));}).active=card!=null;
        if(ClueCardItemPreview.isItem(selected))button("物品详情",rx+54,fy+48,76,()->ClueCardItemPreview.open(this,selected));
        int bw=(rw-12)/3;
        button("编辑",rx,fy+fh-48,bw,()->client.setScreen(new ClueCardEditorScreen(this,card))).active=card!=null&&card.owned()&&!card.locked();
        String publishId=selected;
        button("出示",rx+bw+6,fy+fh-48,bw,()->client.setScreen(new ClueCardConfirmScreen(this,"出示线索","确认出示","出示后该版本将公开并定稿","确认直接出示“"+displayTitle(card)+"”？",()->ClueCardClient.request("publish",publishId)))).active=card!=null;
        button("取出",rx+(bw+6)*2,fy+fh-48,bw,()->ClueCardClient.request("withdraw",selected)).active=card!=null&&card.owned()&&card.inBag();
    }
    private String displayTitle(ClueCardStore.Summary card){if(card==null)return "";String title=card.fields().title();if(title.isEmpty())title="未命名线索";if(card.owned()&&!card.published())title="⌈私⌋"+title;if(card.copy())title+="⌈副⌋";return title;}
    private int blanks(){if(client.player==null||ClueCardClient.state==null)return 0;int count=0;for(int i=0;i<client.player.getInventory().size();i++){var stack=client.player.getInventory().getStack(i);if(stack.isOf(ClueCardItems.BLANK)&&ClueCardClient.state.session().equals(ClueCardItems.data(stack,"clue_session")))count+=stack.getCount();}return count;}
    private int detailX(){return fx+lw+12;}
    private int detailWidth(){return fw-lw-24;}
    private int detailTop(){return fy+76;}
    private int detailBottom(){return fy+fh-58;}
    private int addLines(String text,int x,int y,int width,int color){
        for(var line:textRenderer.wrapLines(net.minecraft.text.Text.literal(text),Math.max(1,width))){
            detailLines.add(new DetailLine(line,x,y,color));y+=RULE_STEP;
        }
        return y;
    }
    private static boolean hasPreview(ClueCardPackets.Detail detail){
        return detail!=null&&(!java.util.Objects.requireNonNullElse(detail.body(),"").isEmpty()||!java.util.Objects.requireNonNullElse(detail.scene(),"").isEmpty()||!java.util.Objects.requireNonNullElse(detail.item(),"").isEmpty()
                ||detail.width()==64&&detail.height()==48&&detail.pixels()!=null&&detail.pixels().length==3072
                &&java.util.Arrays.stream(detail.pixels()).anyMatch(pixel->pixel!=0));
    }
    private void prepareDetails(){
        detailLines.clear();thumbVisible=false;var card=ClueCardClient.find(selected);if(card==null){detailHeight=0;detailScroll=0;return;}
        int x=detailX(),w=detailWidth(),y=0;var detail=ClueCardClient.DETAILS.get(selected);
        // Reserve the existing snapshot location during loading, but collapse confirmed image-less records.
        thumbVisible=detail==null||hasPreview(detail);
        thumbW=Math.min(96,Math.max(48,w/3));thumbH=thumbW*3/4;thumbX=x+w-thumbW-6;
        boolean beside=thumbVisible&&w>=140;
        int headerWidth=beside?w-thumbW-18:w-8;
        if(thumbVisible&&!beside)y=thumbH+8;
        y=addLines(displayTitle(card),x,y,headerWidth,INK);
        y=addLines("时间："+(card.fields().time().isEmpty()?"未填写":card.fields().time()),x,y,headerWidth,MUTED);
        y=addLines("地点："+(card.fields().place().isEmpty()?"未填写":card.fields().place()),x,y,headerWidth,MUTED);
        y=addLines(card.locked()?"出示者："+card.presenter():"个人记录 · 尚未出示",x,y,headerWidth,MUTED);
        if(thumbVisible)y=Math.max(y,thumbH);
        y+=8;
        y=addLines("事件描述：\n"+ClueCardClient.eventText(card),x,y,w-8,INK)+8;
        y=addLines("补充判断：\n"+(card.fields().inference().isEmpty()?"未填写":card.fields().inference()),x,y,w-8,INK);
        detailHeight=y;
        detailScroll=Math.clamp(detailScroll,0,Math.max(0,detailHeight-(detailBottom()-detailTop())));
        thumbY=detailTop()-(int)detailScroll;
    }
    private boolean inDetails(double x,double y){return x>=detailX()&&x<detailX()+detailWidth()&&y>=detailTop()&&y<detailBottom();}
    private boolean onThumbnail(double x,double y){return thumbVisible&&inDetails(x,y)&&x>=thumbX&&x<thumbX+thumbW&&y>=thumbY&&y<thumbY+thumbH;}
    @Override public boolean mouseClicked(double x,double y,int button){
        prepareDetails();
        if(button==0&&onThumbnail(x,y)){
            var detail=ClueCardClient.DETAILS.get(selected);
            if(detail!=null&&hasPreview(detail))client.setScreen(new ClueCardImageScreen(this,detail));
            return true;
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(inDetails(x,y)){
            prepareDetails();detailScroll=Math.clamp(detailScroll-vertical*RULE_STEP*3,0,Math.max(0,detailHeight-(detailBottom()-detailTop())));return true;
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){
        var snapshot=ClueCardClient.DETAILS.get(selected);
        if(snapshot!=thumbnailDetail){
            releaseThumbnail();thumbnailDetail=snapshot;
            if(hasPreview(snapshot)){
                try{thumbnail=new ClueSnapshotTexture();thumbnail.capture(snapshot);}
                catch(RuntimeException error){if(thumbnail!=null){thumbnail.close();thumbnail=null;}thumbnailFailed=true;com.kuilunfuzhe.monvhua.MonvhuaMod.LOGGER.warn("Unable to prepare clue thumbnail",error);}
            }
        }
        background(c,"已收录 "+accessible().size()+" 条记录 · 剩余空白卡 "+blanks()+" 张");c.fill(fx+lw,fy+46,fx+lw+1,fy+fh-24,BRASS);
        line(c,group.isEmpty()?"卷宗目录 · "+accessible().size()+" 条":("遗体资料 / "+(part.isEmpty()?"部位":partName(part))),fx+12,fy+77,lw-24,MUTED);
        prepareDetails();
        int x=detailX(),w=detailWidth(),top=detailTop(),bottom=detailBottom();
        c.enableScissor(x,top,x+w,bottom);
        if(ClueCardClient.find(selected)==null)line(c,"从左页选择记录",x,top,w,MUTED);
        else{
            for(var row:detailLines){int y=top+row.y()-(int)detailScroll;
                if(y+10>=top&&y<bottom){c.fill(row.x(),y+9,x+w-8,y+10,0x26705b3b);c.drawText(textRenderer,row.text(),row.x(),y,row.color(),false);}
            }
            if(thumbVisible){
                if(thumbnail!=null)thumbnail.draw(c,thumbX,thumbY,thumbW,thumbH);
                else if(thumbnailFailed){c.fill(thumbX,thumbY,thumbX+thumbW,thumbY+thumbH,0xff554736);line(c,"预览不可用",thumbX+4,thumbY+thumbH/2,thumbW-8,PAPER);}
                else{c.fill(thumbX,thumbY,thumbX+thumbW,thumbY+thumbH,0xff554736);line(c,"加载中…",thumbX+4,thumbY+thumbH/2,thumbW-8,PAPER);}
                c.drawBorder(thumbX,thumbY,thumbW,thumbH,onThumbnail(mx,my)?RED:BRASS);
            }
            int viewport=bottom-top;
            if(detailHeight>viewport){
                int bar=Math.max(12,viewport*viewport/detailHeight),by=top+(int)(detailScroll/(detailHeight-viewport)*(viewport-bar));
                c.fill(x+w-3,top,x+w-1,bottom,0x305f4b35);c.fill(x+w-3,by,x+w-1,by+bar,BRASS);
            }
        }
        c.disableScissor();
        line(c,"伤情 "+accessible().stream().filter(cardEntry->!cardEntry.group().isEmpty()).count()+" 条",fx+75,fy+fh-43,lw-85,MUTED);
        super.render(c,mx,my,delta);
        status(c,notice.isEmpty()?"遗体伤情自动收录 · 每具遗体仅消耗一张空白卡":notice);
        if(onThumbnail(mx,my))c.drawTooltip(textRenderer,net.minecraft.text.Text.literal(ClueCardClient.DETAILS.get(selected)==null?"正在读取图片…":"点击查看大图"),mx,my);
    }
}

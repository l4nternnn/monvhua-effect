package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Viewer of a frozen record image, with cursor-centred zoom and screen-space pan. */
final class ClueCardImageScreen extends Screen {
    private final Screen parent;
    private final ClueCardPackets.Detail detail;
    private final ClueImageTransform transform=new ClueImageTransform();
    private ClueSnapshotTexture image;
    private boolean dragging,failed;
    ClueCardImageScreen(Screen parent,ClueCardPackets.Detail detail){super(Text.literal("线索图片"));this.parent=parent;this.detail=detail;}
    @Override protected void init(){transform.resize(12,30,Math.max(1,width-24),Math.max(1,height-60));dragging=false;}
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){client.setScreen(parent);}
    @Override public void removed(){dragging=false;if(image!=null){image.close();image=null;}}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==1){close();return true;}
        if(button==0&&inside(x,y)){dragging=true;return true;}return super.mouseClicked(x,y,button);
    }
    private boolean inside(double x,double y){return x>=12&&x<width-12&&y>=30&&y<height-30;}
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0){dragging=false;return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&dragging){transform.pan(dx,dy);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(inside(x,y)){transform.zoom(x,y,vertical);return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        c.fill(0,0,width,height,0xf0181512);
        if(image==null&&!failed){
            try{image=new ClueSnapshotTexture();image.capture(detail);}
            catch(RuntimeException error){if(image!=null){image.close();image=null;}failed=true;com.kuilunfuzhe.monvhua.MonvhuaMod.LOGGER.warn("Unable to prepare clue image",error);}
        }
        c.enableScissor(12,30,width-12,height-30);
        if(image!=null){
            image.draw(c,transform.x(),transform.y(),transform.imageWidth(),transform.imageHeight());
        }else c.drawCenteredTextWithShadow(textRenderer,"预览不可用",width/2,height/2,0xffdfcbaa);
        c.disableScissor();
        c.drawTextWithShadow(textRenderer,"线索图片 · "+Math.round(transform.zoom()*100)+"%",12,12,0xffeadcc0);
        c.drawCenteredTextWithShadow(textRenderer,"滚轮缩放 · 按住左键拖动 · Esc / 右键返回",width/2,height-19,0xffeadcc0);
        super.render(c,mx,my,delta);
    }
}

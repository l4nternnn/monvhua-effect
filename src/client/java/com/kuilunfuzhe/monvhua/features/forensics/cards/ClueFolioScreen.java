package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Shared paper, ink, wood and brass. Layout stays in GUI units; text is never scaled down. */
abstract class ClueFolioScreen extends Screen {
    protected int fx,fy,fw,fh;
    static final int INK=0xff38291e,MUTED=0xff776349,PAPER=0xffb7aa89,RED=0xff833e30,BRASS=0xffad8950;
    ClueFolioScreen(String title){super(Text.literal(title));}
    @Override public boolean shouldPause(){return false;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(!(getFocused() instanceof net.minecraft.client.gui.widget.TextFieldWidget)
                &&com.kuilunfuzhe.monvhua.features.forensics.ClueChat.open(this,key,scan))return true;
        return super.keyPressed(key,scan,modifiers);
    }
    protected void layout(int preferredWidth,int preferredHeight){
        fw=Math.min(width-16,Math.min(preferredWidth,Math.max(300,(int)(width*.75))));
        fh=Math.min(height-16,Math.min(preferredHeight,Math.max(220,(int)(height*.74))));
        fx=(width-fw)/2;fy=(height-fh)/2;
    }
    protected ButtonWidget button(String label,int x,int y,int w,Runnable action){
        return addDrawableChild(new SealButton(label,x,y,Math.max(18,w),action));
    }
    static final class SealButton extends ButtonWidget {
        SealButton(String label,int x,int y,int w,Runnable action){super(x,y,w,18,Text.literal(label),b->action.run(),DEFAULT_NARRATION_SUPPLIER);}
        @Override protected void renderWidget(DrawContext c,int mx,int my,float delta){
            boolean hover=isHovered()||isFocused();int paper=active?(hover?0xffd2c4a3:0xffbaaa86):0xffa99d80;
            c.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),paper);
            c.drawBorder(getX(),getY(),getWidth(),getHeight(),active?(hover?RED:BRASS):MUTED);
            var font=net.minecraft.client.MinecraftClient.getInstance().textRenderer;
            String text=font.trimToWidth(getMessage().getString(),getWidth()-6);
            c.drawText(font,text,getX()+(getWidth()-font.getWidth(text))/2,getY()+5,active?INK:MUTED,false);
        }
    }
    static final int RULE_STEP=14;
    private static final net.minecraft.util.Identifier AGED=net.minecraft.util.Identifier.of("monvhua","textures/gui/clue_folio_aged.png");
    /** Rules are drawn only in text regions; a glyph baseline is always 9 pixels above its rule. */
    static void rules(DrawContext c,int x,int textY,int w,int rows,int step){
        for(int i=0;i<rows;i++)c.fill(x,textY+9+i*step,x+w,textY+10+i*step,0x26705b3b);
    }
    static void paper(DrawContext c,int x,int y,int w,int h){
        if(w<=0||h<=0)return;
        c.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,AGED,x,y,0f,0f,w,h,w,h);
    }
    protected void background(DrawContext c,String subtitle){
        c.fill(0,0,width,height,0x54100d08);
        paper(c,fx,fy,fw,fh);
        c.fill(fx+6,fy+27,fx+fw-6,fy+28,BRASS);
        c.drawText(textRenderer,textRenderer.trimToWidth(title.getString(),Math.max(0,titleWidth())),fx+12,fy+10,INK,false);
        if(!subtitle.isEmpty())c.drawText(textRenderer,textRenderer.trimToWidth(subtitle,fw-24),fx+12,fy+34,MUTED,false);

    }
    protected int titleWidth(){return Math.max(40,fw-160);}
    protected void line(DrawContext c,String text,int x,int y,int w,int color){c.drawText(textRenderer,textRenderer.trimToWidth(text,Math.max(1,w)),x,y,color,false);}
    protected void status(DrawContext c,String text){line(c,text,fx+10,fy+fh-14,fw-20,RED);}
}

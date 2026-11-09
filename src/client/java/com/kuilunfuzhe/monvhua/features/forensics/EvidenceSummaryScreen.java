package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Fullscreen host; world panels share the same presentation and evidence state. */
public final class EvidenceSummaryScreen extends Screen {
    private final EvidencePanel panel = new EvidencePanel(false,this::close);
    public EvidenceSummaryScreen(){super(Text.literal("案件线索总结"));}
    @Override protected void init(){panel.resize(width,height);panel.children().forEach(this::addSelectableChild);}
    @Override public boolean shouldPause(){return false;}
    @Override public void removed(){panel.dispose();}
    @Override public void render(DrawContext c,int x,int y,float delta){panel.render(c,x,y,delta);}
    @Override public boolean mouseClicked(double x,double y,int b){return panel.mouseClicked(x,y,b);}
    @Override public boolean mouseReleased(double x,double y,int b){return panel.mouseReleased(x,y,b);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return panel.mouseDragged(x,y,b,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){return panel.mouseScrolled(x,y,h,v);}
}

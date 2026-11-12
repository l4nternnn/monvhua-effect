package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Presentation is deliberate; cancellation restores the current pair. */
final class ClueCardConfirmScreen extends ClueFolioScreen {
    private final Screen parent;
    private final String message;
    private final Runnable action;
    private final String confirmLabel,subtitle;
    ClueCardConfirmScreen(Screen parent,String message,Runnable action){this(parent,"确认出示","确认出示","出示后，该版本不可修改",message,action);}
    ClueCardConfirmScreen(Screen parent,String title,String confirmLabel,String subtitle,String message,Runnable action){super(title);this.parent=parent;this.message=message;this.action=action;this.confirmLabel=confirmLabel;this.subtitle=subtitle;}
    @Override protected void init(){layout(400,230);button(confirmLabel,fx+12,fy+fh-42,(fw-30)/2,()->{client.setScreen(parent);action.run();});button("取消",fx+18+(fw-30)/2,fy+fh-42,(fw-30)/2,this::close);}
    @Override public void close(){client.setScreen(parent);}
    @Override public void render(DrawContext c,int mx,int my,float delta){background(c,subtitle);var lines=textRenderer.wrapLines(Text.literal(message),fw-32);for(int i=0;i<lines.size()&&i<(fh-116)/14;i++)c.drawText(textRenderer,lines.get(i),fx+16,fy+62+i*14,INK,false);super.render(c,mx,my,delta);}
}

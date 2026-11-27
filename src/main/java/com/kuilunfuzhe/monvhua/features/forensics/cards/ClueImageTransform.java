package com.kuilunfuzhe.monvhua.features.forensics.cards;

/** Screen-space transform for a fixed 4:3 record image. No world/camera state. */
public final class ClueImageTransform {
    private double left,top,viewWidth,viewHeight,x,y,fit,scale;
    public void resize(double left,double top,double width,double height){
        this.left=left;this.top=top;viewWidth=Math.max(1,width);viewHeight=Math.max(1,height);
        fit=Math.min(viewWidth/768,viewHeight/576);scale=fit;
        x=left+(viewWidth-imageWidth())/2;y=top+(viewHeight-imageHeight())/2;
    }
    public void zoom(double mouseX,double mouseY,double amount){
        double next=Math.clamp(scale*Math.pow(1.2,Math.clamp(amount,-10,10)),fit*.5,fit*8);
        double ratio=next/scale;
        x=mouseX-(mouseX-x)*ratio;y=mouseY-(mouseY-y)*ratio;scale=next;clamp();
    }
    public void pan(double dx,double dy){x+=dx;y+=dy;clamp();}
    private void clamp(){
        double marginX=Math.min(viewWidth,imageWidth())*.25,marginY=Math.min(viewHeight,imageHeight())*.25;
        x=Math.clamp(x,left+marginX-imageWidth(),left+viewWidth-marginX);
        y=Math.clamp(y,top+marginY-imageHeight(),top+viewHeight-marginY);
    }
    public double x(){return x;}
    public double y(){return y;}
    public double imageWidth(){return 768*scale;}
    public double imageHeight(){return 576*scale;}
    public double zoom(){return scale/fit;}
}

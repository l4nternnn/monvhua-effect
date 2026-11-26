package com.kuilunfuzhe.monvhua.features.forensics;

import java.util.*;

/** Bounded pure pixel samples. No entity, model, texture, pose or observation references. */
public final class WoundSampleCache {
    public static final WoundSampleCache SHARED = new WoundSampleCache(128, 262144);
    public record Pixel(int x, int y, int color) {}
    public record Sample(int gridWidth, int gridHeight, List<Pixel> pixels) {
        public Sample { pixels = List.copyOf(pixels); }
    }
    private record Key(String kind,int seed,String severity,double width,double height,double u,double v) {}
    private final LinkedHashMap<Key,Sample> samples = new LinkedHashMap<>(16,.75f,true);
    private final int capacity, pixelBudget;
    private int pixels;
    public WoundSampleCache(int capacity,int pixelBudget) {
        if(capacity<1||pixelBudget<1)throw new IllegalArgumentException("Positive cache limits required");
        this.capacity=capacity;this.pixelBudget=pixelBudget;
    }
    public synchronized int size(){return samples.size();}
    public synchronized int pixels(){return pixels;}
    public synchronized void clear(){samples.clear();pixels=0;}
    public synchronized Sample sample(String kind,int seed,String severity,double width,double height,double u,double v){
        if(!Double.isFinite(width)||!Double.isFinite(height)||width<=0||height<=0||width>64||height>64
                ||!Double.isFinite(u)||!Double.isFinite(v)||u<0||u>1||v<0||v>1)
            return new Sample(0,0,List.of());
        String normalized="mild".equals(severity)?"mild":"severe".equals(severity)?"severe":"moderate";
        var key=new Key(kind,seed,normalized,width,height,u,v);
        var found=samples.get(key);if(found!=null)return found;
        var stamp=kind==null?null:WoundAppearance.create(kind,seed,normalized);
        if(stamp==null)return new Sample(0,0,List.of());
        int gw=(int)Math.ceil(width*8),gh=(int)Math.ceil(height*8);
        int minX=Math.max(0,(int)Math.floor((u-stamp.radius()/width)*gw));
        int maxX=Math.min(gw-1,(int)Math.ceil((u+stamp.radius()/width)*gw));
        int minY=Math.max(0,(int)Math.floor((v-stamp.radius()/height)*gh));
        int maxY=Math.min(gh-1,(int)Math.ceil((v+stamp.radius()/height)*gh));
        var result=new ArrayList<Pixel>();
        for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++){
            int color=stamp.color(((x+.5)/gw-u)*width,((y+.5)/gh-v)*height);
            if((color>>>24)!=0)result.add(new Pixel(x,y,color));
        }
        var sample=new Sample(gw,gh,result);
        if(result.size()>pixelBudget)return sample;
        while(samples.size()>=capacity||pixels+result.size()>pixelBudget){
            var oldest=samples.pollFirstEntry();pixels-=oldest.getValue().pixels().size();
        }
        samples.put(key,sample);pixels+=result.size();return sample;
    }
}

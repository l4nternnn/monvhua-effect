package com.kuilunfuzhe.monvhua.features.forensics.butterfly;

import net.minecraft.util.math.Vec3d;
import java.util.List;
import java.util.TreeMap;

/** Plays each validated server sample once; missing samples hold without extrapolation. */
public final class ButterflyPlayback {
    private final TreeMap<Long,Vec3d> points=new TreeMap<>();
    private long cursor;
    private Vec3d anchor;
    public ButterflyPlayback(long tick,Vec3d initial){cursor=tick-4;anchor=initial;}
    public void accept(long tick,List<Vec3d> trail){
        if(trail.size()!=5)throw new IllegalArgumentException("Five samples required");
        for(int i=0;i<5;i++)points.put(tick-4+i,trail.get(i));
        while(points.size()>16)points.pollFirstEntry();
    }
    public Vec3d next(){
        if(!points.isEmpty()){
            cursor=Math.max(cursor,points.firstKey());
            var sample=points.get(cursor);
            if(sample!=null){anchor=sample;cursor++;}
        }
        return anchor;
    }
}

package com.kuilunfuzhe.monvhua.features.forensics;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WoundSampleCacheTest {
    @Test void cachedPixelsMatchFullFaceSamplingIncludingClippedEdges(){
        var cache=new WoundSampleCache(16,100000);
        for(String kind:List.of("cut","puncture","blunt"))for(String severity:List.of("mild","moderate","severe"))
            for(int seed:new int[]{-1,0,123})for(double u:new double[]{0,.5,1}){
                var sample=cache.sample(kind,seed,severity,8,12,u,.25);
                var stamp=WoundAppearance.create(kind,seed,severity);
                var expected=new ArrayList<WoundSampleCache.Pixel>();
                for(int y=0;y<96;y++)for(int x=0;x<64;x++){
                    int color=stamp.color(((x+.5)/64-u)*8,((y+.5)/96-.25)*12);
                    if((color>>>24)!=0)expected.add(new WoundSampleCache.Pixel(x,y,color));
                }
                assertEquals(expected,sample.pixels());
            }
    }
    @Test void hitsReuseImmutableSamplesAndLruIsBounded(){
        var cache=new WoundSampleCache(2,10000);
        var a=cache.sample("cut",1,"mild",8,12,.5,.5);
        var b=cache.sample("cut",2,"mild",8,12,.5,.5);
        assertSame(a,cache.sample("cut",1,"mild",8,12,.5,.5));
        cache.sample("cut",3,"mild",8,12,.5,.5);
        assertEquals(2,cache.size());
        assertNotSame(b,cache.sample("cut",2,"mild",8,12,.5,.5));
        assertThrows(UnsupportedOperationException.class,()->a.pixels().clear());
        cache.clear();assertEquals(0,cache.size());assertEquals(0,cache.pixels());
    }
    @Test void pixelBudgetAndInvalidInputsCannotGrowCache(){
        var cache=new WoundSampleCache(2,1);
        assertFalse(cache.sample("cut",1,"severe",8,12,.5,.5).pixels().isEmpty());
        assertEquals(0,cache.size());
        for(double width:new double[]{Double.NaN,Double.POSITIVE_INFINITY,0,-1,1000})
            assertTrue(cache.sample("cut",1,"mild",width,12,.5,.5).pixels().isEmpty());
        assertTrue(cache.sample("unknown",1,"mild",8,12,.5,.5).pixels().isEmpty());
        assertEquals(0,cache.pixels());
    }
}

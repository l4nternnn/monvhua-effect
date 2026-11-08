package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPlaybackClock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhysicsPlaybackClockTest {
    @Test void jitteredBatchesNeverRewindAtAnyRenderRate(){
        for(int fps:new int[]{30,60,144}){
            var clock=new PhysicsPlaybackClock();double previous=-1;int published=0;
            for(int i=0;i<fps*10;i++){
                long now=(long)(i*1e9/fps);
                // Vary delivery delay independently of the render clock, including repeated batches.
                double delayed=Math.max(0,i/(double)fps-(i%7==0?.08:.01));
                published=Math.max(published,3*(int)(delayed*20));
                double shown=clock.advance(Math.max(0,published-11),published,now,false);
                assertTrue(shown>=previous,"rewound at "+fps+" FPS");assertTrue(shown<=published);previous=shown;
            }
            assertTrue(previous>500,"Playback failed to keep progressing");
        }
    }
    @Test void pausesAndStallsCannotAuthorizeExtrapolationOrCatchup(){
        var clock=new PhysicsPlaybackClock();clock.advance(0,9,0,false);
        double p=clock.advance(0,9,50_000_000,false);
        assertEquals(p,clock.advance(0,9,20_000_000_000L,true));
        assertTrue(clock.advance(0,9,20_016_000_000L,false)-p<1.1);
        for(int i=1;i<30;i++)assertTrue(clock.advance(0,9,21_000_000_000L+i*16_000_000L,false)<=9);
        assertTrue(clock.waits()>0);
        clock.holdAt(9,23_000_000_000L);
        assertEquals(9,clock.advance(0,12,23_016_000_000L,false));
    }
    @Test void reproducesOldTickResetRegression(){
        double oldBefore=30-3+3*.99,oldAfter=30-3;
        assertTrue(oldAfter<oldBefore);
        var clock=new PhysicsPlaybackClock();clock.advance(19,30,0,false);
        double before=clock.advance(19,30,49_000_000,false);
        assertTrue(clock.advance(19,30,50_000_000,false)>=before);
    }
}

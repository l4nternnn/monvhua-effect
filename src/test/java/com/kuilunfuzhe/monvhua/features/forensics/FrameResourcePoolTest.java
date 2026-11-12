package com.kuilunfuzhe.monvhua.features.forensics;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrameResourcePoolTest {
    private static final class Target {String image;boolean closed;}
    @Test void equalSizedDeferredPreviewsKeepIndependentImagesAcrossEitherSideChanging(){
        var pool=new FrameResourcePool<>(Target::new,t->t.closed=true);pool.beginFrame();
        var left=pool.acquire();left.image="left wound";var right=pool.acquire();right.image="right wound";
        var deferred=List.of(left,right);assertNotSame(left,right);assertEquals(List.of("left wound","right wound"),deferred.stream().map(t->t.image).toList());
        pool.beginFrame();var nextLeft=pool.acquire();nextLeft.image="different left wound";var nextRight=pool.acquire();nextRight.image="right wound";
        assertSame(left,nextLeft);assertSame(right,nextRight);assertEquals("different left wound",left.image);assertEquals("right wound",right.image);
        pool.beginFrame();pool.acquire().image="different left wound";pool.acquire().image="different right wound";
        assertEquals("different left wound",left.image);assertEquals("different right wound",right.image);assertFalse(left.closed);assertFalse(right.closed);pool.close();
    }
    @Test void shrinkingDoesNotCloseATargetStillReferencedByCurrentDeferredDraws(){
        var released=new ArrayList<Target>();var pool=new FrameResourcePool<>(Target::new,t->{t.closed=true;released.add(t);});
        pool.beginFrame();var first=pool.acquire();var second=pool.acquire();pool.beginFrame();assertFalse(first.closed);assertFalse(second.closed);
        assertSame(first,pool.acquire());pool.beginFrame();assertTrue(second.closed);assertFalse(first.closed);assertEquals(List.of(second),released);
        pool.beginFrame();assertTrue(first.closed);assertEquals(List.of(second,first),released);pool.close();assertEquals(2,released.size());
    }
    @Test void rendererShutdownReleasesAllTargetsExactlyOnce(){
        var released=new ArrayList<Object>();var pool=new FrameResourcePool<>(Object::new,released::add);var a=pool.acquire();var b=pool.acquire();pool.close();pool.close();assertEquals(List.of(a,b),released);
    }
}

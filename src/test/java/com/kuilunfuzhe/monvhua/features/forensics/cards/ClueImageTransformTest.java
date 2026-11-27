package com.kuilunfuzhe.monvhua.features.forensics.cards;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClueImageTransformTest {
    @Test void initialFitKeepsAspectAndCentresTheWholeImage(){
        var t=new ClueImageTransform();t.resize(20,30,1000,600);
        assertEquals(800,t.imageWidth(),1e-8);assertEquals(600,t.imageHeight(),1e-8);
        assertEquals(120,t.x(),1e-8);assertEquals(30,t.y(),1e-8);
    }
    @Test void wheelKeepsTheImagePixelUnderTheMouseFixed(){
        var t=new ClueImageTransform();t.resize(20,30,1000,600);
        double mx=430,my=260,u=(mx-t.x())/t.imageWidth(),v=(my-t.y())/t.imageHeight();
        t.zoom(mx,my,3);
        assertEquals(u,(mx-t.x())/t.imageWidth(),1e-8);assertEquals(v,(my-t.y())/t.imageHeight(),1e-8);
        t.zoom(mx,my,-3);assertEquals(1,t.zoom(),1e-8);
    }
    @Test void extremeDragsCannotLoseTheImageAndZoomHasBounds(){
        var t=new ClueImageTransform();t.resize(12,30,800,600);
        for(int i=0;i<40;i++)t.zoom(412,330,1);
        assertEquals(8,t.zoom(),1e-8);
        t.pan(1e8,-1e8);
        assertTrue(t.x()<812&&t.x()+t.imageWidth()>12);assertTrue(t.y()<630&&t.y()+t.imageHeight()>30);
        for(int i=0;i<80;i++)t.zoom(412,330,-1);
        assertEquals(.5,t.zoom(),1e-8);
    }
}

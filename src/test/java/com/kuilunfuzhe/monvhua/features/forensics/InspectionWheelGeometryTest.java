package com.kuilunfuzhe.monvhua.features.forensics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InspectionWheelGeometryTest {
    @Test void capOccupiesOneFifthAndTorsoSplitsOnlyCap(){
        int cap=0;
        for(int degree=0;degree<360;degree++){
            double a=Math.toRadians(degree+.5);
            double x=40*Math.sin(a),y=-40*Math.cos(a);
            int normal=InspectionWheelGeometry.sector(x,y,50,false,false);
            int torso=InspectionWheelGeometry.sector(x,y,50,true,false);
            if(normal==1){cap++;assertTrue(torso==1||torso==2);}else assertEquals(0,torso);
        }
        assertEquals(72,cap);
        assertEquals(1,InspectionWheelGeometry.sector(-8,-40,50,true,false));
        assertEquals(2,InspectionWheelGeometry.sector(8,-40,50,true,false));
    }
    @Test void releaseHasOnlyOneActionAndCenterIsEmpty(){
        for(int d=0;d<360;d++)assertEquals(0,InspectionWheelGeometry.sector(40*Math.cos(Math.toRadians(d)),40*Math.sin(Math.toRadians(d)),50,true,true));
        assertEquals(-1,InspectionWheelGeometry.sector(0,0,50,false,false));
        assertEquals(-1,InspectionWheelGeometry.sector(51,0,50,false,true));
    }
}

package com.kuilunfuzhe.monvhua.features.forensics;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvidencePanelPlaneTest {
    @Test void rotatedPanelMapsVisualPointsBackToUi(){
        for(int yaw=0;yaw<360;yaw+=45)for(int pitch=-90;pitch<=90;pitch+=45)for(int roll=0;roll<360;roll+=60){
            var q=new Quaternionf().rotationYXZ((float)Math.toRadians(yaw),(float)Math.toRadians(pitch),(float)Math.toRadians(roll));
            var plane=new EvidencePanelPlane(new Vector3d(1000000,72,-1000000),q,4,2.25);
            var normal=new org.joml.Quaterniond(q).normalize().transform(new Vector3d(0,0,1));
            for(double u:new double[]{.001,.25,.5,.999})for(double v:new double[]{.001,.5,.999}){
                var point=plane.world(u,v);var eye=new Vector3d(point).fma(3,normal);
                var hit=plane.intersect(eye,new Vector3d(normal).negate(),5);
                assertNotNull(hit);assertEquals(u,hit.u(),1e-7);assertEquals(v,hit.v(),1e-7);assertEquals(3,hit.distance(),1e-7);
            }
        }
    }
    @Test void rejectsBackParallelRangeAndOutside(){
        var plane=new EvidencePanelPlane(new Vector3d(),new Quaternionf(),4,2.25);
        assertNull(plane.intersect(new Vector3d(0,0,-3),new Vector3d(0,0,1),5));
        assertNull(plane.intersect(new Vector3d(0,0,3),new Vector3d(1,0,0),5));
        assertNull(plane.intersect(new Vector3d(0,0,6),new Vector3d(0,0,-1),5));
        assertNull(plane.intersect(new Vector3d(2.01,0,3),new Vector3d(0,0,-1),5));
        assertNull(plane.intersect(new Vector3d(0,0,3),new Vector3d(),5));
        assertEquals(1,plane.intersect(new Vector3d(2,0,3),new Vector3d(0,0,-1),5).u());
        assertThrows(IllegalArgumentException.class,()->new EvidencePanelPlane(new Vector3d(),new Quaternionf(),0,1));
    }
    @Test void placedPanelFacesPlayerForAllLookDirections(){
        for(int yaw=0;yaw<360;yaw+=30)for(int pitch=-90;pitch<=90;pitch+=30){
            double y=Math.toRadians(yaw),p=Math.toRadians(pitch);
            var look=new Vector3d(-Math.sin(y)*Math.cos(p),-Math.sin(p),Math.cos(y)*Math.cos(p));
            var q=new Quaternionf().rotationYXZ((float)Math.toRadians(180-yaw),(float)-p,0);
            var plane=new EvidencePanelPlane(new Vector3d(look).mul(3),q,4,2.25);
            var hit=plane.intersect(new Vector3d(),look,5);
            assertNotNull(hit);assertEquals(.5,hit.u(),1e-6);assertEquals(.5,hit.v(),1e-6);
        }
    }
}

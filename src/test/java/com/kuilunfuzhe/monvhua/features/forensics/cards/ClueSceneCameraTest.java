package com.kuilunfuzhe.monvhua.features.forensics.cards;

import org.joml.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClueSceneCameraTest {
    @Test void recordPointStaysCenteredFromEverySideAndVerticalView(){
        for(var eye:new Vector3f[]{new Vector3f(0,1,3),new Vector3f(0,1,-3),new Vector3f(3,1,0),new Vector3f(-3,1,0),new Vector3f(0,3,0),new Vector3f(0,-3,0)}){
            var camera=new Matrix4f(ClueSceneCamera.projection(1)).mul(ClueSceneCamera.view(eye));
            var point=camera.transform(new Vector4f(0,0,0,1));assertTrue(camera.isFinite());assertTrue(point.w>0);
            assertEquals(0,point.x/point.w,.0001);assertEquals(0,point.y/point.w,.0001);
        }
    }
    @Test void viewMatchesRaySamplingAxesAndKeepsInputsImmutable(){
        var eye=new Vector3f(2,1,3);var original=new Vector3f(eye);var view=ClueSceneCamera.view(eye);
        var forward=new Vector3f(eye).negate().normalize();var right=new Vector3f(forward).cross(0,1,0).normalize();
        var up=new Vector3f(right).cross(forward).normalize();
        assertTrue(view.transformDirection(new Vector3f(right)).x>.99);assertTrue(view.transformDirection(new Vector3f(up)).y>.99);
        assertEquals(original,eye);assertEquals(view,ClueSceneCamera.view(eye));
    }
    @Test void projectionMatchesSavedFourByThreeRayField(){
        float tangent=1.2f;var projection=ClueSceneCamera.projection((float)(2*java.lang.Math.atan(tangent*.75)));
        var edge=projection.transform(new Vector4f(tangent,tangent*.75f,-1,1));
        assertEquals(1,edge.x/edge.w,.0001);assertEquals(1,edge.y/edge.w,.0001);
    }
    @Test void corruptCameraAndFieldAreRejected(){
        assertThrows(IllegalArgumentException.class,()->ClueSceneCamera.view(new Vector3f()));
        assertThrows(IllegalArgumentException.class,()->ClueSceneCamera.view(new Vector3f(Float.NaN,0,1)));
        assertThrows(IllegalArgumentException.class,()->ClueSceneCamera.projection(Float.NaN));
        assertThrows(IllegalArgumentException.class,()->ClueSceneCamera.projection(0));
    }
}

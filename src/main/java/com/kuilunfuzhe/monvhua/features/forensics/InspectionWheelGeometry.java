package com.kuilunfuzhe.monvhua.features.forensics;

/** 0 inspect/release, 1 reserved drag, 2 reserved torso carry; top cap occupies 72 degrees. */
public final class InspectionWheelGeometry {
    private InspectionWheelGeometry() {}
    public static int sector(double x,double y,double radius,boolean torso,boolean release) {
        double distance=x*x+y*y;
        if(distance<radius*radius*.16||distance>radius*radius)return -1;
        if(release)return 0;
        double angle=Math.atan2(x,-y);
        if(Math.abs(angle)<=Math.PI/5)return torso&&angle>=0?2:1;
        return 0;
    }
}

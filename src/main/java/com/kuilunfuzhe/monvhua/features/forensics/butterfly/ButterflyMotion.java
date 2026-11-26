package com.kuilunfuzhe.monvhua.features.forensics.butterfly;

import net.minecraft.util.math.Vec3d;

/** Pure steering policy. The service must sweep every resulting movement before accepting it. */
public final class ButterflyMotion {
    public static final int MIN_LIFETIME=100,MAX_LIFETIME=416;
    public static final double SPEED=.065;
    private ButterflyMotion() {}
    public static int lifetime(int roll){
        if(roll<0||roll>MAX_LIFETIME-MIN_LIFETIME)throw new IllegalArgumentException("Lifetime roll out of range");
        return MIN_LIFETIME+roll;
    }
    public static double speed(boolean attracted){return SPEED*(attracted?1.1:1);}
    public static Vec3d orbit(Vec3d center,double phase,int age){
        double angle=phase+age*.055;
        return center.add(Math.cos(angle)*1.05,Math.sin(angle*1.7)*.25,Math.sin(angle)*1.05);
    }
    public static Vec3d steer(Vec3d velocity,Vec3d direction,int age,double phase,double speed,boolean orbit){
        if(direction.lengthSquared()<1e-10)return Vec3d.ZERO;
        Vec3d forward=direction.normalize();
        Vec3d side=new Vec3d(-forward.z,0,forward.x).normalize();
        Vec3d wish=forward.add(side.multiply(Math.sin(age*.19+phase)* (orbit?.12:.48)))
                .add(0,Math.sin(age*.13+phase*2)*(orbit?.08:.24),0).normalize();
        Vec3d previous=velocity.lengthSquared()<1e-10?wish:velocity.normalize();
        return previous.lerp(wish,.48).normalize().multiply(speed);
    }
}

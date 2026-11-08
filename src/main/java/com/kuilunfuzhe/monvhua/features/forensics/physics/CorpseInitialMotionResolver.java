package com.kuilunfuzhe.monvhua.features.forensics.physics;

/** Pure, bounded game motion model. Inputs use Minecraft world axes, blocks/tick for baseline. */
public final class CorpseInitialMotionResolver {
    public static final float IMPACT_SPEED=.4f, MAX_BASELINE=.4f, MAX_SPEED=.8f, TILT=.5f, ANGULAR_SPEED=.6f;
    public record Motion(float yaw,float vx,float vz,float dx,float dz,float tilt,
                         String source,String quality,String incoming,String fallback,
                         float baselineX,float baselineZ,boolean clamped,CorpsePhysicsProfiles.Profile profile) {}
    private CorpseInitialMotionResolver(){}
    public static Motion resolve(float bodyYaw,double directionX,double directionZ,double baselineX,double baselineZ,
                                 String source,String quality,String fallback) {
        return resolve(bodyYaw,directionX,directionZ,baselineX,baselineZ,source,quality,fallback,CorpsePhysicsProfiles.F5);
    }
    public static Motion resolve(float bodyYaw,double directionX,double directionZ,double baselineX,double baselineZ,
                                 String source,String quality,String fallback,CorpsePhysicsProfiles.Profile profile) {
        var config=profile.motion();
        if(!Float.isFinite(bodyYaw))bodyYaw=0;
        bodyYaw=(bodyYaw%360+540)%360-180;
        double a=Math.toRadians(bodyYaw),fx=-Math.sin(a),fz=Math.cos(a);
        double length=Math.hypot(directionX,directionZ);
        boolean known=Double.isFinite(length)&&length>1e-5;
        double dx=known?directionX/length:fx,dz=known?directionZ/length:fz;
        if(!known){quality="unknown";if(fallback.isEmpty())fallback="missing_horizontal_direction";}
        // Baseline is captured before vanilla damage knockback, never taken from onDeath velocity.
        double bx=baselineX*20,bz=baselineZ*20;
        if(!Double.isFinite(bx)||!Double.isFinite(bz)){bx=0;bz=0;}
        double b=Math.hypot(bx,bz);boolean clamped=b>config.maxBaseline();
        if(clamped){bx*=config.maxBaseline()/b;bz*=config.maxBaseline()/b;}
        double impact=known?config.impactSpeed():config.unknownSpeed();
        double vx=bx+dx*impact,vz=bz+dz*impact,n=Math.hypot(vx,vz);
        if(n>config.maxSpeed()){vx*=config.maxSpeed()/n;vz*=config.maxSpeed()/n;clamped=true;}
        double front=-dx*fx-dz*fz,right=dx*Math.cos(a)+dz*Math.sin(a);
        String incoming=!known?"unknown":Math.abs(front)>=Math.abs(right)?(front>=0?"front":"back"):(right>=0?"right":"left");
        return new Motion(bodyYaw,(float)vx,(float)vz,(float)dx,(float)dz,config.tilt(),source,quality,incoming,fallback,(float)bx,(float)bz,clamped,profile);
    }
    public static Motion test(float bodyYaw,String incoming) {return test(bodyYaw,incoming,CorpsePhysicsProfiles.F5);}
    public static Motion test(float bodyYaw,String incoming,CorpsePhysicsProfiles.Profile profile) {
        double a=Math.toRadians(bodyYaw),fx=-Math.sin(a),fz=Math.cos(a),rx=-Math.cos(a),rz=-Math.sin(a);
        return switch(incoming) {
            case "front"->resolve(bodyYaw,-fx,-fz,0,0,"debug","synthetic","",profile);
            case "back"->resolve(bodyYaw,fx,fz,0,0,"debug","synthetic","",profile);
            case "left"->resolve(bodyYaw,rx,rz,0,0,"debug","synthetic","",profile);
            case "right"->resolve(bodyYaw,-rx,-rz,0,0,"debug","synthetic","",profile);
            case "unknown"->resolve(bodyYaw,0,0,0,0,"unknown","unknown","no_direct_impact",profile);
            default->throw new IllegalArgumentException("Unknown incoming side");
        };
    }
}

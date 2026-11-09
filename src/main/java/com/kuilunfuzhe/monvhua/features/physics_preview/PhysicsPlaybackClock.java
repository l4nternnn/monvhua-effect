package com.kuilunfuzhe.monvhua.features.physics_preview;

/** Rendering-only time. Never authorizes solver steps, never extrapolates beyond published data. */
public final class PhysicsPlaybackClock {
    private static final double BUFFER=6;
    private double position=Double.NaN;
    private long previousNanos;
    private boolean buffering=true;
    private long waits,resyncs;
    public double advance(long first,long latest,long now,boolean paused){
        if(first<0||latest<first)throw new IllegalArgumentException("Invalid published range");
        if(Double.isNaN(position)){position=first;previousNanos=now;}
        double elapsed=Math.max(0,(now-previousNanos)/1e9);previousNanos=now;
        if(paused)return position;
        if(position<first){position=first;buffering=true;resyncs++;}
        if(buffering){if(latest-position<BUFFER)return position;buffering=false;elapsed=0;}
        double rate=Math.clamp(1+(latest-BUFFER-position)*.02,.9,1.05);
        position=Math.min(latest,position+Math.min(elapsed,.1)*60*rate);
        if(position>=latest){buffering=true;waits++;}
        return position;
    }
    public void holdAt(long step,long now){position=step;previousNanos=now;buffering=true;}
    public double position(){return position;}
    public long waits(){return waits;}
    public long resyncs(){return resyncs;}
}

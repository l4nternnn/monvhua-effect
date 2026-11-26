package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.butterfly.*;
import com.kuilunfuzhe.monvhua.network.forensics.ButterflyPackets;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.*;
import org.junit.jupiter.api.Test;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import io.netty.buffer.Unpooled;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ButterflyPathTest {
    @Test void onlyLargeIdentifiedCorpsePoolsCanEnterTheSourceScan(){
        var large=new com.kuilunfuzhe.monvhua.features.forensics.blood.BloodPatch("pool",BlockPos.ORIGIN,Direction.UP,.5f,.5f,.75f,1,1,1);
        var small=new com.kuilunfuzhe.monvhua.features.forensics.blood.BloodPatch("small",BlockPos.ORIGIN,Direction.UP,.5f,.5f,.2f,1,1,1);
        var drop=new com.kuilunfuzhe.monvhua.features.forensics.blood.BloodPatch("drop",BlockPos.ORIGIN,Direction.UP,.5f,.5f,.75f,0,1,1);
        assertTrue(BloodButterflyService.poolCandidate(new com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceStore.Entry(large,"corpse","site","stone",false)));
        assertFalse(BloodButterflyService.poolCandidate(new com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceStore.Entry(small,"corpse","site","stone",false)));
        assertFalse(BloodButterflyService.poolCandidate(new com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceStore.Entry(drop,"corpse","site","stone",false)));
        assertFalse(BloodButterflyService.poolCandidate(new com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceStore.Entry(large,"","site","stone",false)));
        assertFalse(BloodButterflyService.poolCandidate(new com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceStore.Entry(large,"corpse",ButterflyResidue.EVENT_PREFIX+"site","stone",false)));
        assertTrue(large.mass()>BloodButterflyService.MIN_POOL_MASS);
        assertEquals(0,large.treated("\u0000".repeat(256),2).mass());
    }
    private static List<Vec3d> samples(int last){
        return java.util.stream.IntStream.rangeClosed(last-4,last).mapToObj(i->new Vec3d(i,0,0)).toList();
    }
    @Test void playbackDoesNotRepeatTheBoundarySampleBetweenRegularPackets(){
        var playback=new ButterflyPlayback(4,Vec3d.ZERO);playback.accept(4,samples(4));
        for(int tick=0;tick<40;tick++){
            if(tick>0&&tick%4==0)playback.accept(tick+4,samples(tick+4));
            assertEquals(new Vec3d(tick,0,0),playback.next());
        }
    }
    @Test void playbackHoldsMissingSamplesThenResumesWithoutReplayingConsumedOnes(){
        var playback=new ButterflyPlayback(4,Vec3d.ZERO);playback.accept(4,samples(4));
        for(int tick=0;tick<=4;tick++)assertEquals(new Vec3d(tick,0,0),playback.next());
        assertEquals(new Vec3d(4,0,0),playback.next());
        playback.accept(8,samples(8));assertEquals(new Vec3d(5,0,0),playback.next());
        playback.accept(8,samples(8));assertEquals(new Vec3d(6,0,0),playback.next());
    }
    private static void finish(ButterflyPath search,ButterflyPath.Clearance space){
        for(int i=0;i<4096&&!search.done();i++)search.step(space,8);
        assertTrue(search.done());
    }
    private static boolean segment(Vec3d a,Vec3d b,Box obstacle){
        return !obstacle.contains(a)&&!obstacle.contains(b)&&obstacle.raycast(a,b).isEmpty();
    }
    @Test void diagonalFlightCanChangeAllThreeAxesTogether(){
        Vec3d start=new Vec3d(0,0,0),goal=new Vec3d(4,3,4);
        var search=new ButterflyPath(start,goal);finish(search,(a,b)->true);
        assertEquals(start,search.result().getFirst());assertEquals(goal,search.result().getLast());
        assertTrue(search.result().stream().anyMatch(p->p.x>0&&p.y>0&&p.z>0));
        assertTrue(search.nodes()<=4096);
    }
    @Test void solidWallRequiresActualThreeDimensionalDetour(){
        var wall=new Box(.8,-1,-1,1.2,2,1);
        ButterflyPath.Clearance space=(a,b)->segment(a,b,wall);
        var search=new ButterflyPath(Vec3d.ZERO,new Vec3d(2,0,0));finish(search,space);
        assertFalse(search.result().isEmpty());
        for(int i=1;i<search.result().size();i++)assertTrue(space.clear(search.result().get(i-1),search.result().get(i)));
        assertTrue(search.result().stream().anyMatch(p->Math.abs(p.z)>=1||p.y<=-1||p.y>=2));
    }
    @Test void budgetYieldResumesWithoutInventingAnEdge(){
        var search=new ButterflyPath(Vec3d.ZERO,new Vec3d(3,2,1));
        search.step((a,b)->{throw new ButterflyPath.Yield();},8);
        assertFalse(search.done());assertTrue(search.result().isEmpty());
        finish(search,(a,b)->true);assertFalse(search.result().isEmpty());
    }
    @Test void enclosedOrUnknownSpaceReturnsNoRoute(){
        var search=new ButterflyPath(Vec3d.ZERO,new Vec3d(3,2,1));finish(search,(a,b)->false);
        assertTrue(search.result().isEmpty());
    }
    @Test void repeatedRandomMotionNeverAccumulatesOutsideCorridor(){
        Vec3d offset=Vec3d.ZERO;
        for(int i=0;i<10000;i++){
            offset=ButterflyPath.drift(offset,new Vec3d(.1,.03,.06),.12);
            assertTrue(offset.length()<=.120000001);
        }
        assertTrue(offset.length()>0);
        assertEquals(Vec3d.ZERO,ButterflyPath.drift(offset,new Vec3d(.1,0,0),0));
    }
    @Test void curvedSteeringKeepsMovingAndTurnsWithoutLosingTheSpeedBoost(){
        Vec3d velocity=Vec3d.ZERO;double sideways=0;
        for(int age=1;age<=100;age++){
            velocity=ButterflyMotion.steer(velocity,new Vec3d(1,0,0),age,.7,ButterflyMotion.speed(true),false);
            assertEquals(.065*1.1,velocity.length(),1e-9);sideways+=Math.abs(velocity.z);
        }
        assertTrue(sideways>1);
        for(int age=101;age<107;age++)velocity=ButterflyMotion.steer(velocity,new Vec3d(-1,0,0),age,.7,.065,false);
        assertTrue(velocity.x<0,"Direction should react within six ticks");
    }
    @Test void orbitMovesAroundBothSidesOfThePlayerAndChangesHeight(){
        var center=new Vec3d(10,4,5);double minX=100,maxX=-100,minY=100,maxY=-100;
        for(int age=0;age<160;age++){
            var point=ButterflyMotion.orbit(center,0,age);
            assertEquals(1.05,Math.hypot(point.x-center.x,point.z-center.z),1e-9);
            minX=Math.min(minX,point.x);maxX=Math.max(maxX,point.x);
            minY=Math.min(minY,point.y);maxY=Math.max(maxY,point.y);
        }
        assertTrue(maxX-minX>2);assertTrue(maxY-minY>.4);
    }
    @Test void packetBoundsAndConfigRejectUnboundedWork(){
        assertThrows(IllegalArgumentException.class,()->new ButterflyPackets.Flight(1,2,300,List.of(Vec3d.ZERO)));
        assertThrows(IllegalArgumentException.class,()->new ButterflyPackets.Sync("overworld",1,Double.NaN,List.of()));
        var config=new ForensicConfig.Butterflies();assertTrue(config.valid());config.maxTotal=129;assertFalse(config.valid());
    }
    @Test void publicSnapshotRoundTripsActualTickPoints(){
        var trail=List.of(Vec3d.ZERO,new Vec3d(.03,.01,0),new Vec3d(.06,.02,0),new Vec3d(.09,.03,0),new Vec3d(.12,.04,0));
        var packet=new ButterflyPackets.Sync("minecraft:overworld",100,.12,List.of(new ButterflyPackets.Flight(1,22,300,trail)));
        var buffer=new RegistryByteBuf(Unpooled.buffer(),DynamicRegistryManager.EMPTY);
        try{ButterflyPackets.Sync.CODEC.encode(buffer,packet);assertEquals(packet,ButterflyPackets.Sync.CODEC.decode(buffer));}
        finally{buffer.release();}
    }
    @Test void randomLifetimeRangeIsFiveToTwentyPointEightSeconds(){
        assertEquals(100,ButterflyMotion.lifetime(0));
        int maximum=ButterflyMotion.lifetime(316);
        assertEquals(416,maximum);
        var durations=new HashSet<Integer>();
        for(int roll=0;roll<=316;roll++)durations.add(ButterflyMotion.lifetime(roll));
        assertEquals(317,durations.size());
        assertThrows(IllegalArgumentException.class,()->ButterflyMotion.lifetime(-1));
        assertThrows(IllegalArgumentException.class,()->ButterflyMotion.lifetime(317));
        assertDoesNotThrow(()->new ButterflyPackets.Flight(1,2,maximum,java.util.Collections.nCopies(5,Vec3d.ZERO)));
        assertThrows(IllegalArgumentException.class,()->new ButterflyPackets.Flight(1,2,833,java.util.Collections.nCopies(5,Vec3d.ZERO)));
    }
    @Test void expiryResidueCannotBecomeABreedingSourceAfterPersistence(){
        var patch=new com.kuilunfuzhe.monvhua.features.forensics.blood.BloodPatch("expiry",net.minecraft.util.math.BlockPos.ORIGIN,
                net.minecraft.util.math.Direction.UP,.5f,.5f,.075f,0,1,1);
        var entry=new com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceStore.Entry(patch,"","butterfly-expiry/test","stone",false);
        assertFalse(com.kuilunfuzhe.monvhua.features.forensics.butterfly.ButterflyResidue.isBirthSource(entry));
        var restored=com.kuilunfuzhe.monvhua.features.forensics.blood.BloodPatch.read(patch.write());
        assertEquals(patch,restored);
        assertTrue(com.kuilunfuzhe.monvhua.features.forensics.butterfly.ButterflyResidue.isBirthSource(
                new com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceStore.Entry(restored,"","wound/test","stone",false)));
    }
}

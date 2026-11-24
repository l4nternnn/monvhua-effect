package com.kuilunfuzhe.monvhua.features.forensics.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CorpsePhysicsNetworkTest {
    @Test void sharedViewKeepsTerminalRevisionScaleTopologyAndRestingThroughCodec(){
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        var pose=new com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument("SETTLED",5,
                java.util.Map.of("torso",new com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument.Pose(1,2,3,0,0,0,1)),
                0,"test","standard_six_v1",1.5f);
        var session=new CorpsePhysicsBridge.Session(){
            public void advanceTo(int step){}
            public java.util.List<com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument> frames(){return java.util.List.of(pose);}
            public java.util.concurrent.CompletableFuture<com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument> completion(){return java.util.concurrent.CompletableFuture.completedFuture(pose);}
            public void close(){}
            public CorpsePhysicsBridge.ResidentState residentState(){return new CorpsePhysicsBridge.ResidentState("SLEEPING",123,1,0,false,pose,"");}
        };
        var id=java.util.UUID.randomUUID();var victim=java.util.UUID.randomUUID();
        var view=new CorpsePhysicsCoordinator.LiveView(id,"minecraft:overworld",new net.minecraft.util.math.Vec3d(5,6,7),net.minecraft.item.ItemStack.EMPTY,false,session,victim,9);
        var encoded=CorpsePhysicsNetwork.encodeView(view,pose,123,false,false);
        assertTrue(encoded.getBoolean("resting",false));assertEquals(9,encoded.getLong("revision",0));
        assertEquals(123,encoded.getLong("tick",0));assertEquals(1.5f,encoded.getFloat("scale",0));
        assertEquals(victim.toString(),encoded.getString("victim",""));
        var root=new net.minecraft.nbt.NbtCompound();var list=new net.minecraft.nbt.NbtList();list.add(encoded);root.put("views",list);root.putString("dimension",view.dimension());
        var buffer=new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.registry.DynamicRegistryManager.EMPTY);
        try{
            CorpsePhysicsNetwork.Snapshot.CODEC.encode(buffer,new CorpsePhysicsNetwork.Snapshot(root));
            assertEquals(root,CorpsePhysicsNetwork.Snapshot.CODEC.decode(buffer).data());
        }finally{buffer.release();}
        var frozen=CorpsePhysicsNetwork.encodeView(view,pose,77,true,true);
        assertEquals(0,frozen.getLong("revision",-1));assertTrue(frozen.getBoolean("frozen",false));assertTrue(frozen.getBoolean("pinned",false));
        assertEquals(77,frozen.getLong("tick",0));
    }
    @Test void grabMarkersPreserveWorldCoordinatesAndRejectMissingOrInvalidPoints(){
        var actual=new net.minecraft.util.math.Vec3d(-72.125,68.75,-99.5);
        var target=new net.minecraft.util.math.Vec3d(-71.25,69.5,-98.25);
        var original=new CorpseGrabController.Status(java.util.UUID.randomUUID(),"left_arm",true,false,2.75,actual,target,"");
        var encoded=CorpsePhysicsNetwork.encodeGrabStatus(original);
        assertEquals(original,CorpsePhysicsNetwork.decodeGrabStatus(encoded));
        var missing=encoded.copy();missing.remove("actual");assertNull(CorpsePhysicsNetwork.decodeGrabStatus(missing));
        encoded.getCompoundOrEmpty("target").putDouble("x",Double.NaN);
        assertNull(CorpsePhysicsNetwork.decodeGrabStatus(encoded));
        assertNull(CorpsePhysicsNetwork.decodeGrabStatus(new net.minecraft.nbt.NbtCompound()));
    }
    @Test void rejectsNonfiniteAndExcessiveNetworkForces(){
        assertTrue(CorpsePhysicsNetwork.validInput(8,0,2,0));
        assertTrue(CorpsePhysicsNetwork.validInput(-8,1,1,1));
        assertFalse(CorpsePhysicsNetwork.validInput(8.1,0,0,0));
        assertFalse(CorpsePhysicsNetwork.validInput(0,2,1,0));
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.MAX_VALUE}){
            assertFalse(CorpsePhysicsNetwork.validInput(bad,0,0,0));
            assertFalse(CorpsePhysicsNetwork.validInput(0,bad,0,0));
            assertFalse(CorpsePhysicsNetwork.validInput(0,0,bad,0));
            assertFalse(CorpsePhysicsNetwork.validInput(0,0,0,bad));
        }
    }
}

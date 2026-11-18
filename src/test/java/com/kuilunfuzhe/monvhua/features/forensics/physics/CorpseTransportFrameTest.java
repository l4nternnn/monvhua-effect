package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.forensics.CorpsePoseRecord;
import com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class CorpseTransportFrameTest {
    @Test void firstInteractionCanUseSeedBeforeWorkerPublishesThenFollowsMovingFrame(){
        SharedConstants.createGameVersion();Bootstrap.initialize();
        var parts=Map.of("torso",new PhysicsPoseDocument.Pose(0,1,0,0,0,0,1));
        var record=new CorpsePoseRecord(1,"generated","default",parts,"standard_six_v1",1.5f);
        var n=new NbtCompound();n.put(CorpsePoseRecord.KEY,record.encode());
        var item=new ItemStack(Items.NETHERITE_SCRAP);item.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(n));
        var session=new FakeSession();
        var initial=CorpseTransportController.transportFrame(session,item);
        assertEquals(parts,initial.poses());assertEquals(1.5f,initial.scale());
        var moving=new PhysicsPoseDocument("SIMULATING",12,Map.of("torso",new PhysicsPoseDocument.Pose(2,1,0,0,0,0,1)),0,"moving","standard_six_v1",1.5f);
        session.frames=List.of(moving);
        assertSame(moving,CorpseTransportController.transportFrame(session,item));
    }
    private static final class FakeSession implements CorpsePhysicsBridge.Session {
        List<PhysicsPoseDocument> frames=List.of();
        public void advanceTo(int step){}
        public List<PhysicsPoseDocument> frames(){return frames;}
        public CompletableFuture<PhysicsPoseDocument> completion(){return new CompletableFuture<>();}
        public void close(){}
    }
}

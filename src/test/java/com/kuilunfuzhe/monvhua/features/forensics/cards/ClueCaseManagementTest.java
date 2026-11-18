package com.kuilunfuzhe.monvhua.features.forensics.cards;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.nbt.*;
import net.minecraft.server.command.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClueCaseManagementTest {
    @Test void defaultNumbersPersistWrapAndNeverOverwriteExistingCases(){
        var s=new ClueCardStore();var player=UUID.randomUUID();
        for(int i=1;i<=9;i++){String id=s.nextDefaultCaseId();assertEquals("case_"+i,id);s.addCase(id,Map.of(player,"测试"));s=roundtrip(s);}
        var full=s;assertThrows(IllegalArgumentException.class,full::nextDefaultCaseId);
        s.deleteCase("case_2");assertEquals("case_2",s.nextDefaultCaseId());assertEquals(8,s.cases().size());
        assertTrue(s.hasCase("case_1"));assertTrue(s.hasCase("case_9"));
    }
    private final UUID a=UUID.randomUUID(),b=UUID.randomUUID();
    private Map<UUID,String> members(){return Map.of(a,"甲",b,"乙");}
    private String card(ClueCardStore store,UUID owner){return store.create(owner,new ClueCardStore.Fields("标题","","","",""),new NbtCompound(),new NbtCompound());}
    private ClueCardStore roundtrip(ClueCardStore store){return ClueCardStore.CODEC.parse(NbtOps.INSTANCE,ClueCardStore.CODEC.encodeStart(NbtOps.INSTANCE,store).getOrThrow()).getOrThrow();}
    @Test void addingDoesNotOpenAndDistributionRequiresOpenCaseAndItsMembers(){
        var store=new ClueCardStore();assertThrows(IllegalArgumentException.class,()->store.requireDistribution(List.of(a)));
        store.addCase("case1",members());assertEquals("",store.session());assertFalse(store.recording(a));assertThrows(IllegalArgumentException.class,()->store.requireDistribution(List.of(a)));
        store.openCase("case1");assertTrue(store.recording(a));assertDoesNotThrow(()->store.requireDistribution(List.of(a,b)));
        assertThrows(IllegalArgumentException.class,()->store.requireDistribution(List.of(a,UUID.randomUUID())));
        store.closeCase("case1");assertFalse(store.recording(a));assertThrows(IllegalArgumentException.class,()->store.requireDistribution(List.of(a)));
    }
    @Test void switchingAndReopeningRetainsSeparateCardsAndComparisonHistory(){
        var store=new ClueCardStore();store.start("first",members());String one=card(store,a),two=card(store,a);store.compare(a,one,two);
        store.addCase("second",Map.of(b,"乙"));store.openCase("second");assertFalse(store.participant(a));assertFalse(store.readable(b,one));assertTrue(store.history(b).isEmpty());
        String other=card(store,b);assertEquals(1,store.cases().stream().filter(ClueCardStore.CaseEntry::open).count());
        store.closeCase("second");assertTrue(store.readable(b,other));assertThrows(IllegalArgumentException.class,()->card(store,b));
        store.openCase("first");assertArrayEquals(new String[]{one,two},store.latest(b));assertFalse(store.readable(b,other));assertEquals(2,store.summaries(a).size());
        var restored=roundtrip(store);assertEquals(store.cases(),restored.cases());assertEquals(store.history(b),restored.history(b));
    }
    @Test void deletePurgesCardsCopiesSharesAndHistoryWithoutDeletingOtherCases(){
        var store=new ClueCardStore();store.start("first",members());String one=card(store,a),copy=store.duplicate(a,one);store.lock(a,one);store.rememberShare("share",b,one);store.openShare(b,"share");store.collect(b,one);store.compare(a,one,copy);
        store.start("second",members());String keep=card(store,b);store.deleteCase("first");assertTrue(store.readable(b,keep));assertEquals(1,store.cases().size());
        assertThrows(IllegalArgumentException.class,()->store.openCase("first"));assertFalse(store.hasCase("first"));assertEquals("",store.openShare(b,"share"));
        var encoded=ClueCardStore.CODEC.encodeStart(NbtOps.INSTANCE,store).getOrThrow();var n=(NbtCompound)encoded;
        assertFalse(n.getCompoundOrEmpty("cards").contains(one));assertFalse(n.getCompoundOrEmpty("cards").contains(copy));assertFalse(n.getCompoundOrEmpty("snapshots").contains(one));assertFalse(n.getCompoundOrEmpty("shares").contains("share"));
        assertFalse(n.getCompoundOrEmpty("collected").getCompoundOrEmpty(b.toString()).contains(one));
        store.deleteCase("second");assertEquals("",store.session());assertTrue(store.summaries(b).isEmpty());assertTrue(roundtrip(store).cases().isEmpty());
    }
    @Test void defaultsAndRevisionPersistAndLegacyRunRemainsReadable(){
        var store=new ClueCardStore();assertEquals(1,store.defaultBags());assertEquals(8,store.defaultCards());int rev=store.adminRevision();store.defaults(2,12);assertTrue(store.adminRevision()>rev);
        assertThrows(IllegalArgumentException.class,()->store.defaults(0,8));assertThrows(IllegalArgumentException.class,()->store.defaults(1,65));
        var restored=roundtrip(store);assertEquals(2,restored.defaultBags());assertEquals(12,restored.defaultCards());assertEquals(store.adminRevision(),restored.adminRevision());
        var legacy=new NbtCompound();legacy.putString("current","legacy");var runs=new NbtCompound();var run=new NbtCompound();var players=new NbtCompound();players.putString(a.toString(),"甲");run.put("players",players);run.putBoolean("recording",true);runs.put("legacy",run);legacy.put("runs",runs);
        var migrated=ClueCardStore.CODEC.parse(NbtOps.INSTANCE,legacy).getOrThrow();assertTrue(migrated.recording(a));assertEquals("legacy",migrated.cases().getFirst().id());assertEquals(8,migrated.defaultCards());
    }
    @Test void invalidOrReusedCasesFailWithoutChangingTheActiveCase(){
        var store=new ClueCardStore();store.start("first",members());assertThrows(IllegalArgumentException.class,()->store.addCase("bad id",members()));assertThrows(IllegalArgumentException.class,()->store.addCase("empty",Map.of()));
        assertThrows(IllegalArgumentException.class,()->store.start("first",members()));assertThrows(IllegalArgumentException.class,()->store.openCase("missing"));assertEquals("first",store.session());assertTrue(store.recording(a));
    }
    @Test void recreatingSameNameUsesNewBlankCardIdentityAndRestoresAfterRestart(){
        var store=new ClueCardStore();store.start("reused",members());String oldToken=store.accessToken(),oldCard=card(store,a);store.deleteCase("reused");store.start("reused",members());
        assertEquals("reused",store.session());assertNotEquals(oldToken,store.accessToken());assertFalse(store.readable(a,oldCard));assertTrue(store.summaries(a).isEmpty());
        var restored=roundtrip(store);assertEquals(store.accessToken(),restored.accessToken());assertTrue(restored.recording(a));
        var legacy=new NbtCompound();var deleted=new NbtCompound();deleted.putBoolean("used_before",true);legacy.put("deleted_runs",deleted);
        var migrated=ClueCardStore.CODEC.parse(NbtOps.INSTANCE,legacy).getOrThrow();assertDoesNotThrow(()->migrated.start("used_before",members()));assertNotEquals("used_before",migrated.accessToken());
    }
    @Test void stableIdentityReconnectRetainsPermissionsAndChangedIdentityNeedsExplicitAdminAction(){
        var store=new ClueCardStore();store.start("reconnect",members());String id=card(store,a);String token=store.accessToken();var restored=roundtrip(store);
        assertTrue(restored.recording(a));assertTrue(restored.readable(a,id));assertEquals(token,restored.accessToken());
        var changed=UUID.randomUUID();assertFalse(restored.recording(changed));restored.closeCase("reconnect");restored.openCase("reconnect");assertFalse(restored.recording(changed));
        restored.rebindMember("reconnect","甲",changed,"新甲");assertTrue(restored.recording(changed));assertTrue(restored.owns(changed,id));assertFalse(restored.participant(a));assertEquals(token,restored.accessToken());
        assertTrue(roundtrip(restored).owns(changed,id));
    }
    @Test void rebindTransfersOwnerReadersCollectionsSharesAndComparisonPresenterOnlyInSelectedCase(){
        var store=new ClueCardStore();store.start("other",members());String other=card(store,a);store.start("repair",members());String one=card(store,a),two=card(store,a);
        store.lock(a,one);store.rememberShare("share",b,one);store.openShare(b,"share");store.collect(b,one);store.compare(a,one,two);
        var newA=UUID.randomUUID();var newB=UUID.randomUUID();store.rebindMember("repair","甲",newA,"新甲");store.rebindMember("repair",b.toString(),newB,"新乙");
        assertTrue(store.owns(newA,one));assertTrue(store.readable(newB,one));assertFalse(store.participant(a));assertFalse(store.participant(b));assertEquals("新甲",store.history(newB).getFirst().presenter());
        assertFalse(store.collect(newB,one));assertEquals(one,store.openShare(newB,"share"));assertEquals("",store.openShare(b,"share"));
        assertThrows(IllegalArgumentException.class,()->store.rebindMember("repair","新甲",newB,"新乙"));assertThrows(IllegalArgumentException.class,()->store.rebindMember("repair","不存在",a,"甲"));
        store.openCase("other");assertTrue(store.owns(a,other));assertFalse(store.participant(newA));
    }
    @Test void addingParticipantsPreservesExistingRecordsAndChecksCapacity(){
        var store=new ClueCardStore();store.start("members",members());String id=card(store,a);var c=UUID.randomUUID();store.addMembers("members",Map.of(c,"丙"));
        assertTrue(store.recording(c));assertTrue(store.owns(a,id));assertFalse(store.readable(c,id));
        var tooMany=new LinkedHashMap<UUID,String>();for(int i=0;i<16;i++)tooMany.put(UUID.randomUUID(),"extra"+i);
        assertThrows(IllegalArgumentException.class,()->store.addMembers("members",tooMany));assertEquals(3,store.cases().getFirst().players().size());
    }
    @Test void adminCommandsRequirePermissionAndSeparateDistributionRequiresExplicitTargetAndCount(){
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        var dispatcher=new CommandDispatcher<ServerCommandSource>();dispatcher.register(net.minecraft.server.command.CommandManager.literal("mvclue").then(ClueCaseAdmin.commands("case")).then(ClueCaseAdmin.commands("experiment")));
        var normal=new ServerCommandSource(CommandOutput.DUMMY,Vec3d.ZERO,Vec2f.ZERO,null,0,"test",Text.literal("test"),null,null);
        var admin=normal.withLevel(2);
        for(String root:List.of("case","experiment")){var node=dispatcher.getRoot().getChild("mvclue").getChild(root);assertFalse(node.canUse(normal));assertTrue(node.canUse(admin));
            assertNotNull(node.getChild("start").getChild("id").getCommand());
            assertNotNull(node.getChild("start").getCommand());
            assertNotNull(node.getChild("close").getCommand());
            for(String selector:List.of("@a","@a[tag=prisoner]","@s")){
                var parsed=dispatcher.parse("mvclue "+root+" start "+selector,admin);
                assertNotNull(parsed.getContext().getCommand());assertFalse(parsed.getReader().canRead());
                assertTrue(parsed.getContext().getArguments().containsKey("default_players"));
            }
            var named=dispatcher.parse("mvclue "+root+" start case_1",admin);
            assertTrue(named.getContext().getArguments().containsKey("id"));
            for(String give:List.of("give_cards","give_bags")){var target=node.getChild(give).getChild("players");assertNull(node.getChild(give).getCommand());assertNull(target.getCommand());assertNotNull(target.getChild("count").getCommand());}
        }
    }
}

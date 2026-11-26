package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.blood.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponBloodDepositsTest {
    private static NbtCompound hit(String id,String part){
        var h=new NbtCompound();h.putString("id",id);h.putString("part",part);h.putFloat("amount",4);return h;
    }
    private static NbtCompound ledger(){
        var n=new NbtCompound();n.putBoolean("pool_initialized",true);n.putBoolean("normal_gravity",true);
        n.putLong("pool_start",0);n.putInt("impact_remaining",7);
        var balances=new NbtCompound();balances.putInt("torso",2);balances.putInt("head",1);n.put("balances",balances);return n;
    }
    @Test void postmortemSpendsOnlyHitPartAndDoesNotRefreshExpiry(){
        var n=ledger();var store=new BloodTransferStore();
        long now=ForensicConfig.current.blood.poolTicks-1L;
        assertEquals(1,WeaponBloodDeposits.deposit(store,n,hit("a","torso"),"weapon","life",true,now));
        assertEquals(1,BloodRules.remaining(n,"torso"));assertEquals(1,BloodRules.remaining(n,"head"));
        assertEquals(7,n.getInt("impact_remaining",0));
        assertEquals(1,store.visible("weapon"));assertEquals(1,store.available("weapon",now));
        assertEquals(0,store.available("weapon",now+1));assertEquals("life",store.residueSummary("weapon"));
        assertEquals(0,WeaponBloodDeposits.deposit(store,n,hit("b","head"),"other","life",true,now+1));
    }
    @Test void replayAndNormalReloadCannotSpendTwice(){
        var n=ledger();var store=new BloodTransferStore();var hit=hit("same","torso");
        assertEquals(1,WeaponBloodDeposits.deposit(store,n,hit,"weapon","life",true,1));
        var saved=n.copy();
        store=BloodTransferStore.CODEC.parse(NbtOps.INSTANCE,BloodTransferStore.CODEC.encodeStart(NbtOps.INSTANCE,store).getOrThrow()).getOrThrow();
        assertEquals(0,WeaponBloodDeposits.deposit(store,saved,hit,"weapon","life",true,2));
        assertEquals(1,store.visible("weapon"));assertEquals(1,BloodRules.remaining(saved,"torso"));
    }
    @Test void exhaustedOrLegacySourcesAndFullReceiversProduceNothing(){
        var n=ledger();var store=new BloodTransferStore();
        for(int i=0;i<2;i++)assertEquals(1,WeaponBloodDeposits.deposit(store,n,hit("hit"+i,"torso"),"weapon","life",true,1));
        assertEquals(0,WeaponBloodDeposits.deposit(store,n,hit("extra","torso"),"weapon","life",true,1));
        assertEquals(0,WeaponBloodDeposits.deposit(store,new NbtCompound(),hit("legacy","torso"),"weapon","life",true,1));
        store.add("full","x",16,100,0);
        int before=BloodRules.remaining(n,"head");
        assertEquals(0,WeaponBloodDeposits.deposit(store,n,hit("full","head"),"full","life",true,1));
        assertEquals(before,BloodRules.remaining(n,"head"));
    }
    @Test void livingContactSharesImpactBudgetAndMixedDamageHasOneReceipt(){
        var n=ledger();var store=new BloodTransferStore();var h=hit("mixed","torso");
        h.putString("kinds","cut+puncture");
        assertEquals(1,WeaponBloodDeposits.deposit(store,n,h,"weapon","life",false,1));
        assertEquals(0,WeaponBloodDeposits.deposit(store,n,h,"weapon","life",false,1));
        assertEquals(6,n.getInt("impact_remaining",0));assertEquals(2,BloodRules.remaining(n,"torso"));
        assertEquals(1,store.visible("weapon"));
    }
    @Test void fullEventJournalAndInvalidHitsLeaveBalancesUntouched(){
        var n=ledger();var events=new NbtList();
        for(int i=0;i<WeaponBloodDeposits.MAX_EVENTS;i++)events.add(NbtString.of("e"+i));
        n.put("weapon_events",events);var store=new BloodTransferStore();
        assertEquals(0,WeaponBloodDeposits.deposit(store,n,hit("new","torso"),"weapon","life",false,1));
        assertEquals(0,store.visible("weapon"));assertEquals(7,n.getInt("impact_remaining",0));
        n.remove("weapon_events");var invalid=hit("bad","torso");invalid.putFloat("amount",Float.NaN);
        assertEquals(0,WeaponBloodDeposits.deposit(store,n,invalid,"weapon","life",false,1));
    }
}

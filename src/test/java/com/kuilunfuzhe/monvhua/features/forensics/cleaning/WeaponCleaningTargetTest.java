package com.kuilunfuzhe.monvhua.features.forensics.cleaning;

import com.kuilunfuzhe.monvhua.features.forensics.blood.*;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponCleaningTargetTest {
    @BeforeAll static void bootstrap(){SharedConstants.createGameVersion();Bootstrap.initialize();}
    @Test void capturingAndCleaningLeaveAllItemComponentsUntouched(){
        var store=new BloodTransferStore();store.add("residue","life",4,100,0);
        var stack=new ItemStack(Items.DIAMOND_SWORD);stack.setDamage(51);
        stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal("自定义武器"));
        var custom=new NbtCompound();custom.putString(BloodTransferService.ITEM_REF,"residue");custom.putString("other_mod_field","keep");
        stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(custom));
        var before=stack.copy().getComponents();
        var target=ForensicCleaningApi.weaponTarget(store,stack).orElseThrow();
        assertEquals("residue",target.residueId());
        store.clean(target.residueId(),.5,1,false);
        assertEquals(target,ForensicCleaningApi.weaponTarget(store,stack).orElseThrow());
        store.clean(target.residueId(),.5,1,true);
        assertNotEquals(target.revision(),ForensicCleaningApi.weaponTarget(store,stack).orElseThrow().revision());
        assertEquals(before,stack.getComponents());assertEquals(51,stack.getDamage());
        assertEquals("keep",stack.get(DataComponentTypes.CUSTOM_DATA).copyNbt().getString("other_mod_field",""));
    }
    @Test void missingReferencesAndUnsupportedStacksAreNotCreatedOrAccepted(){
        var store=new BloodTransferStore();var stack=new ItemStack(Items.IRON_SWORD);
        var plain=ForensicCleaningApi.weaponTarget(store,stack).orElseThrow();
        assertEquals("",plain.residueId());assertEquals(0,plain.revision());
        assertFalse(stack.contains(DataComponentTypes.CUSTOM_DATA));
        var custom=new NbtCompound();custom.putString(BloodTransferService.ITEM_REF,"missing");
        stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(custom));
        assertTrue(ForensicCleaningApi.weaponTarget(store,stack).isEmpty());assertTrue(store.record("missing").isEmpty());
        stack.setCount(2);assertTrue(ForensicCleaningApi.weaponTarget(store,stack).isEmpty());
        assertTrue(ForensicCleaningApi.weaponTarget(store,new ItemStack(Items.STICK)).isEmpty());
        assertTrue(ForensicCleaningApi.weaponTarget(store,ItemStack.EMPTY).isEmpty());
    }
}

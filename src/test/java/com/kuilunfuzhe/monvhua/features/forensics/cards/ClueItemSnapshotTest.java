package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClueItemSnapshotTest {
    @Test void nativeItemRoundtripPreservesAppearanceAndDamageWithoutHiddenPayload(){
        SharedConstants.createGameVersion();Bootstrap.initialize();
        var stack=new ItemStack(Items.DIAMOND_SWORD);stack.setDamage(120);stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal("旧剑"));
        var secret=new NbtCompound();secret.putString("owner","hidden killer");stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(secret));
        var encoded=(NbtCompound)ItemStack.CODEC.encodeStart(NbtOps.INSTANCE,stack).getOrThrow();
        var projected=ClueItemSnapshot.project(encoded);
        var restored=ItemStack.CODEC.parse(NbtOps.INSTANCE,projected).getOrThrow();
        assertTrue(restored.isOf(Items.DIAMOND_SWORD));assertEquals(120,restored.getDamage());assertEquals("旧剑",restored.getName().getString());
        assertNull(restored.get(DataComponentTypes.CUSTOM_DATA));assertTrue(stack.contains(DataComponentTypes.CUSTOM_DATA));
        stack.setDamage(0);assertEquals(120,restored.getDamage());assertFalse(projected.toString().contains("hidden killer"));
    }
    @Test void containerBooksAndUnknownComponentsAreNotProjectedAndSourceIsUntouched(){
        var encoded=new NbtCompound();encoded.putString("id","minecraft:chest");encoded.putInt("count",3);
        var components=new NbtCompound();components.putInt("minecraft:damage",7);
        for(String key:List.of("minecraft:container","minecraft:written_book_content","minecraft:custom_data","another_mod:secret"))components.putString(key,"hidden value");
        encoded.put("components",components);var projected=ClueItemSnapshot.project(encoded);
        assertFalse(projected.toString().contains("hidden value"));assertEquals(3,projected.getInt("count",0));
        assertTrue(projected.getCompoundOrEmpty("components").contains("!minecraft:container"));assertEquals(7,projected.getCompoundOrEmpty("components").getInt("minecraft:damage",0));
        assertTrue(encoded.toString().contains("hidden value"));
    }
    @Test void frozenItemCopiesShareOnlyAfterPermissionAndRemainFixedAfterEditingAndRestart(){
        var a=UUID.randomUUID();var b=UUID.randomUUID();var store=new ClueCardStore();store.start("items",Map.of(a,"甲",b,"乙"));
        var raw=new NbtCompound();raw.putString("id","minecraft:stick");raw.putInt("count",4);var snapshot=ClueItemSnapshot.snapshot(raw);
        var audit=new NbtCompound();audit.putString("original_owner","hidden");
        String id=store.create(a,new ClueCardStore.Fields("物品","","","",""),snapshot,audit);raw.putInt("count",99);snapshot.getCompoundOrEmpty("item").putInt("count",80);
        assertFalse(store.readable(b,id));String copy=store.duplicate(a,id);store.edit(a,copy,0,new ClueCardStore.Fields("另一个名称","昨天","走廊","描述","判断"));store.publish(a,copy);
        assertFalse(store.readable(b,id));assertEquals("item",store.summaries(b).getFirst().kind());assertEquals(4,store.snapshot(b,copy).getCompoundOrEmpty("item").getInt("count",0));
        assertThrows(IllegalArgumentException.class,()->store.edit(a,copy,1,new ClueCardStore.Fields("修改","","","","")));
        var restored=ClueCardStore.CODEC.parse(NbtOps.INSTANCE,ClueCardStore.CODEC.encodeStart(NbtOps.INSTANCE,store).getOrThrow()).getOrThrow();
        assertEquals(store.snapshot(b,copy),restored.snapshot(b,copy));assertFalse(restored.snapshot(b,copy).toString().contains("hidden"));
    }
}

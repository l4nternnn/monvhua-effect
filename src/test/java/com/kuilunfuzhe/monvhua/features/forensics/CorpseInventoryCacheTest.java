package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CorpseInventoryCacheTest {
    @BeforeAll static void bootstrap() { SharedConstants.createGameVersion(); Bootstrap.initialize(); }
    @AfterEach void clear() { CorpseInventoryCache.clear(); }
    @Test void detectsInPlaceCountAndComponentChangesIncludingNestedData() {
        var id=UUID.randomUUID();var item=new ItemStack(Items.DIAMOND,4);
        var inventory=new ArrayList<>(List.of(item,ItemStack.EMPTY));var encoded=new NbtList();
        CorpseInventoryCache.remember(id,inventory,encoded);
        assertTrue(CorpseInventoryCache.unchanged(id,inventory,encoded));
        item.setCount(3);assertFalse(CorpseInventoryCache.unchanged(id,inventory,encoded));
        item.setCount(4);assertTrue(CorpseInventoryCache.unchanged(id,inventory,encoded));
        item.set(DataComponentTypes.CUSTOM_NAME,Text.literal("证物"));
        assertFalse(CorpseInventoryCache.unchanged(id,inventory,encoded));
        CorpseInventoryCache.remember(id,inventory,encoded);
        var data=new NbtCompound();data.putString("evidence","changed");
        item.set(DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.of(data));
        assertFalse(CorpseInventoryCache.unchanged(id,inventory,encoded));
    }
    @Test void externalPersistedChangesAndLifecycleInvalidationForceSave() {
        var id=UUID.randomUUID();var inventory=List.of(new ItemStack(Items.STONE));var persisted=new NbtList();
        persisted.add(new NbtCompound());CorpseInventoryCache.remember(id,inventory,persisted);
        persisted.getCompoundOrEmpty(0).putString("external","change");
        assertFalse(CorpseInventoryCache.unchanged(id,inventory,persisted));
        CorpseInventoryCache.remember(id,inventory,persisted);
        assertTrue(CorpseInventoryCache.unchanged(id,List.of(inventory.getFirst().copy()),persisted.copy()));
        assertFalse(CorpseInventoryCache.unchanged(id,List.of(),persisted));
        CorpseInventoryCache.forget(id);assertFalse(CorpseInventoryCache.unchanged(id,inventory,persisted));
    }
}

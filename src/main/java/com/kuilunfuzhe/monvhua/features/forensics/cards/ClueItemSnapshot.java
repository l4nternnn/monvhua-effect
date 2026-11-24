package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import java.util.Set;
import net.minecraft.registry.Registries;

/** Public appearance is an explicit projection; the original stack stays in server audit data. */
public final class ClueItemSnapshot {
    private static final Set<String> VISIBLE=Set.of("minecraft:custom_name","minecraft:item_name",
        "minecraft:lore","minecraft:damage","minecraft:max_damage","minecraft:unbreakable",
        "minecraft:enchantments","minecraft:stored_enchantments","minecraft:enchantment_glint_override",
        "minecraft:custom_model_data","minecraft:item_model","minecraft:dyed_color",
        "minecraft:rarity","minecraft:tooltip_display","minecraft:trim","minecraft:attribute_modifiers",
        "minecraft:repair_cost","minecraft:max_stack_size");
    private ClueItemSnapshot(){}
    public static NbtCompound encode(ItemStack stack,RegistryWrapper.WrapperLookup registries){
        return ItemStack.CODEC.encodeStart(registries.getOps(NbtOps.INSTANCE),stack.copy()).result()
                .filter(n->n instanceof NbtCompound).map(n->(NbtCompound)n).orElseGet(()->{
                    var fallback=new NbtCompound();fallback.putString("id",Registries.ITEM.getId(stack.getItem()).toString());fallback.putInt("count",stack.getCount());
                    return fallback;
                });
    }
    public static NbtCompound project(NbtCompound encoded){
        var out=new NbtCompound();out.putString("id",encoded.getString("id","minecraft:air"));out.putInt("count",encoded.getInt("count",1));
        var components=encoded.getCompoundOrEmpty("components");var visible=new NbtCompound();
        for(String key:components.getKeys())if(VISIBLE.contains(key)||key.startsWith("!")&&VISIBLE.contains(key.substring(1)))visible.put(key,components.get(key).copy());
        // Explicit removals also strip sensitive defaults supplied by a custom item type.
        for(String key:Set.of("custom_data","container","bundle_contents","charged_projectiles","written_book_content","writable_book_content","block_entity_data","entity_data","bees","lock","container_loot","profile"))visible.put("!minecraft:"+key,new NbtCompound());
        out.put("components",visible);return out;
    }
    public static NbtCompound snapshot(NbtCompound encoded){var n=new NbtCompound();n.putString("kind","item_v1");n.put("item",project(encoded));return n;}
}

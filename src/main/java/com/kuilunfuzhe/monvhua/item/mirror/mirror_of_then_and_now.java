package com.kuilunfuzhe.monvhua.item.mirror;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * 古今镜物品的注册入口。
 * 负责创建MirrorOfThenAndNowItem实例，注册到物品注册表，并加入工具栏物品组。
 */
public final class mirror_of_then_and_now {
    /** 物品注册ID字符串 */
    public static final String ITEM_ID = "mirror_of_then_and_now";
    /** 古今镜物品实例，初始化后赋值 */
    public static Item MIRROR_ITEM;

    /**
     * 初始化古今镜物品：创建实例、注册到注册表、添加到工具栏物品组。
     */
    public static void initialize() {
        Identifier id = Identifier.of("monvhua", ITEM_ID);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        Item.Settings settings = new Item.Settings()
                .registryKey(key)
                .maxCount(1);
        MIRROR_ITEM = new MirrorOfThenAndNowItem(settings);

        Registry.register(Registries.ITEM, id, MIRROR_ITEM);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(MIRROR_ITEM));
    }
}

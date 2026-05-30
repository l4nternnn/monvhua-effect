package com.kuilunfuzhe.monvhua.item.gazeguidance;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * 凝视诱导物品注册类
 * 负责注册魔法棒（MagicStickItem）物品，并将其添加到"工具"物品栏分组中
 */
public class ModItems {
    /** 魔法棒物品实例（最大堆叠数 1） */
    public static final MagicStickItem MAGIC_STICK;

    // 在 static 块中创建物品设置并实例化魔法棒，确保字段赋值在注册之前完成
    static {
        Identifier id = Identifier.of("monvhua", "magic_stick");
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        Item.Settings settings = new Item.Settings()
                .registryKey(key)
                .maxCount(1); // 魔法棒只能持有一个
        MAGIC_STICK = new MagicStickItem(settings);
    }

    /**
     * 执行物品注册：将魔法棒注册到物品注册表，并添加到创造模式"工具"分组
     */
    public static void initialize() {
        Registry.register(Registries.ITEM, Identifier.of("monvhua", "magic_stick"), MAGIC_STICK);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(MAGIC_STICK));
    }
}
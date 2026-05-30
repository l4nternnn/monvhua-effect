package com.kuilunfuzhe.monvhua.item.modblock.moditems;

import com.kuilunfuzhe.monvhua.item.modblock.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * 身体部件方块物品注册类
 * 为 ModBlocks 中定义的 6 个身体部件方块创建对应的 BlockItem，并注册到物品注册表和"建筑方块"创造模式分组
 */
public class Assembly_ModItems {
    public static final BlockItem TORSO_ITEM;
    public static final BlockItem LEFT_ARM_ITEM;
    public static final BlockItem RIGHT_ARM_ITEM;
    public static final BlockItem LEFT_LEG_ITEM;
    public static final BlockItem RIGHT_LEG_ITEM;
    public static final BlockItem HEAD_ITEM;

    // 静态初始化块：在类加载时创建所有身体部件 BlockItem，确保字段定义在注册之前
    static {
        TORSO_ITEM = createBlockItem("torso", ModBlocks.TORSO);
        LEFT_ARM_ITEM = createBlockItem("left_arm", ModBlocks.LEFT_ARM);
        RIGHT_ARM_ITEM = createBlockItem("right_arm", ModBlocks.RIGHT_ARM);
        LEFT_LEG_ITEM = createBlockItem("left_leg", ModBlocks.LEFT_LEG);
        RIGHT_LEG_ITEM = createBlockItem("right_leg", ModBlocks.RIGHT_LEG);
        HEAD_ITEM = createBlockItem("head", ModBlocks.HEAD);
    }

    /**
     * 创建方块对应的物品形式（BlockItem）
     * @param path  物品注册 ID 路径
     * @param block 关联的方块实例
     * @return 配置好注册键和最大堆叠数（64）的 BlockItem
     */
    private static BlockItem createBlockItem(String path, net.minecraft.block.Block block) {
        Identifier id = Identifier.of("monvhua", path);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        Item.Settings settings = new Item.Settings()
                .registryKey(key)
                .maxCount(64); // 身体部件方块的物品形式可堆叠 64 个
        return new BlockItem(block, settings);
    }


    /**
     * 执行初始化：将所有 BlockItem 注册到注册表，并添加到创造模式"建筑方块"分组
     */
    public static void initialize() {

        // 注册所有 BlockItem
        register(TORSO_ITEM, "torso");
        register(LEFT_ARM_ITEM, "left_arm");
        register(RIGHT_ARM_ITEM, "right_arm");
        register(LEFT_LEG_ITEM, "left_leg");
        register(RIGHT_LEG_ITEM, "right_leg");
        register(HEAD_ITEM, "head");


        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS)
                .register(entries -> {
                    entries.add(TORSO_ITEM);
                    entries.add(LEFT_ARM_ITEM);
                    entries.add(RIGHT_ARM_ITEM);
                    entries.add(LEFT_LEG_ITEM);
                    entries.add(RIGHT_LEG_ITEM);
                    entries.add(HEAD_ITEM);
                });
    }

    private static void register(BlockItem item, String path) {
        Identifier id = Identifier.of("monvhua", path);
        Registry.register(Registries.ITEM, id, item);
    }
}


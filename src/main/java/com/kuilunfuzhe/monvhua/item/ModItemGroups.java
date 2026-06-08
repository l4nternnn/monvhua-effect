package com.kuilunfuzhe.monvhua.item;

import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes;
import com.kuilunfuzhe.monvhua.item.gazeguidance.ModItems;
import com.kuilunfuzhe.monvhua.item.gravity.GravityItems;
import com.kuilunfuzhe.monvhua.item.mirror.mirror_of_then_and_now;
import com.kuilunfuzhe.monvhua.item.modblock.moditems.Assembly_ModItems;
import com.kuilunfuzhe.monvhua.item.paint.PaintItems;
import com.kuilunfuzhe.monvhua.item.secrecy.SecrecyItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 模组物品栏分组注册类
 * 创建"魔法重构"创造模式物品栏标签页，汇总所有模组物品（魔法棒、千里眼、镜子、隐秘物品、身体部件）
 */
public class ModItemGroups {
    public static final RegistryKey<ItemGroup> MAIN_GROUP_KEY = RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of("monvhua", "main_group"));

    public static ItemGroup MAIN_GROUP; // 供动态更新器使用

    public static void initialize() {
        MAIN_GROUP = Registry.register(Registries.ITEM_GROUP, MAIN_GROUP_KEY,
            FabricItemGroup.builder()
                .displayName(Text.literal("§c魔法少女的溃论覆辙§r◆§6魔法重构"))
                .icon(() -> new ItemStack(Evil_Eyes.CLAIRVOYANCE_ITEM))
                .entries((displayContext, entries) -> {
                    entries.add(Evil_Eyes.CLAIRVOYANCE_ITEM);
                    entries.add(ModItems.MAGIC_STICK);
                    entries.add(GravityItems.GRAVITY_WAND);
                    entries.add(PaintItems.PAINT_BRUSH);
                    entries.add(PaintItems.ERASER);
                    entries.add(PaintItems.PAINT_PAPER);
                    entries.add(PaintItems.PAINT_BUCKET);
                    entries.add(mirror_of_then_and_now.MIRROR_ITEM);
                    entries.add(SecrecyItem.SECRECY_ITEM);
                    entries.add(Assembly_ModItems.TORSO_ITEM);
                    entries.add(Assembly_ModItems.LEFT_ARM_ITEM);
                    entries.add(Assembly_ModItems.RIGHT_ARM_ITEM);
                    entries.add(Assembly_ModItems.LEFT_LEG_ITEM);
                    entries.add(Assembly_ModItems.RIGHT_LEG_ITEM);
                    entries.add(Assembly_ModItems.HEAD_ITEM);
                    entries.add(Assembly_ModItems.SKELETAL_HEAD_ITEM);
                    entries.add(Assembly_ModItems.SKELETAL_TORSO_ITEM);
                    entries.add(Assembly_ModItems.SKELETAL_LEFT_ARM_ITEM);
                    entries.add(Assembly_ModItems.SKELETAL_RIGHT_ARM_ITEM);
                    entries.add(Assembly_ModItems.SKELETAL_LEFT_LEG_ITEM);
                    entries.add(Assembly_ModItems.SKELETAL_RIGHT_LEG_ITEM);


                })
                .build()
        );
    }
}

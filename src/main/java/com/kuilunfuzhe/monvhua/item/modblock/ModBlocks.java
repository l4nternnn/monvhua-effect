package com.kuilunfuzhe.monvhua.item.modblock;

import com.kuilunfuzhe.monvhua.features.block.body.arm.LeftArmBlock;
import com.kuilunfuzhe.monvhua.features.block.body.arm.RightArmBlock;
import com.kuilunfuzhe.monvhua.features.block.body.leg.LeftLegBlock;
import com.kuilunfuzhe.monvhua.features.block.body.head.HeadBlock;
import com.kuilunfuzhe.monvhua.features.block.body.leg.RightLegBlock;
import com.kuilunfuzhe.monvhua.features.block.body.torso.TorsoBlock;
import com.kuilunfuzhe.monvhua.features.block.body_skeletal.SkeletalHeadBlock;
import com.kuilunfuzhe.monvhua.features.block.body_skeletal.SkeletalLeftArmBlock;
import com.kuilunfuzhe.monvhua.features.block.body_skeletal.SkeletalLeftLegBlock;
import com.kuilunfuzhe.monvhua.features.block.body_skeletal.SkeletalRightArmBlock;
import com.kuilunfuzhe.monvhua.features.block.body_skeletal.SkeletalRightLegBlock;
import com.kuilunfuzhe.monvhua.features.block.body_skeletal.SkeletalTorsoBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Function;

/**
 * 身体部件方块注册类
 * 注册魔法少女的 6 个身体部件方块（躯干、左右臂、左右腿、头部），使用统一的 register 辅助方法简化重复代码
 */
public class ModBlocks {
    // 定义 5 个方块实例
    public static final Block TORSO = register("torso",
            TorsoBlock::new,
            AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(1.0F, 1.0F).nonOpaque()
    );

    public static final Block LEFT_ARM = register("left_arm",
            LeftArmBlock::new,
            AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(1.0F, 1.0F).pistonBehavior(PistonBehavior.DESTROY));

    public static final Block RIGHT_ARM = register("right_arm",
            RightArmBlock::new,
            AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(1.0F, 1.0F).pistonBehavior(PistonBehavior.DESTROY));

    public static final Block LEFT_LEG = register("left_leg",
            LeftLegBlock::new,
            AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(1.0F, 1.0F).pistonBehavior(PistonBehavior.DESTROY));

    public static final Block RIGHT_LEG = register("right_leg",
            RightLegBlock::new,
            AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(1.0F, 1.0F).pistonBehavior(PistonBehavior.DESTROY));

    public static final Block HEAD = register("head",
            HeadBlock::new,
            AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(1.0F, 1.0F).pistonBehavior(PistonBehavior.DESTROY));

    public static final Block SKELETAL_TORSO = register("skeletal_torso",
            SkeletalTorsoBlock::new,
            AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(1.0F, 1.0F).nonOpaque().pistonBehavior(PistonBehavior.DESTROY));

    public static final Block SKELETAL_HEAD = register("skeletal_head",
            SkeletalHeadBlock::new,
            AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(1.0F, 1.0F).nonOpaque().pistonBehavior(PistonBehavior.DESTROY));

    public static final Block SKELETAL_LEFT_ARM = register("skeletal_left_arm",
            SkeletalLeftArmBlock::new,
            AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(1.0F, 1.0F).nonOpaque().pistonBehavior(PistonBehavior.DESTROY));

    public static final Block SKELETAL_RIGHT_ARM = register("skeletal_right_arm",
            SkeletalRightArmBlock::new,
            AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(1.0F, 1.0F).nonOpaque().pistonBehavior(PistonBehavior.DESTROY));

    public static final Block SKELETAL_LEFT_LEG = register("skeletal_left_leg",
            SkeletalLeftLegBlock::new,
            AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(1.0F, 1.0F).nonOpaque().pistonBehavior(PistonBehavior.DESTROY));

    public static final Block SKELETAL_RIGHT_LEG = register("skeletal_right_leg",
            SkeletalRightLegBlock::new,
            AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(1.0F, 1.0F).nonOpaque().pistonBehavior(PistonBehavior.DESTROY));

    // 通用注册方法（模仿原版 Blocks 中的 register）
    private static Block register(String path, Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings) {
        RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of("monvhua", path)); // 使用你的模组 ID
        Block block = factory.apply(settings.registryKey(key));
        return Registry.register(Registries.BLOCK, key, block);
    }

    // 供模组主类调用，触发 static 初始化
    public static void initialize() {
        // static 字段会在类加载时自动初始化，此空方法仅用于确保类被加载
    }
}

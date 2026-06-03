package com.kuilunfuzhe.monvhua.entity;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.block.body.arm.LeftArmBlockEntity;
import com.kuilunfuzhe.monvhua.features.block.body.arm.RightArmBlockEntity;
import com.kuilunfuzhe.monvhua.features.block.body.leg.LeftLegBlockEntity;
import com.kuilunfuzhe.monvhua.features.block.body.leg.RightLegBlockEntity;
import com.kuilunfuzhe.monvhua.features.block.body.head.HeadBlockEntity;
import com.kuilunfuzhe.monvhua.features.block.body.torso.TorsoBlockEntity;
import com.kuilunfuzhe.monvhua.item.modblock.ModBlocks;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * 方块实体类型注册中心。
 * 为身体的五个部位（躯干、左臂、右臂、左腿、右腿）和头部注册对应的 BlockEntityType，
 * 方块实体负责在渲染时提供模型数据和动画状态。
 */
public class ModBlockEntities {
    public static final BlockEntityType<TorsoBlockEntity> TORSO_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MonvhuaMod.MOD_ID, "torso"),
            FabricBlockEntityTypeBuilder.create(TorsoBlockEntity::new, ModBlocks.TORSO).build()
    );

    public static final BlockEntityType<LeftArmBlockEntity> LEFT_ARM_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MonvhuaMod.MOD_ID, "left_arm"),
            FabricBlockEntityTypeBuilder.create(LeftArmBlockEntity::new, ModBlocks.LEFT_ARM).build()
    );

    public static final BlockEntityType<RightArmBlockEntity> RIGHT_ARM_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MonvhuaMod.MOD_ID, "right_arm"),
            FabricBlockEntityTypeBuilder.create(RightArmBlockEntity::new, ModBlocks.RIGHT_ARM).build()
    );
    public static final BlockEntityType<LeftLegBlockEntity> LEFT_LEG_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MonvhuaMod.MOD_ID, "left_leg"),
            FabricBlockEntityTypeBuilder.create(LeftLegBlockEntity::new, ModBlocks.LEFT_LEG).build()
    );
    public static final BlockEntityType<RightLegBlockEntity> RIGHT_LEG_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MonvhuaMod.MOD_ID, "right_leg"),
            FabricBlockEntityTypeBuilder.create(RightLegBlockEntity::new, ModBlocks.RIGHT_LEG).build()
    );

    public static final BlockEntityType<HeadBlockEntity> HEAD_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MonvhuaMod.MOD_ID, "head"),
            FabricBlockEntityTypeBuilder.create(HeadBlockEntity::new, ModBlocks.HEAD).build()
    );

    /**
     * 模组初始化入口，目前留空。
     * 保留方法以维持统一的初始化调用约定。
     */
    public static void initialize() {
//        System.out.println("Registered TORSO_BLOCK_ENTITY: " + TORSO_BLOCK_ENTITY.getRegistryEntry().getIdAsString());
    }
}

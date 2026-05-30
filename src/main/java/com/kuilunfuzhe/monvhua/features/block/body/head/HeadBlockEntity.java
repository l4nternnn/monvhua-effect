package com.kuilunfuzhe.monvhua.features.block.body.head;

import com.kuilunfuzhe.monvhua.entity.ModBlockEntities;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/**
 * 头部方块实体，继承自 {@link BodyPartBlockEntity}。
 * 仅覆写构造函数以绑定对应的方块实体类型 {@code HEAD_BLOCK_ENTITY}，
 * 具体渲染和交互逻辑由父类统一处理。
 */
public class HeadBlockEntity extends BodyPartBlockEntity {
    public HeadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HEAD_BLOCK_ENTITY, pos, state);
    }
}

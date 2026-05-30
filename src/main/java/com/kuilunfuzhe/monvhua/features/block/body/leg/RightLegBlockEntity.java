package com.kuilunfuzhe.monvhua.features.block.body.leg;

import com.kuilunfuzhe.monvhua.entity.ModBlockEntities;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/**
 * 右腿方块实体，继承自 {@link BodyPartBlockEntity}。
 * 仅覆写构造函数以绑定对应的方块实体类型 {@code RIGHT_LEG_BLOCK_ENTITY}，
 * 具体渲染和交互逻辑由父类统一处理。
 */
public class RightLegBlockEntity extends BodyPartBlockEntity{
    public RightLegBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.RIGHT_LEG_BLOCK_ENTITY, pos, state);
        }
}


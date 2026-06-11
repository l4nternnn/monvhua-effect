package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.item.secrecy.SecrecyItem;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class SecrecyPhaseCollisionMixin {
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;", at = @At("HEAD"), cancellable = true)
    private void monvhua$removeCollisionForSecrecyPhase(BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (!(context instanceof EntityShapeContext entityContext)) {
            return;
        }
        Entity entity = entityContext.getEntity();
        if (entity instanceof ServerPlayerEntity player && SecrecyItem.shouldIgnorePhaseCollision(player, pos)) {
            cir.setReturnValue(VoxelShapes.empty());
        }
    }
}

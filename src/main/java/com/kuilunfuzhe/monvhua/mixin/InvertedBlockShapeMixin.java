package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.InvertedBlockShapeTransform;
import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class InvertedBlockShapeMixin {
    @Inject(method = "getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;", at = @At("RETURN"), cancellable = true)
    private void monvhua$mirrorOutlineShape(BlockView world, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        cir.setReturnValue(monvhua$mirrorShape(cir.getReturnValue(), world, pos));
    }

    @Inject(method = "getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;", at = @At("RETURN"), cancellable = true)
    private void monvhua$mirrorOutlineShape(BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
        cir.setReturnValue(monvhua$mirrorShape(cir.getReturnValue(), world, pos, context));
    }

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;", at = @At("RETURN"), cancellable = true)
    private void monvhua$mirrorCollisionShape(BlockView world, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        cir.setReturnValue(monvhua$mirrorShape(cir.getReturnValue(), world, pos));
    }

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;", at = @At("RETURN"), cancellable = true)
    private void monvhua$mirrorCollisionShape(BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
        cir.setReturnValue(monvhua$mirrorShape(cir.getReturnValue(), world, pos, context));
    }

    @Inject(method = "getCameraCollisionShape", at = @At("RETURN"), cancellable = true)
    private void monvhua$mirrorCameraCollisionShape(BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
        cir.setReturnValue(monvhua$mirrorShape(cir.getReturnValue(), world, pos, context));
    }

    @Inject(method = "getRaycastShape", at = @At("RETURN"), cancellable = true)
    private void monvhua$mirrorRaycastShape(BlockView world, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        cir.setReturnValue(monvhua$mirrorShape(cir.getReturnValue(), world, pos));
    }

    @Inject(method = "getSidesShape", at = @At("RETURN"), cancellable = true)
    private void monvhua$mirrorSidesShape(BlockView world, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        cir.setReturnValue(monvhua$mirrorShape(cir.getReturnValue(), world, pos));
    }

    @Inject(method = "getInsideCollisionShape", at = @At("RETURN"), cancellable = true)
    private void monvhua$mirrorInsideCollisionShape(BlockView world, BlockPos pos, Entity entity, CallbackInfoReturnable<VoxelShape> cir) {
        cir.setReturnValue(monvhua$mirrorShape(cir.getReturnValue(), world, pos));
    }

    private VoxelShape monvhua$mirrorShape(VoxelShape shape, BlockView world, BlockPos pos) {
        return InvertedBlockShapeTransform.shouldMirror(world, pos) ? InvertedBlockShapeTransform.mirror(shape) : shape;
    }

    private VoxelShape monvhua$mirrorShape(VoxelShape shape, BlockView world, BlockPos pos, ShapeContext context) {
        return monvhua$shouldMirror(world, pos, context) ? InvertedBlockShapeTransform.mirror(shape) : shape;
    }

    private boolean monvhua$shouldMirror(BlockView world, BlockPos pos, ShapeContext context) {
        if (InvertedBlockShapeTransform.shouldMirror(world, pos)) {
            return true;
        }
        if (context instanceof EntityShapeContext entityContext) {
            Entity entity = entityContext.getEntity();
            return entity != null && GravityMagic.isInInvertedArea(entity);
        }
        return false;
    }
}

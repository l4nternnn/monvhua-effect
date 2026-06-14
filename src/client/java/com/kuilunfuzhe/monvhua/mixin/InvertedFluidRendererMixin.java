package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
public abstract class InvertedFluidRendererMixin {
    private static final ThreadLocal<Boolean> MONVHUA_MIRROR_FLUID_Y = ThreadLocal.withInitial(() -> false);

    @Inject(method = "render", at = @At("HEAD"))
    private void monvhua$beginInvertedFluidRender(BlockRenderView world, BlockPos pos, VertexConsumer vertexConsumer,
                                                 BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        MONVHUA_MIRROR_FLUID_Y.set(world instanceof World realWorld
                && GravityMagic.isInInvertedArea(realWorld.getRegistryKey(), pos.toCenterPos()));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void monvhua$endInvertedFluidRender(BlockRenderView world, BlockPos pos, VertexConsumer vertexConsumer,
                                               BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        MONVHUA_MIRROR_FLUID_Y.set(false);
    }

    @ModifyArg(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/block/FluidRenderer;vertex(Lnet/minecraft/client/render/VertexConsumer;FFFFFFFFI)V"),
            index = 2)
    private float monvhua$mirrorInvertedFluidY(float y) {
        return MONVHUA_MIRROR_FLUID_Y.get() ? 1.0F - y : y;
    }
}

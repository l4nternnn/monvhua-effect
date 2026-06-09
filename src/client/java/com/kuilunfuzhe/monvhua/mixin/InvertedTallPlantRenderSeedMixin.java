package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.InvertedDoubleBlockRenderState;
import net.minecraft.block.BlockState;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TallPlantBlock.class)
public abstract class InvertedTallPlantRenderSeedMixin {
    @Inject(method = "getRenderingSeed", at = @At("HEAD"), cancellable = true)
    private void monvhua$useInvertedLowerHalfRenderingSeed(BlockState state, BlockPos pos, CallbackInfoReturnable<Long> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            return;
        }

        InvertedDoubleBlockRenderState.prepareModelState(client.world.getRegistryKey(), client.world, pos, state);
        if (state.get(Properties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.UPPER
                || !InvertedDoubleBlockRenderState.hasInvertedCounterpart(client.world, pos, state)) {
            return;
        }

        BlockPos anchor = pos.up();
        cir.setReturnValue(MathHelper.hashCode(anchor.getX(), anchor.getY(), anchor.getZ()));
    }
}

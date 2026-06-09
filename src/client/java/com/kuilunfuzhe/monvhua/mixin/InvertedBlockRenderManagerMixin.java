package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.InvertedDoubleBlockRenderState;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BlockStateModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockRenderManager.class)
public abstract class InvertedBlockRenderManagerMixin {
    @Shadow
    @Final
    private BlockModels models;

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void monvhua$useInvertedDoubleBlockModel(BlockState state, CallbackInfoReturnable<BlockStateModel> cir) {
        BlockState replacement = InvertedDoubleBlockRenderState.consumeModelState(state);
        if (replacement != state) {
            cir.setReturnValue(models.getModel(replacement));
        }
    }
}

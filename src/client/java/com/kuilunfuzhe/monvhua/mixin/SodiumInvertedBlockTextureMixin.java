package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.InvertedBlockContext;
import com.kuilunfuzhe.monvhua.features.gravity.InvertedBlockQuadTransform;
import com.kuilunfuzhe.monvhua.features.gravity.InvertedDoubleBlockRenderState;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumInvertedBlockTextureMixin extends AbstractBlockRenderContext {
    @Unique
    private static final ModelQuadFacing MONVHUA_UNASSIGNED_FACING = ModelQuadFacing.UNASSIGNED;

    @Unique
    private BlockPos monvhua$blockPos;

    @Unique
    private BlockState monvhua$blockState;

    @Inject(method = "renderModel", at = @At("HEAD"), remap = false)
    private void monvhua$captureBlockPos(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        monvhua$blockPos = pos;
        monvhua$blockState = state;
    }

    @Inject(method = "renderModel", at = @At("RETURN"), remap = false)
    private void monvhua$clearBlockPos(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        monvhua$blockPos = null;
        monvhua$blockState = null;
    }

    @ModifyVariable(method = "renderModel", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private BlockStateModel monvhua$useInvertedDoubleBlockModel(BlockStateModel model, BlockStateModel originalModel, BlockState state, BlockPos pos, BlockPos origin) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return model;
        }

        BlockState replacement = InvertedDoubleBlockRenderState.replacementState(client.world.getRegistryKey(), this.level, pos, state);
        return replacement == state ? model : client.getBlockRenderManager().getModel(replacement);
    }

    @Inject(method = "bufferQuad", at = @At("HEAD"), remap = false)
    private void monvhua$mirrorInvertedAreaBlockQuad(@Coerce MutableQuadView quad, float[] brightnesses, @Coerce Object material, CallbackInfo ci) {
        if (!monvhua$isRenderingInvertedAreaBlock()) {
            return;
        }
        InvertedBlockQuadTransform.mirror(quad, brightnesses);
    }

    @ModifyArg(
            method = "bufferQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuilder;getVertexBuffer(Lnet/caffeinemc/mods/sodium/client/model/quad/properties/ModelQuadFacing;)Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/builder/ChunkMeshBufferBuilder;"
            ),
            index = 0,
            remap = false
    )
    private ModelQuadFacing monvhua$useUnassignedVertexBucket(ModelQuadFacing normalFace) {
        return monvhua$isRenderingInvertedAreaBlock() ? MONVHUA_UNASSIGNED_FACING : normalFace;
    }

    @ModifyArg(
            method = "bufferQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/TranslucentGeometryCollector;appendQuad([Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;Lnet/caffeinemc/mods/sodium/client/model/quad/properties/ModelQuadFacing;I)Z"
            ),
            index = 1,
            remap = false
    )
    private ModelQuadFacing monvhua$useUnassignedTranslucentBucket(ModelQuadFacing normalFace) {
        return monvhua$isRenderingInvertedAreaBlock() ? MONVHUA_UNASSIGNED_FACING : normalFace;
    }

    @Unique
    private boolean monvhua$isRenderingInvertedAreaBlock() {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockPos pos = monvhua$blockPos;
        BlockState state = monvhua$blockState;
        return client.world != null
                && pos != null
                && state != null
                && InvertedBlockContext.shouldMirror(client.world.getRegistryKey(), this.level, pos, state);
    }

}

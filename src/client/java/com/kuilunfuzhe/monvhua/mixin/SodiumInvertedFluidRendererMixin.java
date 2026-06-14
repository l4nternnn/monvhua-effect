package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadViewMutable;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer", remap = false)
public abstract class SodiumInvertedFluidRendererMixin {
    @Unique
    private boolean monvhua$renderingInvertedFluid;

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void monvhua$captureInvertedFluid(LevelSlice level, BlockState blockState, FluidState fluidState,
                                             BlockPos pos, BlockPos origin, TranslucentGeometryCollector collector,
                                             ChunkModelBuilder builder, Material material,
                                             ColorProvider<FluidState> colorProvider, Sprite[] sprites, CallbackInfo ci) {
        this.monvhua$renderingInvertedFluid = monvhua$isInverted(pos);
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void monvhua$clearInvertedFluid(LevelSlice level, BlockState blockState, FluidState fluidState,
                                           BlockPos pos, BlockPos origin, TranslucentGeometryCollector collector,
                                           ChunkModelBuilder builder, Material material,
                                           ColorProvider<FluidState> colorProvider, Sprite[] sprites, CallbackInfo ci) {
        this.monvhua$renderingInvertedFluid = false;
    }

    @Inject(method = "fluidHeight", at = @At("HEAD"), cancellable = true, remap = false)
    private void monvhua$getInvertedFluidHeight(BlockRenderView world, Fluid fluid, BlockPos pos, Direction direction,
                                               CallbackInfoReturnable<Float> cir) {
        if (!monvhua$isInverted(pos)) {
            return;
        }

        BlockState blockState = world.getBlockState(pos);
        FluidState fluidState = blockState.getFluidState();
        if (fluid.matchesType(fluidState.getFluid())) {
            FluidState belowState = world.getFluidState(pos.down());
            cir.setReturnValue(fluid.matchesType(belowState.getFluid()) ? 1.0F : fluidState.getHeight());
            return;
        }

        cir.setReturnValue(blockState.isSolid() ? -1.0F : 0.0F);
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/DefaultFluidRenderer;isFullBlockFluidOccluded(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;Lnet/minecraft/block/BlockState;Lnet/minecraft/fluid/FluidState;)Z"
            ),
            index = 2,
            remap = false
    )
    private Direction monvhua$flipInvertedFluidOcclusionDirection(Direction direction) {
        return monvhua$flipVerticalDirection(direction);
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/DefaultFluidRenderer;isSideExposed(Lnet/minecraft/world/BlockRenderView;IIILnet/minecraft/util/math/Direction;F)Z"
            ),
            index = 4,
            remap = false
    )
    private Direction monvhua$flipInvertedFluidExposureDirection(Direction direction) {
        return monvhua$flipVerticalDirection(direction);
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/DefaultFluidRenderer;setVertex(Lnet/caffeinemc/mods/sodium/client/model/quad/ModelQuadViewMutable;IFFFFF)V"
            ),
            index = 3,
            remap = false
    )
    private float monvhua$mirrorInvertedFluidVertexY(float y) {
        return this.monvhua$renderingInvertedFluid ? 1.0F - y : y;
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/DefaultFluidRenderer;updateQuad(Lnet/caffeinemc/mods/sodium/client/model/quad/ModelQuadViewMutable;Lnet/caffeinemc/mods/sodium/client/world/LevelSlice;Lnet/minecraft/util/math/BlockPos;Lnet/caffeinemc/mods/sodium/client/model/light/LightPipeline;Lnet/minecraft/util/math/Direction;Lnet/caffeinemc/mods/sodium/client/model/quad/properties/ModelQuadFacing;FLnet/caffeinemc/mods/sodium/client/model/color/ColorProvider;Lnet/minecraft/fluid/FluidState;)V"
            ),
            index = 4,
            remap = false
    )
    private Direction monvhua$flipInvertedFluidLightFace(Direction direction) {
        return monvhua$flipVerticalDirection(direction);
    }

    @Unique
    private Direction monvhua$flipVerticalDirection(Direction direction) {
        if (!this.monvhua$renderingInvertedFluid || direction == null || direction.getAxis() != Direction.Axis.Y) {
            return direction;
        }
        return direction.getOpposite();
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/DefaultFluidRenderer;updateQuad(Lnet/caffeinemc/mods/sodium/client/model/quad/ModelQuadViewMutable;Lnet/caffeinemc/mods/sodium/client/world/LevelSlice;Lnet/minecraft/util/math/BlockPos;Lnet/caffeinemc/mods/sodium/client/model/light/LightPipeline;Lnet/minecraft/util/math/Direction;Lnet/caffeinemc/mods/sodium/client/model/quad/properties/ModelQuadFacing;FLnet/caffeinemc/mods/sodium/client/model/color/ColorProvider;Lnet/minecraft/fluid/FluidState;)V"
            ),
            index = 5,
            remap = false
    )
    private ModelQuadFacing monvhua$flipInvertedFluidUpdateFacing(ModelQuadFacing facing) {
        return monvhua$flipVerticalFacing(facing);
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/DefaultFluidRenderer;writeQuad(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuilder;Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/TranslucentGeometryCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;Lnet/minecraft/util/math/BlockPos;Lnet/caffeinemc/mods/sodium/client/model/quad/ModelQuadView;Lnet/caffeinemc/mods/sodium/client/model/quad/properties/ModelQuadFacing;Z)V"
            ),
            index = 5,
            remap = false
    )
    private ModelQuadFacing monvhua$flipInvertedFluidWriteFacing(ModelQuadFacing facing) {
        return monvhua$flipVerticalFacing(facing);
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/DefaultFluidRenderer;writeQuad(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuilder;Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/TranslucentGeometryCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;Lnet/minecraft/util/math/BlockPos;Lnet/caffeinemc/mods/sodium/client/model/quad/ModelQuadView;Lnet/caffeinemc/mods/sodium/client/model/quad/properties/ModelQuadFacing;Z)V"
            ),
            index = 6,
            remap = false
    )
    private boolean monvhua$flipInvertedFluidWinding(boolean flip) {
        return this.monvhua$renderingInvertedFluid ? !flip : flip;
    }

    @Unique
    private ModelQuadFacing monvhua$flipVerticalFacing(ModelQuadFacing facing) {
        if (!this.monvhua$renderingInvertedFluid) {
            return facing;
        }
        if (facing == ModelQuadFacing.POS_Y) {
            return ModelQuadFacing.NEG_Y;
        }
        if (facing == ModelQuadFacing.NEG_Y) {
            return ModelQuadFacing.POS_Y;
        }
        return facing;
    }

    @Unique
    private static boolean monvhua$isInverted(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null
                && pos != null
                && GravityMagic.isInInvertedArea(client.world.getRegistryKey(), pos.toCenterPos());
    }
}

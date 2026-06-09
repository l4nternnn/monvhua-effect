package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext", remap = false)
public abstract class SodiumInvertedBlockCullMixin {
    @Shadow
    protected BlockPos pos;

    @Shadow
    protected BlockState state;

    @Inject(method = "isFaceCulled", at = @At("HEAD"), cancellable = true, remap = false)
    private void monvhua$disableCullingInInvertedArea(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (direction == null || client.world == null || pos == null
                || !GravityMagic.isInInvertedArea(client.world.getRegistryKey(), Vec3d.ofCenter(pos))) {
            return;
        }
        cir.setReturnValue(false);
    }

    @ModifyArg(
            method = "shadeQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/model/light/LightPipeline;calculate(Lnet/caffeinemc/mods/sodium/client/model/quad/ModelQuadView;Lnet/minecraft/util/math/BlockPos;Lnet/caffeinemc/mods/sodium/client/model/light/data/QuadLightData;Lnet/minecraft/util/math/Direction;Lnet/minecraft/util/math/Direction;ZZ)V"
            ),
            index = 3,
            remap = false
    )
    private Direction monvhua$flipStopLayerCullFaceLight(Direction direction) {
        return monvhua$flipStopLayerHorizontalLightDirection(direction);
    }

    @ModifyArg(
            method = "shadeQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/model/light/LightPipeline;calculate(Lnet/caffeinemc/mods/sodium/client/model/quad/ModelQuadView;Lnet/minecraft/util/math/BlockPos;Lnet/caffeinemc/mods/sodium/client/model/light/data/QuadLightData;Lnet/minecraft/util/math/Direction;Lnet/minecraft/util/math/Direction;ZZ)V"
            ),
            index = 4,
            remap = false
    )
    private Direction monvhua$flipStopLayerLightFaceLight(Direction direction) {
        return monvhua$flipStopLayerHorizontalLightDirection(direction);
    }

    private Direction monvhua$flipStopLayerHorizontalLightDirection(Direction direction) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (direction == null || direction.getAxis() != Direction.Axis.Y
                || client.world == null || pos == null || state == null
                || !monvhua$isGravityStopLayer(state)
                || !GravityMagic.isInInvertedArea(client.world.getRegistryKey(), Vec3d.ofCenter(pos))) {
            return direction;
        }
        return direction.getOpposite();
    }

    private static boolean monvhua$isGravityStopLayer(BlockState state) {
        return state.isOf(Blocks.BEDROCK)
                || state.isOf(Blocks.OBSIDIAN)
                || state.isOf(Blocks.REINFORCED_DEEPSLATE)
                || state.isOf(Blocks.BARRIER);
    }
}

package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.InvertedBlockContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext", remap = false)
public abstract class SodiumInvertedBlockCullMixin {
    @Shadow
    protected BlockPos pos;

    @Shadow
    protected BlockState state;

    @ModifyVariable(method = "isFaceCulled", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private Direction monvhua$flipInvertedCullDirection(Direction direction) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (direction == null || direction.getAxis() != Direction.Axis.Y
                || client.world == null || pos == null || state == null
                || !InvertedBlockContext.shouldMirror(client.world.getRegistryKey(), client.world, pos, state)) {
            return direction;
        }
        return direction.getOpposite();
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
        return monvhua$flipInvertedVerticalLightDirection(direction);
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
        return monvhua$flipInvertedVerticalLightDirection(direction);
    }

    private Direction monvhua$flipInvertedVerticalLightDirection(Direction direction) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (direction == null || direction.getAxis() != Direction.Axis.Y
                || client.world == null || pos == null || state == null
                || !InvertedBlockContext.shouldMirror(client.world.getRegistryKey(), client.world, pos, state)) {
            return direction;
        }
        return direction.getOpposite();
    }

}

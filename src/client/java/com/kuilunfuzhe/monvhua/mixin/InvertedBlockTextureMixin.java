package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.InvertedBlockContext;
import com.kuilunfuzhe.monvhua.features.gravity.InvertedBlockTextureVertexConsumer;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(BlockModelRenderer.class)
public abstract class InvertedBlockTextureMixin {
    @ModifyVariable(
            method = "render(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private VertexConsumer monvhua$flipInvertedAreaBlockTextures(VertexConsumer vertexConsumer, BlockRenderView world, List<BlockModelPart> parts, BlockState state, BlockPos pos, MatrixStack matrices) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !InvertedBlockContext.shouldMirror(client.world.getRegistryKey(), world, pos, state)) {
            return vertexConsumer;
        }
        return new InvertedBlockTextureVertexConsumer(vertexConsumer);
    }
}

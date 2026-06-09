package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.kuilunfuzhe.monvhua.features.gravity.InvertedBedVertexConsumerProvider;
import net.minecraft.block.entity.BedBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BedBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BedBlockEntityRenderer.class)
public abstract class InvertedBedBlockEntityRendererMixin {
    @Unique
    private boolean monvhua$mirroredBed;

    @Inject(
            method = "render(Lnet/minecraft/block/entity/BedBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/util/math/Vec3d;)V",
            at = @At("HEAD")
    )
    private void monvhua$mirrorInvertedBedModel(BedBlockEntity bed, float tickProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                int light, int overlay, Vec3d cameraPos, CallbackInfo ci) {
        monvhua$mirroredBed = bed.getWorld() != null
                && GravityMagic.isInInvertedArea(bed.getWorld().getRegistryKey(), bed.getPos().toCenterPos());
        if (!monvhua$mirroredBed) {
            return;
        }

        matrices.push();
        matrices.translate(0.0D, 1.0D, 0.0D);
        matrices.scale(1.0F, -1.0F, 1.0F);
    }

    @ModifyVariable(
            method = "render(Lnet/minecraft/block/entity/BedBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/util/math/Vec3d;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private VertexConsumerProvider monvhua$wrapInvertedBedVertexConsumers(VertexConsumerProvider vertexConsumers, BedBlockEntity bed) {
        if (monvhua$mirroredBed || bed.getWorld() != null
                && GravityMagic.isInInvertedArea(bed.getWorld().getRegistryKey(), bed.getPos().toCenterPos())) {
            return new InvertedBedVertexConsumerProvider(vertexConsumers);
        }
        return vertexConsumers;
    }

    @Inject(
            method = "render(Lnet/minecraft/block/entity/BedBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/util/math/Vec3d;)V",
            at = @At("RETURN")
    )
    private void monvhua$restoreInvertedBedModel(BedBlockEntity bed, float tickProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                 int light, int overlay, Vec3d cameraPos, CallbackInfo ci) {
        if (!monvhua$mirroredBed) {
            return;
        }

        matrices.pop();
        monvhua$mirroredBed = false;
    }
}

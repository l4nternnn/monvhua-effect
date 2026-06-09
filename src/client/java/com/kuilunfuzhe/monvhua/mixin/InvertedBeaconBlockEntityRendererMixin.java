package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.block.entity.BeamEmitter;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BeaconBlockEntityRenderer.class)
public abstract class InvertedBeaconBlockEntityRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/util/math/Vec3d;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void monvhua$renderInvertedBeaconBeam(BlockEntity blockEntity, float tickDelta, MatrixStack matrices,
                                                  VertexConsumerProvider vertexConsumers, int light, int overlay,
                                                  Vec3d cameraPos, CallbackInfo ci) {
        World world = blockEntity.getWorld();
        if (!(blockEntity instanceof BeamEmitter beamEmitter)
                || world == null
                || !GravityMagic.isInInvertedArea(world.getRegistryKey(), blockEntity.getPos().toCenterPos())) {
            return;
        }

        float horizontalDistance = (float) cameraPos.subtract(blockEntity.getPos().toCenterPos()).horizontalLength();
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        float scale = player != null && player.isUsingSpyglass() ? 1.0F : Math.max(1.0F, horizontalDistance / 96.0F);
        List<BeamEmitter.BeamSegment> segments = beamEmitter.getBeamSegments();
        int yOffset = 0;

        for (int i = 0; i < segments.size(); i++) {
            BeamEmitter.BeamSegment segment = segments.get(i);
            int height = i == segments.size() - 1 ? BeaconBlockEntityRenderer.MAX_BEAM_HEIGHT : segment.getHeight();
            BeaconBlockEntityRenderer.renderBeam(
                    matrices,
                    vertexConsumers,
                    BeaconBlockEntityRenderer.BEAM_TEXTURE,
                    tickDelta,
                    1.0F,
                    world.getTime(),
                    -yOffset - height,
                    height,
                    segment.getColor(),
                    0.2F * scale,
                    0.25F * scale
            );
            yOffset += segment.getHeight();
        }

        ci.cancel();
    }
}

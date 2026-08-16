package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class PossessionFirstPersonHandMixin {
    @Unique private boolean monvhua$overridingPose;
    @Unique private float monvhua$yaw;
    @Unique private float monvhua$pitch;
    @Unique private float monvhua$lastYaw;
    @Unique private float monvhua$lastPitch;
    @Unique private float monvhua$renderYaw;
    @Unique private float monvhua$renderPitch;
    @Unique private float monvhua$lastRenderYaw;
    @Unique private float monvhua$lastRenderPitch;

    @Inject(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At("HEAD")
    )
    private void monvhua$usePossessionViewPose(float tickDelta, MatrixStack matrices,
                                               VertexConsumerProvider.Immediate vertexConsumers,
                                               ClientPlayerEntity player, int light, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        Entity camera = client.cameraEntity;
        if (!PossessionClient.isActive() || player != client.player || camera == null || camera == player) {
            return;
        }

        monvhua$overridingPose = true;
        monvhua$yaw = player.getYaw();
        monvhua$pitch = player.getPitch();
        monvhua$lastYaw = player.lastYaw;
        monvhua$lastPitch = player.lastPitch;
        monvhua$renderYaw = player.renderYaw;
        monvhua$renderPitch = player.renderPitch;
        monvhua$lastRenderYaw = player.lastRenderYaw;
        monvhua$lastRenderPitch = player.lastRenderPitch;

        player.lastYaw = camera.lastYaw;
        player.lastPitch = camera.lastPitch;
        player.setYaw(camera.getYaw());
        player.setPitch(camera.getPitch());
        player.lastRenderYaw = camera.lastYaw;
        player.lastRenderPitch = camera.lastPitch;
        player.renderYaw = camera.getYaw();
        player.renderPitch = camera.getPitch();
    }

    @Inject(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At("RETURN")
    )
    private void monvhua$restoreControllerViewPose(float tickDelta, MatrixStack matrices,
                                                    VertexConsumerProvider.Immediate vertexConsumers,
                                                    ClientPlayerEntity player, int light, CallbackInfo ci) {
        if (!monvhua$overridingPose) {
            return;
        }
        player.setYaw(monvhua$yaw);
        player.setPitch(monvhua$pitch);
        player.lastYaw = monvhua$lastYaw;
        player.lastPitch = monvhua$lastPitch;
        player.renderYaw = monvhua$renderYaw;
        player.renderPitch = monvhua$renderPitch;
        player.lastRenderYaw = monvhua$lastRenderYaw;
        player.lastRenderPitch = monvhua$lastRenderPitch;
        monvhua$overridingPose = false;
    }
}

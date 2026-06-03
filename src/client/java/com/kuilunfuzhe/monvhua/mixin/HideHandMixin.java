package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import com.kuilunfuzhe.monvhua.features.evil_eyes.watch.CameraWatchClientHandler;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 隐藏手持物品渲染的Mixin。
 * 当CameraWatchClientHandler激活时（例如窥视模式下），取消HeldItemRenderer.renderItem调用，
 * 从而隐藏玩家手持物品的渲染，实现无手持物品干扰的观察视角。
 */
@Mixin(HeldItemRenderer.class)
public class HideHandMixin {

    /**
     * 拦截手持物品渲染方法，在窥视模式激活时取消渲染。
     * @param tickDelta 帧插值系数（0.0~1.0），用于平滑动画
     * @param matrices 矩阵栈，用于变换渲染位置
     * @param vertexConsumers 顶点消费者，提供渲染缓冲区
     * @param player 当前客户端玩家实体
     * @param light 光照值，用于物品着色
     * @param ci 回调信息，用于取消原方法执行
     */
    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"), cancellable = true)
    private void onRenderItem(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers, ClientPlayerEntity player, int light, CallbackInfo ci) {
        if (CameraWatchClientHandler.isActive() || CarryPoseClientState.isCarrier(player.getId()) || CarryPoseClientState.isCarried(player.getId())) {
            ci.cancel();
        }
    }
}
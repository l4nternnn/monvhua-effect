package com.kuilunfuzhe.monvhua.event;

import com.kuilunfuzhe.monvhua.MonvhuaModClient;
import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes;
import com.kuilunfuzhe.monvhua.features.area_tip.AreaTipAreaRenderer;
import com.kuilunfuzhe.monvhua.features.gravity.GravityAreaBoundaryRenderer;
import com.kuilunfuzhe.monvhua.features.paint.PaintBucketCarryClientState;
import com.kuilunfuzhe.monvhua.features.paint.PaintOverlayClient;
import com.kuilunfuzhe.monvhua.item.gazeguidance.ModItems;
import com.kuilunfuzhe.monvhua.item.through.ThroughItem;
import com.kuilunfuzhe.monvhua.renderer.bodypose.BodyPoseWorldPreviewRenderer;
import com.kuilunfuzhe.monvhua.renderer.picturerender.AnchorButtonRenderer;
import com.kuilunfuzhe.monvhua.renderer.picturerender.BackTextureRenderer;
import com.kuilunfuzhe.monvhua.renderer.picturerender.OrbitRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * 世界渲染事件处理器，在实体渲染完成后（AFTER_ENTITIES阶段）绘制自定义HUD元素。
 * 包括背景纹理、轨道、锚点按钮等，仅在玩家持有特定物品时显示。
 */
public class WorldRenderHandler {
    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            PlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                ItemStack mainHand = player.getMainHandStack();
                // 仅当手持千里眼、凝视法杖或隐秘相关物品时渲染背景纹理和轨道
                if (mainHand.getItem() == Evil_Eyes.CLAIRVOYANCE_ITEM || mainHand.getItem() == ModItems.MAGIC_STICK || ThroughItem.isHoldingSecrecy(mainHand)) {
                    // BackTextureRenderer.render(context.matrixStack(), context.consumers(), player, MonvhuaModClient.currentPlayerStage);
                    // OrbitRenderer.render(context.matrixStack(), context.consumers(), player, MonvhuaModClient.currentPlayerStage);
                }
            }
            // 锚点按钮始终渲染（不受手持物品限制）
            AnchorButtonRenderer.render(context.matrixStack(), context.consumers());
            // 身体姿势编辑器世界3D预览
            BodyPoseWorldPreviewRenderer.render(context.matrixStack(), context.consumers());
            GravityAreaBoundaryRenderer.render(context);
            AreaTipAreaRenderer.render(context);
            PaintOverlayClient.render(context);
            PaintBucketCarryClientState.render(context);
        });
    }
}

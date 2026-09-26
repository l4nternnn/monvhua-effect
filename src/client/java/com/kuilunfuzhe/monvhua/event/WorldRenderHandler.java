package com.kuilunfuzhe.monvhua.event;

import com.kuilunfuzhe.monvhua.MonvhuaModClient;
import com.kuilunfuzhe.monvhua.client.imitate.AreaSelectRenderer;
import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes;
import com.kuilunfuzhe.monvhua.features.area_tip.AreaTipAreaRenderer;
import com.kuilunfuzhe.monvhua.features.binding.PlayerBindingClient;
import com.kuilunfuzhe.monvhua.features.gravity.GravityAreaBoundaryRenderer;
import com.kuilunfuzhe.monvhua.features.glitch.client.GlitchPlaneClientFeature;
import com.kuilunfuzhe.monvhua.features.playerglitch.client.PlayerGlitchClientFeature;
import com.kuilunfuzhe.monvhua.features.injured_and_bleeding.InjuredBleedingClient;
import com.kuilunfuzhe.monvhua.features.paint.PaintBucketCarryClientState;
import com.kuilunfuzhe.monvhua.features.paint.PaintOverlayClient;
import com.kuilunfuzhe.monvhua.features.paint.PaintToolTargetPreviewRenderer;
import com.kuilunfuzhe.monvhua.features.chestlink.ChestLinkHighlightRenderer;
import com.kuilunfuzhe.monvhua.item.gazeguidance.ModItems;
import com.kuilunfuzhe.monvhua.item.through.ThroughItem;
import com.kuilunfuzhe.monvhua.renderer.bodypose.BodyPoseWorldPreviewRenderer;
import com.kuilunfuzhe.monvhua.renderer.activity.UiActivityBubbleRenderer;
import com.kuilunfuzhe.monvhua.renderer.picturerender.AnchorButtonRenderer;
import com.kuilunfuzhe.monvhua.renderer.picturerender.BackTextureRenderer;
import com.kuilunfuzhe.monvhua.renderer.picturerender.OrbitRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * 涓栫晫娓叉煋浜嬩欢澶勭悊鍣紝鍦ㄥ疄浣撴覆鏌撳畬鎴愬悗锛圓FTER_ENTITIES闃舵锛夌粯鍒惰嚜瀹氫箟HUD鍏冪礌銆?
 * 鍖呮嫭鑳屾櫙绾圭悊銆佽建閬撱€侀敋鐐规寜閽瓑锛屼粎鍦ㄧ帺瀹舵寔鏈夌壒瀹氱墿鍝佹椂鏄剧ず銆?
 */
public class WorldRenderHandler {
    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            PlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                ItemStack mainHand = player.getMainHandStack();
                // 浠呭綋鎵嬫寔鍗冮噷鐪笺€佸嚌瑙嗘硶鏉栨垨闅愮鐩稿叧鐗╁搧鏃舵覆鏌撹儗鏅汗鐞嗗拰杞ㄩ亾
                if (mainHand.getItem() == Evil_Eyes.CLAIRVOYANCE_ITEM || mainHand.getItem() == ModItems.MAGIC_STICK || ThroughItem.isHoldingSecrecy(mainHand)) {
                    // BackTextureRenderer.render(context.matrixStack(), context.consumers(), player, MonvhuaModClient.currentPlayerStage);
                    // OrbitRenderer.render(context.matrixStack(), context.consumers(), player, MonvhuaModClient.currentPlayerStage);
                }
            }
            // 閿氱偣鎸夐挳濮嬬粓娓叉煋锛堜笉鍙楁墜鎸佺墿鍝侀檺鍒讹級
            AnchorButtonRenderer.render(context.matrixStack(), context.consumers());
            // 韬綋濮垮娍缂栬緫鍣ㄤ笘鐣?D棰勮
            BodyPoseWorldPreviewRenderer.render(context.matrixStack(), context.consumers());
            UiActivityBubbleRenderer.render(context);
            GravityAreaBoundaryRenderer.render(context);
            GlitchPlaneClientFeature.render(context);
            PlayerGlitchClientFeature.render(context);
            AreaTipAreaRenderer.render(context);
            PaintOverlayClient.render(context);
            PaintToolTargetPreviewRenderer.render(context);
            InjuredBleedingClient.render(context);
            PlayerBindingClient.render(context);
            PaintBucketCarryClientState.render(context);
            AreaSelectRenderer.render(context);
            ChestLinkHighlightRenderer.render(context);
        });
    }
}

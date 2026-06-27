package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.paint.PlayerSkinPaintManager;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void monvhua$overridePaintedSkinTexture(AbstractClientPlayerEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        Identifier paintedId = PlayerSkinPaintManager.getPaintedTexture(player.getUuid());
        if (paintedId != null) {
            SkinTextures original = ((PlayerEntityRenderStateAccessor) state).getSkinTextures();
            if (original != null) {
                SkinTextures modified = new SkinTextures(
                        paintedId,
                        original.textureUrl(),
                        original.capeTexture(),
                        original.elytraTexture(),
                        original.model(),
                        original.secure()
                );
                ((PlayerEntityRenderStateAccessor) state).setSkinTextures(modified);
            }
        }
    }
}

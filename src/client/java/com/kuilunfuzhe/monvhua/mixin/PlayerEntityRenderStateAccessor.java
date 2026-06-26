package com.kuilunfuzhe.monvhua.mixin;

import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerEntityRenderState.class)
public interface PlayerEntityRenderStateAccessor {
    @Accessor("skinTextures")
    SkinTextures getSkinTextures();

    @Accessor("skinTextures")
    void setSkinTextures(SkinTextures skinTextures);
}

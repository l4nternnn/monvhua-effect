package com.kuilunfuzhe.monvhua.mixin.portal;

import com.kuilunfuzhe.monvhua.features.portal.client.PortalFramebufferRenderer;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalRemoteChunkCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.LightData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPortalLightUpdateMixin {
    @Inject(method = "readLightData", at = @At("TAIL"))
    private void monvhua$rebuildRemotePortalChunkAfterLight(int chunkX, int chunkZ,
                                                            LightData data, boolean trustEdges,
                                                            CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && PortalRemoteChunkCache.accepts(client.world, chunkX, chunkZ)) {
            PortalFramebufferRenderer.onRemoteChunkLoaded(chunkX, chunkZ);
        }
    }
}

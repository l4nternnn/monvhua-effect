package com.kuilunfuzhe.monvhua.mixin.portal;

import com.kuilunfuzhe.monvhua.features.portal.client.PortalRemoteRenderContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobTyped;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobResult;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderTask;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = ChunkJobTyped.class, remap = false)
public abstract class SodiumChunkJobRemoteContextMixin {
    @Unique
    private BlockPos monvhua$remoteSourcePos;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void monvhua$captureRemoteContext(ChunkBuilderTask<?> task,
                                              Consumer<? extends ChunkJobResult<?>> consumer,
                                              boolean blocking,
                                              CallbackInfo ci) {
        if (!PortalRemoteRenderContext.isPortalPass()) {
            return;
        }
        BlockPos sourcePos = PortalRemoteRenderContext.getRemoteSourcePos();
        if (sourcePos != null) {
            monvhua$remoteSourcePos = sourcePos.toImmutable();
        }
    }

    @WrapMethod(method = "execute")
    private void monvhua$runWithRemoteContext(ChunkBuildContext context, Operation<Void> original) {
        if (monvhua$remoteSourcePos == null) {
            original.call(context);
            return;
        }

        boolean previousPortalPass = PortalRemoteRenderContext.isPortalPass();
        BlockPos previousSourcePos = PortalRemoteRenderContext.getRemoteSourcePos();
        PortalRemoteRenderContext.beginWorkerPass(monvhua$remoteSourcePos);
        try {
            original.call(context);
        } finally {
            if (previousPortalPass && previousSourcePos != null) {
                PortalRemoteRenderContext.beginWorkerPass(previousSourcePos);
            } else {
                PortalRemoteRenderContext.endWorkerPass();
            }
        }
    }
}

package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.swing.SwingEntity;
import com.kuilunfuzhe.monvhua.features.swing.SwingSpatialIndex;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 800)
public abstract class SwingCrosshairTargetMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "updateCrosshairTarget", at = @At("RETURN"))
    private void monvhua$targetSwingSeat(float tickProgress, CallbackInfo ci) {
        if (client.player == null || client.world == null) return;
        Vec3d start = client.player.getEyePos();
        double reach = client.player.getEntityInteractionRange();
        Vec3d end = start.add(client.player.getRotationVec(tickProgress).multiply(reach));
        SwingEntity selected = null;
        Vec3d selectedHit = null;
        double best = Double.POSITIVE_INFINITY;
        for (SwingEntity swing : SwingSpatialIndex.find(client.world, new Box(start, end).expand(0.5))) {
            var hit = swing.raycastSeat(start, end, tickProgress);
            if (hit.isEmpty()) continue;
            double distance = start.squaredDistanceTo(hit.get());
            if (distance < best) {
                selected = swing;
                selectedHit = hit.get();
                best = distance;
            }
        }
        if (selected == null) return;
        boolean replacingSameSwing = client.crosshairTarget instanceof EntityHitResult entityHit
                && entityHit.getEntity() == selected;
        if (!replacingSameSwing && client.crosshairTarget != null
                && client.crosshairTarget.getPos() != null
                && start.squaredDistanceTo(client.crosshairTarget.getPos()) + 1.0e-6 < best) return;
        client.crosshairTarget = new EntityHitResult(selected, selectedHit);
        client.targetedEntity = selected;
    }
}

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
        // Vanilla may select the large broad-phase box, including empty space between chains.
        if (client.crosshairTarget instanceof EntityHitResult old && old.getEntity() instanceof SwingEntity) {
            client.crosshairTarget = client.player.raycast(client.player.getBlockInteractionRange(), tickProgress, false);
            client.targetedEntity = null;
            double maxDistance = Math.min(reach * reach, start.squaredDistanceTo(client.crosshairTarget.getPos()));
            var other = net.minecraft.entity.projectile.ProjectileUtil.raycast(client.player, start, end,
                    new Box(start, end).expand(1), entity -> !(entity instanceof SwingEntity) && entity.canHit() && !entity.isSpectator(), maxDistance);
            if (other != null) {
                client.crosshairTarget = other;
                client.targetedEntity = other.getEntity();
            }
        }
        SwingEntity selected = null;
        Vec3d selectedHit = null;
        double best = Double.POSITIVE_INFINITY;
        for (SwingEntity swing : SwingSpatialIndex.find(client.world, new Box(start, end).expand(0.5))) {
            var hit = swing.raycastStructure(start, end, tickProgress);
            if (hit.isEmpty()) continue;
            double distance = start.squaredDistanceTo(hit.get().worldPoint());
            if (distance < best) {
                selected = swing;
                selectedHit = hit.get().worldPoint();
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

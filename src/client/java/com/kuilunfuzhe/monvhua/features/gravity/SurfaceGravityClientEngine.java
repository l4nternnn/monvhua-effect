package com.kuilunfuzhe.monvhua.features.gravity;

import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.network.gravity.GravityPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SurfaceGravityClientEngine {
    private static final Vector3f CAMERA_LOCAL_FORWARD = new Vector3f(0.0F, 0.0F, -1.0F);
    private static final Vector3f CAMERA_LOCAL_UP = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final Vector3f CAMERA_LOCAL_LEFT = new Vector3f(-1.0F, 0.0F, 0.0F);

    private static float lastSyncedYaw = Float.NaN;
    private static float lastSyncedPitch = Float.NaN;
    private static int syncCooldown;

    private SurfaceGravityClientEngine() {
    }

    public static boolean isActive(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        return entity != null && client.player == entity && GravityMagic.hasNonNormalSurfaceGravity(entity);
    }

    public static boolean isRenderActive(Entity entity) {
        return entity != null && GravityMagic.hasNonNormalSurfaceGravity(entity);
    }

    public static Vec3d eyePos(Entity entity, float tickProgress) {
        return GravityMagic.getSurfaceEyePos(entity, tickProgress);
    }

    public static Vec3d look(Entity entity) {
        return GravityMagic.getSurfaceLook(entity);
    }

    public static HitResult raycast(MinecraftClient client, float tickProgress) {
        if (client == null || client.world == null || client.player == null || !isActive(client.player)) {
            return null;
        }
        ClientPlayerEntity player = client.player;
        double blockReach = player.getBlockInteractionRange();
        double entityReach = player.getEntityInteractionRange();
        double reach = Math.max(blockReach, entityReach);
        Vec3d start = eyePos(player, tickProgress);
        Vec3d look = look(player);
        Vec3d end = start.add(look.multiply(reach));

        BlockHitResult blockHit = client.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        double blockDistanceSq = blockHit.getType() == HitResult.Type.MISS ? reach * reach : start.squaredDistanceTo(blockHit.getPos());
        double entityMaxSq = entityReach * entityReach;
        Vec3d entityEnd = start.add(look.multiply(entityReach));
        Box searchBox = player.getBoundingBox().stretch(look.multiply(entityReach)).expand(1.0D);
        EntityHitResult entityHit = ProjectileUtil.raycast(
                player,
                start,
                entityEnd,
                searchBox,
                entity -> !entity.isSpectator() && entity.canHit(),
                entityMaxSq
        );
        if (entityHit != null && start.squaredDistanceTo(entityHit.getPos()) <= blockDistanceSq) {
            return entityHit;
        }
        return blockHit;
    }

    public static Quaternionf modelRotation(Entity entity) {
        Direction direction = GravityMagic.getSurfaceGravityDirection(entity);
        return SurfaceGravityBasis.modelRotation(direction);
    }

    public static void applyCamera(Entity entity, Quaternionf rotation, Vector3f horizontalPlane, Vector3f verticalPlane, Vector3f diagonalPlane, boolean inverseView) {
        GravityMagic.SurfaceView view = GravityMagic.getSurfaceView(entity);
        if (view == null) {
            return;
        }

        float cameraYaw = inverseView ? view.localYaw() + 180.0F : view.localYaw();
        float cameraPitch = inverseView ? -view.localPitch() : view.localPitch();
        rotation.set(SurfaceGravityBasis.cameraRotation(view.downDirection(), cameraYaw, cameraPitch));
        horizontalPlane.set(CAMERA_LOCAL_FORWARD).rotate(rotation).normalize();
        verticalPlane.set(CAMERA_LOCAL_UP).rotate(rotation).normalize();
        diagonalPlane.set(CAMERA_LOCAL_LEFT).rotate(rotation).normalize();
    }

    public static void onLookInput(Entity entity, double cursorDeltaX, double cursorDeltaY) {
        if (!isActive(entity)) {
            return;
        }
        GravityMagic.SurfaceView view = GravityMagic.getSurfaceView(entity);
        if (view == null) {
            return;
        }
        float nextYaw = view.localYaw() + (float) cursorDeltaX * 0.15F;
        float nextPitch = Math.clamp(view.localPitch() + (float) cursorDeltaY * 0.15F, -89.0F, 89.0F);
        GravityMagic.setSurfaceLook(entity, nextYaw, nextPitch);
        syncLook(entity, nextYaw, nextPitch, false);
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || !isActive(client.player)) {
            lastSyncedYaw = Float.NaN;
            lastSyncedPitch = Float.NaN;
            syncCooldown = 0;
            return;
        }
        GravityMagic.SurfaceView view = GravityMagic.getSurfaceView(client.player);
        if (view != null) {
            syncLook(client.player, view.localYaw(), view.localPitch(), true);
        }
    }

    private static void syncLook(Entity entity, float localYaw, float localPitch, boolean periodic) {
        if (!isActive(entity)) {
            return;
        }
        if (syncCooldown > 0) {
            syncCooldown--;
        }
        boolean changed = Float.isNaN(lastSyncedYaw)
                || Math.abs(localYaw - lastSyncedYaw) > 0.1F
                || Math.abs(localPitch - lastSyncedPitch) > 0.1F;
        if (!changed && (!periodic || syncCooldown > 0)) {
            return;
        }
        lastSyncedYaw = localYaw;
        lastSyncedPitch = localPitch;
        syncCooldown = 5;
        SafeClientNetworking.send(new GravityPackets.SurfaceLookC2S(localYaw, localPitch));
    }
}

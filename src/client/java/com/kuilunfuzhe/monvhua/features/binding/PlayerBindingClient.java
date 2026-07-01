package com.kuilunfuzhe.monvhua.features.binding;

import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import com.kuilunfuzhe.monvhua.network.binding.BindingPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerBindingClient {
    private static final Map<UUID, BindingView> BINDINGS_BY_TARGET = new ConcurrentHashMap<>();

    private PlayerBindingClient() {
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(BindingPackets.StateS2C.ID, (packet, context) ->
                context.client().execute(() -> receiveState(packet)));

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (trySendStruggle(player, hand)) {
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (trySendStruggle(player, hand)) {
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }

    public static boolean handleScroll(double yOffset) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null || !client.player.getMainHandStack().isOf(Items.LEAD) || !isHoldingBinding(client.player)) {
            return false;
        }
        int direction = yOffset > 0.0D ? 1 : -1;
        SafeClientNetworking.send(new BindingPackets.AdjustLengthC2S(direction));
        return true;
    }

    public static boolean isBoundTarget(ClientPlayerEntity player) {
        return player != null && BINDINGS_BY_TARGET.containsKey(player.getUuid());
    }

    public static boolean isHoldingBinding(ClientPlayerEntity player) {
        if (player == null) {
            return false;
        }
        UUID playerUuid = player.getUuid();
        for (BindingView view : BINDINGS_BY_TARGET.values()) {
            if (view.holderUuid.equals(playerUuid)) {
                return true;
            }
        }
        return false;
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || BINDINGS_BY_TARGET.isEmpty() || context.consumers() == null || context.matrixStack() == null) {
            return;
        }
        Vec3d camera = context.camera().getPos();
        Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
        VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getLeash());
        for (BindingView view : BINDINGS_BY_TARGET.values()) {
            Entity holder = entityByView(client, view.holderUuid, view.holderId);
            Entity target = entityByView(client, view.targetUuid, view.targetId);
            if (holder == null || target == null || holder.getWorld() != target.getWorld()) {
                continue;
            }
            drawBinding(vertices, matrix, camera, holder, target);
        }
    }

    private static void receiveState(BindingPackets.StateS2C packet) {
        if (!packet.active()) {
            BINDINGS_BY_TARGET.remove(packet.targetUuid());
            return;
        }
        BINDINGS_BY_TARGET.put(packet.targetUuid(),
                new BindingView(packet.holderUuid(), packet.targetUuid(), packet.holderId(), packet.targetId(), packet.length()));
    }

    private static boolean trySendStruggle(net.minecraft.entity.player.PlayerEntity player, Hand hand) {
        if (!(player instanceof ClientPlayerEntity clientPlayer) || hand != Hand.MAIN_HAND) {
            return false;
        }
        if (!isBoundTarget(clientPlayer) || !clientPlayer.isSneaking()) {
            return false;
        }
        if (CarryPoseClientState.isAnyCarrier(clientPlayer.getId()) || CarryPoseClientState.isCarried(clientPlayer.getId())) {
            return false;
        }
        if (!clientPlayer.getMainHandStack().isEmpty() || !clientPlayer.getOffHandStack().isEmpty()) {
            return false;
        }
        SafeClientNetworking.send(new BindingPackets.StruggleC2S());
        return true;
    }

    private static Entity entityByView(MinecraftClient client, UUID uuid, int entityId) {
        Entity byId = entityId >= 0 ? client.world.getEntityById(entityId) : null;
        if (byId != null && byId.getUuid().equals(uuid)) {
            return byId;
        }
        for (Entity entity : client.world.getEntities()) {
            if (entity.getUuid().equals(uuid)) {
                return entity;
            }
        }
        return null;
    }

    private static void drawBinding(VertexConsumer vertices, Matrix4f matrix, Vec3d camera, Entity holder, Entity target) {
        float tickProgress = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
        Vec3d start = holder.getLerpedPos(tickProgress)
                .add(0.0D, holder.getStandingEyeHeight() * 0.72D, 0.0D);
        Vec3d end = target.getLerpedPos(tickProgress)
                .add(0.0D, target.getStandingEyeHeight() * 0.62D, 0.0D);
        Vec3d delta = end.subtract(start);
        if (delta.lengthSquared() < 1.0E-6D) {
            return;
        }

        float dx = (float) delta.x;
        float dy = (float) delta.y;
        float dz = (float) delta.z;
        float horizontalInvLength = (float) (1.0D / Math.sqrt(dx * dx + dz * dz));
        if (!Float.isFinite(horizontalInvLength)) {
            horizontalInvLength = 0.0F;
        }
        float sideX = dz * horizontalInvLength * 0.025F;
        float sideZ = dx * horizontalInvLength * 0.025F;
        Vec3d origin = start.subtract(camera);
        int startBlockLight = lightLevel(holder.getWorld(), LightType.BLOCK, start);
        int endBlockLight = lightLevel(target.getWorld(), LightType.BLOCK, end);
        int startSkyLight = lightLevel(holder.getWorld(), LightType.SKY, start);
        int endSkyLight = lightLevel(target.getWorld(), LightType.SKY, end);
        int segments = 24;
        for (int i = 0; i <= segments; i++) {
            emitVanillaLeashSegment(vertices, matrix, origin, dx, dy, dz, 0.05F, 0.05F, sideX, sideZ, i, false,
                    startBlockLight, endBlockLight, startSkyLight, endSkyLight);
        }
        for (int i = segments; i >= 0; i--) {
            emitVanillaLeashSegment(vertices, matrix, origin, dx, dy, dz, 0.05F, 0.0F, sideX, sideZ, i, true,
                    startBlockLight, endBlockLight, startSkyLight, endSkyLight);
        }
    }

    private static void emitVanillaLeashSegment(VertexConsumer vertices, Matrix4f matrix, Vec3d origin,
                                                float dx, float dy, float dz, float height, float yOffset,
                                                float sideX, float sideZ, int index, boolean reverse,
                                                int startBlockLight, int endBlockLight,
                                                int startSkyLight, int endSkyLight) {
        float t = index / 24.0F;
        int blockLight = Math.round(startBlockLight + (endBlockLight - startBlockLight) * t);
        int skyLight = Math.round(startSkyLight + (endSkyLight - startSkyLight) * t);
        int light = LightmapTextureManager.pack(blockLight, skyLight);
        float shade = (index % 2 == (reverse ? 1 : 0)) ? 0.7F : 1.0F;
        float red = 0.5F * shade;
        float green = 0.4F * shade;
        float blue = 0.3F * shade;
        float x = (float) origin.x + dx * t;
        float y = (float) origin.y + dy * t;
        float z = (float) origin.z + dz * t;
        emitVertex(vertices, matrix, x - sideX, y + yOffset, z + sideZ, red, green, blue, light);
        emitVertex(vertices, matrix, x + sideX, y + height - yOffset, z - sideZ, red, green, blue, light);
    }

    private static void emitVertex(VertexConsumer vertices, Matrix4f matrix, float x, float y, float z,
                                   float red, float green, float blue, int light) {
        vertices.vertex(matrix, x, y, z)
                .color(red, green, blue, 1.0F)
                .light(light);
    }

    private static int lightLevel(World world, LightType type, Vec3d pos) {
        if (world == null) {
            return type == LightType.BLOCK ? 15 : 0;
        }
        return world.getLightLevel(type, BlockPos.ofFloored(pos));
    }

    private record BindingView(UUID holderUuid, UUID targetUuid, int holderId, int targetId, double length) {
    }
}

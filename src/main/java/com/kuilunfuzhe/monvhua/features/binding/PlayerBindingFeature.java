package com.kuilunfuzhe.monvhua.features.binding;

import com.kuilunfuzhe.monvhua.features.carryentity.CarryManager;
import com.kuilunfuzhe.monvhua.network.binding.BindingPackets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerBindingFeature {
    public static final double DEFAULT_LENGTH = 6.0D;
    public static final double MIN_LENGTH = 2.0D;
    public static final double MAX_LENGTH = 16.0D;
    public static final double LENGTH_STEP = 0.5D;

    private static final int STRUGGLE_THRESHOLD = 3;
    private static final double SOFT_PULL_MARGIN = 0.15D;
    private static final double HARD_CLAMP_MARGIN = 3.0D;
    private static final double MAX_PULL_SPEED = 0.85D;
    private static final Map<UUID, BindingState> BINDINGS_BY_TARGET = new ConcurrentHashMap<>();

    private PlayerBindingFeature() {
    }

    public static void initialize() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity holder) || !(entity instanceof ServerPlayerEntity target)) {
                return ActionResult.PASS;
            }
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(Items.LEAD) || holder == target) {
                return ActionResult.PASS;
            }
            bind(holder, target, stack);
            return ActionResult.SUCCESS_SERVER;
        });

        ServerTickEvents.END_SERVER_TICK.register(PlayerBindingFeature::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncAllTo(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cleanupForDisconnect(handler.getPlayer()));

        ServerPlayNetworking.registerGlobalReceiver(BindingPackets.AdjustLengthC2S.ID, (packet, context) ->
                context.server().execute(() -> adjustLength(context.player(), packet.direction())));
        ServerPlayNetworking.registerGlobalReceiver(BindingPackets.StruggleC2S.ID, (packet, context) ->
                context.server().execute(() -> struggle(context.player())));
    }

    public static boolean isBoundTarget(ServerPlayerEntity player) {
        return BINDINGS_BY_TARGET.containsKey(player.getUuid());
    }

    private static void bind(ServerPlayerEntity holder, ServerPlayerEntity target, ItemStack leadStack) {
        if (CarryManager.CARRIED_BY.containsKey(target) || CarryManager.CARRIED_ENTITIES.containsKey(target)) {
            holder.sendMessage(Text.literal("§c目标正在抱人状态中，无法拴住"), true);
            return;
        }
        BindingState previous = BINDINGS_BY_TARGET.get(target.getUuid());
        BindingState state = new BindingState(holder.getUuid(), target.getUuid(), DEFAULT_LENGTH);
        BINDINGS_BY_TARGET.put(target.getUuid(), state);
        state.struggleCount = 0;
        if (previous == null && !holder.isCreative()) {
            leadStack.decrement(1);
        }
        syncToAll(holder.getServer(), holder, target, state, true);
        holder.sendMessage(Text.literal("§b已用拴绳拴住 " + target.getName().getString()), true);
        target.sendMessage(Text.literal("§e你被 " + holder.getName().getString() + " 拴住了。潜行并空手右键 3 次可挣脱。"), false);
    }

    private static void adjustLength(ServerPlayerEntity holder, int direction) {
        if (direction == 0 || !holder.getMainHandStack().isOf(Items.LEAD)) {
            return;
        }
        int adjusted = 0;
        double lastLength = DEFAULT_LENGTH;
        for (BindingState state : BINDINGS_BY_TARGET.values()) {
            if (!state.holderUuid.equals(holder.getUuid())) {
                continue;
            }
            ServerPlayerEntity target = holder.getServer().getPlayerManager().getPlayer(state.targetUuid);
            if (target == null) {
                removeBinding(holder.getServer(), state.targetUuid);
                continue;
            }
            state.length = Math.clamp(state.length + Math.signum(direction) * LENGTH_STEP, MIN_LENGTH, MAX_LENGTH);
            state.struggleCount = 0;
            lastLength = state.length;
            adjusted++;
            syncToAll(holder.getServer(), holder, target, state, true);
        }
        if (adjusted > 0) {
            holder.sendMessage(Text.literal("§b拴绳长度: " + formatLength(lastLength) + " 格"), true);
        }
    }

    private static void struggle(ServerPlayerEntity target) {
        BindingState state = BINDINGS_BY_TARGET.get(target.getUuid());
        if (state == null) {
            return;
        }
        if (!target.isSneaking() || !target.getMainHandStack().isEmpty() || !target.getOffHandStack().isEmpty()) {
            return;
        }
        ServerPlayerEntity holder = target.getServer().getPlayerManager().getPlayer(state.holderUuid);
        if (holder == null) {
            removeBinding(target.getServer(), target.getUuid());
            return;
        }
        state.struggleCount++;
        if (state.struggleCount >= STRUGGLE_THRESHOLD) {
            removeBinding(target.getServer(), target.getUuid());
            holder.sendMessage(Text.literal("§c" + target.getName().getString() + " 挣脱了拴绳"), false);
            target.sendMessage(Text.literal("§a你挣脱了拴绳"), false);
        } else {
            target.sendMessage(Text.literal("§e挣脱进度: " + state.struggleCount + "/" + STRUGGLE_THRESHOLD), true);
        }
    }

    private static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, BindingState>> iterator = BINDINGS_BY_TARGET.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BindingState> entry = iterator.next();
            BindingState state = entry.getValue();
            ServerPlayerEntity holder = server.getPlayerManager().getPlayer(state.holderUuid);
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(state.targetUuid);
            if (holder == null || target == null || !holder.isAlive() || !target.isAlive()
                    || holder.getWorld() != target.getWorld()
                    || !isHoldingLead(holder)
                    || CarryManager.CARRIED_BY.containsKey(target)
                    || CarryManager.CARRIED_ENTITIES.containsKey(target)) {
                iterator.remove();
                sendRemove(server, state);
                continue;
            }
            enforceDistance(holder, target, state.length);
        }
    }

    private static boolean isHoldingLead(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(Items.LEAD) || player.getOffHandStack().isOf(Items.LEAD);
    }

    private static void enforceDistance(ServerPlayerEntity holder, ServerPlayerEntity target, double length) {
        Vec3d holderAnchor = holder.getPos().add(0.0D, holder.getStandingEyeHeight() * 0.75D, 0.0D);
        Vec3d targetAnchor = target.getPos().add(0.0D, target.getStandingEyeHeight() * 0.65D, 0.0D);
        Vec3d offset = targetAnchor.subtract(holderAnchor);
        double distance = offset.length();
        if (distance <= length + SOFT_PULL_MARGIN || distance < 1.0E-4D) {
            return;
        }

        Vec3d directionToHolder = offset.normalize().negate();
        double excess = distance - length;
        Vec3d velocity = target.getVelocity().add(directionToHolder.multiply(Math.min(MAX_PULL_SPEED, excess * 0.22D)));
        target.setVelocity(velocity.x, Math.max(velocity.y, -0.35D), velocity.z);
        target.velocityModified = true;
        target.fallDistance = 0.0F;

        if (excess > HARD_CLAMP_MARGIN) {
            Vec3d clamped = holderAnchor.add(offset.normalize().multiply(length))
                    .subtract(0.0D, target.getStandingEyeHeight() * 0.65D, 0.0D);
            target.requestTeleport(clamped.x, clamped.y, clamped.z);
        }
    }

    private static void cleanupForDisconnect(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        removeBinding(server, player.getUuid());
        for (BindingState state : BINDINGS_BY_TARGET.values()) {
            if (state.holderUuid.equals(player.getUuid())) {
                removeBinding(server, state.targetUuid);
            }
        }
    }

    private static void removeBinding(MinecraftServer server, UUID targetUuid) {
        BindingState removed = BINDINGS_BY_TARGET.remove(targetUuid);
        if (removed != null) {
            sendRemove(server, removed);
        }
    }

    private static void syncAllTo(ServerPlayerEntity receiver) {
        MinecraftServer server = receiver.getServer();
        if (server == null) {
            return;
        }
        for (BindingState state : BINDINGS_BY_TARGET.values()) {
            ServerPlayerEntity holder = server.getPlayerManager().getPlayer(state.holderUuid);
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(state.targetUuid);
            if (holder != null && target != null) {
                ServerPlayNetworking.send(receiver, new BindingPackets.StateS2C(
                        state.holderUuid,
                        state.targetUuid,
                        holder.getId(),
                        target.getId(),
                        state.length,
                        true));
            }
        }
    }

    private static void syncToAll(MinecraftServer server, ServerPlayerEntity holder, ServerPlayerEntity target, BindingState state, boolean active) {
        if (server == null) {
            return;
        }
        BindingPackets.StateS2C packet = new BindingPackets.StateS2C(
                state.holderUuid,
                state.targetUuid,
                holder.getId(),
                target.getId(),
                state.length,
                active);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    private static void sendRemove(MinecraftServer server, BindingState state) {
        if (server == null) {
            return;
        }
        ServerPlayerEntity holder = server.getPlayerManager().getPlayer(state.holderUuid);
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(state.targetUuid);
        int holderId = holder != null ? holder.getId() : -1;
        int targetId = target != null ? target.getId() : -1;
        BindingPackets.StateS2C packet = new BindingPackets.StateS2C(
                state.holderUuid,
                state.targetUuid,
                holderId,
                targetId,
                state.length,
                false);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    private static String formatLength(double length) {
        return String.format(java.util.Locale.ROOT, "%.1f", length);
    }

    private static final class BindingState {
        private final UUID holderUuid;
        private final UUID targetUuid;
        private double length;
        private int struggleCount;

        private BindingState(UUID holderUuid, UUID targetUuid, double length) {
            this.holderUuid = holderUuid;
            this.targetUuid = targetUuid;
            this.length = length;
        }
    }
}
